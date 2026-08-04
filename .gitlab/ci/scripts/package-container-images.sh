#!/bin/sh

set -eu

DEPLOY_BACKEND_REPOSITORY="a707-dev-backend"
DEPLOY_YJS_REPOSITORY="a707-dev-yjs"
DEPLOY_MIGRATION_REPOSITORY="a707-dev-migration"

fail_package() {
  echo "Container packaging failed: $1" >&2
  exit 1
}

for package_command in aws buildah jq grep; do
  command -v "$package_command" >/dev/null 2>&1 \
    || fail_package "$package_command is unavailable."
done

sh .gitlab/ci/scripts/validate-production-inputs.sh container

deploy_image_tag="git-${CI_COMMIT_SHA}"
aws_account_id="$(
  aws sts get-caller-identity --query Account --output text 2>/dev/null
)" || fail_package "the AWS account could not be resolved."
printf '%s' "$aws_account_id" | grep -Eq '^[0-9]{12}$' \
  || fail_package "STS returned an invalid AWS account identifier."
ecr_registry="${aws_account_id}.dkr.ecr.${AWS_REGION}.amazonaws.com"

check_repository_contract() {
  package_repository="$1"
  repository_contract="$(
    aws ecr describe-repositories \
      --repository-names "$package_repository" \
      --query 'repositories[0].[imageTagMutability,imageScanningConfiguration.scanOnPush]' \
      --output text 2>/dev/null
  )" || fail_package "an expected ECR repository is unavailable."

  # AWS text output is deliberately split into two fixed enum fields.
  # shellcheck disable=SC2086
  set -- $repository_contract
  [ "${1:-}" = "IMMUTABLE" ] \
    || fail_package "an ECR repository does not enforce immutable tags."
  [ "${2:-}" = "True" ] \
    || fail_package "an ECR repository does not scan images on push."
}

for package_repository in \
  "$DEPLOY_BACKEND_REPOSITORY" \
  "$DEPLOY_YJS_REPOSITORY" \
  "$DEPLOY_MIGRATION_REPOSITORY"; do
  check_repository_contract "$package_repository"
done

ecr_password="$(aws ecr get-login-password 2>/dev/null)" \
  || fail_package "ECR authorization failed."
printf '%s' "$ecr_password" \
  | buildah login --username AWS --password-stdin "$ecr_registry" >/dev/null 2>&1 \
  || fail_package "Buildah could not authenticate to ECR."
unset ecr_password

verify_remote_image() {
  package_repository="$1"
  remote_contract="$(
    aws ecr describe-images \
      --repository-name "$package_repository" \
      --image-ids "imageTag=$deploy_image_tag" \
      --query 'imageDetails[0].[imageDigest,imageManifestMediaType]' \
      --output text 2>/dev/null
  )" || return 1

  # AWS text output is deliberately split into digest and media-type fields.
  # shellcheck disable=SC2086
  set -- $remote_contract
  printf '%s' "${1:-}" | grep -Eq '^sha256:[0-9a-f]{64}$' || return 1
  case "${2:-}" in
    application/vnd.docker.distribution.manifest.v2+json | \
    application/vnd.oci.image.manifest.v1+json)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

build_and_push() {
  package_repository="$1"
  package_dockerfile="$2"
  package_context="$3"
  package_label="$4"

  if verify_remote_image "$package_repository"; then
    echo "$package_label image already exists for this commit; reusing it."
    return
  fi

  local_image="localhost/a707-${package_label}:${deploy_image_tag}"
  remote_image="${ecr_registry}/${package_repository}:${deploy_image_tag}"

  echo "Building the $package_label linux/amd64 image."
  buildah build \
    --platform linux/amd64 \
    --format docker \
    --pull=always \
    --label "org.opencontainers.image.revision=${CI_COMMIT_SHA}" \
    --file "$package_dockerfile" \
    --tag "$local_image" \
    "$package_context" \
    || fail_package "$package_label image build failed."

  buildah push --quiet "$local_image" "docker://${remote_image}" >/dev/null 2>&1 \
    || fail_package "$package_label image push failed."

  verify_remote_image "$package_repository" \
    || fail_package "$package_label did not produce a single-image manifest."
  echo "$package_label image push and digest verification succeeded."
}

build_and_push \
  "$DEPLOY_BACKEND_REPOSITORY" \
  Main/Backend/Dockerfile \
  Main/Backend \
  backend
build_and_push \
  "$DEPLOY_YJS_REPOSITORY" \
  Main/Backend-Yjs/Dockerfile \
  Main/Backend-Yjs \
  yjs
build_and_push \
  "$DEPLOY_MIGRATION_REPOSITORY" \
  Main/Backend/Dockerfile.migration \
  Main/Backend \
  migration

buildah logout "$ecr_registry" >/dev/null 2>&1 || true
unset aws_account_id ecr_registry remote_image local_image

echo "All immutable container images are ready for the production release."
