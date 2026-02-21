package main

import (
	"context"
	"embed"
	"io/fs"
	"log"
	"net/http"
	"strings"

	"foodinventory/internal/config"
	"foodinventory/internal/db"
	"foodinventory/internal/handler"
	"foodinventory/internal/service"
)

//go:embed all:ui
var staticFiles embed.FS

func main() {
	ctx := context.Background()

	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("configuration error: %v", err)
	}

	pool, err := db.NewPool(ctx, cfg.DatabaseURL, cfg.DBSSLCACert)
	if err != nil {
		log.Fatalf("database connection failed: %v", err)
	}
	defer pool.Close()

	if err := db.RunMigrations(ctx, pool); err != nil {
		log.Fatalf("migrations failed: %v", err)
	}

	productSvc := service.NewProductService(pool, cfg.OFFTimeout)
	inventorySvc := service.NewInventoryService(pool, productSvc)
	alertSvc := service.NewAlertService(pool)
	settingsSvc := service.NewSettingsService(pool)

	mux := http.NewServeMux()
	handler.RegisterInventory(mux, inventorySvc)
	handler.RegisterAlerts(mux, alertSvc)
	handler.RegisterSettings(mux, settingsSvc)

	uiFS, err := fs.Sub(staticFiles, "ui")
	if err != nil {
		log.Fatalf("static files error: %v", err)
	}
	mux.Handle("/", spaHandler(uiFS))

	addr := ":" + cfg.Port
	log.Printf("server listening on %s", addr)
	if err := http.ListenAndServe(addr, mux); err != nil {
		log.Fatalf("server error: %v", err)
	}
}

// spaHandler serves static files from fsys and falls back to index.html for
// any path that does not resolve to a regular file (SPA client-side routing).
func spaHandler(fsys fs.FS) http.Handler {
	fileServer := http.FileServerFS(fsys)
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		path := strings.TrimPrefix(r.URL.Path, "/")
		if path == "" {
			path = "."
		}
		f, err := fsys.Open(path)
		if err == nil {
			stat, statErr := f.Stat()
			f.Close()
			if statErr == nil && !stat.IsDir() {
				fileServer.ServeHTTP(w, r)
				return
			}
		}
		// Unknown path or directory — serve index.html for SPA routing.
		r = r.Clone(r.Context())
		r.URL.Path = "/"
		fileServer.ServeHTTP(w, r)
	})
}
