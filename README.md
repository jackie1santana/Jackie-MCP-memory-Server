# ChatGPT MCP Memory Server (v1)

Personal external-memory MCP server built with Java, Spring Boot, Spring AI MCP, Spring JDBC, and PostgreSQL.

## What it provides

- Durable memory storage in PostgreSQL using `NamedParameterJdbcTemplate`
- Scoped retrieval (`personal`, `fidelity`, `custody`, `all`, `off`, etc.)
- Optional `scopeContext` isolation so one chat can switch scope without affecting another
- MCP tools for write/read/update/forget/scope/timeline operations
- Soft delete (`active=false`) instead of hard delete

## Architecture

`ChatGPT -> secure MCP tunnel/remote MCP connection -> local Spring Boot MCP server -> MemoryService -> MemoryRepository -> local PostgreSQL`

`MCP Tool Layer -> MemoryService -> MemoryRepository -> NamedParameterJdbcTemplate -> PostgreSQL`

## Local-first security rules

- Keep PostgreSQL local-only and bound to `127.0.0.1`
- Do not expose PostgreSQL (`5432`) publicly
- Keep database credentials in environment variables only
- Do not expose the local MCP server publicly until authentication and local database setup are verified

## Database

Flyway migrations are under `src/main/resources/db/`:

- `V1__create_memory_schema.sql`
- `V2__seed_memory_categories.sql`
- `V3__migrate_default_scope_key.sql`
- `V4__expand_personal_memory_categories.sql`
- `V5__add_spiritual_session_categories.sql`

Additional scopes and categories: `spirituality`, `medium_sessions`, and
`psychic_sessions`. Save session dates in `occurred_at`, notes in `content`,
and optional details such as practitioner or session type in `metadata`.
These categories store user-provided notes and interpretations.

Starting the application with the default `local` profile runs these migrations
automatically against the configured PostgreSQL database. The database itself
must already exist (`memory_db` by default), and `POSTGRES_DB_PASSWORD` must be
set. The `nodb` profile does not create tables.

The five application tables are `memory`, `memory_category`, `memory_tag`,
`memory_tag_map`, and `memory_settings`. Flyway also maintains its own
`flyway_schema_history` table. The complete table definitions are in V1; V4
adds category choices without deleting or changing existing memories.

Available memory areas/categories: `personal`, `family`, `custody`,
`child_support`, `ashley`, `relationships`, `legal`, `work`, `fidelity`,
`software_engineering`, `education`, `home`, `travel`, `vehicles`, `finance`,
`health`, `apple`, `creative`, and `general`. `all` and `off` are switch modes,
not areas where memories can be saved. Use tags for additional people or topics.

For example, a child support payment can use scope `custody`, category
`child_support`, and metadata such as `amount`, `currency`, and `payment_date`.
Alternatively, use scope `child_support` to retrieve payments separately from
other custody memories. Ashley memories can use scope `ashley` and category
`relationships`. These are organizational choices; no personal records are
created by the migrations. The initial memory switch remains `off`.

## Main MCP tools

- `remember_memory`
- `search_memory`
- `get_memory`
- `update_memory`
- `forget_memory`
- `set_memory_scope`
- `get_memory_scope`
- `list_memory_categories`
- `get_memory_timeline`

`set_memory_scope`, `get_memory_scope`, `search_memory`, `remember_memory`, and `get_memory_timeline` accept optional `scopeContext` for per-chat scope isolation.

If `remember_memory.scope` is omitted, the tool stores memory using the active scope for the given `scopeContext`.

## Profiles

- `local` (default): full memory stack with PostgreSQL + Flyway
- `nodb`: diagnostics mode with memory components disabled
- `railway`: cloud runtime profile for Railway deployment and remote MCP usage

## ChatGPT MCP usage

When connected as an MCP server, ChatGPT can call your tools to write and retrieve memory:

- write/save: `remember_memory`, `update_memory`, `forget_memory`, `set_memory_scope`
- read/search: `search_memory`, `get_memory`, `get_memory_scope`, `list_memory_categories`, `get_memory_timeline`

For remote endpoints, enable MCP auth and provide a token header in your MCP connector configuration.

## Runtime checks

- `GET /actuator/health`
- `GET /internal/status`

`/internal/status` reports Spring Boot up status, PostgreSQL reachability, and discovered MCP tools.

## Java requirement

This project targets Java `25`.

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4.1"
$env:Path = "$env:JAVA_HOME\\bin;$env:Path"
java -version
```

## Quick run

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
$env:POSTGRES_DB_PASSWORD = "<your_password>"
.\mvnw.cmd spring-boot:run
```

## Quick test

```powershell
.\mvnw.cmd test
```

## Quick verify

```powershell
.\scripts\check-local-runtime.ps1
```

## Railway deploy configuration (optional)

Set environment variables in Railway:

- `SPRING_PROFILES_ACTIVE=railway`
- `MCP_AUTH_ENABLED=true`
- `MCP_AUTH_TOKEN=<long-random-secret>`
- `MCP_AUTH_HEADER_NAME=X-MCP-API-KEY`
- database vars from Railway PostgreSQL (`DATABASE_URL` or `PG*`)

Then configure your ChatGPT MCP connector to send the same `X-MCP-API-KEY` header.

Detailed guide: `docs/railway-mcp-setup.md`.

Integration test uses Testcontainers and is skipped automatically when Docker is unavailable.

See full setup and verification steps in `docs/local-first-setup.md`.

