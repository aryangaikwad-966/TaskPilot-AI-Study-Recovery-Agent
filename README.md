# TaskPilot — AI Study Recovery Agent

TaskPilot helps a student recover from missed study time using their actual pending tasks. It has a React dashboard, a Spring Boot API, Gemini-powered proposal generation, and a self-hosted MCP server.

## What works

- Create, filter, and complete tasks held in one in-memory `TaskService`.
- Generate a recovery proposal from real pending tasks. A proposal never changes tasks by itself.
- Approve recommended tasks to mark only those tasks complete, or cancel with no mutation.
- Expose `add_task`, `list_tasks`, and `complete_task` through MCP 2025-11-25 Streamable HTTP at `POST /mcp`.

Task data is intentionally in memory and is lost when the backend restarts.

## Architecture

```text
React UI → REST API → RecoveryService → Gemini API
                        │
                        └→ TaskService ← MCP 2025-11-25 Streamable HTTP
                                           ├─ add_task
                                           ├─ list_tasks
                                           └─ complete_task
```

The REST API and MCP endpoint use the same `TaskService` instance.

## Requirements

- Java 17+
- Maven 3.6+
- Node.js 18+ and npm
- `GEMINI_API_KEY` only for live Gemini recovery and chat

## Configure and run

```bash
# Terminal 1
cd Backend
export JAVA_HOME=/path/to/jdk-17
export GEMINI_API_KEY=your_key
# Optional; default is gemini-3.8-flash
export GEMINI_MODEL=gemini-3.8-flash
mvn spring-boot:run

# Terminal 2
cd Frontend
npm ci
printf 'VITE_API_BASE_URL=http://localhost:8080\n' > .env
npm run dev
```

Open `http://localhost:5173`. `VITE_API_BASE_URL` is browser-visible configuration: never place Gemini credentials in the frontend `.env` file.

The backend permits `http://localhost:5173` by default. Set `TASKPILOT_CORS_ALLOWED_ORIGINS` to an explicit frontend origin for a deployed demo.

## Verification

```bash
cd Backend
mvn test
mvn package

cd ../Frontend
npm run build
npm run lint
```

The backend has 35 tests, including HTTP-level MCP initialize, discovery, tool execution, invalid-request, and shared-state coverage. There is no frontend test runner in the current dependency set; build and lint are run as frontend checks.

## MCP connection

`POST /mcp` accepts JSON-RPC 2.0 with `Content-Type: application/json` and `Accept: application/json, text/event-stream`.

1. Call `initialize` with `protocolVersion: "2025-11-25"`.
2. Keep the returned `MCP-Session-Id` and send it with `MCP-Protocol-Version: 2025-11-25` on subsequent requests.
3. Send `notifications/initialized`, then `tools/list` or `tools/call`.

An unsupported protocol version or malformed tool arguments returns a JSON-RPC error. Tool business errors return an MCP tool result with `isError: true`.

## Gemini and recovery behavior

Gemini requests are made only from the backend using the `x-goog-api-key` header. Timeouts, malformed replies, missing keys, and provider failures return safe errors. The recovery prompt receives pending task titles from the live task store; model task titles are filtered against that store before the proposal is returned.

Without `GEMINI_API_KEY`, basic task management and MCP remain available. Recovery requests with pending tasks return HTTP 503 rather than fabricated advice.

## Security notes

- `.env` files are ignored; do not commit credentials.
- CORS is restricted by configuration rather than wildcard origins.
- MCP dispatch is an allow-list of three tools with JSON argument validation.
- AI text is rendered as React text, not HTML.
- The project has no authentication and should be kept behind an appropriate access boundary if deployed publicly.

## Three-minute demo outline

1. Show the empty dashboard and add three study tasks.
2. Use the recovery panel to request help after missed study time.
3. Review assumptions and recommended real task titles; approve and show the dashboard refresh.
4. Show `tools/list` and one `tools/call` through an MCP client, then show that task through REST.

## Hackathon submission checklist

- [ ] Publish a public repository with this MIT [LICENSE](LICENSE), or grant the required reviewers access to a private repository.
- [ ] Record a public English-language YouTube or Vimeo demonstration under three minutes.
- [ ] Submit the project description, repository URL, video URL, track selection, and required product-feedback responses.
- [ ] Confirm eligibility, project-start timing, and the submission deadline in the official rules.

The Alexa+ self-hosted MCP route is the selected technical path. The official rules require MCP 2025-11-25 or later over Streamable HTTP; TaskPilot implements and locally verifies that protocol path. Submission publication and account-only tasks remain the entrant's responsibility.
