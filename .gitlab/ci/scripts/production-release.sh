#!/bin/sh

set -eu

DEPLOY_CLUSTER_NAME="a707-dev-cluster"
DEPLOY_BACKEND_SERVICE="a707-dev-backend"
DEPLOY_YJS_SERVICE="a707-dev-yjs"
DEPLOY_BACKEND_TASK_FAMILY="a707-dev-backend"
DEPLOY_YJS_TASK_FAMILY="a707-dev-yjs"
DEPLOY_MIGRATION_TASK_FAMILY="a707-dev-migration"
DEPLOY_BACKEND_CONTAINER="backend"
DEPLOY_YJS_CONTAINER="yjs"
DEPLOY_MIGRATION_CONTAINER="migration"
DEPLOY_BACKEND_REPOSITORY="a707-dev-backend"
DEPLOY_YJS_REPOSITORY="a707-dev-yjs"
DEPLOY_MIGRATION_REPOSITORY="a707-dev-migration"

fail_release() {
  echo "Production release failed: $1" >&2
  exit 1
}

for release_command in aws curl jq grep mktemp; do
  command -v "$release_command" >/dev/null 2>&1 \
    || fail_release "$release_command is unavailable."
done

sh .gitlab/ci/scripts/validate-production-inputs.sh release
[ "${AWS_REGION:-}" = "ap-northeast-2" ] \
  || fail_release "the AWS region does not match the deployment contract."

deploy_image_tag="git-${CI_COMMIT_SHA}"
release_work_dir="$(mktemp -d)" \
  || fail_release "a temporary workspace could not be created."
trap 'rm -rf -- "$release_work_dir"' 0 1 2 15

[ -n "${CI_API_V4_URL:-}" ] \
  || fail_release "the GitLab API URL is unavailable."
[ -n "${CI_PROJECT_ID:-}" ] \
  || fail_release "the GitLab project identifier is unavailable."
[ -n "${CI_JOB_TOKEN:-}" ] \
  || fail_release "the GitLab job token is unavailable."
printf '%s' "$CI_PROJECT_ID" | grep -Eq '^[0-9]+$' \
  || fail_release "the GitLab project identifier is invalid."

gitlab_api_url="${CI_API_V4_URL%/}"
case "$gitlab_api_url" in
  https://*) ;;
  *) fail_release "the GitLab API must use HTTPS." ;;
esac

curl --fail --silent --show-error \
  --connect-timeout 10 --max-time 30 \
  --header "JOB-TOKEN: $CI_JOB_TOKEN" \
  "${gitlab_api_url}/projects/${CI_PROJECT_ID}/repository/branches?search=%5Emain%24&per_page=100" \
  >"$release_work_dir/main-branch.json" 2>/dev/null \
  || fail_release "the current main branch could not be verified."

latest_main_sha="$(
  jq -r \
    '[.[] | select(.name == "main") | .commit.id]
     | if length == 1 then .[0] else empty end' \
    "$release_work_dir/main-branch.json"
)" || fail_release "the current main branch response is invalid."
printf '%s' "$latest_main_sha" | grep -Eq '^[0-9a-f]{40}$' \
  || fail_release "the current main branch commit is invalid."
[ "$latest_main_sha" = "$CI_COMMIT_SHA" ] \
  || fail_release "a newer main commit exists; this release is outdated."
unset latest_main_sha gitlab_api_url
echo "The release commit is the current main commit."

aws_account_id="$(
  aws sts get-caller-identity --query Account --output text 2>/dev/null
)" || fail_release "the AWS account could not be resolved."
printf '%s' "$aws_account_id" | grep -Eq '^[0-9]{12}$' \
  || fail_release "STS returned an invalid AWS account identifier."
ecr_registry="${aws_account_id}.dkr.ecr.${AWS_REGION}.amazonaws.com"

check_repository_contract() {
  release_repository="$1"
  repository_contract="$(
    aws ecr describe-repositories \
      --repository-names "$release_repository" \
      --query 'repositories[0].[imageTagMutability,imageScanningConfiguration.scanOnPush]' \
      --output text 2>/dev/null
  )" || fail_release "an expected ECR repository is unavailable."

  # AWS text output is deliberately split into two fixed enum fields.
  # shellcheck disable=SC2086
  set -- $repository_contract
  [ "${1:-}" = "IMMUTABLE" ] \
    || fail_release "an ECR repository does not enforce immutable tags."
  [ "${2:-}" = "True" ] \
    || fail_release "an ECR repository does not scan images on push."
}

