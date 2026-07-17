#!/usr/bin/env bash
# 10 §4 point 2: "run a smoke script (signup → hire → task → approve via API)."
# Run after every staging deploy, before calling the deploy "done." Exits
# non-zero on any unexpected response — a real gate, not advisory output.
#
# Usage: BASE_URL=https://api-staging.example.com/api/v1 ./smoke-test.sh
set -euo pipefail

BASE_URL="${BASE_URL:?BASE_URL env var required, e.g. https://api-staging.example.com/api/v1}"
POLL_ATTEMPTS="${SMOKE_TEST_POLL_ATTEMPTS:-20}" # 20 * 5s = up to 100s waiting for a real agent to work the task
POLL_INTERVAL_SECONDS=5

slug="smoke-$(date +%s)-${RANDOM}"
email="${slug}@smoke-test.local"

echo "== 1/4: signup =="
signup_body=$(curl -sf -X POST "${BASE_URL}/auth/signup" \
  -H "Content-Type: application/json" \
  -d "{\"companyName\":\"Smoke Test Co\",\"companySlug\":\"${slug}\",\"displayName\":\"Smoke Test Admin\",\"email\":\"${email}\",\"password\":\"correct-horse-battery-staple\"}")
token=$(echo "$signup_body" | jq -r '.token')
company_id=$(echo "$signup_body" | jq -r '.companyId')
if [ "$token" = "null" ] || [ -z "$token" ]; then
  echo "FAIL: signup did not return a token. Response: $signup_body" >&2
  exit 1
fi
echo "  company $company_id created"

echo "== 2/4: hire an agent =="
hire_body=$(curl -sf -X POST "${BASE_URL}/companies/${company_id}/agents" \
  -H "Content-Type: application/json" -H "Authorization: Bearer ${token}" \
  -d '{"name":"Smoke Test Coder","roleTemplateKey":"coder","roleTitle":"Coder","skillTags":["coding"],"modelProvider":"google","modelName":"gemini-3.1-flash-lite"}')
agent_id=$(echo "$hire_body" | jq -r '.id')
if [ "$agent_id" = "null" ] || [ -z "$agent_id" ]; then
  echo "FAIL: hire did not return an agent id. Response: $hire_body" >&2
  exit 1
fi
echo "  agent $agent_id hired"

echo "== 3/4: create a task and wait for it to reach pending_review =="
task_body=$(curl -sf -X POST "${BASE_URL}/companies/${company_id}/tasks" \
  -H "Content-Type: application/json" -H "Authorization: Bearer ${token}" \
  -d '{"title":"Smoke test: write a one-line comment","description":"Reply with a single short Java comment. This is an automated smoke test task — safe to ignore.","requiredSkill":"coding","priority":1}')
task_id=$(echo "$task_body" | jq -r '.task.id') # POST/GET /tasks return a TaskDetailResponse: {task:{...}, subtasks:[], latestArtifact}
if [ "$task_id" = "null" ] || [ -z "$task_id" ]; then
  echo "FAIL: task creation did not return a task id. Response: $task_body" >&2
  exit 1
fi
echo "  task $task_id created — polling..."

status="queued"
for i in $(seq 1 "$POLL_ATTEMPTS"); do
  status=$(curl -sf "${BASE_URL}/tasks/${task_id}" -H "Authorization: Bearer ${token}" | jq -r '.task.status')
  echo "  poll ${i}/${POLL_ATTEMPTS}: status=${status}"
  if [ "$status" = "pending_review" ]; then
    break
  fi
  sleep "$POLL_INTERVAL_SECONDS"
done

if [ "$status" != "pending_review" ]; then
  echo "FAIL: task never reached pending_review within $((POLL_ATTEMPTS * POLL_INTERVAL_SECONDS))s (last status: $status)." >&2
  echo "This can mean the agent never claimed/completed it — check whether GOOGLE_API_KEY is actually configured in this environment (the pre-dispatch gate parks agents silently if not, 12 §9)." >&2
  exit 1
fi

echo "== 4/4: approve =="
approve_status=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}/tasks/${task_id}/approve" \
  -H "Authorization: Bearer ${token}")
if [ "$approve_status" != "200" ]; then
  echo "FAIL: approve returned HTTP $approve_status" >&2
  exit 1
fi

echo "OK: signup -> hire -> task -> approve all succeeded for company ${company_id}."
