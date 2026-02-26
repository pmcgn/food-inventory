package com.foodinventory;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    private static ApiService instance;
    private static String currentBaseUrl;

    public static ApiService getInstance(String baseUrl) {
        // Ensure URL ends with /
        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }
        if (instance == null || !baseUrl.equals(currentBaseUrl)) {
            currentBaseUrl = baseUrl;
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            instance = retrofit.create(ApiService.class);
        }
        return instance;
    }

    public static void reset() {
        instance = null;
        currentBaseUrl = null;
    }
}
