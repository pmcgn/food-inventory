package handler

import (
	"encoding/json"
	"net/http"

	"foodinventory/internal/model"
	"foodinventory/internal/service"
)

// RegisterProduct wires product endpoints onto mux.
func RegisterProduct(mux *http.ServeMux, svc *service.ProductService) {
	mux.HandleFunc("PATCH /api/products/{ean}", updateProduct(svc))
}

func updateProduct(svc *service.ProductService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ean := r.PathValue("ean")
		if !validateEAN(ean) {
			writeError(w, http.StatusUnprocessableEntity, "INVALID_EAN",
				"EAN must be 8 or 13 digits")
			return
		}

		var req model.UpdateProductRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			writeError(w, http.StatusBadRequest, "BAD_REQUEST", "invalid request body")
			return
		}
		if req.Name == "" {
			writeError(w, http.StatusUnprocessableEntity, "INVALID_PRODUCT",
				"name must not be empty")
			return
		}

		if err := svc.UpdateProduct(r.Context(), ean, req.Name, req.Category); err != nil {
			writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		w.WriteHeader(http.StatusNoContent)
	}
}
