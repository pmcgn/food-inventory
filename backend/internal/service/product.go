package service

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"foodinventory/internal/model"
)

const openFoodFactsURL = "https://world.openfoodfacts.org/api/v2/product/%s"

// ErrFetchTimeout is returned by GetOrFetch when the Open Food Facts request
// exceeded the configured timeout. The caller may still add the product to
// inventory using a stub row; the next scan will retry the lookup.
var ErrFetchTimeout = errors.New("open food facts request timed out")

// ProductService resolves EAN codes to product metadata,
// caching results in the local products table.
type ProductService struct {
	db      *pgxpool.Pool
	timeout time.Duration
}

func NewProductService(db *pgxpool.Pool, timeout time.Duration) *ProductService {
	return &ProductService{db: db, timeout: timeout}
}

// GetOrFetch returns a cached product or fetches it from Open Food Facts.
// Returns nil, nil when the EAN is unknown in the external API.
// Returns nil, ErrFetchTimeout when the external request exceeded the timeout.
func (s *ProductService) GetOrFetch(ctx context.Context, ean string) (*model.Product, error) {
	p, err := s.getFromDB(ctx, ean)
	if err != nil {
		return nil, err
	}
	if p != nil {
		return p, nil
	}

	fetchCtx, cancel := context.WithTimeout(ctx, s.timeout)
	defer cancel()

	p, err = fetchFromOpenFoodFacts(fetchCtx, ean)
	if err != nil {
		if errors.Is(err, context.DeadlineExceeded) && ctx.Err() == nil {
			return nil, ErrFetchTimeout
		}
		return nil, err
	}
	if p == nil {
		return nil, nil
	}

	if err := s.upsert(ctx, p); err != nil {
		return nil, err
	}
	return p, nil
}

// InsertStub inserts a placeholder products row for the given EAN with
// resolved = FALSE and the EAN itself as the name. It is a no-op if any row
// for that EAN already exists (resolved or stub), preserving a previously
// cached entry.
func (s *ProductService) InsertStub(ctx context.Context, ean string) error {
	_, err := s.db.Exec(ctx,
		`INSERT INTO products (ean, name, resolved)
		 VALUES ($1, $2, FALSE)
		 ON CONFLICT (ean) DO NOTHING`,
		ean, ean,
	)
	return err
}

// getFromDB returns the cached product for ean, or nil if not found / not yet
// resolved (stub row inserted after a previous timeout).
func (s *ProductService) getFromDB(ctx context.Context, ean string) (*model.Product, error) {
	var p model.Product
	err := s.db.QueryRow(ctx,
		`SELECT ean, name, category, image_url FROM products WHERE ean = $1 AND resolved = TRUE`, ean,
	).Scan(&p.EAN, &p.Name, &p.Category, &p.ImageURL)
	if err == pgx.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &p, nil
}

func (s *ProductService) upsert(ctx context.Context, p *model.Product) error {
	_, err := s.db.Exec(ctx,
		`INSERT INTO products (ean, name, category, image_url, resolved)
		 VALUES ($1, $2, $3, $4, TRUE)
		 ON CONFLICT (ean) DO UPDATE
		   SET name = $2, category = $3, image_url = $4, resolved = TRUE`,
		p.EAN, p.Name, p.Category, p.ImageURL,
	)
	return err
}

// offResponse maps the subset of the Open Food Facts API response we need.
type offResponse struct {
	Status  int `json:"status"`
	Product struct {
		ProductName        string   `json:"product_name"`
		CategoriesTags     []string `json:"categories_tags"`
		ImageFrontSmallURL string   `json:"image_front_small_url"`
	} `json:"product"`
}

func fetchFromOpenFoodFacts(ctx context.Context, ean string) (*model.Product, error) {
	url := fmt.Sprintf(openFoodFactsURL, ean)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "FoodInventory/1.0 (home warehouse tool)")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	var off offResponse
	if err := json.NewDecoder(resp.Body).Decode(&off); err != nil {
		return nil, err
	}
	if off.Status == 0 {
		return nil, nil // product not found in Open Food Facts
	}

	p := &model.Product{EAN: ean, Name: off.Product.ProductName}
	if len(off.Product.CategoriesTags) > 0 {
		cat := off.Product.CategoriesTags[0]
		p.Category = &cat
	}
	if off.Product.ImageFrontSmallURL != "" {
		img := off.Product.ImageFrontSmallURL
		p.ImageURL = &img
	}
	return p, nil
}
