# ChatGPT sign-in

OAuth is opt-in. The existing API-key filter remains active until the `oauth`
profile is enabled. Never disable authentication to connect ChatGPT.

## GitHub application

Register an OAuth app in https://github.com/settings/developers with:

- Name: Jackie Memory Sign-in
- Homepage: https://jackie-mcp-memory-server-production.up.railway.app
- Callback: https://jackie-mcp-memory-server-production.up.railway.app/login/oauth2/code/github
- No wildcard callback matching and no device flow.

Place the issued client ID and client secret in the **Java service**, not Postgres:
`GITHUB_OAUTH_CLIENT_ID`, `GITHUB_OAUTH_CLIENT_SECRET`.
Do not commit secrets. Only the GitHub login named by `MCP_GITHUB_OWNER`
(default `jackie1santana`) can complete sign-in. GitHub is used only for identity;
GitHub tokens cannot be used as MCP access tokens.

## Railway Java service settings

Configure these before enabling the profile:

| Variable | Value |
| --- | --- |
| APP_MCP_PUBLIC_BASE_URL | https://jackie-mcp-memory-server-production.up.railway.app |
| MCP_OAUTH_CLIENT_ID | jackie-memory-chatgpt |
| MCP_OAUTH_CLIENT_SECRET | A newly generated random secret; use the same value in ChatGPT |
| MCP_OAUTH_REDIRECT_URI | The exact redirect URL shown in ChatGPT's connection management page |
| MCP_GITHUB_OWNER | jackie1santana |
| SPRING_PROFILES_ACTIVE | railway,oauth |

Keep `MCP_AUTH_ENABLED=true`, `MCP_DB_ADMIN_ENABLED=false`, and
`MCP_DB_ADMIN_ALLOW_DDL=false`. OAuth mode protects `/mcp` using OAuth tokens
instead of the legacy static API key. Health remains public without details in
the OAuth profile. OAuth discovery is public; it does not expose memory data.

## ChatGPT

Create the custom MCP connection using
`https://jackie-mcp-memory-server-production.up.railway.app/mcp`, select OAuth,
and enter the predefined MCP client ID and secret under advanced OAuth settings.
This server uses a predefined client, not open dynamic client registration.
Copy ChatGPT's exact callback URI into `MCP_OAUTH_REDIRECT_URI` before signing in.
Do not substitute the GitHub OAuth client credentials into ChatGPT.

Discovery URLs:

- `/.well-known/oauth-protected-resource`
- `/.well-known/oauth-authorization-server`

The `memory` scope grants access to memory tools. Authorization uses PKCE S256,
an exact callback allowlist, an owner-only GitHub sign-in and consent. Tokens
are bound to the canonical `/mcp` resource. Access tokens last 30 minutes;
refresh tokens last seven days and rotate after use. Revocation is supported.

This initial implementation stores OAuth authorizations and consent in memory.
Run one Java replica. Restarting or redeploying invalidates issued tokens and
requires reconnecting ChatGPT. Memory records themselves remain in PostgreSQL.
Persistent token storage is a future enhancement, not enabled here.

The automated security test checks public discovery, blocked missing/invalid
tokens, resource validation, PKCE code exchange and token revocation. Actual
GitHub sign-in must also be verified after real credentials are configured.
