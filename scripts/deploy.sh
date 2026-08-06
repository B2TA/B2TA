#!/usr/bin/env bash
# Build and deploy the frontend to AWS Amplify (manual zip deploy).
#
# Usage: ./scripts/deploy.sh
#
# Amplify allows only one in-flight job per branch, so any PENDING/RUNNING job
# is cancelled first — otherwise create-deployment fails with
# "The last job(deployment) N was not finished".
set -euo pipefail

APP_ID="${AMPLIFY_APP_ID:-dpezcexvnbo0g}"
BRANCH="${AMPLIFY_BRANCH:-main}"
REGION="${AWS_REGION:-us-east-1}"

cd "$(dirname "$0")/.."

echo "==> Building"
mise exec -- pnpm build

echo "==> Cancelling any in-flight jobs"
aws amplify list-jobs --app-id "$APP_ID" --branch-name "$BRANCH" --region "$REGION" \
  --query "jobSummaries[?status=='PENDING'||status=='RUNNING'].jobId" --output text \
  | tr '\t' '\n' | while read -r job; do
      [ -n "$job" ] || continue
      echo "    stopping job $job"
      aws amplify stop-job --app-id "$APP_ID" --branch-name "$BRANCH" \
        --job-id "$job" --region "$REGION" >/dev/null
    done

echo "==> Packaging dist/"
ZIP="$(mktemp -t b2ta-dist-XXXXXX).zip"
trap 'rm -f "$ZIP"' EXIT
(cd dist && zip -qr "$ZIP" .)

echo "==> Creating deployment"
OUT=$(aws amplify create-deployment --app-id "$APP_ID" --branch-name "$BRANCH" \
        --region "$REGION" --output json)
URL=$(printf '%s' "$OUT" | python3 -c 'import sys,json;print(json.load(sys.stdin)["zipUploadUrl"])')
JOB=$(printf '%s' "$OUT" | python3 -c 'import sys,json;print(json.load(sys.stdin)["jobId"])')

echo "==> Uploading (job $JOB)"
curl -sf -o /dev/null -X PUT -T "$ZIP" "$URL"

aws amplify start-deployment --app-id "$APP_ID" --branch-name "$BRANCH" \
  --job-id "$JOB" --region "$REGION" >/dev/null

echo "==> Waiting for job $JOB"
for _ in $(seq 1 60); do
  STATUS=$(aws amplify get-job --app-id "$APP_ID" --branch-name "$BRANCH" \
             --job-id "$JOB" --region "$REGION" --query 'job.summary.status' --output text)
  case "$STATUS" in
    SUCCEED) break ;;
    FAILED|CANCELLED) echo "Deployment $STATUS" >&2; exit 1 ;;
  esac
  sleep 5
done
[ "$STATUS" = "SUCCEED" ] || { echo "Timed out (last: $STATUS)" >&2; exit 1; }

SITE="https://${BRANCH}.${APP_ID}.amplifyapp.com"
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$SITE/")
echo "==> $STATUS — $SITE (HTTP $CODE)"
[ "$CODE" = "200" ] || exit 1
