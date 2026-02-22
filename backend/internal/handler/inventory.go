package handler

import (
	"encoding/json"
	"errors"
	"net/http"

	"foodinventory/internal/model"
	"foodinventory/internal/service"
)

// RegisterInventory wires inventory endpoints onto mux.
func RegisterInventory(mux *http.ServeMux, svc *service.InventoryService) {
	mux.HandleFunc("GET /api/inventory", listInventory(svc))
	mux.HandleFunc("POST /api/inventory", addProduct(svc))
	mux.HandleFunc("DELETE /api/inventory/{ean}", removeProduct(svc))
}

func listInventory(svc *service.InventoryService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		entries, err := svc.List(r.Context())
		if err != nil {
			writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}
		writeJSON(w, http.StatusOK, entries)
	}
}

func addProduct(svc *service.InventoryService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var req model.AddProductRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			writeError(w, http.StatusBadRequest, "BAD_REQUEST", "invalid request body")
			return
		}
		if !validateEAN(req.EAN) {
			writeError(w, http.StatusUnprocessableEntity, "INVALID_EAN",
				"EAN must be 8 or 13 digits")
			return
		}

		entry, created, err := svc.Add(r.Context(), req)
		if err != nil {
			writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}

		status := http.StatusOK
		if created {
			status = http.StatusCreated
		}
		writeJSON(w, status, entry)
	}
}

func removeProduct(svc *service.InventoryService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ean := r.PathValue("ean")
		if !validateEAN(ean) {
			writeError(w, http.StatusUnprocessableEntity, "INVALID_EAN",
				"EAN must be 8 or 13 digits")
			return
		}

		entry, err := svc.Remove(r.Context(), ean)
		if errors.Is(err, service.ErrInventoryEntryNotFound) {
			writeError(w, http.StatusNotFound, "INVENTORY_ENTRY_NOT_FOUND",
				"No inventory entry for EAN "+ean)
			return
		}
		if err != nil {
			writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
			return
		}

		if entry == nil {
			// Quantity reached 0 — entry deleted.
			w.WriteHeader(http.StatusNoContent)
			return
		}
		writeJSON(w, http.StatusOK, entry)
	}
}
