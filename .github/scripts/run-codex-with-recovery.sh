#!/usr/bin/env bash

set -Eeuo pipefail

prompt_file="${1:-prompt.md}"
max_attempts="${CODEX_MAX_ATTEMPTS:-5}"
attempt_timeout_minutes="${CODEX_ATTEMPT_TIMEOUT_MINUTES:-45}"
retry_base_seconds="${CODEX_RETRY_BASE_SECONDS:-15}"
retry_max_seconds="${CODEX_RETRY_MAX_SECONDS:-120}"
recap_file="${CODEX_RECAP_FILE:-ai-response-recap.txt}"
state_dir="${CODEX_RECOVERY_DIR:-${RUNNER_TEMP:-/tmp}/codex-recovery}"
run_label="${GITHUB_RUN_ATTEMPT:-local}"
execution_log="$state_dir/execution-$run_label.log"

: "${CODEX_HOME:?CODEX_HOME must point to a persistent directory for session recovery}"

for value_name in max_attempts attempt_timeout_minutes retry_base_seconds retry_max_seconds; do
  value="${!value_name}"
  if [[ ! "$value" =~ ^[0-9]+$ ]]; then
    echo "::error::$value_name must be a non-negative integer, got: $value"
    exit 2
  fi
done

if (( max_attempts < 1 || attempt_timeout_minutes < 1 )); then
  echo "::error::CODEX_MAX_ATTEMPTS and CODEX_ATTEMPT_TIMEOUT_MINUTES must be greater than zero"
  exit 2
fi

if [[ ! -f "$prompt_file" ]]; then
  echo "::error::Prompt file not found: $prompt_file"
  exit 2
fi

mkdir -p "$CODEX_HOME" "$state_dir" "$(dirname "$recap_file")"
: > "$execution_log"

checkpoint_dir="$CODEX_HOME/recovery-checkpoint"
mkdir -p "$checkpoint_dir"

base_sha="${CODEX_BASE_SHA:-}"
if [[ -z "$base_sha" && -s "$checkpoint_dir/base-sha.txt" ]]; then
  base_sha="$(<"$checkpoint_dir/base-sha.txt")"
fi
if [[ -z "$base_sha" ]]; then
  base_sha="$(git rev-parse HEAD)"
fi
printf '%s\n' "$base_sha" > "$checkpoint_dir/base-sha.txt"

session_id=""
if [[ -s "$checkpoint_dir/session-id.txt" ]]; then
  session_id="$(<"$checkpoint_dir/session-id.txt")"
fi

checkpoint_worktree() {
  local patch_tmp="$checkpoint_dir/worktree.patch.tmp"
  local untracked_list="$checkpoint_dir/untracked-files.list"
  local checkpoint_status=0
  local command_status

  git diff --binary --full-index "$base_sha" -- . > "$patch_tmp"
  command_status=$?
  if (( command_status == 0 )); then
    mv "$patch_tmp" "$checkpoint_dir/worktree.patch"
    command_status=$?
  fi
  if (( command_status != 0 )); then
    checkpoint_status="$command_status"
    rm -f "$patch_tmp"
  fi

  git ls-files --others --exclude-standard -z > "$untracked_list"
  command_status=$?
  if (( command_status == 0 )); then
    if [[ -s "$untracked_list" ]]; then
      tar --null --files-from="$untracked_list" -czf "$checkpoint_dir/untracked-files.tar.gz"
      command_status=$?
    else
      rm -f "$checkpoint_dir/untracked-files.tar.gz"
      command_status=$?
    fi
  fi
  if (( command_status != 0 && checkpoint_status == 0 )); then
    checkpoint_status="$command_status"
  fi

  if [[ -n "$session_id" ]]; then
    printf '%s\n' "$session_id" > "$checkpoint_dir/session-id.txt"
    command_status=$?
    if (( command_status != 0 && checkpoint_status == 0 )); then
      checkpoint_status="$command_status"
    fi
  fi

  return "$checkpoint_status"
}

