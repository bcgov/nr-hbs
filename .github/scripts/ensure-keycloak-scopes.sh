#!/usr/bin/env bash
#
# Ensure the OAuth2 client scopes the scale data API enforces exist in the
# target Keycloak realm. Idempotent: lists the realm's client scopes and creates
# only the missing ones (existing scopes are left untouched). Same approach as
# nr-user-lookup-api's .github/scripts/ensure-keycloak-scopes.sh.
#
# Only creates the scopes. Assigning them to a client's account (as DEFAULT
# client scopes, so its client_credentials token always carries them) is done
# per client, like the HBS_SUBMISSION_API role (docs/roles-and-security.md,
# "Service accounts: scale data API").
#
# Authenticates with a confidential admin service-account client
# (client_credentials) in the same realm; it needs the realm-management
# `manage-clients` role. The realm and endpoints come from KEYCLOAK_ISSUER_URI
# (works with or without an `/auth` base path).
#
# Required environment:
#   KEYCLOAK_ISSUER_URI   e.g. https://test.loginproxy.gov.bc.ca/auth/realms/forests
#   KC_SA_CLIENT_ID       admin service-account client id (manage-clients)
#   KC_SA_CLIENT_SECRET   admin service-account client secret
#
# Keep SCOPES in step with ca.bc.gov.nrs.hbs.api.scaledata.ScaleDataApi.SCOPES
# (KeycloakScopesScriptTest checks it).
set -euo pipefail

: "${KEYCLOAK_ISSUER_URI:?KEYCLOAK_ISSUER_URI is required}"
: "${KC_SA_CLIENT_ID:?KC_SA_CLIENT_ID is required}"
: "${KC_SA_CLIENT_SECRET:?KC_SA_CLIENT_SECRET is required}"

# "<scope>|<description>"
SCOPES=(
  "hbs.scale-data.submit|HBS scale data API: send and check scale data files"
  "hbs.scale-data.read|HBS scale data API: read file results and documents"
  "hbs.statements.read|HBS scale data API: list and download statements"
)

issuer="${KEYCLOAK_ISSUER_URI%/}"
realm="${issuer##*/realms/}"
base="${issuer%/realms/*}"
token_url="${issuer}/protocol/openid-connect/token"
scopes_url="${base}/admin/realms/${realm}/client-scopes"

echo "Keycloak realm: ${realm}"
echo "Client-scopes endpoint: ${scopes_url}"

# --- obtain an admin token via client_credentials ---------------------------
token="$(curl -sS -X POST "${token_url}" \
  -d grant_type=client_credentials \
  -d client_id="${KC_SA_CLIENT_ID}" \
  --data-urlencode "client_secret=${KC_SA_CLIENT_SECRET}" \
  | jq -r '.access_token // empty')"

if [ -z "${token}" ]; then
  echo "::error::Could not obtain a Keycloak admin token. Check the admin service-account client id/secret and that it has the realm-management 'manage-clients' role."
  exit 1
fi

# --- existing scope names ---------------------------------------------------
existing="$(curl -sS -f -H "Authorization: Bearer ${token}" "${scopes_url}" | jq -r '.[].name')"

created=0
for entry in "${SCOPES[@]}"; do
  scope="${entry%%|*}"
  description="${entry#*|}"

  if grep -qxF "${scope}" <<< "${existing}"; then
    echo "✓ exists: ${scope}"
    continue
  fi

  echo "+ creating: ${scope}"
  body="$(jq -n --arg name "${scope}" --arg desc "${description} (managed by nr-hbs CI)" '{
    name: $name,
    protocol: "openid-connect",
    description: $desc,
    attributes: {
      "include.in.token.scope": "true",
      "display.on.consent.screen": "false"
    }
  }')"

  code="$(curl -sS -o /dev/null -w '%{http_code}' -X POST "${scopes_url}" \
    -H "Authorization: Bearer ${token}" \
    -H 'Content-Type: application/json' \
    -d "${body}")"

  if [ "${code}" != "201" ]; then
    echo "::error::Failed to create client scope '${scope}' (HTTP ${code})."
    exit 1
  fi
  created=$((created + 1))
done

echo "Done. ${created} scope(s) created, $(( ${#SCOPES[@]} - created )) already present."
