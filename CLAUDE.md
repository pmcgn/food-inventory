# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Food Inventory is a single-user home warehouse management tool. Users scan product barcodes via a smartphone camera in the mobile web UI. The system resolves EAN codes against an external product API (e.g. Open Food Facts), stores inventory in PostgreSQL, and alerts the user on low stock or nearing expiry dates.

## Use Cases

| ID  | Name                  | Summary |
|-----|-----------------------|---------|
| UC1 | Scan Product via Camera | User activates smartphone camera in the mobile web UI; app reads barcode and extracts EAN code |
| UC2 | Add Product to Inventory | EAN is resolved via external API; product is added or quantity incremented in the database |
| UC3 | Remove Product from Inventory | Scan decrements quantity; entry removed when quantity reaches zero |
| UC4 | View Current Inventory | User browses product list (name, quantity, category) on mobile web UI |
| UC5 | Low Stock Alert | Warning shown when a product's quantity drops below a defined threshold |
| UC6 | Expiry Alert | Warning shown when a product is nearing its stored expiry date |

### Out of Scope (initial version)
- USB/Bluetooth barcode scanner (camera only for now)
- Multi-user / household sharing
- Expiry date management UI (date stored at add time only)
- Manual EAN entry

## Tools

| Tool | Purpose | Command |
|------|---------|---------|
| [yamllint](https://github.com/adrienverge/yamllint) | Lint YAML files (e.g. `docs/openapi.yaml`) | `yamllint <file>` |

Run after every edit to a YAML file:
```bash
yamllint docs/openapi.yaml
```

## Backend (Go)

Source lives in `backend/`. All commands below run from that directory.

```bash
go build ./...        # compile check
go test ./...         # run tests
go vet ./...          # static analysis
go mod tidy           # sync dependencies
```

### Running locally

```bash
# Start PostgreSQL (example with Docker)
docker run -d --name pg \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=foodinventory \
  -p 5432:5432 postgres:16

# Run the server (schema is applied automatically on startup)
cd backend
go run ./cmd/server
```

### Building the Docker image

```bash
cd backend
docker build -t foodinventory-backend .
docker run -p 8080:8080 \
  -e DATABASE_URL=postgres://postgres:postgres@host.docker.internal:5432/foodinventory?sslmode=disable \
  foodinventory-backend
```

### Database migrations

Migration files live in `backend/internal/db/migrations/` and follow the naming pattern `NNN_name.sql` (e.g. `001_initial.sql`). On every startup `RunMigrations` creates the `schema_migrations` table if absent, then applies any migrations whose version number is not yet recorded there — in ascending order, each in its own transaction. To add a schema change, drop a new file with the next version number; it will be applied automatically on the next boot and never re-run.

### Backend layout

```
backend/
├── cmd/server/main.go              # entry point
├── internal/
│   ├── config/config.go            # env-based configuration
│   ├── db/db.go                    # pgxpool setup + migration runner
│   ├── db/migrations/001_initial.sql
│   ├── model/model.go              # shared data types
│   ├── service/
│   │   ├── product.go              # EAN lookup + cache
│   │   ├── inventory.go            # add / remove / list stock
│   │   ├── alert.go                # low-stock + expiry alerts
│   │   └── settings.go            # global settings
│   └── handler/
│       ├── common.go               # writeJSON / writeError helpers
│       ├── inventory.go            # GET|POST /inventory, DELETE /inventory/{ean}
│       ├── alert.go                # GET /alerts
│       └── settings.go            # GET|PATCH /settings
├── Dockerfile                      # multi-stage, alpine runtime
└── go.mod                          # module: foodinventory, go 1.22
```

## Frontend (Svelte)

Source lives in `frontend/`. All commands below run from that directory.

```bash
npm run dev      # start dev server (proxies /inventory, /alerts, /settings → localhost:8080)
npm run build    # production build → frontend/build/
npm run check    # type-check with svelte-check
```

### Building the Docker image

```bash
cd frontend
docker build -t foodinventory-frontend .
docker run -p 80:80 foodinventory-frontend
```

The nginx container proxies API requests to `foodinventory-backend:8080` (by container name).

### Frontend layout

```
frontend/
├── src/
│   ├── app.html                          # shell HTML with mobile meta tags
│   ├── app.css                           # design tokens + global styles
│   ├── lib/
│   │   ├── api.ts                        # typed API client
│   │   ├── stores/toast.ts               # toast notification store
│   │   └── components/
│   │       ├── BarcodeScanner.svelte     # camera overlay (BarcodeDetector → ZXing fallback)
│   │       └── Toast.svelte              # fixed toast stack
│   └── routes/
│       ├── +layout.svelte                # bottom nav (Inventory / Alerts / Settings)
│       ├── +page.svelte                  # inventory list + FAB + scanner + expiry modal
│       ├── alerts/+page.svelte           # alert list
│       └── settings/+page.svelte        # expiry warning days stepper
├── nginx.conf                            # SPA fallback + API reverse proxy
├── Dockerfile                            # node:20-alpine build → nginx:1.27-alpine
├── svelte.config.js                      # adapter-static, fallback: 'index.html'
└── vite.config.ts                        # dev proxy config
```

## Architecture

- **Frontend**: SvelteKit 2 + Svelte 5 + TypeScript, `adapter-static` SPA served by nginx; `BarcodeDetector` API with `@zxing/browser` fallback
- **Backend**: Go 1.22, `net/http` ServeMux, `pgx/v5`, runs in Docker
- **Database**: PostgreSQL 16 (tables: `products`, `inventory`, `settings`)
- **External dependency**: EAN product lookup API (e.g. [Open Food Facts](https://world.openfoodfacts.org/))
- **API spec**: `docs/openapi.yaml` (OpenAPI 3.1.0)
- **Agents**: Claude sub-agents for focused development tasks (see `agents/`)

## Agents

| Agent | File | Responsibility |
|-------|------|----------------|
| `api-designer` | `agents/api-designer.md` | REST API design, OpenAPI spec, authentication patterns, versioning |
| `backend-developer` | `agents/backend-developer.md` | Server-side API implementation, database integration, Docker |
| `frontend-developer` | `agents/frontend-developer.md` | Frontend application across React/Vue/Angular, full-stack integration |
| `ui-designer` | `agents/ui-designer.md` | Visual interfaces, design systems, component libraries, accessibility |
| `websocket-engineer` | `agents/websocket-engineer.md` | Real-time bidirectional communication (WebSockets, Socket.IO) |
| `golang-pro` | `agents/golang-pro.md` | Idiomatic Go patterns, concurrency, pgx, performance, Docker builds |
| `postgres-pro` | `agents/postgres-pro.md` | Query optimisation, schema design, replication, backup strategies |
| `business-analyst` | `agents/business-analyst.md` | Requirements gathering, process analysis, stakeholder alignment |
| `barcode-scanner` | `agents/barcode-scanner.md` | Camera access, barcode decoding in the browser (project-specific) |
| `ean-lookup` | `agents/ean-lookup.md` | Open Food Facts API integration and local caching (project-specific) |
| `inventory-api` | `agents/inventory-api.md` | Inventory CRUD design and database schema (project-specific) |
| `alert` | `agents/alert.md` | Low-stock and expiry alert logic (project-specific) |
