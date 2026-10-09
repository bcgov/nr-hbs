/// <reference types="vite/client" />

interface ImportMetaEnv {
  // BC Gov SSO (Keycloak) — same contract as nr-fta / nr-rept.
  // The realm issuer URI, e.g. https://test.loginproxy.gov.bc.ca/auth/realms/standard;
  // oidc-client-ts discovers every endpoint from it.
  readonly VITE_KEYCLOAK_URL: string;
  // Must equal the backend's KEYCLOAK_CLIENT_ID: it is checked there as the
  // token's `azp`.
  readonly VITE_KEYCLOAK_CLIENT_ID: string;
  readonly VITE_BASE_PATH: string;
  // Backend API base path; usually '/api' so Caddy reverse-proxies to the
  // same-zone backend Service.
  readonly VITE_API_BASE_URL: string;
  // Display / theming
  readonly VITE_APP_NAME: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
