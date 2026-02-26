package com.foodinventory.model;

import com.google.gson.annotations.SerializedName;

public class AddRequest {
    @SerializedName("ean")
    public String ean;

    public AddRequest(String ean) {
        this.ean = ean;
    }
}
