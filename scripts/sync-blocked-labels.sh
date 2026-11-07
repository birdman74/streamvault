#!/bin/bash
# scripts/sync-blocked-labels.sh
#
# Reconciles the 'blocked' label on every open story issue so it always
# matches the prerequisites in the spec files:
#   - prerequisites met and labeled 'blocked'     -> label removed
#   - prerequisites not met and not 'blocked'     -> label added
#     (except on 'in-progress' stories: a story already being worked
#     is never re-blocked; it can still have a stale 'blocked' removed)
#
# Idempotent: safe to run any number of times. Prerequisite rules live in
# check-prerequisites.sh, not here.
#
# Called by:
#   - .github/workflows/sync-blocked-labels.yml (issue events, manual dispatch)
#   - .github/workflows/trigger-test-next-story.yml (story spec pushes to main)
#
# Usage: ./scripts/sync-blocked-labels.sh
#
# Optional env:
#   DRY_RUN=true   Print intended changes without modifying any labels
#
# Exit code 0: success (including nothing to change)
# Exit code 1: one or more label changes or lookups failed

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHECK_PREREQS="$SCRIPT_DIR/check-prerequisites.sh"
DRY_RUN="${DRY_RUN:-false}"

# Must match COMPLETED_FILTER in check-prerequisites.sh
COMPLETED_FILTER='[.[] | select(.stateReason == "COMPLETED" or .stateReason == null or .stateReason == "") | .title]'

# Removes 'blocked' from an issue. A 404 means the label is already gone
# (removed by hand, or by another run), which is the desired end state,
# so it is not treated as a failure.
remove_blocked_label() {
  local issue_number="$1"
  local api_response
  api_response=$(gh api --method DELETE \
    "repos/{owner}/{repo}/issues/$issue_number/labels/blocked" 2>&1)
  if [ $? -eq 0 ]; then
    return 0
  fi
  if echo "$api_response" | grep -q "HTTP 404"; then
    echo "  'blocked' was already removed from #$issue_number"
    return 0
  fi
  echo "$api_response" >&2
  return 1
}

OPEN_STORIES=$(gh issue list \
  --label "story" \
  --state open \
  --limit 500 \
  --json number,title,labels \
  --jq 'sort_by(.number)')
if [ $? -ne 0 ] || [ -z "$OPEN_STORIES" ]; then
  echo "ERROR: Could not fetch open story issues" >&2
  exit 1
fi

COMPLETED_STORIES=$(gh issue list \
  --label "story" \
  --state closed \
  --limit 500 \
  --json title,stateReason \
  --jq "$COMPLETED_FILTER")
if [ $? -ne 0 ] || [ -z "$COMPLETED_STORIES" ]; then
  echo "ERROR: Could not fetch completed story issues" >&2
  exit 1
fi
export COMPLETED_STORIES

[ "$DRY_RUN" == "true" ] && echo "DRY RUN: no labels will be changed"

STORY_COUNT=$(echo "$OPEN_STORIES" | jq length)
FAILURES=0
CHANGES=0

for i in $(seq 0 $((STORY_COUNT - 1))); do
  ISSUE_NUMBER=$(echo "$OPEN_STORIES" | jq -r ".[$i].number")
  ISSUE_TITLE=$(echo "$OPEN_STORIES" | jq -r ".[$i].title")
  ISSUE_LABELS=$(echo "$OPEN_STORIES" | jq -r ".[$i].labels[].name")
  STORY_ID=$(echo "$ISSUE_TITLE" | grep -oP 'story-\d+' | head -1)

  if [ -z "$STORY_ID" ]; then
    echo "SKIP #$ISSUE_NUMBER: no story ID in title '$ISSUE_TITLE'"
    continue
  fi

  IS_IN_PROGRESS=false
  echo "$ISSUE_LABELS" | grep -qx "in-progress" && IS_IN_PROGRESS=true

  IS_BLOCKED=false
  echo "$ISSUE_LABELS" | grep -qx "blocked" && IS_BLOCKED=true

  UNMET_PREREQS=$("$CHECK_PREREQS" "$STORY_ID")
  CHECK_RESULT=$?

  if [ $CHECK_RESULT -eq 2 ]; then
    echo "ERROR #$ISSUE_NUMBER $STORY_ID: prerequisite check failed" >&2
    FAILURES=$((FAILURES + 1))
    continue
  fi

  if [ $CHECK_RESULT -eq 0 ] && [ "$IS_BLOCKED" == "true" ]; then
    echo "UNBLOCK #$ISSUE_NUMBER $STORY_ID: all prerequisites completed"
    CHANGES=$((CHANGES + 1))
    if [ "$DRY_RUN" != "true" ]; then
      remove_blocked_label "$ISSUE_NUMBER" \
        || { echo "ERROR: failed to remove 'blocked' from #$ISSUE_NUMBER" >&2; FAILURES=$((FAILURES + 1)); }
    fi
  elif [ $CHECK_RESULT -eq 1 ] && [ "$IS_BLOCKED" == "false" ] && [ "$IS_IN_PROGRESS" == "true" ]; then
    echo "SKIP #$ISSUE_NUMBER $STORY_ID: in-progress, not re-blocking (waiting on $(echo $UNMET_PREREQS))"
  elif [ $CHECK_RESULT -eq 1 ] && [ "$IS_BLOCKED" == "false" ]; then
    echo "BLOCK #$ISSUE_NUMBER $STORY_ID: waiting on $(echo $UNMET_PREREQS)"
    CHANGES=$((CHANGES + 1))
    if [ "$DRY_RUN" != "true" ]; then
      gh api --method POST \
        "repos/{owner}/{repo}/issues/$ISSUE_NUMBER/labels" \
        -f 'labels[]=blocked' > /dev/null \
        || { echo "ERROR: failed to add 'blocked' to #$ISSUE_NUMBER" >&2; FAILURES=$((FAILURES + 1)); }
    fi
  else
    echo "OK #$ISSUE_NUMBER $STORY_ID: blocked=$IS_BLOCKED"
  fi
done

echo "Done: $CHANGES change(s), $FAILURES failure(s)"
[ $FAILURES -eq 0 ]