resolve_image_reference() {
  release_repository="$1"
  image_digest="$(
    aws ecr describe-images \
      --repository-name "$release_repository" \
      --image-ids "imageTag=$deploy_image_tag" \
      --query 'imageDetails[0].imageDigest' \
      --output text 2>/dev/null
  )" || fail_release "a release image is unavailable."
  printf '%s' "$image_digest" | grep -Eq '^sha256:[0-9a-f]{64}$' \
    || fail_release "a release image digest is invalid."
  printf '%s' "${ecr_registry}/${release_repository}@${image_digest}"
}

check_service_contract() {
  release_service="$1"
  release_family="$2"
  release_container="$3"
  release_output="$4"

  aws ecs describe-services \
    --cluster "$DEPLOY_CLUSTER_NAME" \
    --services "$release_service" \
    >"$release_output" 2>/dev/null \
    || fail_release "an expected ECS service could not be described."

  jq -e \
    --arg family "$release_family" \
    '(.failures | length) == 0
      and (.services | length) == 1
      and .services[0].status == "ACTIVE"
      and .services[0].desiredCount >= 1
      and .services[0].launchType == "FARGATE"
      and .services[0].deploymentController.type == "ECS"
      and .services[0].enableExecuteCommand == false
      and .services[0].networkConfiguration.awsvpcConfiguration.assignPublicIp == "DISABLED"
      and .services[0].deploymentConfiguration.deploymentCircuitBreaker.enable == true
      and .services[0].deploymentConfiguration.deploymentCircuitBreaker.rollback == true
      and (.services[0].taskDefinition | contains("task-definition/" + $family + ":"))' \
    "$release_output" >/dev/null 2>&1 \
    || fail_release "an ECS service violates the deployment safety contract."

  source_task_definition="$(jq -r '.services[0].taskDefinition' "$release_output")"
  aws ecs describe-task-definition \
    --task-definition "$source_task_definition" \
    >"${release_output}.task" 2>/dev/null \
    || fail_release "an active ECS task definition could not be described."
  jq -e \
    --arg family "$release_family" \
    --arg container "$release_container" \
    '.taskDefinition.family == $family
      and ([.taskDefinition.containerDefinitions[] | select(.name == $container)] | length) == 1
      and (.taskDefinition.requiresCompatibilities | index("FARGATE")) != null
      and .taskDefinition.networkMode == "awsvpc"' \
    "${release_output}.task" >/dev/null 2>&1 \
    || fail_release "an active ECS task definition violates the deployment contract."
}

cluster_status="$(
  aws ecs describe-clusters \
    --clusters "$DEPLOY_CLUSTER_NAME" \
    --query 'clusters[0].status' \
    --output text 2>/dev/null
)" || fail_release "the ECS cluster could not be described."
[ "$cluster_status" = "ACTIVE" ] || fail_release "the ECS cluster is not active."

for release_repository in \
  "$DEPLOY_BACKEND_REPOSITORY" \
  "$DEPLOY_YJS_REPOSITORY" \
  "$DEPLOY_MIGRATION_REPOSITORY"; do
  check_repository_contract "$release_repository"
done

backend_service_file="$release_work_dir/backend-service.json"
yjs_service_file="$release_work_dir/yjs-service.json"
check_service_contract \
  "$DEPLOY_BACKEND_SERVICE" \
  "$DEPLOY_BACKEND_TASK_FAMILY" \
  "$DEPLOY_BACKEND_CONTAINER" \
  "$backend_service_file"
check_service_contract \
  "$DEPLOY_YJS_SERVICE" \
  "$DEPLOY_YJS_TASK_FAMILY" \
  "$DEPLOY_YJS_CONTAINER" \
  "$yjs_service_file"

