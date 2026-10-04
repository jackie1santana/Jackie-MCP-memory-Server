# Railway + ChatGPT MCP Setup

Use this profile when you want the server deployable on Railway while keeping memory tools available to ChatGPT over MCP.

## 1) Railway environment variables

Set these variables in Railway service settings:

- `SPRING_PROFILES_ACTIVE=railway`
- `MCP_AUTH_ENABLED=true`
- `MCP_AUTH_TOKEN=<long-random-secret>`
- `MCP_AUTH_HEADER_NAME=X-MCP-API-KEY`
- `MCP_DB_ADMIN_ENABLED=false` (set `true` to expose DB admin read tools)
- `MCP_DB_ADMIN_ALLOW_DDL=false` (set `true` to allow ChatGPT table creation)

Database values come from Railway PostgreSQL plugin automatically. This project reads:

- `DATABASE_URL` (preferred), or
- `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`

## 2) Verify deployment health

After deploy, verify:

- `GET /actuator/health` returns `UP`
- `GET /internal/status` returns runtime info when auth header is present

Example with header:

```text
X-MCP-API-KEY: <same MCP_AUTH_TOKEN>
```

## 3) Configure ChatGPT MCP connector

In your MCP connector configuration, use:

- Server URL: `https://<your-railway-domain>/mcp`
- Header: `X-MCP-API-KEY: <same MCP_AUTH_TOKEN>`

ChatGPT can then call tools for memory save/get/search/update/scope operations.

If `MCP_DB_ADMIN_ENABLED=true`, ChatGPT can also inspect database metadata and table schemas.
If `MCP_DB_ADMIN_ALLOW_DDL=true`, ChatGPT can run `CREATE TABLE` statements via MCP.

## 4) Security notes

- Keep `MCP_AUTH_TOKEN` secret and rotate periodically.
- Keep DB-admin MCP features disabled unless actively needed.
- Do not log token values.
- Keep PostgreSQL private to Railway networking; do not expose DB publicly.
- If your threat model requires stronger controls, put MCP behind additional auth/proxy controls.

