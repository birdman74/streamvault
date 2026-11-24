#!/bin/bash
# scripts/escalate-to-brian.sh
#
# Opens (or updates) a 'needs-brian' issue when the pipeline cannot continue
# without a human: a persona stalled, crashed, timed out or asked a question
# only Brian can answer; a round limit in .github/pipeline-config.env was
# exceeded; or a story PR was closed without merging.
#
# With --resolve it does the opposite: when a persona run for a story
# succeeds, the pipeline is moving again, so every open 'needs-brian' issue
# for that story is closed with a link to the run that moved it.
#
# The issue is created by the bot account and assigned to Brian. GitHub never
# notifies a user about their own activity, so it must NOT be created with a
# token belonging to the assignee. Being assigned makes Brian a participant,
# so he is notified (and emailed, if participating email notifications are on).
#
# One open issue per story + stage: if a matching open 'needs-brian' issue
# already exists, a comment is added to it instead of opening a duplicate.
#
# Usage:
#   escalate-to-brian.sh              open or update an escalation
#   escalate-to-brian.sh --resolve    close the story's open escalations
#
# Required env:
#   GH_TOKEN      bot account token (STREAMVAULT_BOT_GH_TOKEN)
#   GH_REPO       owner/repo
#   STORY_ID      e.g. story-009 (or a specs reference such as specs PR #12)
#   STAGE         short description, e.g. "Dev implementation stalled"
#                 (with --resolve: the stage that just succeeded)
#   REASON        one line: what was expected and what was found
#                 (not needed with --resolve)
# Optional env:
#   BRANCH        feature or specs branch involved
#   PR_NUMBER     PR involved
#   RUN_URL       link to the workflow run
#   RESUME        how to restart the pipeline once the cause is fixed
#   OUTPUT_FILE   file holding the persona's output; its tail is included
#   ASSIGNEE      defaults to ESCALATION_ASSIGNEE from pipeline-config.env
#
# Exit code 0: issue created, updated or resolved (or nothing to resolve)
# Exit code 1: could not create, update or resolve the issue

set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -z "$ASSIGNEE" ]; then
  # shellcheck source=pipeline-config.sh
  source "$SCRIPT_DIR/pipeline-config.sh"
  ASSIGNEE="$ESCALATION_ASSIGNEE"
fi
MAX_OUTPUT_CHARS=50000
MODE="${1:-open}"

REQUIRED_VARS="GH_TOKEN GH_REPO STORY_ID STAGE REASON"
[ "$MODE" == "--resolve" ] && REQUIRED_VARS="GH_TOKEN GH_REPO STORY_ID STAGE"
for REQUIRED_VAR in $REQUIRED_VARS; do
  if [ -z "${!REQUIRED_VAR}" ]; then
    echo "ERROR: $REQUIRED_VAR must be set" >&2
    exit 1
  fi
done

TITLE_PREFIX="needs-brian: $STORY_ID "
TITLE="${TITLE_PREFIX}${STAGE}"

OPEN_ESCALATIONS=$(gh issue list --repo "$GH_REPO" --label needs-brian --state open \
  --limit 200 --json number,title)
SEARCH_CODE=$?

# ---------------------------------------------------------------------------
# --resolve: close every open escalation for this story
# ---------------------------------------------------------------------------
if [ "$MODE" == "--resolve" ]; then
  if [ $SEARCH_CODE -ne 0 ]; then
    echo "ERROR: could not search for open escalation issues" >&2
    exit 1
  fi
  RESOLVE_NUMBERS=$(echo "$OPEN_ESCALATIONS" | jq -r --arg prefix "$TITLE_PREFIX" \
    '.[] | select(.title | startswith($prefix)) | .number')
  if [ -z "$RESOLVE_NUMBERS" ]; then
    echo "No open escalation for $STORY_ID - nothing to resolve."
    exit 0
  fi
  RESOLVE_FAILURES=0
  for ISSUE_NUMBER in $RESOLVE_NUMBERS; do
    if gh issue close "$ISSUE_NUMBER" --repo "$GH_REPO" --reason completed \
        --comment "Resolved automatically: the pipeline moved on (\"$STAGE\" succeeded${RUN_URL:+ in $RUN_URL})." > /dev/null; then
      echo "Closed escalation issue #$ISSUE_NUMBER"
    else
      echo "ERROR: could not close escalation issue #$ISSUE_NUMBER" >&2
      RESOLVE_FAILURES=$((RESOLVE_FAILURES + 1))
    fi
  done
  [ $RESOLVE_FAILURES -eq 0 ] && exit 0 || exit 1
