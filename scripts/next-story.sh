#!/bin/bash
# scripts/next-story.sh
#
# Determines the next eligible story to start by querying GitHub Issues.
# State is inferred from GitHub Issues - no state files needed.
#
# A story is eligible when:
# 1. Its GitHub Issue is open and labeled 'story'
# 2. No other story issue is currently labeled 'in-progress'
# 3. All prerequisite stories have their GitHub Issues closed as completed
#    (rule lives in scripts/check-prerequisites.sh, shared with
#    scripts/sync-blocked-labels.sh)
#
# Fails closed: if any GitHub API call fails, the script exits 1 rather
# than guessing, so a transient error can never start a second story or
# silently report an empty queue.
#
# Usage: ./scripts/next-story.sh
# Output: story ID (e.g. "story-005") to stdout, or empty string if nothing eligible
# Exit code 0: success (story ID or empty string on stdout)
# Exit code 1: error (an API call failed)
# Exit code 3: a story is already in-progress, so nothing can start

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHECK_PREREQS="$SCRIPT_DIR/check-prerequisites.sh"

# Must match COMPLETED_FILTER in check-prerequisites.sh
COMPLETED_FILTER='[.[] | select(.stateReason == "COMPLETED" or .stateReason == null or .stateReason == "") | .title]'

# Check for any story already in-progress
IN_PROGRESS=$(gh issue list \
  --label "story,in-progress" \
  --state open \
  --limit 500 \
  --json number,title \
  --jq '.[0].title // ""')
if [ $? -ne 0 ]; then
  echo "ERROR: Could not check for in-progress stories" >&2
  exit 1
fi

if [ -n "$IN_PROGRESS" ]; then
  echo "A story is already in-progress: $IN_PROGRESS. Nothing can start until it completes." >&2
  exit 3
fi

# Get all open story issues sorted by issue number (proxy for story number)
OPEN_STORIES=$(gh issue list \
  --label "story" \
  --state open \
  --limit 500 \
  --json number,title \
  --jq 'sort_by(.number)')
if [ $? -ne 0 ] || [ -z "$OPEN_STORIES" ]; then
  echo "ERROR: Could not fetch open story issues" >&2
  exit 1
fi

if [ "$OPEN_STORIES" == "[]" ]; then
  echo ""
  exit 0
fi

# Fetch completed story titles once and share them with check-prerequisites.sh
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

# Iterate open stories in order and find the first eligible one
STORY_COUNT=$(echo "$OPEN_STORIES" | jq length)

for i in $(seq 0 $((STORY_COUNT - 1))); do
  ISSUE_TITLE=$(echo "$OPEN_STORIES" | jq -r ".[$i].title")
  STORY_ID=$(echo "$ISSUE_TITLE" | grep -oP 'story-\d+' | head -1)

  if [ -z "$STORY_ID" ]; then
    continue
  fi

  "$CHECK_PREREQS" "$STORY_ID" > /dev/null
  CHECK_RESULT=$?

  if [ $CHECK_RESULT -eq 2 ]; then
    echo "WARNING: Could not check prerequisites for $STORY_ID - skipping." >&2
    continue
  fi

  if [ $CHECK_RESULT -eq 0 ]; then
    echo "$STORY_ID"
    exit 0
  fi
done

# No eligible story found
echo ""
exit 0