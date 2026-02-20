package handler

import (
	"net/http"

	"foodinventory/internal/service"
)

// RegisterAlerts wires the alerts endpoint onto mux.
func RegisterAlerts(mux *http.ServeMux, svc *service.AlertService) {
	mux.HandleFunc("GET /alerts", listAlerts(svc))
}

func listAlerts(svc *service.AlertService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		alerts, err := svc.List(r.Context())
		if err != nil {
			writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		writeJSON(w, http.StatusOK, alerts)
	}
}
