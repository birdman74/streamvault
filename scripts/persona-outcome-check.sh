#!/bin/bash
# scripts/persona-outcome-check.sh
#
# Verifies that a persona run actually produced the result its trigger
# workflow launched it for. A persona that stops to ask a question, crashes,
# or silently gives up still exits 0, so the launch step alone cannot tell
# success from a stall. This check looks at the repo state on GitHub instead
# of trusting the persona's exit code.
#
# Usage:
#   persona-outcome-check.sh branch-created <story-id>
#       A branch named feature/<story-id>-* exists on origin.
#   persona-outcome-check.sh file-on-branch <branch> <path> [<path>...]
#       At least one of the given paths exists at the head of <branch>.
#   persona-outcome-check.sh pr-open <branch>
#       An open PR exists with <branch> as its head.
#   persona-outcome-check.sh branch-advanced <branch> <before-sha> <git-author-name>
#       <branch> head moved past <before-sha> and the new head commit was
#       authored by <git-author-name> (e.g. claude-streamvault-dev).
#   persona-outcome-check.sh verdict-since <pr-number> <iso-utc-time> <login> [APPROVED|CHANGES_REQUESTED|ANY]
#       <login> posted a PR comment carrying a Test verdict marker
#       (<!-- test-verdict: ... -->) at or after <iso-utc-time>
#       (format 2026-11-07T08:00:00Z). Defaults to ANY verdict.
#   persona-outcome-check.sh head-sha <branch>
#       Helper, not a check: prints the current head SHA of <branch>, for
#       recording the "before" state that branch-advanced compares against.
#   persona-outcome-check.sh verdict-count <pr-number> <iso-utc-time-or-empty> <login> <APPROVED|CHANGES_REQUESTED>
#       Helper, not a check: prints how many verdict comments of that kind
#       <login> posted at or after the given time (all time when empty).
#
# Required env: GH_TOKEN, GH_REPO (owner/repo)
#
# Output: one human-readable line saying what was found or missing
# Exit code 0: expected outcome found
# Exit code 1: expected outcome missing
# Exit code 2: usage error or API failure (treated as "not verified")

set -o pipefail

CHECK="$1"
shift

if [ -z "$GH_REPO" ]; then
  echo "ERROR: GH_REPO must be set (owner/repo)" >&2
  exit 2
fi

# Prints how many PR comments by <login>, created at or after <since>,
# contain <marker>. Paginated pages are summed.
count_verdicts() {
  local pr_number="$1" since="$2" login="$3" marker="$4"
  gh api --paginate "repos/$GH_REPO/issues/$pr_number/comments" \
    --jq "[.[] | select(.user.login == \"$login\" and .created_at >= \"$since\" and (.body | contains(\"$marker\")))] | length" \
    | awk '{ total += $1 } END { print total + 0 }'
}