write_summary() {
  local result="$1"
  local attempts_used="$2"
  local reason="$3"
  local last_message_file="$4"
  local resumed_count="$5"

  {
    echo "Codex recovery result: $result"
    echo "Attempts used: $attempts_used/$max_attempts"
    echo "Resume turns: $resumed_count"
    if [[ -n "$session_id" ]]; then
      echo "Session ID: $session_id"
    else
      echo "Session ID: unavailable"
    fi
    if [[ -n "$reason" ]]; then
      echo "Last status: $reason"
    fi
    echo
    echo "Final Codex response:"
    if [[ -s "$last_message_file" ]]; then
      cat "$last_message_file"
    else
      echo "No final response was produced. See the codex-recovery diagnostic artifact."
    fi
  } > "$recap_file"

  cp "$recap_file" "$state_dir/summary-$run_label.txt"
}

saved_session_exists() {
  [[ -d "$CODEX_HOME/sessions" ]] && find "$CODEX_HOME/sessions" -type f -name '*.jsonl' -print -quit | grep -q .
}

extract_session_id() {
  jq -Rsr '
    [split("\n")[] | fromjson? | select(.type == "thread.started") | .thread_id]
    | last // empty
  ' "$1"
}

has_event() {
  local event_type="$1"
  local log_file="$2"
  jq -Rsr --arg event_type "$event_type" '
    [split("\n")[] | fromjson? | select(.type == $event_type)] | length > 0
  ' "$log_file"
}

