# Agent: EAN Lookup

## Responsibility
Resolves an EAN code to product details (name, category, image, expiry hint) by querying an external product database API.

## Scope
- HTTP integration with the Open Food Facts API (`https://world.openfoodfacts.org/api/v2/product/{ean}`)
- Mapping API response fields to the internal `Product` model
- Local caching of resolved products to avoid repeated external calls for the same EAN
- Handling unknown EANs gracefully (product not found in external API)

## Key Decisions
- Cache resolved EANs in the PostgreSQL `products` table so the external API is only called once per unique EAN
- If the external API returns no result, prompt the user to enter product details manually (future scope — for now return "unknown product" and abort)
- Do not store raw API responses; only map and persist the fields the app needs

## Internal Product Model (target fields)
| Field        | Source                          |
|--------------|---------------------------------|
| `ean`        | input                           |
| `name`       | `product_name`                  |
| `category`   | `categories_tags[0]`            |
| `image_url`  | `image_front_small_url`         |

## Interfaces
- **Input**: EAN string (from Barcode Scanner agent)
- **Output**: `Product` object or "not found" error
- **External API**: Open Food Facts REST API (no auth required)
