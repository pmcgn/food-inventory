package service

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"foodinventory/internal/model"
)

const openFoodFactsURL = "https://world.openfoodfacts.org/api/v2/product/%s"

// ProductService resolves EAN codes to product metadata,
// caching results in the local products table.
type ProductService struct {
	db *pgxpool.Pool
}

func NewProductService(db *pgxpool.Pool) *ProductService {
	return &ProductService{db: db}
}

// GetOrFetch returns a cached product or fetches it from Open Food Facts.
// Returns nil, nil when the EAN is unknown in the external API.
func (s *ProductService) GetOrFetch(ctx context.Context, ean string) (*model.Product, error) {
	p, err := s.getFromDB(ctx, ean)
	if err != nil {
		return nil, err
	}
	if p != nil {
		return p, nil
	}

	p, err = fetchFromOpenFoodFacts(ctx, ean)
	if err != nil {
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

func (s *ProductService) getFromDB(ctx context.Context, ean string) (*model.Product, error) {
	var p model.Product
	err := s.db.QueryRow(ctx,
		`SELECT ean, name, category, image_url FROM products WHERE ean = $1`, ean,
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
		`INSERT INTO products (ean, name, category, image_url)
		 VALUES ($1, $2, $3, $4)
		 ON CONFLICT (ean) DO UPDATE
		   SET name = $2, category = $3, image_url = $4`,
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
