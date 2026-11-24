#!/bin/bash
# scripts/transition.sh
#
# The pipeline's single registry of persona hand-offs (ADR-005). Everything
# that decides WHETHER, WHO, WITH WHAT PROMPT and WITH WHAT EXPECTED RESULT a
# persona is launched lives here, so workflows stay thin and every rule runs
# from main.
#
# Commands:
#   route <event>
#       Gate for an event adapter workflow. Reads the event from env (below),
#       decides which transition (if any) the event hands off to, checks live
#       state and round limits, and writes the decision to GITHUB_OUTPUT:
#         run (true|false), transition, persona, story_id, branch, pr_number,
#         round, mode, ref_id, ref_time, stage, resume, prompt
#       A round limit exceeded, or a story PR closed without merging,
#       escalates to Brian and exits 1 (red run).
#       Events: feature-push, pr-opened, test-verdict, review-submitted,
#               main-or-merge, manual
#   recheck <transition>
#       Run by persona-run.yml just before launching: is the hand-off still
#       needed? A run can wait hours behind another persona on the single
#       self-hosted runner, and the work may have been done meanwhile.
#       Exit 0 needed, 1 already handled, 2 error.
#   verify <transition>
#       Run by persona-run.yml after the persona exits: did it produce the
#       expected result on GitHub (ADR-003)? Prints one reason line.
#       Exit 0 found, 1 missing, 2 error.
#
# Transitions:
#   test-kickoff          queue -> Test Phase 1 (branch, plan, contracts, failing tests)
#   dev-design-review     Test plan/revision -> Dev design review
#   test-design-revision  Dev feedback -> Test revision
#   dev-implement         Dev agreement, or Test's impl-feedback response -> Dev
#   test-impl-feedback    Dev reports defective tests -> Test fixes or explains
#   test-final-review     Dev opened PR -> Test final verification + verdict
#   test-reverify         Dev pushed a fix after CHANGES_REQUESTED -> Test re-verifies
#   dev-fix               Test CHANGES_REQUESTED verdict -> Dev fixes
#   test-brian-review     Brian requested changes on a story PR -> Test Phase 4
#   po-spec-revision      Brian requested changes on a specs PR -> PO revises
#   po-clarify            Test or Dev asks PO about acceptance criteria -> PO answers
#   resume-after-po       PO answered -> the persona that asked resumes
#
# Env for route (set by the adapter workflow; unused ones may be empty):
#   GH_TOKEN (bot), QUEUE_GH_TOKEN (queue manager), GH_REPO, RUN_URL
#   ACTOR, PUSH_SHA, PUSH_BEFORE, PUSH_FORCED, BRANCH, PR_NUMBER, PR_MERGED,
#   COMMENT_ID, COMMENT_USER, COMMENT_CREATED_AT, COMMENT_BODY,
#   REVIEWER, REVIEW_STATE, REVIEW_AT, PULL_EVENT (push|pull_request),
#   INPUT_TRANSITION, INPUT_STORY_ID, INPUT_BRANCH, INPUT_PR_NUMBER,
#   INPUT_ROUND, INPUT_MODE (manual dispatch)
# Env for recheck/verify (set by persona-run.yml from the route outputs):
#   GH_TOKEN, GH_REPO, PERSONA, STORY_ID, BRANCH, PR_NUMBER, ROUND, MODE,
#   REF_ID, REF_TIME, STARTED_AT, HEAD_SHA_BEFORE, PR_OPEN_AT_START

set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=pipeline-config.sh
source "$SCRIPT_DIR/pipeline-config.sh"
CHECK="$SCRIPT_DIR/persona-outcome-check.sh"
DESIGN_DIR="docs/specs/design"

if [ -z "$GH_REPO" ]; then
  echo "ERROR: GH_REPO must be set (owner/repo)" >&2
  exit 2
fi

# ===========================================================================
# Helpers
# ===========================================================================

now_minus_60s() { date -u -d '-60 seconds' +%Y-%m-%dT%H:%M:%SZ; }

# Writes one route output (multi-line safe) to GITHUB_OUTPUT and stdout
emit() {
  local key="$1" value="$2" delimiter
  delimiter="EOF_$(date +%s%N)_$RANDOM"
  if [ -n "$GITHUB_OUTPUT" ]; then
    { echo "$key<<$delimiter"; echo "$value"; echo "$delimiter"; } >> "$GITHUB_OUTPUT"
  fi
  if [[ "$value" == *$'\n'* ]]; then
    echo "$key: (multi-line, $(echo "$value" | wc -l) lines)"
  else
    echo "$key: $value"
  fi
}

skip() {
  echo "SKIP: $1"
  emit run false
  exit 0
}

persona_for_author() {
  case "$1" in
    "$PO_GIT_AUTHOR") echo po ;;
    "$DEV_GIT_AUTHOR") echo dev ;;
    "$TEST_GIT_AUTHOR") echo test ;;
    *) echo "" ;;
  esac
}

author_for_persona() {
  case "$1" in
    po) echo "$PO_GIT_AUTHOR" ;;
    dev) echo "$DEV_GIT_AUTHOR" ;;
    test) echo "$TEST_GIT_AUTHOR" ;;
  esac
}

persona_title() {
  case "$1" in po) echo PO ;; dev) echo Dev ;; test) echo Test ;; esac
}

story_from_branch() { echo "$1" | grep -oE 'story-[0-9]+' | head -1; }

# Prints "<author-name><TAB><committer-date>" for a commit SHA or branch
commit_info() {
  local sha="$1"
  if [[ ! "$sha" =~ ^[0-9a-f]{40}$ ]]; then
    sha=$("$CHECK" head-sha "$1") || return 2
  fi
  gh api "repos/$GH_REPO/commits/$sha" --jq '"\(.commit.author.name)\t\(.commit.committer.date)"'
}

# Prints the design file names on a branch (live, from the API)
design_files() {
  local listing
  if ! listing=$(gh api "repos/$GH_REPO/contents/$DESIGN_DIR?ref=$1" --jq '.[].name' 2>&1); then
    if echo "$listing" | grep -q "HTTP 404"; then
      return 0
    fi
    echo "ERROR: could not list $DESIGN_DIR on $1: $listing" >&2
    return 2
  fi
  echo "$listing"
}

# Highest round number of <story>-<kind>-rN.md in a design file listing
highest_round() {
  local highest
  highest=$(echo "$1" | grep -E "^$2-$3-r[0-9]+\.md$" | sed -E 's/.*-r([0-9]+)\.md$/\1/' | sort -n | tail -1)
  echo "${highest:-0}"
}

