package com.foodinventory.model;

import com.google.gson.annotations.SerializedName;

public class ApiError {
    @SerializedName("code")
    public String code;

    @SerializedName("message")
    public String message;
}
