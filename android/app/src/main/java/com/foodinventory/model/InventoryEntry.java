package com.foodinventory.model;

import com.google.gson.annotations.SerializedName;

public class InventoryEntry {
    @SerializedName("id")
    public int id;

    @SerializedName("product")
    public Product product;

    @SerializedName("quantity")
    public int quantity;

    @SerializedName("expiry_date")
    public String expiryDate;

    @SerializedName("low_stock_threshold")
    public int lowStockThreshold;
}
