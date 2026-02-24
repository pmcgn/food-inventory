package service

import (
	"context"
	"errors"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"foodinventory/internal/model"
)

// Sentinel errors mapped to HTTP status codes in the handler layer.
var ErrInventoryEntryNotFound = errors.New("inventory entry not found")

// InventoryService manages stock CRUD operations.
type InventoryService struct {
	db         *pgxpool.Pool
	productSvc *ProductService
}

func NewInventoryService(db *pgxpool.Pool, productSvc *ProductService) *InventoryService {
	return &InventoryService{db: db, productSvc: productSvc}
}

// List returns all inventory entries ordered by product name.
func (s *InventoryService) List(ctx context.Context) ([]model.InventoryEntry, error) {
	rows, err := s.db.Query(ctx, `
		SELECT i.id, i.quantity,
		       TO_CHAR(i.expiry_date, 'YYYY-MM-DD'),
		       i.low_stock_threshold,
		       p.ean, p.name, p.category, p.image_url, p.resolved
		FROM inventory i
		JOIN products p ON p.ean = i.ean
		ORDER BY p.name`,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	entries := []model.InventoryEntry{}
	for rows.Next() {
		var e model.InventoryEntry
		if err := rows.Scan(
			&e.ID, &e.Quantity, &e.ExpiryDate, &e.LowStockThreshold,
			&e.Product.EAN, &e.Product.Name, &e.Product.Category, &e.Product.ImageURL, &e.Product.Resolved,
		); err != nil {
			return nil, err
		}
		entries = append(entries, e)
	}
	return entries, rows.Err()
}

// Add adds a product to inventory or increments its quantity.
// Returns the entry and true when a new row was created, false on increment.
func (s *InventoryService) Add(
	ctx context.Context, req model.AddProductRequest,
) (*model.InventoryEntry, bool, error) {
	product, err := s.productSvc.GetOrFetch(ctx, req.EAN)
	if err != nil && !errors.Is(err, ErrFetchTimeout) {
		return nil, false, err
	}
	if product == nil {
		// EAN not found in external API or fetch timed out — insert a stub
		// row using the EAN as the name with resolved = false.
		if stubErr := s.productSvc.InsertStub(ctx, req.EAN); stubErr != nil {
			return nil, false, stubErr
		}
	}

	var id int
	err = s.db.QueryRow(ctx,
		`SELECT id FROM inventory WHERE ean = $1`, req.EAN,
	).Scan(&id)

	if err == pgx.ErrNoRows {
		// First time this product is added — create a new entry.
		err = s.db.QueryRow(ctx,
			`INSERT INTO inventory (ean, quantity, expiry_date)
			 VALUES ($1, 1, $2::date)
			 RETURNING id`,
			req.EAN, req.ExpiryDate,
		).Scan(&id)
		if err != nil {
			return nil, false, err
		}
		entry, err := s.getByID(ctx, id)
		return entry, true, err
	}
	if err != nil {
		return nil, false, err
	}

	// Product already in inventory — increment quantity.
	_, err = s.db.Exec(ctx,
		`UPDATE inventory SET quantity = quantity + 1 WHERE id = $1`, id,
	)
	if err != nil {
		return nil, false, err
	}
	entry, err := s.getByID(ctx, id)
	return entry, false, err
}

// Remove decrements quantity by 1.
// Returns nil when quantity reached 0 and the entry was deleted.
func (s *InventoryService) Remove(ctx context.Context, ean string) (*model.InventoryEntry, error) {
	var id, quantity int
	err := s.db.QueryRow(ctx,
		`SELECT id, quantity FROM inventory WHERE ean = $1`, ean,
	).Scan(&id, &quantity)
	if err == pgx.ErrNoRows {
		return nil, ErrInventoryEntryNotFound
	}
	if err != nil {
		return nil, err
	}

	if quantity == 1 {
		_, err = s.db.Exec(ctx, `DELETE FROM inventory WHERE id = $1`, id)
		return nil, err
	}

	_, err = s.db.Exec(ctx,
		`UPDATE inventory SET quantity = quantity - 1 WHERE id = $1`, id,
	)
	if err != nil {
		return nil, err
	}
	return s.getByID(ctx, id)
}

func (s *InventoryService) getByID(ctx context.Context, id int) (*model.InventoryEntry, error) {
	var e model.InventoryEntry
	err := s.db.QueryRow(ctx, `
		SELECT i.id, i.quantity,
		       TO_CHAR(i.expiry_date, 'YYYY-MM-DD'),
		       i.low_stock_threshold,
		       p.ean, p.name, p.category, p.image_url, p.resolved
		FROM inventory i
		JOIN products p ON p.ean = i.ean
		WHERE i.id = $1`, id,
	).Scan(
		&e.ID, &e.Quantity, &e.ExpiryDate, &e.LowStockThreshold,
		&e.Product.EAN, &e.Product.Name, &e.Product.Category, &e.Product.ImageURL, &e.Product.Resolved,
	)
	if err != nil {
		return nil, err
	}
	return &e, nil
}
