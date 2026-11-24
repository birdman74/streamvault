#!/bin/bash
# scripts/pipeline-config.sh
#
# Reads and validates .github/pipeline-config.env, the single home of every
# pipeline knob (accounts, launchers, round limits, timing, board names).
# The file is parsed, never sourced or evaluated, so a value can never run
# as code.
#
# Usage:
#   source scripts/pipeline-config.sh
#       Loads every key as a shell variable (and exports it). Exits the
#       calling script with code 2 if the file is missing or invalid.
#   scripts/pipeline-config.sh
#       Validates and prints KEY=value lines. When GITHUB_OUTPUT is set the
#       same lines are appended to it, so every key becomes a step output.
#   scripts/pipeline-config.sh get <KEY>
#       Prints one value.
#
# Optional env:
#   PIPELINE_CONFIG_FILE   path to the config file
#                          (default: .github/pipeline-config.env next to scripts/)
#
# Exit code 0: valid
# Exit code 2: missing file, unknown or missing key, or invalid value

_PIPELINE_CONFIG_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
_PIPELINE_CONFIG_FILE="${PIPELINE_CONFIG_FILE:-$_PIPELINE_CONFIG_DIR/../.github/pipeline-config.env}"

_PIPELINE_LOGIN_KEYS="OWNER_LOGIN BOT_LOGIN ESCALATION_ASSIGNEE"
_PIPELINE_TEXT_KEYS="PO_GIT_AUTHOR DEV_GIT_AUTHOR TEST_GIT_AUTHOR VERIFY_COMMAND PROJECT_TITLE STATUS_FIELD COLUMN_DONE COLUMN_IN_PROGRESS COLUMN_BLOCKED COLUMN_READY"
_PIPELINE_PATH_KEYS="PO_LAUNCHER DEV_LAUNCHER TEST_LAUNCHER"
_PIPELINE_NUMBER_KEYS="DESIGN_ROUND_LIMIT IMPL_FEEDBACK_ROUND_LIMIT DEV_FIX_ROUND_LIMIT PO_QUESTION_LIMIT PERSONA_TIMEOUT_MINUTES OUTCOME_CHECK_ATTEMPTS OUTCOME_CHECK_INTERVAL_SECONDS API_PROPAGATION_WAIT_SECONDS"
_PIPELINE_ALL_KEYS="$_PIPELINE_LOGIN_KEYS $_PIPELINE_TEXT_KEYS $_PIPELINE_PATH_KEYS $_PIPELINE_NUMBER_KEYS"

# Parses and validates the file into variables. Returns 0 or 2.
_pipeline_config_load() {
  local line key value line_number=0 errors=0
  local -A seen=()

  if [ ! -f "$_PIPELINE_CONFIG_FILE" ]; then
    echo "ERROR: pipeline config not found: $_PIPELINE_CONFIG_FILE" >&2
    return 2
  fi

  while IFS= read -r line || [ -n "$line" ]; do
    line_number=$((line_number + 1))
    line="${line%$'\r'}"
    [[ "$line" =~ ^[[:space:]]*(#|$) ]] && continue
    if [[ ! "$line" =~ ^([A-Z][A-Z0-9_]*)=(.*)$ ]]; then
      echo "ERROR: pipeline config line $line_number is not KEY=value: $line" >&2
      errors=$((errors + 1))
      continue
    fi
    key="${BASH_REMATCH[1]}"
    value="${BASH_REMATCH[2]}"
    # Trim surrounding whitespace
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    if [[ " $_PIPELINE_ALL_KEYS " != *" $key "* ]]; then
      echo "ERROR: pipeline config line $line_number has unknown key $key" >&2
      errors=$((errors + 1))
      continue
    fi
    if [ -n "${seen[$key]}" ]; then
      echo "ERROR: pipeline config key $key is set twice (lines ${seen[$key]} and $line_number)" >&2
      errors=$((errors + 1))
      continue
    fi
    seen[$key]=$line_number
    printf -v "$key" '%s' "$value"
    export "${key?}"
  done < "$_PIPELINE_CONFIG_FILE"

  for key in $_PIPELINE_ALL_KEYS; do
    value="${!key}"
    if [ -z "${seen[$key]}" ] || [ -z "$value" ]; then
      echo "ERROR: pipeline config is missing $key" >&2
      errors=$((errors + 1))
      continue
    fi
    if [[ " $_PIPELINE_NUMBER_KEYS " == *" $key "* ]] && [[ ! "$value" =~ ^[1-9][0-9]*$ ]]; then
      echo "ERROR: pipeline config $key must be a positive whole number, got '$value'" >&2
      errors=$((errors + 1))
    fi
    if [[ " $_PIPELINE_LOGIN_KEYS " == *" $key "* ]] && [[ ! "$value" =~ ^[A-Za-z0-9-]+$ ]]; then
      echo "ERROR: pipeline config $key is not a valid GitHub login: '$value'" >&2
      errors=$((errors + 1))
    fi
    if [[ " $_PIPELINE_PATH_KEYS " == *" $key "* ]] && [[ "$value" != /* ]]; then
      echo "ERROR: pipeline config $key must be an absolute path, got '$value'" >&2
      errors=$((errors + 1))
    fi
  done

  if [[ "${PERSONA_TIMEOUT_MINUTES:-0}" =~ ^[0-9]+$ ]] && [ "${PERSONA_TIMEOUT_MINUTES:-0}" -gt 330 ]; then
    echo "ERROR: PERSONA_TIMEOUT_MINUTES must be at most 330 (the 360-minute job limit would cancel the run first, skipping escalation)" >&2
    errors=$((errors + 1))
  fi

  [ $errors -eq 0 ] && return 0 || return 2
}

if [[ "${BASH_SOURCE[0]}" != "$0" ]]; then
  # Sourced: load into the caller, or stop the caller on an invalid config
  _pipeline_config_load || exit 2
  return 0
fi

_pipeline_config_load || exit 2

if [ "$1" == "get" ]; then
  if [ -z "$2" ] || [[ " $_PIPELINE_ALL_KEYS " != *" $2 "* ]]; then
    echo "Usage: $0 get <KEY>" >&2
    exit 2
  fi
  echo "${!2}"
  exit 0
fi

for key in $_PIPELINE_ALL_KEYS; do
  echo "$key=${!key}"
  [ -n "$GITHUB_OUTPUT" ] && echo "$key=${!key}" >> "$GITHUB_OUTPUT"
done
exit 0
