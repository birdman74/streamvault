#!/bin/bash
# scripts/check-prerequisites.sh
#
# Single source of truth for "is this story unblocked?"
# Used by next-story.sh (queue selection) and sync-blocked-labels.sh
# (blocked label reconciliation). Change prerequisite rules here only.
#
# A story's prerequisites are the story IDs listed in the
# "## Prerequisites" section of its spec file in docs/specs/.
#
# A prerequisite is met only when its GitHub Issue was closed as
# COMPLETED. A story closed as "not planned" (or as a duplicate) is NOT
# done, so everything that depends on it stays blocked until Brian
# resolves the dependency chain by hand.
#
# Usage: ./scripts/check-prerequisites.sh <story-id>
#
# Optional env:
#   COMPLETED_STORIES  JSON array of titles of story issues closed as
#                      completed. Callers that check many stories in a
#                      loop should fetch this once with the same filter
#                      and export it, to avoid one API call per story.
#
# Output: unmet prerequisite IDs, one per line, to stdout (empty when all met)
# Exit code 0: all prerequisites met (or the story has none)
# Exit code 1: one or more prerequisites not met
# Exit code 2: error (bad usage, missing spec file, or API failure)

SPECS_DIR="docs/specs"
STORY_ID="$1"

# Keeps only issues closed as completed. A null or empty stateReason is
# treated as completed: that is GitHub's default for a plain close.
COMPLETED_FILTER='[.[] | select(.stateReason == "COMPLETED" or .stateReason == null or .stateReason == "") | .title]'

if [ -z "$STORY_ID" ]; then
  echo "Usage: $0 <story-id>" >&2
  exit 2
fi

SPEC_FILE=$(ls "$SPECS_DIR"/${STORY_ID}-*.md 2>/dev/null | head -1)

if [ -z "$SPEC_FILE" ]; then
  echo "ERROR: No spec file found for $STORY_ID in $SPECS_DIR" >&2
  exit 2
fi

if [ -z "$COMPLETED_STORIES" ]; then
  COMPLETED_STORIES=$(gh issue list \
    --label "story" \
    --state closed \
    --limit 500 \
    --json title,stateReason \
    --jq "$COMPLETED_FILTER")
  if [ $? -ne 0 ] || [ -z "$COMPLETED_STORIES" ]; then
    echo "ERROR: Could not fetch completed story issues" >&2
    exit 2
  fi
fi

PREREQS=$(awk '/^## Prerequisites/{found=1; next} found && /^##/{exit} found{print}' "$SPEC_FILE" \
  | grep -oP 'story-\d+' \
  | sort -u)

UNMET_PREREQS=""
for PREREQ in $PREREQS; do
  # Word-boundary match so story-01 can never match story-010
  PREREQ_COMPLETED=$(echo "$COMPLETED_STORIES" | jq -r --arg id "$PREREQ" \
    'any(.[]; test("\\b" + $id + "\\b"))')
  if [ "$PREREQ_COMPLETED" != "true" ]; then
    UNMET_PREREQS="$UNMET_PREREQS $PREREQ"
  fi
done

if [ -n "$UNMET_PREREQS" ]; then
  for PREREQ in $UNMET_PREREQS; do
    echo "$PREREQ"
  done
  exit 1
fi

exit 0