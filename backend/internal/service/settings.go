package service

import (
	"context"

	"github.com/jackc/pgx/v5/pgxpool"

	"foodinventory/internal/model"
)

// SettingsService reads and updates the singleton settings row.
type SettingsService struct {
	db *pgxpool.Pool
}

func NewSettingsService(db *pgxpool.Pool) *SettingsService {
	return &SettingsService{db: db}
}

func (s *SettingsService) Get(ctx context.Context) (*model.Settings, error) {
	var settings model.Settings
	err := s.db.QueryRow(ctx,
		`SELECT expiry_warning_days FROM settings WHERE id = 1`,
	).Scan(&settings.ExpiryWarningDays)
	if err != nil {
		return nil, err
	}
	return &settings, nil
}

func (s *SettingsService) Update(ctx context.Context, in model.Settings) (*model.Settings, error) {
	_, err := s.db.Exec(ctx,
		`UPDATE settings SET expiry_warning_days = $1 WHERE id = 1`,
		in.ExpiryWarningDays,
	)
	if err != nil {
		return nil, err
	}
	return &in, nil
}