fi

if [ "$MODE" != "open" ]; then
  echo "Usage: $0 [--resolve]" >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Open or update an escalation
# ---------------------------------------------------------------------------
BODY_FILE=$(mktemp)
trap 'rm -f "$BODY_FILE"' EXIT

{
  echo "The automated pipeline stopped and needs a decision from Brian."
  echo
  echo "| | |"
  echo "|---|---|"
  echo "| **Story** | $STORY_ID |"
  echo "| **Stage** | $STAGE |"
  echo "| **What went wrong** | $REASON |"
  [ -n "$RESUME" ] && echo "| **How to resume** | $RESUME |"
  [ -n "$BRANCH" ] && echo "| **Branch** | \`$BRANCH\` |"
  [ -n "$PR_NUMBER" ] && echo "| **PR** | #$PR_NUMBER |"
  [ -n "$RUN_URL" ] && echo "| **Workflow run** | $RUN_URL |"
  echo "| **Detected** | $(date -u +%Y-%m-%dT%H:%M:%SZ) |"
  echo
  if [ -n "$OUTPUT_FILE" ] && [ -s "$OUTPUT_FILE" ]; then
    echo "### Persona output (last part)"
    echo
    echo "If the persona asked a question or stopped with NEEDS BRIAN, it is usually at the end of this output."
    echo
    echo "~~~~~~"
    # The repo is public and Actions log masking does not apply to issue
    # bodies, so redact anything shaped like a GitHub or Anthropic credential.
    tail -c "$MAX_OUTPUT_CHARS" "$OUTPUT_FILE" | sed -E \
      -e 's/(ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{20,}/[REDACTED]/g' \
      -e 's/github_pat_[A-Za-z0-9_]{20,}/[REDACTED]/g' \
      -e 's/sk-ant-[A-Za-z0-9_-]{20,}/[REDACTED]/g'
    echo
    echo "~~~~~~"
  else
    echo "_No persona output was captured._"
  fi
  echo
  echo "This issue closes itself when the next persona run for $STORY_ID succeeds. Close it by hand if you resolve it another way."
} > "$BODY_FILE"

# Make sure the label exists (no-op when it already does)
gh label create needs-brian --repo "$GH_REPO" --color B60205 \
  --description "Pipeline stopped: needs Brian's decision" > /dev/null 2>&1 || true

EXISTING_ISSUE=""
if [ $SEARCH_CODE -eq 0 ]; then
  EXISTING_ISSUE=$(echo "$OPEN_ESCALATIONS" | jq -r --arg title "$TITLE" \
    '[.[] | select(.title == $title) | .number][0] // empty')
else
  echo "WARNING: could not search for an existing escalation issue - creating a new one" >&2
fi

if [ -n "$EXISTING_ISSUE" ]; then
  if gh issue comment "$EXISTING_ISSUE" --repo "$GH_REPO" --body-file "$BODY_FILE" > /dev/null; then
    echo "Escalation added to existing issue #$EXISTING_ISSUE"
    exit 0
  fi
  echo "ERROR: could not comment on issue #$EXISTING_ISSUE" >&2
  exit 1
fi

ISSUE_URL=$(gh issue create --repo "$GH_REPO" \
  --title "$TITLE" \
  --label needs-brian \
  --assignee "$ASSIGNEE" \
  --body-file "$BODY_FILE")
if [ $? -ne 0 ]; then
  echo "ERROR: could not create escalation issue" >&2
  exit 1
fi

echo "Escalation issue created: $ISSUE_URL"
exit 0
