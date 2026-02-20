package handler

import (
	"encoding/json"
	"net/http"

	"foodinventory/internal/model"
	"foodinventory/internal/service"
)

// RegisterSettings wires settings endpoints onto mux.
func RegisterSettings(mux *http.ServeMux, svc *service.SettingsService) {
	mux.HandleFunc("GET /settings", getSettings(svc))
	mux.HandleFunc("PATCH /settings", updateSettings(svc))
}

func getSettings(svc *service.SettingsService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		settings, err := svc.Get(r.Context())
		if err != nil {
			writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		writeJSON(w, http.StatusOK, settings)
	}
}

func updateSettings(svc *service.SettingsService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var s model.Settings
		if err := json.NewDecoder(r.Body).Decode(&s); err != nil {
			writeError(w, http.StatusBadRequest, "BAD_REQUEST", "invalid request body")
			return
		}
		if s.ExpiryWarningDays < 1 {
			writeError(w, http.StatusUnprocessableEntity, "INVALID_SETTINGS",
				"expiry_warning_days must be >= 1")
			return
		}
		updated, err := svc.Update(r.Context(), s)
		if err != nil {
			writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		writeJSON(w, http.StatusOK, updated)
	}
}
