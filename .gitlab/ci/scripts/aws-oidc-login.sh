#!/bin/sh

# This file must be sourced so the temporary AWS credentials remain available
# to the rest of the GitLab job.
set -eu

fail_oidc() {
  echo "AWS OIDC authentication failed: $1" >&2
  return 1
}

command -v aws >/dev/null 2>&1 || fail_oidc "AWS CLI is unavailable."
[ -n "${AWS_OIDC_ROLE_ARN:-}" ] || fail_oidc "AWS_OIDC_ROLE_ARN is not configured."
[ -n "${GITLAB_OIDC_TOKEN:-}" ] || fail_oidc "GitLab did not issue an OIDC ID token."

oidc_duration="${AWS_OIDC_DURATION_SECONDS:-900}"
case "$oidc_duration" in
  *[!0-9]* | "") fail_oidc "AWS_OIDC_DURATION_SECONDS must be numeric." ;;
esac
if [ "$oidc_duration" -lt 900 ] || [ "$oidc_duration" -gt 43200 ]; then
  fail_oidc "AWS_OIDC_DURATION_SECONDS is outside the STS range."
fi

unset AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN AWS_SECURITY_TOKEN AWS_PROFILE
export AWS_PAGER=""

aws_sts_output="$(
  aws sts assume-role-with-web-identity \
    --role-arn "$AWS_OIDC_ROLE_ARN" \
    --role-session-name "gitlab-${CI_PROJECT_ID:-0}-${CI_PIPELINE_ID:-0}" \
    --web-identity-token "$GITLAB_OIDC_TOKEN" \
    --duration-seconds "$oidc_duration" \
    --query 'Credentials.[AccessKeyId,SecretAccessKey,SessionToken]' \
    --output text 2>/dev/null
)" || fail_oidc "STS rejected the web identity token."

# STS text output is deliberately split into its three whitespace-free fields.
# shellcheck disable=SC2086
set -- $aws_sts_output
[ "$#" -eq 3 ] || fail_oidc "STS returned an unexpected credential response."

export AWS_ACCESS_KEY_ID="$1"
export AWS_SECRET_ACCESS_KEY="$2"
export AWS_SESSION_TOKEN="$3"
unset aws_sts_output GITLAB_OIDC_TOKEN oidc_duration

aws sts get-caller-identity >/dev/null 2>&1 \
  || fail_oidc "the temporary credentials could not call STS."