case "$CHECK" in
  branch-created)
    STORY_ID="$1"
    [ -z "$STORY_ID" ] && { echo "Usage: $0 branch-created <story-id>" >&2; exit 2; }
    MATCHING_REFS=$(gh api "repos/$GH_REPO/git/matching-refs/heads/feature/${STORY_ID}-" \
      --jq '[.[].ref] | join(" ")') || { echo "ERROR: could not list branches" >&2; exit 2; }
    if [ -n "$MATCHING_REFS" ]; then
      echo "Found branch: $MATCHING_REFS"
      exit 0
    fi
    echo "No feature/${STORY_ID}-* branch exists on origin"
    exit 1
    ;;

  file-on-branch)
    BRANCH="$1"
    shift
    [ -z "$BRANCH" ] || [ $# -eq 0 ] && { echo "Usage: $0 file-on-branch <branch> <path>..." >&2; exit 2; }
    for FILE_PATH in "$@"; do
      if gh api -X GET "repos/$GH_REPO/contents/$FILE_PATH" -f ref="$BRANCH" --silent 2>/dev/null; then
        echo "Found $FILE_PATH on $BRANCH"
        exit 0
      fi
    done
    echo "None of these exist on $BRANCH: $*"
    exit 1
    ;;

  pr-open)
    BRANCH="$1"
    [ -z "$BRANCH" ] && { echo "Usage: $0 pr-open <branch>" >&2; exit 2; }
    PR_NUMBER=$(gh pr list --repo "$GH_REPO" --head "$BRANCH" --state open --json number \
      --jq '.[0].number // ""') || { echo "ERROR: could not list PRs" >&2; exit 2; }
    if [ -n "$PR_NUMBER" ]; then
      echo "Found open PR #$PR_NUMBER for $BRANCH"
      exit 0
    fi
    echo "No open PR for $BRANCH"
    exit 1
    ;;

  branch-advanced)
    BRANCH="$1"
    BEFORE_SHA="$2"
    EXPECTED_AUTHOR="$3"
    [ -z "$BRANCH" ] || [ -z "$EXPECTED_AUTHOR" ] && { echo "Usage: $0 branch-advanced <branch> <before-sha> <git-author-name>" >&2; exit 2; }
    # Resolve the branch through git/ref first: branch names contain '/',
    # which the commits/{ref} endpoint does not reliably accept.
    HEAD_SHA=$(gh api "repos/$GH_REPO/git/ref/heads/$BRANCH" \
      --jq '.object.sha') || { echo "ERROR: could not read head of $BRANCH" >&2; exit 2; }
    HEAD_AUTHOR=$(gh api "repos/$GH_REPO/commits/$HEAD_SHA" \
      --jq '.commit.author.name') || { echo "ERROR: could not read commit $HEAD_SHA" >&2; exit 2; }
    if [ "$HEAD_SHA" == "$BEFORE_SHA" ]; then
      echo "$BRANCH did not move (still $BEFORE_SHA): no commit was pushed"
      exit 1
    fi
    if [ "$HEAD_AUTHOR" != "$EXPECTED_AUTHOR" ]; then
      echo "$BRANCH moved to $HEAD_SHA but the head commit is by $HEAD_AUTHOR, not $EXPECTED_AUTHOR"
      exit 1
    fi
    echo "$BRANCH advanced to $HEAD_SHA by $HEAD_AUTHOR"
    exit 0
    ;;

  verdict-since)
    PR_NUMBER="$1"
    SINCE="$2"
    LOGIN="$3"
    VERDICT="${4:-ANY}"
    [ -z "$PR_NUMBER" ] || [ -z "$SINCE" ] || [ -z "$LOGIN" ] && { echo "Usage: $0 verdict-since <pr-number> <iso-utc-time> <login> [APPROVED|CHANGES_REQUESTED|ANY]" >&2; exit 2; }
    if [ "$VERDICT" == "ANY" ]; then MARKER="<!-- test-verdict: "; else MARKER="<!-- test-verdict: $VERDICT -->"; fi
    VERDICT_COUNT=$(count_verdicts "$PR_NUMBER" "$SINCE" "$LOGIN" "$MARKER") || { echo "ERROR: could not read PR comments" >&2; exit 2; }
    if [ "$VERDICT_COUNT" -gt 0 ]; then
      echo "Found $VERDICT_COUNT $VERDICT verdict comment(s) by $LOGIN on PR #$PR_NUMBER since $SINCE"
      exit 0
    fi
    echo "No $VERDICT verdict comment by $LOGIN on PR #$PR_NUMBER since $SINCE"
    exit 1
    ;;

  verdict-count)
    PR_NUMBER="$1"
    SINCE="$2"
    LOGIN="$3"
    VERDICT="$4"
    [ -z "$PR_NUMBER" ] || [ -z "$LOGIN" ] || [ -z "$VERDICT" ] && { echo "Usage: $0 verdict-count <pr-number> <iso-utc-time-or-empty> <login> <APPROVED|CHANGES_REQUESTED>" >&2; exit 2; }
    count_verdicts "$PR_NUMBER" "${SINCE:-1970-01-01T00:00:00Z}" "$LOGIN" "<!-- test-verdict: $VERDICT -->" \
      || { echo "ERROR: could not read PR comments" >&2; exit 2; }
    exit 0
    ;;

  head-sha)
    BRANCH="$1"
    [ -z "$BRANCH" ] && { echo "Usage: $0 head-sha <branch>" >&2; exit 2; }
    gh api "repos/$GH_REPO/git/ref/heads/$BRANCH" --jq '.object.sha' \
      || { echo "ERROR: could not read head of $BRANCH" >&2; exit 2; }
    exit 0
    ;;

  *)
    echo "Unknown check '$CHECK'. Use: branch-created, file-on-branch, pr-open, branch-advanced, verdict-since, head-sha, verdict-count" >&2
    exit 2
    ;;
esac