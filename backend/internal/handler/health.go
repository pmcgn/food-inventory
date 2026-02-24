package handler

import (
	"context"
	"net/http"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
)

// RegisterHealth wires the liveness and readiness endpoints onto mux.
//
//	GET /api/health  — liveness:  always 200 while the process is running
//	GET /api/ready   — readiness: 200 when the database is reachable, 503 otherwise
func RegisterHealth(mux *http.ServeMux, pool *pgxpool.Pool) {
	mux.HandleFunc("GET /api/health", healthHandler())
	mux.HandleFunc("GET /api/ready", readyHandler(pool))
}

func healthHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
	}
}

func readyHandler(pool *pgxpool.Pool) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
		defer cancel()

		if err := pool.Ping(ctx); err != nil {
			writeError(w, http.StatusServiceUnavailable, "DB_UNAVAILABLE",
				"database is not reachable")
			return
		}
		writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
	}
}
