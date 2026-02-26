package com.foodinventory.model;

import com.google.gson.annotations.SerializedName;

public class Product {
    @SerializedName("ean")
    public String ean;

    @SerializedName("name")
    public String name;

    @SerializedName("category")
    public String category;

    @SerializedName("image_url")
    public String imageUrl;

    @SerializedName("resolved")
    public boolean resolved;
}
