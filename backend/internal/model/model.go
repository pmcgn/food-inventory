package model

// Product holds EAN-resolved metadata cached from Open Food Facts.
type Product struct {
	EAN      string  `json:"ean"`
	Name     string  `json:"name"`
	Category *string `json:"category"`
	ImageURL *string `json:"image_url"`
	Resolved bool    `json:"resolved"`
}

// InventoryEntry is one product line in the current stock.
type InventoryEntry struct {
	ID                int     `json:"id"`
	Product           Product `json:"product"`
	Quantity          int     `json:"quantity"`
	ExpiryDate        *string `json:"expiry_date"`
	LowStockThreshold int     `json:"low_stock_threshold"`
}

// AddProductRequest is the body for POST /inventory.
type AddProductRequest struct {
	EAN        string  `json:"ean"`
	ExpiryDate *string `json:"expiry_date"`
}

// UpdateProductRequest is the body for PATCH /products/{ean}.
type UpdateProductRequest struct {
	Name     string  `json:"name"`
	Category *string `json:"category"`
}

// AlertType classifies an alert.
type AlertType string

const (
	AlertLowStock   AlertType = "low_stock"
	AlertExpirySoon AlertType = "expiry_soon"
)

// Alert represents a single active warning surfaced to the user.
type Alert struct {
	Type        AlertType `json:"type"`
	EAN         string    `json:"ean"`
	ProductName string    `json:"product_name"`
	Detail      string    `json:"detail"`
}

// Settings holds global application configuration stored in the database.
type Settings struct {
	ExpiryWarningDays int `json:"expiry_warning_days"`
}

// APIError is the standard error response body.
type APIError struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}
