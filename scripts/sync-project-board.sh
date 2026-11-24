#!/bin/bash
# scripts/sync-project-board.sh
#
# Moves story issues to the project board column that matches their LIVE
# state, read from the GitHub API when the script runs (never from an event
# payload, which can be stale by the time a queued run starts):
#
#   closed               -> Done
#   label 'in-progress'  -> In Progress
#   label 'blocked'      -> Backlog
#   anything else        -> Ready
#
# The board, its Status field and the column options are found by name, so
# no opaque node IDs are hard-coded. An issue that is not on the board yet is
# added to it. Non-story issues are skipped.
#
# Usage:
#   sync-project-board.sh <issue-number>   sync one issue
#   sync-project-board.sh --all            sync every story issue (open and closed)
#
# Required env:
#   GH_TOKEN   classic PAT from the board owner with 'project' and 'repo'
#              scopes (PROJECT_BOARD_GH_TOKEN). GITHUB_TOKEN and fine-grained
#              PATs cannot reach a user-owned Projects V2 board.
#   GH_REPO    owner/repo
# Optional env (the workflow sets these from .github/pipeline-config.env):
#   PROJECT_OWNER        user that owns the board (default: owner of GH_REPO)
#   PROJECT_TITLE        board title (default: StreamVault)
#   STATUS_FIELD         single-select field name (default: Status)
#   COLUMN_DONE          default: Done
#   COLUMN_IN_PROGRESS   default: In Progress
#   COLUMN_BLOCKED       default: Backlog
#   COLUMN_READY         default: Ready
#   DRY_RUN              'true' reports moves without changing anything
#
# Output: one line per issue: MOVE, ADD, OK, SKIP or FAIL
# Exit code 0: every issue synced (or already correct)
# Exit code 1: one or more issues failed
# Exit code 2: usage or setup error (token, board, field or column not found)

set -o pipefail

PROJECT_OWNER="${PROJECT_OWNER:-${GH_REPO%%/*}}"
PROJECT_TITLE="${PROJECT_TITLE:-StreamVault}"
STATUS_FIELD="${STATUS_FIELD:-Status}"
COLUMN_DONE="${COLUMN_DONE:-Done}"
COLUMN_IN_PROGRESS="${COLUMN_IN_PROGRESS:-In Progress}"
COLUMN_BLOCKED="${COLUMN_BLOCKED:-Backlog}"
COLUMN_READY="${COLUMN_READY:-Ready}"
DRY_RUN="${DRY_RUN:-false}"

if [ -z "$GH_TOKEN" ]; then
  echo "ERROR: GH_TOKEN is empty. Is the PROJECT_BOARD_GH_TOKEN secret set?" >&2
  exit 2