has_file() { echo "$1" | grep -qx "$2"; }

# 0 when a PO question on the branch is still waiting for its answer
po_question_open() {
  local listing="$1" story="$2" asked answered
  asked=$(highest_round "$listing" "$story" po-question)
  answered=$(highest_round "$listing" "$story" po-answer)
  [ "$asked" -gt "$answered" ]
}

# Escalates to Brian and ends the gate red
escalate_and_stop() {
  local stage="$1" reason="$2" resume="$3"
  STAGE="$stage" REASON="$reason" RESUME="$resume" STORY_ID="${STORY_ID:-pipeline}" \
    BRANCH="$BRANCH" PR_NUMBER="$PR_NUMBER" RUN_URL="$RUN_URL" \
    "$SCRIPT_DIR/escalate-to-brian.sh" || echo "ERROR: escalation failed too" >&2
  emit run false
  exit 1
}

manual_resume() {
  local transition="$1"
  echo "Fix the cause, then in Actions run 'Pipeline - Manual Dispatch' with transition=$transition, story_id=$STORY_ID${BRANCH:+, branch=$BRANCH}${PR_NUMBER:+, pr_number=$PR_NUMBER}."
}

stage_for() {
  case "$1" in
    test-kickoff) echo "Test story kickoff stalled" ;;
    dev-design-review) echo "Dev design review stalled" ;;
    test-design-revision) echo "Test design revision stalled" ;;
    dev-implement) echo "Dev implementation stalled" ;;
    test-impl-feedback) echo "Test response to implementation feedback stalled" ;;
    test-final-review) echo "Test final review stalled" ;;
    test-reverify) echo "Test re-verification stalled" ;;
    dev-fix) echo "Dev fix for Test verdict stalled" ;;
    test-brian-review) echo "Test failing tests for Brian's review stalled" ;;
    po-spec-revision) echo "PO spec revision stalled" ;;
    po-clarify) echo "PO clarification needs Brian or stalled" ;;
    resume-after-po) echo "Resume after PO answer stalled" ;;
  esac
}

persona_for_transition() {
  case "$1" in
    test-*) echo test ;;
    dev-*) echo dev ;;
    po-*) echo po ;;
    resume-after-po) echo "" ;;
  esac
}

# ===========================================================================
# State checks (shared by route and recheck). Each returns
# 0 needed, 1 already handled (prints why), 2 error. Each may set ROUND, MODE,
# PR_NUMBER, REF_ID, REF_TIME, PERSONA.
# ===========================================================================

needed_dev_design_review() {
  local listing
  listing=$(design_files "$BRANCH") || return 2
  [ -z "$ROUND" ] && ROUND=$(( $(highest_round "$listing" "$STORY_ID" dev-feedback) + 1 ))
  has_file "$listing" "$STORY_ID-agreed.md" && { echo "$STORY_ID-agreed.md already exists - design already agreed"; return 1; }
  has_file "$listing" "$STORY_ID-dev-feedback-r$ROUND.md" && { echo "Dev feedback r$ROUND already exists"; return 1; }
  po_question_open "$listing" "$STORY_ID" && { echo "a PO question is still open - waiting for PO"; return 1; }
  if [ "$ROUND" -gt 1 ] && ! has_file "$listing" "$STORY_ID-test-revision-r$((ROUND - 1)).md"; then
    echo "Test has not answered Dev feedback r$((ROUND - 1)) yet"
    return 1
  fi
  return 0
}

needed_test_design_revision() {
  local listing
  listing=$(design_files "$BRANCH") || return 2
  ROUND=$(highest_round "$listing" "$STORY_ID" dev-feedback)
  [ "$ROUND" -lt 1 ] && { echo "no Dev feedback file on $BRANCH"; return 1; }
  has_file "$listing" "$STORY_ID-agreed.md" && { echo "design already agreed"; return 1; }
  has_file "$listing" "$STORY_ID-test-revision-r$ROUND.md" && { echo "Test revision r$ROUND already exists"; return 1; }
  po_question_open "$listing" "$STORY_ID" && { echo "a PO question is still open - waiting for PO"; return 1; }
  return 0
}

needed_dev_implement() {
  local listing head_author
  PR_NUMBER=$("$CHECK" pr-number "$BRANCH") || return 2
  listing=$(design_files "$BRANCH") || return 2
  po_question_open "$listing" "$STORY_ID" && { echo "a PO question is still open - waiting for PO"; return 1; }
  if [ "$MODE" == "resume" ]; then
    ROUND=$(highest_round "$listing" "$STORY_ID" impl-feedback-response)
    head_author=$(commit_info "$BRANCH" | cut -f1) || return 2
    if [ "$head_author" != "$TEST_GIT_AUTHOR" ] && [ "$EVENT_IS_MANUAL" != "true" ]; then
      echo "Dev has already acted since Test's response r$ROUND (head commit by $head_author)"
      return 1
    fi
    return 0
  fi
  # start: implementation is done once the PR is open (re-dispatch is manual)
  if [ -n "$PR_NUMBER" ] && [ "$EVENT_IS_MANUAL" != "true" ]; then
    echo "PR #$PR_NUMBER is already open - implementation already done"
    return 1
  fi
  has_file "$listing" "$STORY_ID-agreed.md" || { echo "no $STORY_ID-agreed.md on $BRANCH"; return 1; }
  return 0
}

needed_test_impl_feedback() {
  local listing
  listing=$(design_files "$BRANCH") || return 2
  ROUND=$(highest_round "$listing" "$STORY_ID" impl-feedback)
  [ "$ROUND" -lt 1 ] && { echo "no implementation feedback file on $BRANCH"; return 1; }
  has_file "$listing" "$STORY_ID-impl-feedback-response-r$ROUND.md" && { echo "Test response r$ROUND already exists"; return 1; }
  return 0
}