bucket_versioning="$(
  aws s3api get-bucket-versioning \
    --bucket "$DEPLOY_FRONTEND_BUCKET" \
    --query Status --output text 2>/dev/null
)" || fail_release "the frontend bucket could not be inspected."
[ "$bucket_versioning" = "Enabled" ] \
  || fail_release "the frontend bucket must have versioning enabled."

distribution_contract="$(
  aws cloudfront get-distribution \
    --id "$DEPLOY_CLOUDFRONT_DISTRIBUTION_ID" \
    --query '[Distribution.Status,Distribution.DistributionConfig.Enabled]' \
    --output text 2>/dev/null
)" || fail_release "the frontend distribution could not be inspected."
# AWS text output is deliberately split into status and enabled fields.
# shellcheck disable=SC2086
set -- $distribution_contract
if [ "${1:-}" != "Deployed" ] || [ "${2:-}" != "True" ]; then
  fail_release "the frontend distribution is not ready."
fi

backend_image="$(resolve_image_reference "$DEPLOY_BACKEND_REPOSITORY")"
yjs_image="$(resolve_image_reference "$DEPLOY_YJS_REPOSITORY")"
migration_image="$(resolve_image_reference "$DEPLOY_MIGRATION_REPOSITORY")"

echo "Production preflight passed for the intentionally reused physical environment."

register_task_revision() {
  register_source="$1"
  register_family="$2"
  register_container="$3"
  register_image="$4"
  register_label="$5"
  register_source_file="$release_work_dir/${register_label}-source.json"
  register_input_file="$release_work_dir/${register_label}-register.json"

  aws ecs describe-task-definition \
    --task-definition "$register_source" \
    --include TAGS \
    >"$register_source_file" 2>/dev/null \
    || fail_release "$register_label task definition could not be read."

  jq \
    --arg family "$register_family" \
    --arg container "$register_container" \
    --arg image "$register_image" \
    '
      if .taskDefinition.family != $family then
        error("unexpected task family")
      elif ([.taskDefinition.containerDefinitions[] | select(.name == $container)] | length) != 1 then
        error("unexpected container contract")
      else
        .
      end
      | . as $source
      | {
          family: $source.taskDefinition.family,
          taskRoleArn: $source.taskDefinition.taskRoleArn,
          executionRoleArn: $source.taskDefinition.executionRoleArn,
          networkMode: $source.taskDefinition.networkMode,
          containerDefinitions: (
            $source.taskDefinition.containerDefinitions
            | map(if .name == $container then .image = $image else . end)
          ),
          volumes: ($source.taskDefinition.volumes // []),
          placementConstraints: ($source.taskDefinition.placementConstraints // []),
          requiresCompatibilities: $source.taskDefinition.requiresCompatibilities,
          cpu: $source.taskDefinition.cpu,
          memory: $source.taskDefinition.memory,
          pidMode: $source.taskDefinition.pidMode,
          ipcMode: $source.taskDefinition.ipcMode,
          proxyConfiguration: $source.taskDefinition.proxyConfiguration,
          inferenceAccelerators: $source.taskDefinition.inferenceAccelerators,
          ephemeralStorage: $source.taskDefinition.ephemeralStorage,
          runtimePlatform: $source.taskDefinition.runtimePlatform,
          tags: ($source.tags // [])
        }
      | with_entries(select(.value != null))
    ' "$register_source_file" >"$register_input_file" 2>/dev/null \
    || fail_release "$register_label task definition could not be transformed safely."

  registered_task_definition="$(
    aws ecs register-task-definition \
      --cli-input-json "file://${register_input_file}" \
      --query 'taskDefinition.taskDefinitionArn' \
      --output text 2>/dev/null
  )" || fail_release "$register_label task definition registration failed."

  case "$registered_task_definition" in
    *":task-definition/${register_family}:"*) ;;
    *) fail_release "$register_label registration returned an unexpected task family." ;;
  esac
  printf '%s' "$registered_task_definition"
}

migration_task_definition="$(
  register_task_revision \
    "$DEPLOY_MIGRATION_TASK_FAMILY" \
    "$DEPLOY_MIGRATION_TASK_FAMILY" \
    "$DEPLOY_MIGRATION_CONTAINER" \
    "$migration_image" \
    migration
)"

