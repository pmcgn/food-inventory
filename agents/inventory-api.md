# Agent: Inventory API

## Responsibility
Manages all backend CRUD operations for the food inventory and owns the PostgreSQL database schema.

## Scope
- REST API endpoints for adding, removing, and listing inventory entries
- Database schema design and migrations
- Business logic: quantity increment/decrement, zero-stock cleanup
- Exposing inventory data to the frontend

## Key Decisions
- Separate `products` table (EAN → product metadata, cached from EAN Lookup) from `inventory` table (product + quantity + expiry date)
- Quantity is always `>= 0`; when decremented to 0 the inventory row is deleted
- Expiry date is optional and stored at add time only — no update endpoint needed for initial version

## Database Schema (draft)

```sql
-- Cached product metadata from external API
CREATE TABLE products (
    ean         VARCHAR(13) PRIMARY KEY,
    name        TEXT NOT NULL,
    category    TEXT,
    image_url   TEXT
);

-- Current inventory state
CREATE TABLE inventory (
    id          SERIAL PRIMARY KEY,
    ean         VARCHAR(13) REFERENCES products(ean),
    quantity    INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    expiry_date DATE,
    low_stock_threshold INT NOT NULL DEFAULT 1
);
```

## API Endpoints (draft)

| Method | Path                  | Description                          |
|--------|-----------------------|--------------------------------------|
| POST   | `/inventory/add`      | Add or increment a product by EAN    |
| POST   | `/inventory/remove`   | Decrement or delete a product by EAN |
| GET    | `/inventory`          | List all current inventory entries   |

## Interfaces
- **Input**: EAN code + optional expiry date (add), EAN code (remove)
- **Output**: Updated inventory entry or list of inventory entries
- **Database**: PostgreSQL