# Test re-verifies only a Dev push that answers a CHANGES_REQUESTED verdict
# and has not been verified yet (F1: never a second wake after PR open)
needed_test_reverify() {
  local verdict head head_author head_date
  [ -z "$PR_NUMBER" ] && PR_NUMBER=$("$CHECK" pr-number "$BRANCH")
  [ -z "$PR_NUMBER" ] && { echo "no open PR for $BRANCH"; return 1; }
  verdict=$("$CHECK" latest-verdict "$PR_NUMBER" "$BOT_LOGIN") || return 2
  [ "$EVENT_IS_MANUAL" == "true" ] && return 0
  [ -z "$verdict" ] && { echo "Test has not posted its first verdict yet - the final review covers this push"; return 1; }
  [[ "$verdict" == *" CHANGES_REQUESTED" ]] || { echo "latest verdict is APPROVED - nothing to re-verify"; return 1; }
  head=$(commit_info "$BRANCH") || return 2
  head_author=$(echo "$head" | cut -f1)
  head_date=$(echo "$head" | cut -f2)
  [ "$head_author" == "$DEV_GIT_AUTHOR" ] || { echo "head commit is by $head_author, not Dev"; return 1; }
  local verdict_time
  verdict_time=$(echo "$verdict" | cut -d' ' -f2)
  [[ "$head_date" > "$verdict_time" ]] || { echo "head commit predates the latest verdict - already verified"; return 1; }
  return 0
}

needed_dev_fix() {
  local verdict head head_author head_date
  [ -z "$PR_NUMBER" ] && PR_NUMBER=$("$CHECK" pr-number "$BRANCH")
  [ -z "$PR_NUMBER" ] && { echo "no open PR for $BRANCH"; return 1; }
  [ "$EVENT_IS_MANUAL" == "true" ] && return 0
  verdict=$("$CHECK" latest-verdict "$PR_NUMBER" "$BOT_LOGIN") || return 2
  [ "$(echo "$verdict" | cut -d' ' -f1)" == "$REF_ID" ] || { echo "a newer verdict exists - this one is stale"; return 1; }
  head=$(commit_info "$BRANCH") || return 2
  head_author=$(echo "$head" | cut -f1)
  head_date=$(echo "$head" | cut -f2)
  if [ "$head_author" == "$DEV_GIT_AUTHOR" ] && [[ "$head_date" > "$REF_TIME" ]]; then
    echo "Dev already pushed after this verdict"
    return 1
  fi
  return 0
}

# 0 when no verdict of <kind> was posted since REF_TIME, 1 when one was, 2 error
no_verdict_since_ref() {
  "$CHECK" verdict-since "$PR_NUMBER" "$REF_TIME" "$BOT_LOGIN" "$1" > /dev/null
  case $? in 0) return 1 ;; 1) return 0 ;; *) return 2 ;; esac
}

needed_test_final_review() {
  [ -z "$PR_NUMBER" ] && PR_NUMBER=$("$CHECK" pr-number "$BRANCH")
  [ -z "$PR_NUMBER" ] && { echo "no open PR for $BRANCH"; return 1; }
  [ "$EVENT_IS_MANUAL" == "true" ] && return 0
  no_verdict_since_ref ANY
  case $? in 1) echo "Test already posted a verdict since the PR was opened"; return 1 ;; 2) return 2 ;; esac
  return 0
}

needed_test_brian_review() {
  [ -z "$PR_NUMBER" ] && PR_NUMBER=$("$CHECK" pr-number "$BRANCH")
  [ -z "$PR_NUMBER" ] && { echo "no open PR for $BRANCH"; return 1; }
  [ "$EVENT_IS_MANUAL" == "true" ] && return 0
  no_verdict_since_ref CHANGES_REQUESTED
  case $? in 1) echo "Test already answered this review"; return 1 ;; 2) return 2 ;; esac
  return 0
}

needed_po_spec_revision() {
  local head head_author head_date pr_state
  pr_state=$(gh pr view "$PR_NUMBER" --repo "$GH_REPO" --json state --jq .state) || return 2
  [ "$pr_state" == "OPEN" ] || { echo "PR #$PR_NUMBER is $pr_state"; return 1; }
  [ "$EVENT_IS_MANUAL" == "true" ] && return 0
  head=$(commit_info "$BRANCH") || return 2
  head_author=$(echo "$head" | cut -f1)
  head_date=$(echo "$head" | cut -f2)
  if [ "$head_author" == "$PO_GIT_AUTHOR" ] && [[ "$head_date" > "$REF_TIME" ]]; then
    echo "PO already pushed after this review"
    return 1
  fi
  return 0
}

needed_po_clarify() {
  local listing
  listing=$(design_files "$BRANCH") || return 2
  ROUND=$(highest_round "$listing" "$STORY_ID" po-question)
  [ "$ROUND" -lt 1 ] && { echo "no PO question on $BRANCH"; return 1; }
  has_file "$listing" "$STORY_ID-po-answer-r$ROUND.md" && { echo "PO answer r$ROUND already exists"; return 1; }
  return 0
}

needed_resume_after_po() {
  local listing head_author asker_author
  listing=$(design_files "$BRANCH") || return 2
  ROUND=$(highest_round "$listing" "$STORY_ID" po-answer)
  [ "$ROUND" -lt 1 ] && { echo "no PO answer on $BRANCH"; return 1; }
  asker_author=$(gh api "repos/$GH_REPO/commits?sha=$BRANCH&path=$DESIGN_DIR/$STORY_ID-po-question-r$ROUND.md&per_page=100" \
    --jq '.[-1].commit.author.name') || return 2
  PERSONA=$(persona_for_author "$asker_author")
  [ -z "$PERSONA" ] || [ "$PERSONA" == "po" ] && { echo "question r$ROUND was not asked by Dev or Test (author: $asker_author)"; return 1; }
  PR_NUMBER=$("$CHECK" pr-number "$BRANCH") || return 2
  [ "$EVENT_IS_MANUAL" == "true" ] && return 0
  head_author=$(commit_info "$BRANCH" | cut -f1) || return 2
  [ "$head_author" == "$PO_GIT_AUTHOR" ] || { echo "$(persona_title "$PERSONA") already acted since the answer (head commit by $head_author)"; return 1; }
  return 0
}

# Duplicate kickoffs (two queue events racing) are stopped here: once the
# story branch exists, a kickoff that did not know about it is stale.
needed_test_kickoff() {
  local existing
  [ -n "$BRANCH" ] && return 0
  existing=$(gh api "repos/$GH_REPO/git/matching-refs/heads/feature/${STORY_ID}-" --jq '.[0].ref // ""') || return 2
  [ -n "$existing" ] && { echo "${existing#refs/heads/} already exists - $STORY_ID was already kicked off"; return 1; }
  return 0
}

run_needed() {
  local transition="$1" function_name
  function_name="needed_${transition//-/_}"
  "$function_name"
}

# ===========================================================================
# Prompts
# ===========================================================================

