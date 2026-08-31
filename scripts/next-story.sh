#!/bin/bash
# scripts/next-story.sh
#
# Determines the next eligible story to start by querying GitHub Issues.
# State is inferred from GitHub Issues — no state files needed.
#
# A story is eligible when:
# 1. Its GitHub Issue is open and labeled 'story'
# 2. No other story issue is currently labeled 'in-progress'
# 3. All prerequisite stories (listed in the spec file ## Prerequisites section)
#    have their GitHub Issues closed
#
# Usage: ./scripts/next-story.sh
# Output: story ID (e.g. "story-005") to stdout, or empty string if nothing eligible
# Exit code 1: a story is already in-progress (error condition)
# Exit code 0: success

SPECS_DIR="docs/specs"

# Check for any story already in-progress
IN_PROGRESS=$(gh issue list \
  --label "story,in-progress" \
  --state open \
  --json number,title \
  --jq '.[0].title' 2>/dev/null)

if [ -n "$IN_PROGRESS" ]; then
  echo "ERROR: A story is already in-progress: $IN_PROGRESS. Complete it before starting a new story." >&2
  exit 1
fi

# Get all open story issues sorted by issue number (proxy for story number)
OPEN_STORIES=$(gh issue list \
  --label "story" \
  --state open \
  --json number,title,labels \
  --jq '[.[] | select(.labels[].name == "story")] | sort_by(.number)' 2>/dev/null)

if [ -z "$OPEN_STORIES" ] || [ "$OPEN_STORIES" == "[]" ]; then
  echo ""
  exit 0
fi

# Get all closed story issue titles (completed stories) for prerequisite checking
CLOSED_STORIES=$(gh issue list \
  --label "story" \
  --state closed \
  --json title \
  --jq '[.[].title]' 2>/dev/null)

# Iterate open stories in order and find the first eligible one
STORY_COUNT=$(echo "$OPEN_STORIES" | jq length)

for i in $(seq 0 $((STORY_COUNT - 1))); do
  ISSUE_TITLE=$(echo "$OPEN_STORIES" | jq -r ".[$i].title")
  STORY_ID=$(echo "$ISSUE_TITLE" | grep -oP 'story-\d+')

  if [ -z "$STORY_ID" ]; then
    continue
  fi

  # Find the spec file for this story
  SPEC_FILE=$(ls "$SPECS_DIR"/${STORY_ID}-*.md 2>/dev/null | head -1)

  if [ -z "$SPEC_FILE" ]; then
    echo "WARNING: No spec file found for $STORY_ID — skipping." >&2
    continue
  fi

  # Read prerequisites from spec file
  PREREQS=$(awk '/^## Prerequisites/{found=1; next} found && /^##/{exit} found{print}' "$SPEC_FILE" | grep -oP 'story-\d+')

  PREREQS_MET=true
  for PREREQ in $PREREQS; do
    # Check if prerequisite story's issue is closed
    PREREQ_CLOSED=$(echo "$CLOSED_STORIES" | jq -r ".[] | select(test(\"$PREREQ\"))" 2>/dev/null)
    if [ -z "$PREREQ_CLOSED" ]; then
      PREREQS_MET=false
      break
    fi
  done

  if [ "$PREREQS_MET" == "true" ]; then
    echo "$STORY_ID"
    exit 0
  fi
done

# No eligible story found
echo ""
exit 0