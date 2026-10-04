# Local-First Setup (Windows)

This project is intentionally local-first:

`ChatGPT -> secure MCP tunnel/remote MCP connection -> local Spring Boot MCP server -> local PostgreSQL`

## Security boundary

- PostgreSQL must stay local-only.
- Do not expose PostgreSQL (`5432`) publicly.
- Keep PostgreSQL bound to `127.0.0.1` unless you have a strict, audited reason to change it.
- Keep DB credentials in environment variables (`POSTGRES_DB_PASSWORD`, etc.).
- Do not expose the local MCP server publicly until authentication and the local database setup are verified.

## 1) Start PostgreSQL locally

1. Install PostgreSQL on this PC.
2. Ensure it listens only on localhost in `postgresql.conf`:
   - `listen_addresses = '127.0.0.1'`
   - `port = 5432`
3. Restrict host auth in `pg_hba.conf` to localhost only.
4. Create the `memory_db` database if it does not already exist:

```powershell
psql -U postgres -h 127.0.0.1 -p 5432 -c "CREATE DATABASE memory_db;"
```

## 2) Start Spring Boot locally

From project root:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
$env:POSTGRES_DB_PASSWORD = "<your_password>"
$env:DB_USERNAME = "postgres"
$env:DB_URL = "jdbc:postgresql://127.0.0.1:5432/memory_db"
.\mvnw.cmd spring-boot:run
```

The app binds to `127.0.0.1:8080` in `local` profile.

## 3) Start secure MCP tunnel / remote MCP connector

Use a tunnel/connector that supports authenticated remote MCP access and TLS.
Point the tunnel target to local Spring Boot (`127.0.0.1:8080`).

Do not tunnel PostgreSQL.

## 4) Verify runtime + MCP readiness

In another terminal:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health | ConvertTo-Json -Depth 10
Invoke-RestMethod http://127.0.0.1:8080/internal/status | ConvertTo-Json -Depth 10
```

`/internal/status` confirms:
- Spring Boot process is up
- PostgreSQL reachability check (`SELECT 1`)
- MCP tool bean discovery (`registeredMcpTools`)

Then verify the remote MCP connection using your tunnel URL in ChatGPT by running a tool discovery/handshake in the MCP connector UI.

## Optional: Start without database

Use `nodb` profile only for local diagnostics when PostgreSQL is intentionally offline.

```powershell
$env:SPRING_PROFILES_ACTIVE = "nodb"
.\mvnw.cmd spring-boot:run
```

In `nodb` mode, memory tools are disabled by configuration.

## Operational note

Remote memory access works only while this home PC is:
- powered on
- awake (not sleeping)
- online
- running PostgreSQL + Spring Boot + tunnel