prompt_for() {
  local transition="$1" pr_text=""
  [ -n "$PR_NUMBER" ] && pr_text=" (PR #$PR_NUMBER)"
  case "$transition" in
    test-kickoff)
      echo "A new user story is ready to begin. Read the story file $STORY_FILE and execute Phase 1 of your workflow per your CLAUDE.md: create the feature branch feature/$STORY_ID-short-kebab-case-description, write the test plan and API contracts, write failing automated tests, commit all artifacts to the feature branch, and push the branch to origin. If an acceptance criterion is ambiguous, contradictory, or conflicts with existing behavior, ask PO instead of guessing (see Asking PO in your CLAUDE.md)."
      ;;
    dev-design-review)
      echo "Test has committed a design artifact to feature branch $BRANCH for $STORY_ID. This is design review round $ROUND of $DESIGN_ROUND_LIMIT. Pull the branch, then read docs/specs/design/$STORY_ID-test-plan.md, $STORY_ID-api-contracts.md, every revision file and every PO answer so far. Then read every test file Test has committed on this branch and run the Test Code Conformance check from your CLAUDE.md: every constructor, method signature, endpoint, DTO and status code the tests use must match the current contracts. Execute your design review phase per your CLAUDE.md: commit $STORY_ID-agreed.md only if the design is sound AND the test code conforms to the contracts (fill in the Test Code Conformance section); otherwise commit $STORY_ID-dev-feedback-r$ROUND.md with specific actionable concerns, listing each test/contract mismatch as its own concern. If your concern is with an acceptance criterion itself, ask PO instead (see Asking PO in your CLAUDE.md). Commit and push to origin/$BRANCH."
      ;;
    test-design-revision)
      echo "Dev has committed feedback file docs/specs/design/$STORY_ID-dev-feedback-r$ROUND.md on branch $BRANCH. Pull the branch, read Dev's concerns carefully and execute your revision phase per your CLAUDE.md: revise the test plan and contracts where Dev's feedback is technically valid without dropping AC coverage. Then reconcile your test code: every contract change in this round (constructors, method signatures, endpoints, DTOs, status codes, error shapes) must be applied to every existing test that uses it, in the same commit. Search the test sources for each changed name to find them all. Fill in the Test Code Reconciliation table in $STORY_ID-test-revision-r$ROUND.md, then commit the revision file, the updated test plan and contracts, and all changed test files together, and push to origin/$BRANCH. If Dev's feedback conflicts with an acceptance criterion, ask PO instead of deciding it yourself (see Asking PO in your CLAUDE.md)."
      ;;
    dev-implement)
      if [ "$MODE" == "resume" ] && [ -n "$PR_NUMBER" ]; then
        echo "Test has answered your implementation feedback in docs/specs/design/$STORY_ID-impl-feedback-response-r$ROUND.md on $BRANCH$pr_text. Resume Phase 3 (PR fix) per your CLAUDE.md: pull the branch, read Test's response and its latest verdict comment (gh pr view $PR_NUMBER --comments), fix the implementation until all tests pass, run $VERIFY_COMMAND, commit the fix and push to origin/$BRANCH. Do not open a new PR and do not edit Test's tests. If a test is still defective, report it with a new impl-feedback file; if an acceptance criterion is the problem, ask PO."
      else
        local opening="Design agreement has been reached for $STORY_ID on branch $BRANCH (docs/specs/design/$STORY_ID-agreed.md)."
        [ "$MODE" == "resume" ] && opening="Test has answered your implementation feedback for $STORY_ID in docs/specs/design/$STORY_ID-impl-feedback-response-r$ROUND.md on branch $BRANCH."
        echo "$opening Execute your implementation phase (Phase 2) per your CLAUDE.md. Pull the branch first and read the agreed design plus every implementation feedback response and PO answer on it. If your implementation is already committed and the full suite passes, go straight to opening the PR. If one of Test's tests is defective (no correct implementation can pass it), do not edit it and do not stop: report it to Test with docs/specs/design/$STORY_ID-impl-feedback-rN.md as your CLAUDE.md describes. If an acceptance criterion is the problem, ask PO. Otherwise write failing unit tests first, implement until all tests pass including Test's failing tests, run $VERIFY_COMMAND, commit the implementation, push to origin/$BRANCH, then open a PR as the bot account targeting main."
      fi
      ;;
    test-impl-feedback)
      echo "Dev has reported defects in your tests for $STORY_ID in docs/specs/design/$STORY_ID-impl-feedback-r$ROUND.md on branch $BRANCH$pr_text. This is implementation feedback round $ROUND of $IMPL_FEEDBACK_ROUND_LIMIT. Pull the branch and execute Responding to Implementation Feedback per your CLAUDE.md: for each reported test, either fix the test so it matches the agreed contract and acceptance criteria, or explain with contract references why it is correct. Commit $STORY_ID-impl-feedback-response-r$ROUND.md together with every changed test file in one commit and push to origin/$BRANCH. Do not modify implementation code. If the dispute is really about an acceptance criterion, ask PO instead."
      ;;
    test-final-review)
      echo "Dev has opened PR #$PR_NUMBER for $STORY_ID on branch $BRANCH. Execute Phase 3 (final verification) per your CLAUDE.md: pull the branch, run the full test suite, analyze the diff for regressions, and verify every AC-N criterion is covered by a passing test. Commit and push any new regression or failing tests FIRST. Then, as your final action, post your Test Run Summary as a PR comment (gh pr comment $PR_NUMBER --body-file <file>) whose first line is exactly <!-- test-verdict: APPROVED --> or <!-- test-verdict: CHANGES_REQUESTED -->, including the Spec Changes section. Do not run gh pr review: the bot account opened this PR and cannot review it. A CHANGES_REQUESTED verdict comment automatically wakes Dev. If an acceptance criterion itself is in question, ask PO instead of posting a verdict."
      ;;
    test-reverify)
      echo "Dev has pushed a fix to $BRANCH for $STORY_ID on PR #$PR_NUMBER. Execute Phase 3 (re-verification) per your CLAUDE.md: pull the branch, run the full suite, and confirm every AC-N criterion and every test from your previous verdict now passes. Commit and push any new regression or failing tests FIRST. Then, as your final action, post an updated Test Run Summary as a PR comment (gh pr comment $PR_NUMBER --body-file <file>) whose first line is exactly <!-- test-verdict: APPROVED --> or <!-- test-verdict: CHANGES_REQUESTED -->, including the Spec Changes section. Do not run gh pr review: the bot account opened this PR and cannot review it. A CHANGES_REQUESTED verdict comment automatically wakes Dev."
      ;;
    dev-fix)
      echo "Test has posted a CHANGES_REQUESTED verdict on PR #$PR_NUMBER for $STORY_ID (branch $BRANCH). Execute Phase 3 of your workflow per your CLAUDE.md: pull the latest branch, read Test's most recent verdict comment on the PR (gh pr view $PR_NUMBER --comments), including any Non-testable review points, and any new failing tests. Fix the implementation until all tests pass, run $VERIFY_COMMAND, commit the fix, and push to origin/$BRANCH in a single push at the end. Do not open a new PR and do not edit Test's tests. If one of Test's tests is defective, report it with docs/specs/design/$STORY_ID-impl-feedback-rN.md as your CLAUDE.md describes; if the verdict hinges on an acceptance criterion, ask PO."
      ;;
    test-brian-review)
      echo "Brian has posted a Changes Requested review on PR #$PR_NUMBER for $STORY_ID on branch $BRANCH. Read Brian's review and its line comments carefully. Execute Phase 4 of your workflow per your CLAUDE.md: for every point that a test can express, write a new failing test, run $VERIFY_COMMAND to confirm it fails for the right reason, commit and push to origin/$BRANCH. For a point no test can express (naming, structure, documentation), list it under Non-testable review points with exactly what Dev must change. Then, as your final action, post a Test Run Summary as a PR comment (gh pr comment $PR_NUMBER --body-file <file>) whose first line is exactly <!-- test-verdict: CHANGES_REQUESTED -->, listing each of Brian's points and the failing test or non-testable instruction that covers it. That comment is what wakes Dev. Do not run gh pr review."
      ;;
    po-spec-revision)
      echo "Brian has posted a Changes Requested review on specs PR #$PR_NUMBER on branch $BRANCH. Read Brian's review comments carefully. Update the affected spec files on the same branch to address his feedback. Update any GitHub Issues whose story scope changed. Commit and push to origin/$BRANCH. Do not open a new PR - the existing one updates automatically."
      ;;
    po-clarify)
      echo "$ASKER_TITLE has asked you about $STORY_ID's acceptance criteria in docs/specs/design/$STORY_ID-po-question-r$ROUND.md on branch $BRANCH$pr_text. This is question $ROUND of $PO_QUESTION_LIMIT allowed for this story. Execute Answering a Clarification Question per your CLAUDE.md: check out $BRANCH, read the question, the story spec and the design files it cites, decide, and commit docs/specs/design/$STORY_ID-po-answer-r$ROUND.md. If your answer changes this story's acceptance criteria, edit docs/specs/$STORY_ID-*.md on this same branch in the same commit. If the right answer would add scope, change another story, or change prerequisites, do not commit anything: end your run with a first line starting NEEDS BRIAN: and explain. Push to origin/$BRANCH."
      ;;
    resume-after-po)
      echo "PO has answered your question about $STORY_ID in docs/specs/design/$STORY_ID-po-answer-r$ROUND.md on branch $BRANCH$pr_text (your question: $STORY_ID-po-question-r$ROUND.md). Pull the branch, read the answer and any change PO made to docs/specs/$STORY_ID-*.md, then resume the phase you were in when you asked (the Phase field of your question file) and finish it exactly as your CLAUDE.md describes, including its normal hand-off file, PR, or verdict comment."
      ;;
  esac
}