jq \
  '{
    awsvpcConfiguration: {
      subnets: .services[0].networkConfiguration.awsvpcConfiguration.subnets,
      securityGroups: .services[0].networkConfiguration.awsvpcConfiguration.securityGroups,
      assignPublicIp: "DISABLED"
    }
  }' "$backend_service_file" >"$release_work_dir/migration-network.json" \
  || fail_release "the private migration network configuration could not be prepared."
jq -n \
  --arg container "$DEPLOY_MIGRATION_CONTAINER" \
  '{containerOverrides: [{name: $container, command: ["migrate"]}]}' \
  >"$release_work_dir/migration-overrides.json" \
  || fail_release "the migration command override could not be prepared."

aws ecs run-task \
  --cluster "$DEPLOY_CLUSTER_NAME" \
  --task-definition "$migration_task_definition" \
  --launch-type FARGATE \
  --platform-version LATEST \
  --count 1 \
  --started-by "gitlab-${CI_PIPELINE_ID}" \
  --network-configuration "file://$release_work_dir/migration-network.json" \
  --overrides "file://$release_work_dir/migration-overrides.json" \
  >"$release_work_dir/migration-run.json" 2>/dev/null \
  || fail_release "the migration task could not be started."

jq -e '(.failures | length) == 0 and (.tasks | length) == 1' \
  "$release_work_dir/migration-run.json" >/dev/null 2>&1 \
  || fail_release "ECS rejected the migration task."
migration_task="$(jq -r '.tasks[0].taskArn' "$release_work_dir/migration-run.json")"

aws ecs wait tasks-stopped \
  --cluster "$DEPLOY_CLUSTER_NAME" \
  --tasks "$migration_task" >/dev/null 2>&1 \
  || fail_release "the migration task did not stop within the waiter limit."
aws ecs describe-tasks \
  --cluster "$DEPLOY_CLUSTER_NAME" \
  --tasks "$migration_task" \
  >"$release_work_dir/migration-result.json" 2>/dev/null \
  || fail_release "the migration task result could not be read."

jq -e \
  --arg container "$DEPLOY_MIGRATION_CONTAINER" \
  '(.failures | length) == 0
    and (.tasks | length) == 1
    and .tasks[0].lastStatus == "STOPPED"
    and .tasks[0].stopCode != "TaskFailedToStart"
    and ([.tasks[0].containers[] | select(.name == $container and .exitCode == 0)] | length) == 1' \
  "$release_work_dir/migration-result.json" >/dev/null 2>&1 \
  || fail_release "the migration task did not exit successfully."
echo "Database migration completed successfully."

backend_source_task="$(jq -r '.services[0].taskDefinition' "$backend_service_file")"
yjs_source_task="$(jq -r '.services[0].taskDefinition' "$yjs_service_file")"
backend_task_definition="$(
  register_task_revision \
    "$backend_source_task" \
    "$DEPLOY_BACKEND_TASK_FAMILY" \
    "$DEPLOY_BACKEND_CONTAINER" \
    "$backend_image" \
    backend
)"
yjs_task_definition="$(
  register_task_revision \
    "$yjs_source_task" \
    "$DEPLOY_YJS_TASK_FAMILY" \
    "$DEPLOY_YJS_CONTAINER" \
    "$yjs_image" \
    yjs
)"

aws ecs update-service \
  --cluster "$DEPLOY_CLUSTER_NAME" \
  --service "$DEPLOY_BACKEND_SERVICE" \
  --task-definition "$backend_task_definition" >/dev/null 2>&1 \
  || fail_release "the Backend service update was rejected."
aws ecs update-service \
  --cluster "$DEPLOY_CLUSTER_NAME" \
  --service "$DEPLOY_YJS_SERVICE" \
  --task-definition "$yjs_task_definition" >/dev/null 2>&1 \
  || fail_release "the Yjs service update was rejected."

aws ecs wait services-stable \
  --cluster "$DEPLOY_CLUSTER_NAME" \
  --services "$DEPLOY_BACKEND_SERVICE" "$DEPLOY_YJS_SERVICE" \
  >/dev/null 2>&1 \
  || fail_release "the ECS services did not stabilize."

