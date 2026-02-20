package service

import (
	"context"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	"foodinventory/internal/model"
)

// AlertService computes active alerts from the current inventory state.
type AlertService struct {
	db *pgxpool.Pool
}

func NewAlertService(db *pgxpool.Pool) *AlertService {
	return &AlertService{db: db}
}

// List returns all active low-stock and expiry-soon alerts.
// Alerts are computed on demand; no background jobs are required.
func (s *AlertService) List(ctx context.Context) ([]model.Alert, error) {
	var expiryWarningDays int
	err := s.db.QueryRow(ctx,
		`SELECT expiry_warning_days FROM settings WHERE id = 1`,
	).Scan(&expiryWarningDays)
	if err != nil {
		return nil, err
	}

	rows, err := s.db.Query(ctx, `
		SELECT i.ean, p.name, i.quantity, i.low_stock_threshold,
		       TO_CHAR(i.expiry_date, 'YYYY-MM-DD')
		FROM inventory i
		JOIN products p ON p.ean = i.ean`,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	warnBefore := time.Now().AddDate(0, 0, expiryWarningDays)
	alerts := []model.Alert{}

	for rows.Next() {
		var (
			ean, name  string
			quantity   int
			threshold  int
			expiryDate *string
		)
		if err := rows.Scan(&ean, &name, &quantity, &threshold, &expiryDate); err != nil {
			return nil, err
		}

		if quantity <= threshold {
			alerts = append(alerts, model.Alert{
				Type:        model.AlertLowStock,
				EAN:         ean,
				ProductName: name,
				Detail: fmt.Sprintf(
					"Only %d item(s) left (threshold: %d)", quantity, threshold,
				),
			})
		}

		if expiryDate != nil {
			expiry, err := time.Parse("2006-01-02", *expiryDate)
			if err == nil && !expiry.After(warnBefore) {
				daysLeft := int(time.Until(expiry).Hours()/24) + 1
				alerts = append(alerts, model.Alert{
					Type:        model.AlertExpirySoon,
					EAN:         ean,
					ProductName: name,
					Detail: fmt.Sprintf(
						"Expires in %d day(s) (%s)", daysLeft, *expiryDate,
					),
				})
			}
		}
	}
	return alerts, rows.Err()
}