fi
if [ -z "$GH_REPO" ] || [[ "$GH_REPO" != */* ]]; then
  echo "ERROR: GH_REPO must be set (owner/repo)" >&2
  exit 2
fi
if [ $# -ne 1 ]; then
  echo "Usage: $0 <issue-number> | --all" >&2
  exit 2
fi

REPO_OWNER="${GH_REPO%%/*}"
REPO_NAME="${GH_REPO#*/}"

# ---------------------------------------------------------------------------
# Resolve the board, the Status field and the four column options by name
# ---------------------------------------------------------------------------
PROJECTS_JSON=$(gh api graphql \
  -f query='
    query($login: String!, $field: String!) {
      user(login: $login) {
        projectsV2(first: 100) {
          nodes {
            id
            title
            field(name: $field) {
              ... on ProjectV2SingleSelectField { id options { id name } }
            }
          }
        }
      }
    }' \
  -f login="$PROJECT_OWNER" \
  -f field="$STATUS_FIELD")
if [ $? -ne 0 ] || [ -z "$PROJECTS_JSON" ]; then
  echo "ERROR: could not list $PROJECT_OWNER's project boards. The token needs the 'project' scope." >&2
  exit 2
fi

BOARD_JSON=$(echo "$PROJECTS_JSON" | jq -c --arg title "$PROJECT_TITLE" \
  '[.data.user.projectsV2.nodes[] | select(.title == $title)]')
BOARD_COUNT=$(echo "$BOARD_JSON" | jq 'length')
if [ "$BOARD_COUNT" != "1" ]; then
  echo "ERROR: expected exactly one board titled '$PROJECT_TITLE' owned by $PROJECT_OWNER, found $BOARD_COUNT" >&2
  exit 2
fi

PROJECT_ID=$(echo "$BOARD_JSON" | jq -r '.[0].id')
FIELD_ID=$(echo "$BOARD_JSON" | jq -r '.[0].field.id // empty')
if [ -z "$FIELD_ID" ]; then
  echo "ERROR: board '$PROJECT_TITLE' has no single-select field named '$STATUS_FIELD'" >&2
  exit 2
fi

# Prints the option ID for a column name, or nothing
option_id_for() {
  echo "$BOARD_JSON" | jq -r --arg name "$1" '.[0].field.options[] | select(.name == $name) | .id' | head -1
}

for COLUMN_NAME in "$COLUMN_DONE" "$COLUMN_IN_PROGRESS" "$COLUMN_BLOCKED" "$COLUMN_READY"; do
  if [ -z "$(option_id_for "$COLUMN_NAME")" ]; then
    echo "ERROR: field '$STATUS_FIELD' on board '$PROJECT_TITLE' has no option named '$COLUMN_NAME'" >&2
    exit 2
  fi
done

# ---------------------------------------------------------------------------
# Sync one issue. Returns 0 on success (including SKIP and OK), 1 on failure.
# ---------------------------------------------------------------------------
sync_issue() {
  local issue_number="$1"
  local issue_json issue_id issue_title issue_state labels target_column
  local item_id current_column option_id

  issue_json=$(gh api graphql \
    -f query='
      query($owner: String!, $repo: String!, $number: Int!, $field: String!) {
        repository(owner: $owner, name: $repo) {
          issue(number: $number) {
            id
            title
            state
            labels(first: 100) { nodes { name } }
            projectItems(first: 50) {
              nodes {
                id
                project { id }
                fieldValueByName(name: $field) {
                  ... on ProjectV2ItemFieldSingleSelectValue { name }
                }
              }
            }
          }
        }
      }' \
    -f owner="$REPO_OWNER" \
    -f repo="$REPO_NAME" \
    -F number="$issue_number" \
    -f field="$STATUS_FIELD")
  if [ $? -ne 0 ] || [ "$(echo "$issue_json" | jq -r '.data.repository.issue.id // empty')" == "" ]; then
    echo "FAIL #$issue_number: could not read issue"
    return 1
  fi

  issue_id=$(echo "$issue_json" | jq -r '.data.repository.issue.id')
  issue_title=$(echo "$issue_json" | jq -r '.data.repository.issue.title')
  issue_state=$(echo "$issue_json" | jq -r '.data.repository.issue.state')
  labels=$(echo "$issue_json" | jq -r '.data.repository.issue.labels.nodes[].name')

  if ! echo "$labels" | grep -qx 'story'; then
    echo "SKIP #$issue_number $issue_title: not a story issue"
    return 0
  fi

  if [ "$issue_state" == "CLOSED" ]; then
    target_column="$COLUMN_DONE"
  elif echo "$labels" | grep -qx 'in-progress'; then
    target_column="$COLUMN_IN_PROGRESS"
  elif echo "$labels" | grep -qx 'blocked'; then
    target_column="$COLUMN_BLOCKED"
  else
    target_column="$COLUMN_READY"
  fi
  option_id=$(option_id_for "$target_column")

  item_id=$(echo "$issue_json" | jq -r --arg project "$PROJECT_ID" \
    '[.data.repository.issue.projectItems.nodes[] | select(.project.id == $project)][0].id // empty')
  current_column=$(echo "$issue_json" | jq -r --arg project "$PROJECT_ID" \
    '[.data.repository.issue.projectItems.nodes[] | select(.project.id == $project)][0].fieldValueByName.name // empty')

  if [ -z "$item_id" ]; then
    if [ "$DRY_RUN" == "true" ]; then
      echo "ADD #$issue_number $issue_title: not on board, would add to $target_column"
      return 0
    fi
    item_id=$(gh api graphql \
      -f query='
        mutation($project: ID!, $content: ID!) {
          addProjectV2ItemById(input: { projectId: $project, contentId: $content }) { item { id } }
        }' \
      -f project="$PROJECT_ID" \
      -f content="$issue_id" \
      --jq '.data.addProjectV2ItemById.item.id')
    if [ $? -ne 0 ] || [ -z "$item_id" ]; then
      echo "FAIL #$issue_number $issue_title: could not add to board"
      return 1
    fi
    echo "ADD #$issue_number $issue_title: added to board"
    current_column=""
  fi

  if [ "$current_column" == "$target_column" ]; then
    echo "OK #$issue_number $issue_title: $target_column"
    return 0
  fi

  if [ "$DRY_RUN" == "true" ]; then
    echo "MOVE #$issue_number $issue_title: ${current_column:-no column} -> $target_column (dry run)"
    return 0
  fi

  if ! gh api graphql \
      -f query='
        mutation($project: ID!, $item: ID!, $field: ID!, $option: String!) {
          updateProjectV2ItemFieldValue(input: {
            projectId: $project, itemId: $item, fieldId: $field,
            value: { singleSelectOptionId: $option }
          }) { projectV2Item { id } }
        }' \
      -f project="$PROJECT_ID" \
      -f item="$item_id" \
      -f field="$FIELD_ID" \
      -f option="$option_id" > /dev/null; then
    echo "FAIL #$issue_number $issue_title: could not move to $target_column"
    return 1
  fi
  echo "MOVE #$issue_number $issue_title: ${current_column:-no column} -> $target_column"
  return 0
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
[ "$DRY_RUN" == "true" ] && echo "DRY RUN: the board will not be changed"

if [ "$1" == "--all" ]; then
  ISSUE_NUMBERS=$(gh issue list --repo "$GH_REPO" --label story --state all --limit 500 \
    --json number --jq 'sort_by(.number) | .[].number')
  if [ $? -ne 0 ]; then
    echo "ERROR: could not list story issues" >&2
    exit 2
  fi
elif [[ "$1" =~ ^[0-9]+$ ]]; then
  ISSUE_NUMBERS="$1"
else
  echo "Usage: $0 <issue-number> | --all" >&2
  exit 2
fi

CHANGES=0
FAILURES=0
for ISSUE_NUMBER in $ISSUE_NUMBERS; do
  RESULT_LINE=$(sync_issue "$ISSUE_NUMBER") && RESULT_CODE=0 || RESULT_CODE=$?
  echo "$RESULT_LINE"
  [ $RESULT_CODE -ne 0 ] && FAILURES=$((FAILURES + 1))
  echo "$RESULT_LINE" | grep -qE '^(MOVE|ADD) ' && CHANGES=$((CHANGES + 1))
done

echo "Done: $CHANGES change(s), $FAILURES failure(s)"
[ $FAILURES -eq 0 ] && exit 0 || exit 1