verify_service_rollout() {
  verify_service="$1"
  verify_task_definition="$2"
  verify_output="$3"

  aws ecs describe-services \
    --cluster "$DEPLOY_CLUSTER_NAME" \
    --services "$verify_service" \
    >"$verify_output" 2>/dev/null \
    || fail_release "an ECS rollout result could not be read."
  jq -e \
    --arg task "$verify_task_definition" \
    '(.failures | length) == 0
      and (.services | length) == 1
      and .services[0].desiredCount == .services[0].runningCount
      and .services[0].pendingCount == 0
      and ([.services[0].deployments[]
        | select(.status == "PRIMARY"
          and .taskDefinition == $task
          and .rolloutState == "COMPLETED")]
        | length) == 1' \
    "$verify_output" >/dev/null 2>&1 \
    || fail_release "an ECS service rolled back or failed its health checks."
}

verify_service_rollout \
  "$DEPLOY_BACKEND_SERVICE" \
  "$backend_task_definition" \
  "$release_work_dir/backend-rollout.json"
verify_service_rollout \
  "$DEPLOY_YJS_SERVICE" \
  "$yjs_task_definition" \
  "$release_work_dir/yjs-rollout.json"
echo "Backend and Yjs ECS rollouts completed on the requested revisions."

frontend_dist="Main/Frontend/dist"
[ -f "$frontend_dist/index.html" ] \
  || fail_release "the frontend build artifact is missing index.html."

aws s3 sync "$frontend_dist" "s3://${DEPLOY_FRONTEND_BUCKET}" \
  --exclude index.html \
  --cache-control 'no-cache' \
  --no-progress --only-show-errors >/dev/null 2>&1 \
  || fail_release "frontend files could not be uploaded."

if [ -d "$frontend_dist/assets" ]; then
  aws s3 cp "$frontend_dist/assets" "s3://${DEPLOY_FRONTEND_BUCKET}/assets" \
    --recursive \
    --cache-control 'public,max-age=31536000,immutable' \
    --no-progress --only-show-errors >/dev/null 2>&1 \
    || fail_release "fingerprinted frontend assets could not be uploaded."
fi

aws s3 cp "$frontend_dist/index.html" "s3://${DEPLOY_FRONTEND_BUCKET}/index.html" \
  --content-type 'text/html; charset=utf-8' \
  --cache-control 'no-cache,no-store,must-revalidate' \
  --no-progress --only-show-errors >/dev/null 2>&1 \
  || fail_release "the frontend entry point could not be published."

aws s3 sync "$frontend_dist" "s3://${DEPLOY_FRONTEND_BUCKET}" \
  --exclude index.html \
  --delete \
  --no-progress --only-show-errors >/dev/null 2>&1 \
  || fail_release "stale frontend objects could not be cleaned up."

invalidation_id="$(
  aws cloudfront create-invalidation \
    --distribution-id "$DEPLOY_CLOUDFRONT_DISTRIBUTION_ID" \
    --paths '/*' \
    --query 'Invalidation.Id' \
    --output text 2>/dev/null
)" || fail_release "the CloudFront invalidation could not be created."
if [ -z "$invalidation_id" ] || [ "$invalidation_id" = "None" ]; then
  fail_release "CloudFront returned an invalid invalidation identifier."
fi
aws cloudfront wait invalidation-completed \
  --distribution-id "$DEPLOY_CLOUDFRONT_DISTRIBUTION_ID" \
  --id "$invalidation_id" >/dev/null 2>&1 \
  || fail_release "the CloudFront invalidation did not complete."
echo "Frontend S3 publication and CloudFront invalidation completed."

fetch_http_200() {
  fetch_url="$1"
  fetch_output="$2"
  fetch_code="$(
    curl --silent --show-error \
      --connect-timeout 10 --max-time 20 \
      --output "$fetch_output" \
      --write-out '%{http_code}' \
      "$fetch_url" 2>/dev/null
  )" || fail_release "an external smoke request failed."
  [ "$fetch_code" = "200" ] || fail_release "an external smoke endpoint did not return HTTP 200."
}

fetch_http_200 "$DEPLOY_FRONTEND_URL" "$release_work_dir/frontend-root.html"
fetch_http_200 "$DEPLOY_FRONTEND_URL/login" "$release_work_dir/frontend-spa.html"
grep -Fq '<div id="app"' "$release_work_dir/frontend-root.html" \
  || fail_release "the frontend root is not the expected SPA document."