has_blocked_response() {
  jq -Rsr '
    [
      split("\n")[]
      | fromjson?
      | select(.type == "item.completed" and .item.type == "agent_message")
      | (.item.text // "")
    ]
    | (last // "")
    | test(
        "(content|request|response) (was|has been) (blocked|filtered)|blocked by (the )?(content|safety) filter|i (cannot|can.t) (help|assist|comply) with";
        "i"
      )
  ' "$1"
}

classify_failure() {
  local command_status="$1"
  local tee_status="$2"
  local log_file="$3"

  if (( command_status == 124 )); then
    echo "attempt timed out after ${attempt_timeout_minutes} minute(s)"
  elif (( command_status == 137 || command_status == 143 )); then
    echo "attempt was terminated by a signal"
  elif (( tee_status != 0 )); then
    echo "failed to persist the Codex event stream (tee exit $tee_status)"
  elif [[ "$(has_event turn.failed "$log_file")" == "true" ]]; then
    echo "Codex reported turn.failed"
  elif [[ "$(has_event error "$log_file")" == "true" ]]; then
    echo "Codex reported an API or stream error"
  elif [[ "$(has_blocked_response "$log_file")" == "true" ]]; then
    echo "provider reported blocked or filtered content"
  elif (( command_status != 0 )); then
    echo "Codex exited with status $command_status"
  else
    echo "event stream ended without turn.completed"
  fi
}

retry_delay() {
  local attempt_number="$1"
  local delay="$retry_base_seconds"
  local step

  for (( step = 1; step < attempt_number; step++ )); do
    delay=$((delay * 2))
    if (( delay >= retry_max_seconds )); then
      delay="$retry_max_seconds"
      break
    fi
  done

  if (( delay > retry_max_seconds )); then
    delay="$retry_max_seconds"
  fi
  if (( delay > 0 && delay < retry_max_seconds )); then
    delay=$((delay + RANDOM % 6))
    if (( delay > retry_max_seconds )); then
      delay="$retry_max_seconds"
    fi
  fi
  echo "$delay"
}

last_message_file=""
last_failure=""
resume_count=0

for (( attempt = 1; attempt <= max_attempts; attempt++ )); do
  attempt_log="$state_dir/attempt-${run_label}-${attempt}.jsonl"
  last_message_file="$state_dir/last-message-${run_label}-${attempt}.txt"
  rm -f "$last_message_file"

  recovery_prompt="The previous Codex execution did not finish successfully. Continue the original task in this same session from the current workspace state. Inspect the transcript and existing edits first. Do not restart completed work, duplicate changes, or revert correct progress. Finish only the remaining work and run appropriate verification. If some content or operation was blocked by a safety/content policy, do not bypass the policy; continue the allowed parts and use a safe, compliant alternative. If the task is already complete, verify it and return the final summary. Previous failure: ${last_failure:-workflow interruption or restored session}."

  if [[ -n "$session_id" ]]; then
    mode="resume session $session_id"
    command=(
      codex exec resume
      --dangerously-bypass-approvals-and-sandbox
      --json
      --output-last-message "$last_message_file"
      "$session_id"
      "$recovery_prompt"
    )
    resume_count=$((resume_count + 1))
    use_prompt_stdin=false
  elif saved_session_exists; then
    mode="resume most recent saved session"
    command=(
      codex exec resume
      --last
      --dangerously-bypass-approvals-and-sandbox
      --json
      --output-last-message "$last_message_file"
      "$recovery_prompt"
    )
    resume_count=$((resume_count + 1))
    use_prompt_stdin=false
  else
    mode="start initial session"
    command=(
      codex exec
      --dangerously-bypass-approvals-and-sandbox
      --json
      --output-last-message "$last_message_file"
      -
    )
    use_prompt_stdin=true
  fi

  {
    echo
    echo "===== Codex attempt $attempt/$max_attempts: $mode ====="
  } | tee -a "$execution_log"

  set +e
  if [[ "$use_prompt_stdin" == "true" ]]; then
    timeout --signal=TERM --kill-after=30s "${attempt_timeout_minutes}m" \
      "${command[@]}" < "$prompt_file" 2>&1 | tee "$attempt_log"
  else
    timeout --signal=TERM --kill-after=30s "${attempt_timeout_minutes}m" \
      "${command[@]}" 2>&1 | tee "$attempt_log"
  fi
  pipeline_status=("${PIPESTATUS[@]}")
  set -e

  command_status="${pipeline_status[0]}"
  tee_status="${pipeline_status[1]}"
  cat "$attempt_log" >> "$execution_log"

  discovered_session_id="$(extract_session_id "$attempt_log")"
  if [[ -n "$discovered_session_id" ]]; then
    session_id="$discovered_session_id"
    printf '%s\n' "$session_id" > "$checkpoint_dir/session-id.txt"
  fi

  set +e
  checkpoint_worktree
  checkpoint_status=$?
  set -e
  if (( checkpoint_status != 0 )); then
    echo "::warning::Could not persist the worktree checkpoint (exit $checkpoint_status)"
  fi

  turn_completed="$(has_event turn.completed "$attempt_log")"
  turn_failed="$(has_event turn.failed "$attempt_log")"
  blocked_response="$(has_blocked_response "$attempt_log")"

  if (( command_status == 0 && tee_status == 0 )) \
    && [[ "$turn_completed" == "true" && "$turn_failed" == "false" && "$blocked_response" == "false" ]]; then
    echo "Codex completed successfully on attempt $attempt."
    write_summary "success" "$attempt" "completed" "$last_message_file" "$resume_count"
    exit 0
  fi

  last_failure="$(classify_failure "$command_status" "$tee_status" "$attempt_log")"
  echo "::warning::Codex attempt $attempt failed: $last_failure"

  if (( attempt < max_attempts )); then
    delay="$(retry_delay "$attempt")"
    echo "Resuming the same Codex session after ${delay}s backoff."
    if (( delay > 0 )); then
      sleep "$delay"
    fi
  fi
done

write_summary "failed" "$max_attempts" "$last_failure" "$last_message_file" "$resume_count"
echo "::error::Codex did not complete after $max_attempts attempts. The session and worktree checkpoint were preserved."
exit 1
