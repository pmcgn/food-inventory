package handler

import (
	"encoding/json"
	"net/http"
	"regexp"

	"foodinventory/internal/model"
)

// eanPattern matches EAN-8 (8 digits) and EAN-13 (13 digits).
var eanPattern = regexp.MustCompile(`^\d{8}(\d{5})?$`)

func validateEAN(ean string) bool {
	return eanPattern.MatchString(ean)
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, status int, code, message string) {
	writeJSON(w, status, model.APIError{Code: code, Message: message})
}
