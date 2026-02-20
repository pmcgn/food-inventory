# Agent: Alert

## Responsibility
Evaluates inventory state and surfaces warnings to the user when stock is low or products are nearing expiry.

## Scope
- Low stock detection: quantity at or below `low_stock_threshold`
- Expiry detection: expiry date within a configurable look-ahead window (e.g. 7 days)
- Delivering alerts in the mobile web UI (in-app banners or a dedicated alerts view)
- Alerts are computed on demand (at inventory load time), not via background jobs

## Key Decisions
- No push notifications for initial version — alerts are visible only while the app is open
- `low_stock_threshold` is stored per inventory row (defaulting to `1`) so each product can have its own threshold
- Expiry look-ahead window is a global app setting (default: 7 days)
- Alerts are purely read-only — they do not trigger any automatic inventory changes

## Alert Types

| Type       | Trigger condition                                        | Display |
|------------|----------------------------------------------------------|---------|
| Low Stock  | `quantity <= low_stock_threshold`                        | Warning banner per affected product |
| Expiry Soon | `expiry_date IS NOT NULL AND expiry_date <= today + 7d` | Warning banner per affected product |

## Interfaces
- **Input**: Current inventory list (from Inventory API)
- **Output**: List of `Alert` objects `{ type, ean, productName, detail }` rendered in the UI
