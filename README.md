# Food Inventory

Food Inventory is a little warehouse tool for home usage. You can add products to your storage by EAN codes (later via barcode scan) and remove them accordingly. The backend will store the current inventor inside a postgresSQL databse.

## Running with Docker

A single container serves both the frontend and the API.

**Prerequisites:** Docker installed.

```powershell
# 1. Build the combined image (from the repo root)
docker build -t foodinventory .

# 2. Start PostgreSQL
docker run -d --name foodinventory-db `
  -e POSTGRES_PASSWORD=postgres `
  -e POSTGRES_DB=foodinventory `
  -p 5432:5432 `
  postgres:16

# 3. Start the app
docker run -d --name foodinventory `
  -e DATABASE_URL=postgres://postgres:postgres@foodinventory-db:5432/foodinventory?sslmode=disable `
  -p 8080:8080 `
  --link foodinventory-db `
  foodinventory
```

The UI is then available at `http://localhost:8080` and the API at `http://localhost:8080/api`.

The database schema is applied automatically on first startup — no manual migration step is needed.

### Stopping and removing containers

```powershell
docker stop foodinventory foodinventory-db
docker rm foodinventory foodinventory-db
```

## Configuration

All settings are passed as environment variables.

### Database connection

Two modes are supported. Set **either** `DATABASE_URL` **or** the individual `DB_*` variables.

| Variable | Default | Description |
|---|---|---|
| `DATABASE_URL` | — | Full libpq connection string. When set, all `DB_*` variables below are ignored. |
| `DB_HOST` | `localhost` | Database server hostname or IP |
| `DB_PORT` | `5432` | Database server port |
| `DB_USER` | `postgres` | Database user |
| `DB_PASSWORD` | `postgres` | Database password |
| `DB_NAME` | `foodinventory` | Database name |
| `DB_SSL_MODE` | `disable` | TLS mode: `disable` · `require` · `verify-ca` · `verify-full` |
| `DB_SSL_CA_CERT` | — | PEM-encoded CA certificate, supplied inline. Takes priority over `DB_SSL_CA_CERT_FILE`. |
| `DB_SSL_CA_CERT_FILE` | — | Path to a PEM CA certificate file (ideal for Kubernetes secret/configmap mounts). Used when `DB_SSL_CA_CERT` is not set. |

When neither CA variable is set the system / container trust store is used.

> **Note:** `DB_SSL_MODE` is only used when building the connection string from `DB_*` variables. When using `DATABASE_URL`, embed `sslmode=` in that string directly. The CA certificate variables are always honoured regardless of which mode is used.

### Server

| Variable | Default | Description |
|---|---|---|
| `PORT` | `8080` | HTTP listen port |

### Optional settings

| Variable | Default | Description |
|---|---|---|
| `PRODUCT_LOOKUP_TIMEOUT_MS` | `500` | Timeout in milliseconds for Open Food Facts product lookup requests. When the request exceeds this limit the product is still added to inventory (with a stub entry); the next scan will retry the lookup. |

### TLS examples (verify-ca with a private CA)

**Inline cert (Docker / shell):**

```bash
docker run -d --name foodinventory \
  -e DB_HOST=my-db-host \
  -e DB_SSL_MODE=verify-ca \
  -e DB_SSL_CA_CERT="$(cat /path/to/ca.pem)" \
  -p 8080:8080 \
  foodinventory
```

**Mounted file (Kubernetes):**

```yaml
env:
  - name: DB_SSL_MODE
    value: verify-ca
  - name: DB_SSL_CA_CERT_FILE
    value: /etc/ssl/db-ca/ca.pem
volumeMounts:
  - name: db-ca
    mountPath: /etc/ssl/db-ca
    readOnly: true
volumes:
  - name: db-ca
    secret:
      secretName: db-ca-cert
```

## Android app — MQTT smart home integration

The Android companion app can publish scan events and receive screen-control commands via MQTT. All settings are optional and configured inside the app under **Settings → MQTT Smart Home**.

### Settings

| Setting | Default | Description |
|---------|---------|-------------|
| Enabled | off | Master switch; no connection is made when disabled |
| Broker host / IP | — | Hostname or IP address of the MQTT broker |
| Port | 1883 / 8883 | Port to connect on; defaults to 1883 (plain) or 8883 (TLS) |
| Use TLS / SSL | off | Encrypt the connection with TLS |
| Skip certificate validation | off | Accept any server certificate (useful with self-signed CAs); only available when TLS is on |
| Username / Password | — | Broker credentials; leave blank if the broker allows anonymous access |
| Topic prefix | `foodinventory/` | Prepended to every topic name |

### Topics

All topic names below use the default prefix `foodinventory/`. Substitute your configured prefix if you changed it.

#### Published by the app

| Topic | Retained | Payload | When |
|-------|----------|---------|------|
| `foodinventory/status` | yes | `online` | On successful connection |
| `foodinventory/status` | yes | `offline` | On graceful shutdown (synchronous, delivered before disconnect) |
| `foodinventory/status` | yes | `offline` | Last Will — sent by broker if the connection drops unexpectedly |
| `foodinventory/scan/add` | no | EAN string | After a product is successfully added to inventory |
| `foodinventory/scan/remove` | no | EAN string | After a product is successfully removed from inventory |

Use `foodinventory/scan/add` and `foodinventory/scan/remove` to trigger smarthome actions such as flashing a green or red indicator lamp to confirm the scan.

#### Subscribed by the app

| Topic | Payload | Effect |
|-------|---------|--------|
| `foodinventory/screen` | `on` | Turns the screen on and re-applies keep-screen-on |
| `foodinventory/screen` | `off` | Releases keep-screen-on (screen dims/turns off at next OS timeout); a CPU wake lock and WiFi lock are held so the app stays reachable to receive the `on` command |

Payload matching is case-insensitive (`ON`, `Off`, etc. all work).

### Example (mosquitto_pub)

```bash
# Turn screen off
mosquitto_pub -h 192.168.1.x -t "foodinventory/screen" -m "off"

# Turn screen on
mosquitto_pub -h 192.168.1.x -t "foodinventory/screen" -m "on"

# Watch all app events
mosquitto_sub -h 192.168.1.x -t "foodinventory/#" -v
```

## Development

| | Processes | How |
|---|---|---|
| **Development** | 2 — Vite dev server + Go | `npm run dev -- --host` + `go run ./cmd/server` |
| **Production (Docker)** | 1 — Go binary only | `docker build` → Go serves embedded frontend |

## API

The full API is documented in [`docs/openapi.yaml`](docs/openapi.yaml) (OpenAPI 3.1.0).

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/health` | Liveness probe — `200 OK` while the process is running |
| `GET` | `/api/ready` | Readiness probe — `200 OK` when the database is reachable, `503` otherwise |
| `GET` | `/api/inventory` | List current stock |
| `POST` | `/api/inventory` | Add or increment a product by EAN |
| `DELETE` | `/api/inventory/{ean}` | Decrement or remove a product |
| `GET` | `/api/alerts` | Active low-stock and expiry alerts |
| `GET` | `/api/settings` | Get application settings |
| `PATCH` | `/api/settings` | Update application settings |