grep -Fq '<div id="app"' "$release_work_dir/frontend-spa.html" \
  || fail_release "the direct SPA route was not rewritten to the application."

fetch_http_200 "$DEPLOY_API_BASE_URL/actuator/health" "$release_work_dir/backend-health.json"
jq -e '.status == "UP"' "$release_work_dir/backend-health.json" >/dev/null 2>&1 \
  || fail_release "the Backend health payload is invalid."

fetch_http_200 "$DEPLOY_API_BASE_URL/.well-known/jwks.json" "$release_work_dir/jwks.json"
jq -e '.keys | any(.kty == "RSA" and .alg == "RS256" and (.n | length > 0) and (.e | length > 0))' \
  "$release_work_dir/jwks.json" >/dev/null 2>&1 \
  || fail_release "the Backend JWKS payload is invalid."

yjs_https_url="https://${DEPLOY_YJS_WEBSOCKET_URL#wss://}"
yjs_health_url="${yjs_https_url%/collaboration}/health"
fetch_http_200 "$yjs_health_url" "$release_work_dir/yjs-health.json"
jq -e '.status == "ok" and .database == "postgresql"' \
  "$release_work_dir/yjs-health.json" >/dev/null 2>&1 \
  || fail_release "the Yjs health payload is invalid."

: >"$release_work_dir/websocket-headers.txt"
websocket_key="$(head -c 16 /dev/urandom | base64 | tr -d '\n')"
[ -n "$websocket_key" ] \
  || fail_release "a WebSocket handshake key could not be generated."
curl --silent --show-error --http1.1 \
  --connect-timeout 10 --max-time 5 \
  --dump-header "$release_work_dir/websocket-headers.txt" \
  --output /dev/null \
  --header 'Connection: Upgrade' \
  --header 'Upgrade: websocket' \
  --header 'Sec-WebSocket-Version: 13' \
  --header "Sec-WebSocket-Key: ${websocket_key}" \
  "$DEPLOY_YJS_WEBSOCKET_URL" 2>/dev/null || true
grep -Eq '^HTTP/[0-9.]+ 101([[:space:]]|$)' "$release_work_dir/websocket-headers.txt" \
  || fail_release "the Yjs WebSocket endpoint did not upgrade to HTTP 101."

allowed_cors_code="$(
  curl --silent --show-error \
    --connect-timeout 10 --max-time 20 \
    --request OPTIONS \
    --header "Origin: ${DEPLOY_FRONTEND_URL}" \
    --header 'Access-Control-Request-Method: POST' \
    --dump-header "$release_work_dir/allowed-cors-headers.txt" \
    --output /dev/null \
    --write-out '%{http_code}' \
    "$DEPLOY_API_BASE_URL/api/v1/auth/login" 2>/dev/null
)" || fail_release "the allowed-origin CORS request failed."
case "$allowed_cors_code" in 200|204) ;; *) fail_release "the allowed Origin failed CORS preflight." ;; esac
grep -Fqi "access-control-allow-origin: ${DEPLOY_FRONTEND_URL}" \
  "$release_work_dir/allowed-cors-headers.txt" \
  || fail_release "the allowed Origin was not returned by CORS."

blocked_cors_code="$(
  curl --silent --show-error \
    --connect-timeout 10 --max-time 20 \
    --request OPTIONS \
    --header 'Origin: https://blocked.invalid' \
    --header 'Access-Control-Request-Method: POST' \
    --output /dev/null \
    --write-out '%{http_code}' \
    "$DEPLOY_API_BASE_URL/api/v1/auth/login" 2>/dev/null
)" || fail_release "the blocked-origin CORS request failed."
[ "$blocked_cors_code" = "403" ] \
  || fail_release "an untrusted Origin was not rejected."

unset \
  aws_account_id ecr_registry backend_image yjs_image migration_image \
  backend_task_definition yjs_task_definition migration_task_definition \
  migration_task invalidation_id websocket_key

echo "Production smoke checks passed without printing deployment endpoints or identifiers."
echo "Production release completed successfully."
