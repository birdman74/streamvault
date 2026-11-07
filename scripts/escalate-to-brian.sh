#!/bin/bash
# scripts/escalate-to-brian.sh
#
# Opens (or updates) a 'needs-brian' issue when the pipeline cannot continue
# without a human: a persona stalled, crashed, or asked a question; or the
# design iteration limit was reached.
#
# The issue is created by the bot account and assigned to Brian. GitHub never
# notifies a user about their own activity, so it must NOT be created with a
# token belonging to birdman74. Being assigned makes Brian a participant, so
# he is notified (and emailed, if participating email notifications are on).
#
# One open issue per story + stage: if a matching open 'needs-brian' issue
# already exists, a comment is added to it instead of opening a duplicate.
#
# Required env:
#   GH_TOKEN      bot account token (STREAMVAULT_BOT_GH_TOKEN)
#   GH_REPO       owner/repo
#   STORY_ID      e.g. story-009 (or a specs reference such as epic-003)
#   STAGE         short description, e.g. "Dev implementation stalled"
#   REASON        one line: what was expected and what was found
# Optional env:
#   BRANCH        feature or specs branch involved
#   PR_NUMBER     PR involved
#   RUN_URL       link to the workflow run
#   OUTPUT_FILE   file holding the persona's output; its tail is included
#   ASSIGNEE      defaults to birdman74
#
# Exit code 0: issue created or updated
# Exit code 1: could not create or update the issue

set -o pipefail

ASSIGNEE="${ASSIGNEE:-birdman74}"
MAX_OUTPUT_CHARS=50000

for REQUIRED_VAR in GH_TOKEN GH_REPO STORY_ID STAGE REASON; do
  if [ -z "${!REQUIRED_VAR}" ]; then
    echo "ERROR: $REQUIRED_VAR must be set" >&2
    exit 1
  fi
done

TITLE="needs-brian: $STORY_ID $STAGE"

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
  [ -n "$BRANCH" ] && echo "| **Branch** | \`$BRANCH\` |"
  [ -n "$PR_NUMBER" ] && echo "| **PR** | #$PR_NUMBER |"
  [ -n "$RUN_URL" ] && echo "| **Workflow run** | $RUN_URL |"
  echo "| **Detected** | $(date -u +%Y-%m-%dT%H:%M:%SZ) |"
  echo
  if [ -n "$OUTPUT_FILE" ] && [ -s "$OUTPUT_FILE" ]; then
    echo "### Persona output (last part)"
    echo
    echo "If the persona asked a question, it is usually at the end of this output."
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
  echo "Close this issue once the pipeline is moving again."
} > "$BODY_FILE"

# Make sure the label exists (no-op when it already does)
gh label create needs-brian --repo "$GH_REPO" --color B60205 \
  --description "Pipeline stopped: needs Brian's decision" > /dev/null 2>&1 || true

EXISTING_ISSUE=$(gh issue list --repo "$GH_REPO" --label needs-brian --state open \
  --limit 200 --json number,title \
  | jq -r --arg title "$TITLE" '[.[] | select(.title == $title) | .number][0] // empty')
if [ $? -ne 0 ]; then
  echo "WARNING: could not search for an existing escalation issue - creating a new one" >&2
  EXISTING_ISSUE=""
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