# ===========================================================================
# route: decide and emit
# ===========================================================================

# Emits the decision for $TRANSITION after its state check passed
emit_run() {
  [ -z "$PERSONA" ] && PERSONA=$(persona_for_transition "$TRANSITION")
  emit run true
  emit transition "$TRANSITION"
  emit persona "$PERSONA"
  emit story_id "$STORY_ID"
  emit branch "$BRANCH"
  emit pr_number "$PR_NUMBER"
  emit round "$ROUND"
  emit mode "$MODE"
  emit ref_id "$REF_ID"
  emit ref_time "$REF_TIME"
  emit stage "$(stage_for "$TRANSITION")"
  emit resume "$(manual_resume "$TRANSITION")"
  emit prompt "$(prompt_for "$TRANSITION")"
  exit 0
}

# Runs the state check and round limit for $TRANSITION, then emits
check_and_emit() {
  local why code why_file
  # Run in this shell (not a $(...) subshell) so the variables it sets stay
  why_file=$(mktemp)
  run_needed "$TRANSITION" > "$why_file"
  code=$?
  why=$(cat "$why_file")
  rm -f "$why_file"
  [ $code -eq 2 ] && { echo "ERROR: state check failed for $TRANSITION: $why" >&2; exit 2; }
  [ $code -eq 1 ] && skip "$TRANSITION: $why"

  if [ "$EVENT_IS_MANUAL" != "true" ]; then
    case "$TRANSITION" in
      dev-design-review)
        if [ "$ROUND" -gt "$DESIGN_ROUND_LIMIT" ]; then
          escalate_and_stop "design round limit reached" \
            "Test and Dev completed $DESIGN_ROUND_LIMIT design rounds (DESIGN_ROUND_LIMIT) without agreement. Review docs/specs/design/ on the branch." \
            "Settle the design, either by committing docs/specs/design/$STORY_ID-agreed.md yourself or by raising DESIGN_ROUND_LIMIT in .github/pipeline-config.env, then run 'Pipeline - Manual Dispatch' with transition=dev-implement (or dev-design-review), story_id=$STORY_ID, branch=$BRANCH."
        fi
        ;;
      test-impl-feedback)
        if [ "$ROUND" -gt "$IMPL_FEEDBACK_ROUND_LIMIT" ]; then
          escalate_and_stop "implementation feedback limit reached" \
            "Dev has reported defective tests $ROUND times (IMPL_FEEDBACK_ROUND_LIMIT is $IMPL_FEEDBACK_ROUND_LIMIT). Read the impl-feedback and impl-feedback-response files in docs/specs/design/ on the branch." \
            "Settle the disputed tests (fix them yourself or write the response file), then run 'Pipeline - Manual Dispatch' with transition=dev-implement, story_id=$STORY_ID, branch=$BRANCH."
        fi
        ;;
      po-clarify)
        if [ "$ROUND" -gt "$PO_QUESTION_LIMIT" ]; then
          escalate_and_stop "PO question limit reached" \
            "This story has needed $ROUND PO clarifications (PO_QUESTION_LIMIT is $PO_QUESTION_LIMIT), which usually means the story itself needs rework. Read docs/specs/design/$STORY_ID-po-question-r$ROUND.md." \
            "Answer it yourself in docs/specs/design/$STORY_ID-po-answer-r$ROUND.md on the branch, then run 'Pipeline - Manual Dispatch' with transition=resume-after-po, story_id=$STORY_ID, branch=$BRANCH. Or rework the story."
        fi
        ;;
      dev-fix)
        local last_review rounds
        last_review=$(gh api --paginate "repos/$GH_REPO/pulls/$PR_NUMBER/reviews" \
          --jq ".[] | select(.user.login == \"$OWNER_LOGIN\") | .submitted_at" | tail -1) || exit 2
        rounds=$("$CHECK" verdict-count "$PR_NUMBER" "$last_review" "$BOT_LOGIN" CHANGES_REQUESTED) || exit 2
        echo "CHANGES_REQUESTED verdicts since Brian's last review (${last_review:-none}): $rounds"
        if [ "$rounds" -gt "$DEV_FIX_ROUND_LIMIT" ]; then
          escalate_and_stop "Dev fix round limit reached" \
            "Test has requested changes $rounds times since Brian's last review (DEV_FIX_ROUND_LIMIT is $DEV_FIX_ROUND_LIMIT) without reaching APPROVED. Read Test's latest verdict comment on the PR." \
            "Submit a review on PR #$PR_NUMBER (this resets the count), or run 'Pipeline - Manual Dispatch' with transition=dev-fix, story_id=$STORY_ID, branch=$BRANCH, pr_number=$PR_NUMBER."
        fi
        ;;
    esac
  fi
  emit_run
}

