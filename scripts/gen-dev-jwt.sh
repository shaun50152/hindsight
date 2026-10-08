#!/usr/bin/env bash
# Generate an HS256 dev JWT for policy-service. Requires HINDSIGHT_JWT_SECRET (>= 32 bytes).
# Usage: SUB=maker-1 ROLES=MAKER,OPS ./scripts/gen-dev-jwt.sh
set -euo pipefail

if [[ -z "${HINDSIGHT_JWT_SECRET:-}" ]]; then
  echo "Set HINDSIGHT_JWT_SECRET to your dev HMAC secret (not stored in repo)." >&2
  exit 1
fi

SUB="${SUB:-dev-user}"
IFS=',' read -r -a ROLE_ARR <<< "${ROLES:-MAKER}"
ROLES_JSON=$(printf '"%s",' "${ROLE_ARR[@]}")
ROLES_JSON="[${ROLES_JSON%,}]"

header='{"alg":"HS256","typ":"JWT"}'
payload=$(printf '{"sub":"%s","roles":%s}' "$SUB" "$ROLES_JSON")

b64url() { openssl base64 -e -A | tr '+/' '-_' | tr -d '='; }

header_b64=$(printf '%s' "$header" | b64url)
payload_b64=$(printf '%s' "$payload" | b64url)
signing_input="${header_b64}.${payload_b64}"
signature=$(printf '%s' "$signing_input" | openssl dgst -sha256 -hmac "$HINDSIGHT_JWT_SECRET" -binary | b64url)

echo "${signing_input}.${signature}"
