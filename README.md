# Food Inventory

Food Inventory is a little warehouse tool for home usage. You can add products to your storage by EAN codes (later via barcode scan) and remove them accordingly. The backend will store the current inventor inside a postgresSQL databse.

## Running with Docker

The backend and database can be started together with Docker Compose.

**Prerequisites:** Docker and Docker Compose installed.

```powershell
# 1. Build the backend image
docker build -t foodinventory-backend ./backend

# 2. Start PostgreSQL and the backend
docker run -d --name foodinventory-db `
  -e POSTGRES_PASSWORD=postgres `
  -e POSTGRES_DB=foodinventory `
  -p 5432:5432 `
  postgres:16

docker run -d --name foodinventory-backend `
  -e DATABASE_URL=postgres://postgres:postgres@foodinventory-db:5432/foodinventory?sslmode=disable `
  -p 8080:8080 `
  --link foodinventory-db `
  foodinventory-backend
```

The API is then available at `http://localhost:8080`.

The database schema is applied automatically on first startup — no manual migration step is needed.

### Stopping and removing containers

```powershell
docker stop foodinventory-backend foodinventory-db
docker rm foodinventory-backend foodinventory-db
```

## API

The full API is documented in [`docs/openapi.yaml`](docs/openapi.yaml) (OpenAPI 3.1.0).

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/inventory` | List current stock |
| `POST` | `/inventory` | Add or increment a product by EAN |
| `DELETE` | `/inventory/{ean}` | Decrement or remove a product |
| `GET` | `/alerts` | Active low-stock and expiry alerts |
| `GET` | `/settings` | Get application settings |
| `PATCH` | `/settings` | Update application settings |