# Files the push brought in (ADR-002 as amended by ADR-005):
#   new branch   -> everything since it forked from main
#   force-push   -> the head commit's own changes (first-parent diff)
#   normal push  -> everything from the previous head to the new head
push_changed_files() {
  if [[ "$PUSH_BEFORE" =~ ^0+$ ]] || [ -z "$PUSH_BEFORE" ]; then
    "$CHECK" changed-files main "$PUSH_SHA"
  elif [ "$PUSH_FORCED" == "true" ]; then
    local first_parent
    first_parent=$(gh api "repos/$GH_REPO/commits/$PUSH_SHA" --jq '.parents[0].sha') || return 2
    "$CHECK" changed-files "$first_parent" "$PUSH_SHA"
  else
    "$CHECK" changed-files "$PUSH_BEFORE" "$PUSH_SHA"
  fi
}

route_feature_push() {
  local head_author changed author_persona
  [ "$ACTOR" == "$OWNER_LOGIN" ] || [ "$ACTOR" == "$BOT_LOGIN" ] || skip "pushed by $ACTOR"
  STORY_ID=$(story_from_branch "$BRANCH")
  [ -z "$STORY_ID" ] && skip "$BRANCH has no story ID"
  head_author=$(gh api "repos/$GH_REPO/commits/$PUSH_SHA" --jq '.commit.author.name') || exit 2
  author_persona=$(persona_for_author "$head_author")
  [ -z "$author_persona" ] && skip "head commit by $head_author, not a persona (Brian resumes with Manual Dispatch)"
  changed=$(push_changed_files) || exit 2
  echo "Head commit author: $head_author"
  echo "Changed files: $(echo "$changed" | tr '\n' ' ')"

  local design_prefix="$DESIGN_DIR/$STORY_ID"
  changed_matches() { echo "$changed" | grep -qE "^${design_prefix}-$1\.md$"; }

  # One push hands off to at most one transition, in this priority order
  if [ "$author_persona" == "po" ] && changed_matches 'po-answer-r[0-9]+'; then
    TRANSITION="resume-after-po"
  elif [ "$author_persona" != "po" ] && changed_matches 'po-question-r[0-9]+'; then
    TRANSITION="po-clarify"
    ASKER_TITLE=$(persona_title "$author_persona")
  elif [ "$author_persona" == "dev" ] && changed_matches 'impl-feedback-r[0-9]+'; then
    TRANSITION="test-impl-feedback"
  elif [ "$author_persona" == "test" ] && changed_matches 'impl-feedback-response-r[0-9]+'; then
    TRANSITION="dev-implement"
    MODE=resume
  elif [ "$author_persona" == "dev" ] && changed_matches 'agreed'; then
    TRANSITION="dev-implement"
    MODE=start
  elif [ "$author_persona" == "dev" ] && changed_matches 'dev-feedback-r[0-9]+'; then
    TRANSITION="test-design-revision"
  elif [ "$author_persona" == "test" ] && changed_matches '(test-plan|test-revision-r[0-9]+)'; then
    TRANSITION="dev-design-review"
  elif [ "$author_persona" == "dev" ]; then
    TRANSITION="test-reverify"
  else
    skip "push by $head_author carries no hand-off file"
  fi
  echo "Hand-off: $TRANSITION"
  check_and_emit
}

