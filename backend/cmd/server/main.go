package main

import (
	"context"
	"log"
	"net/http"

	"foodinventory/internal/config"
	"foodinventory/internal/db"
	"foodinventory/internal/handler"
	"foodinventory/internal/service"
)

func main() {
	ctx := context.Background()

	cfg := config.Load()

	pool, err := db.NewPool(ctx, cfg.DatabaseURL)
	if err != nil {
		log.Fatalf("database connection failed: %v", err)
	}
	defer pool.Close()

	if err := db.RunMigrations(ctx, pool); err != nil {
		log.Fatalf("migrations failed: %v", err)
	}

	productSvc := service.NewProductService(pool)
	inventorySvc := service.NewInventoryService(pool, productSvc)
	alertSvc := service.NewAlertService(pool)
	settingsSvc := service.NewSettingsService(pool)

	mux := http.NewServeMux()
	handler.RegisterInventory(mux, inventorySvc)
	handler.RegisterAlerts(mux, alertSvc)
	handler.RegisterSettings(mux, settingsSvc)

	addr := ":" + cfg.Port
	log.Printf("server listening on %s", addr)
	if err := http.ListenAndServe(addr, mux); err != nil {
		log.Fatalf("server error: %v", err)
	}
}