route_pr_opened() {
  [ "$ACTOR" == "$BOT_LOGIN" ] || skip "PR opened by $ACTOR, not the bot"
  STORY_ID=$(story_from_branch "$BRANCH")
  [[ "$BRANCH" == feature/* ]] && [ -n "$STORY_ID" ] || skip "$BRANCH is not a story branch"
  TRANSITION="test-final-review"
  REF_TIME=$(now_minus_60s)
  check_and_emit
}

route_test_verdict() {
  [ "$COMMENT_USER" == "$BOT_LOGIN" ] || skip "comment by $COMMENT_USER, not the bot"
  echo "$COMMENT_BODY" | grep -q '<!-- test-verdict: CHANGES_REQUESTED -->' || skip "not a CHANGES_REQUESTED verdict"
  local pr_info pr_state
  pr_info=$(gh pr view "$PR_NUMBER" --repo "$GH_REPO" --json headRefName,state --jq '"\(.headRefName) \(.state)"') || exit 2
  BRANCH="${pr_info% *}"
  pr_state="${pr_info##* }"
  STORY_ID=$(story_from_branch "$BRANCH")
  [ "$pr_state" == "OPEN" ] && [[ "$BRANCH" == feature/* ]] && [ -n "$STORY_ID" ] \
    || skip "PR #$PR_NUMBER is $pr_state on '$BRANCH' - not an open story PR"
  TRANSITION="dev-fix"
  REF_ID="$COMMENT_ID"
  REF_TIME="$COMMENT_CREATED_AT"
  check_and_emit
}

route_review_submitted() {
  [ "$REVIEWER" == "$OWNER_LOGIN" ] || skip "review by $REVIEWER - only $OWNER_LOGIN's reviews route to personas"
  [ "$REVIEW_STATE" == "changes_requested" ] || skip "review state is $REVIEW_STATE"
  REF_TIME="$REVIEW_AT"
  if [[ "$BRANCH" == specs/* ]]; then
    TRANSITION="po-spec-revision"
    STORY_ID="specs PR #$PR_NUMBER"
  elif [[ "$BRANCH" == feature/* ]] && [ -n "$(story_from_branch "$BRANCH")" ]; then
    TRANSITION="test-brian-review"
    STORY_ID=$(story_from_branch "$BRANCH")
  else
    skip "$BRANCH is neither a story nor a specs branch"
  fi
  check_and_emit
}

# Finds the story issue number (word-boundary match: story-01 never matches story-010)
story_issue_number() {
  GH_TOKEN="$QUEUE_GH_TOKEN" gh issue list --repo "$GH_REPO" --label story --state "$2" --limit 500 \
    --json number,title \
    | jq -r --arg id "$1" '[.[] | select(.title | test("\\b" + $id + "\\b")) | .number][0] // empty'
}

route_main_or_merge() {
  if [ "$PULL_EVENT" == "push" ]; then
    [ "$ACTOR" == "$OWNER_LOGIN" ] || [ "$ACTOR" == "$BOT_LOGIN" ] || skip "pushed to main by $ACTOR"
    # First-parent diff: works for regular, squash and merge commits (ADR-002)
    local first_parent pushed_files story_specs
    first_parent=$(gh api "repos/$GH_REPO/commits/$PUSH_SHA" --jq '.parents[0].sha') || exit 2
    pushed_files=$("$CHECK" changed-files "$first_parent" "$PUSH_SHA") || exit 2
    story_specs=$(echo "$pushed_files" | grep -E '^docs/specs/story-.*\.md$')
    [ -z "$story_specs" ] && skip "no story spec changed in this push"
    echo "Story specs changed: $(echo "$story_specs" | tr '\n' ' ')"
    GH_TOKEN="$QUEUE_GH_TOKEN" "$SCRIPT_DIR/sync-blocked-labels.sh" || exit 2
  else
    [[ "$BRANCH" == feature/* ]] || skip "closed PR is from $BRANCH, not a story branch"
    STORY_ID=$(story_from_branch "$BRANCH")
    [ -z "$STORY_ID" ] && skip "$BRANCH has no story ID"
    if [ "$PR_MERGED" != "true" ]; then
      local open_issue
      open_issue=$(story_issue_number "$STORY_ID" open) || exit 2
      escalate_and_stop "story PR closed without merge" \
        "PR #$PR_NUMBER for $STORY_ID was closed without merging. The story is still in-progress, so the queue is paused." \
        "Reopen PR #$PR_NUMBER to continue the story (Test re-reviews it). To abandon it instead: close issue #${open_issue:-?} as not planned, remove its in-progress label, then run 'Pipeline - Manual Dispatch' with transition=test-kickoff and story_id left blank."
    fi
    local issue_number
    issue_number=$(story_issue_number "$STORY_ID" open) || exit 2
    if [ -n "$issue_number" ]; then
      local api_response
      if ! api_response=$(GH_TOKEN="$QUEUE_GH_TOKEN" gh api --method DELETE \
          "repos/$GH_REPO/issues/$issue_number/labels/in-progress" 2>&1); then
        echo "$api_response" | grep -q "HTTP 404" || { echo "$api_response" >&2; exit 2; }
      fi
      GH_TOKEN="$QUEUE_GH_TOKEN" gh issue close "$issue_number" --repo "$GH_REPO" --reason completed \
        --comment "Completed - PR #$PR_NUMBER merged to main." || exit 2
      echo "Closed issue #$issue_number for $STORY_ID"
      sleep "$API_PROPAGATION_WAIT_SECONDS"
    else
      echo "No open issue for $STORY_ID - already closed."
    fi
  fi
  pick_next_story
}

pick_next_story() {
  local next_story exit_code issue_number
  if [ -n "$INPUT_STORY_ID" ]; then
    next_story="$INPUT_STORY_ID"
    echo "Manual override - using story: $next_story"
  else
    next_story=$(GH_TOKEN="$QUEUE_GH_TOKEN" "$SCRIPT_DIR/next-story.sh") && exit_code=0 || exit_code=$?
    [ $exit_code -eq 3 ] && skip "a story is already in-progress - the queue resumes when its PR merges"
    [ $exit_code -ne 0 ] && { echo "ERROR: queue manager failed (exit $exit_code)" >&2; exit 2; }
    [ -z "$next_story" ] && skip "no eligible story in the queue"
  fi
  STORY_ID="$next_story"
  STORY_FILE=$(ls docs/specs/"$STORY_ID"-*.md 2>/dev/null | head -1)
  [ -z "$STORY_FILE" ] && { echo "ERROR: no spec file for $STORY_ID on main" >&2; exit 2; }
  issue_number=$(story_issue_number "$STORY_ID" open) || exit 2
  if [ -n "$issue_number" ]; then
    GH_TOKEN="$QUEUE_GH_TOKEN" gh api --method POST "repos/$GH_REPO/issues/$issue_number/labels" \
      -f 'labels[]=in-progress' > /dev/null || exit 2
    echo "Marked issue #$issue_number as in-progress"
  else
    echo "WARNING: no open issue for $STORY_ID"
  fi
  BRANCH=$(gh api "repos/$GH_REPO/git/matching-refs/heads/feature/${STORY_ID}-" --jq '.[0].ref // ""' | sed 's|^refs/heads/||') || exit 2
  TRANSITION="test-kickoff"
  emit_run
}

route_manual() {
  TRANSITION="$INPUT_TRANSITION"
  EVENT_IS_MANUAL=true
  [ -z "$(stage_for "$TRANSITION")" ] && { echo "ERROR: unknown transition '$TRANSITION'" >&2; exit 2; }
  if [ "$TRANSITION" == "test-kickoff" ]; then
    pick_next_story
  fi
  STORY_ID="$INPUT_STORY_ID"
  BRANCH="$INPUT_BRANCH"
  PR_NUMBER="$INPUT_PR_NUMBER"
  ROUND="$INPUT_ROUND"
  MODE="${INPUT_MODE:-start}"
  if [ "$TRANSITION" == "po-spec-revision" ]; then
    [ -z "$PR_NUMBER" ] && { echo "ERROR: pr_number is required for po-spec-revision" >&2; exit 2; }
    BRANCH=$(gh pr view "$PR_NUMBER" --repo "$GH_REPO" --json headRefName --jq .headRefName) || exit 2
    STORY_ID="specs PR #$PR_NUMBER"
  fi
  [ -z "$STORY_ID" ] && { echo "ERROR: story_id is required for $TRANSITION" >&2; exit 2; }
  if [ -z "$BRANCH" ]; then
    BRANCH=$(gh api "repos/$GH_REPO/git/matching-refs/heads/feature/${STORY_ID}-" --jq '.[0].ref // ""' | sed 's|^refs/heads/||') || exit 2
    [ -z "$BRANCH" ] && { echo "ERROR: no feature/${STORY_ID}-* branch; pass branch" >&2; exit 2; }
  fi
  [ -z "$PR_NUMBER" ] && PR_NUMBER=$("$CHECK" pr-number "$BRANCH")
  if [ "$TRANSITION" == "dev-implement" ] && [ -n "$PR_NUMBER" ]; then
    echo "ERROR: PR #$PR_NUMBER is already open for $BRANCH. Use transition=dev-fix to have Dev work on the open PR." >&2
    exit 2
  fi
  if [ "$TRANSITION" == "po-clarify" ]; then
    local listing asker_author
    listing=$(design_files "$BRANCH") || exit 2
    ROUND=$(highest_round "$listing" "$STORY_ID" po-question)
    asker_author=$(gh api "repos/$GH_REPO/commits?sha=$BRANCH&path=$DESIGN_DIR/$STORY_ID-po-question-r$ROUND.md&per_page=100" --jq '.[-1].commit.author.name') || exit 2
    ASKER_TITLE=$(persona_title "$(persona_for_author "$asker_author")")
  fi
  if [ "$TRANSITION" == "dev-fix" ]; then
    local verdict
    verdict=$("$CHECK" latest-verdict "$PR_NUMBER" "$BOT_LOGIN") || exit 2
    REF_ID=$(echo "$verdict" | cut -d' ' -f1)
    REF_TIME=$(echo "$verdict" | cut -d' ' -f2)
  fi
  [ -z "$REF_TIME" ] && REF_TIME=$(now_minus_60s)
  check_and_emit
}

# ===========================================================================
# verify: expected outcome per transition (ADR-003)
# ===========================================================================

advanced_by() { "$CHECK" branch-advanced "$BRANCH" "$HEAD_SHA_BEFORE" "$(author_for_persona "$1")"; }
touched() { "$CHECK" files-touched "$BRANCH" "$HEAD_SHA_BEFORE" "^$DESIGN_DIR/$STORY_ID-($1)\.md$"; }
verdict_since() { "$CHECK" verdict-since "$PR_NUMBER" "$STARTED_AT" "$BOT_LOGIN" "$1"; }

verify_transition() {
  case "$1" in
    test-kickoff)
      if [ -z "$BRANCH" ]; then
        BRANCH=$(gh api "repos/$GH_REPO/git/matching-refs/heads/feature/${STORY_ID}-" --jq '.[0].ref // ""' | sed 's|^refs/heads/||') || return 2
        [ -z "$BRANCH" ] && { echo "No feature/${STORY_ID}-* branch exists on origin"; return 1; }
      else
        advanced_by test || return $?
      fi
      touched 'po-question-r[0-9]+' && return 0
      "$CHECK" file-on-branch "$BRANCH" "$DESIGN_DIR/$STORY_ID-test-plan.md" || return $?
      "$CHECK" file-on-branch "$BRANCH" "$DESIGN_DIR/$STORY_ID-api-contracts.md"
      ;;
    dev-design-review)
      advanced_by dev && touched "agreed|dev-feedback-r$ROUND|po-question-r[0-9]+" ;;
    test-design-revision)
      advanced_by test && touched "test-revision-r$ROUND|po-question-r[0-9]+" ;;
    dev-implement)
      if [ "$PR_OPEN_AT_START" == "true" ]; then
        advanced_by dev
        return $?
      fi
      if advanced_by dev > /dev/null && touched 'impl-feedback-r[0-9]+|po-question-r[0-9]+' > /dev/null; then
        echo "Dev reported a test defect or asked PO"
        return 0
      fi
      # Step 6: a resumed Dev may only need to open the PR
      "$CHECK" pr-open "$BRANCH" || return $?
      advanced_by dev || "$CHECK" pr-created-since "$BRANCH" "$STARTED_AT"
      ;;
    test-impl-feedback)
      advanced_by test && touched "impl-feedback-response-r$ROUND|po-question-r[0-9]+" ;;
    test-final-review|test-reverify)
      verdict_since ANY && return 0
      advanced_by test > /dev/null && touched 'po-question-r[0-9]+' && return 0
      echo "No verdict comment by $BOT_LOGIN on PR #$PR_NUMBER since $STARTED_AT, and no PO question"
      return 1
      ;;
    dev-fix)
      advanced_by dev ;;
    test-brian-review)
      verdict_since CHANGES_REQUESTED ;;
    po-spec-revision)
      advanced_by po ;;
    po-clarify)
      advanced_by po && touched "po-answer-r$ROUND" ;;
    resume-after-po)
      advanced_by "$PERSONA" && return 0
      if [ "$PERSONA" == "test" ] && [ -n "$PR_NUMBER" ]; then
        verdict_since ANY && return 0
      fi
      echo "$(persona_title "$PERSONA") neither pushed to $BRANCH nor posted a verdict since resuming"
      return 1
      ;;
    *)
      echo "ERROR: unknown transition '$1'" >&2
      return 2
      ;;
  esac
}

# ===========================================================================
# Main
# ===========================================================================

COMMAND="$1"
case "$COMMAND" in
  route)
    case "$2" in
      feature-push) route_feature_push ;;
      pr-opened) route_pr_opened ;;
      test-verdict) route_test_verdict ;;
      review-submitted) route_review_submitted ;;
      main-or-merge) route_main_or_merge ;;
      manual) route_manual ;;
      *) echo "Usage: $0 route <feature-push|pr-opened|test-verdict|review-submitted|main-or-merge|manual>" >&2; exit 2 ;;
    esac
    ;;
  recheck)
    TRANSITION="$2"
    [ -z "$(stage_for "$TRANSITION")" ] && { echo "ERROR: unknown transition '$TRANSITION'" >&2; exit 2; }
    run_needed "$TRANSITION"
    CODE=$?
    [ $CODE -eq 0 ] && echo "Still needed: $TRANSITION for $STORY_ID"
    exit $CODE
    ;;
  verify)
    [ -z "$(stage_for "$2")" ] && { echo "ERROR: unknown transition '$2'" >&2; exit 2; }
    verify_transition "$2"
    exit $?
    ;;
  *)
    echo "Usage: $0 route <event> | recheck <transition> | verify <transition>" >&2
    exit 2
    ;;
esac
