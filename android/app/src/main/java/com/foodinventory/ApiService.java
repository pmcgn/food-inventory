package com.foodinventory;

import com.foodinventory.model.AddRequest;
import com.foodinventory.model.InventoryEntry;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface ApiService {

    @POST("api/inventory")
    Call<InventoryEntry> addProduct(@Body AddRequest request);

    // Returns InventoryEntry (200) if qty > 0 after decrement, or null body (204) if entry deleted.
    @DELETE("api/inventory/{ean}")
    Call<InventoryEntry> removeProduct(@Path("ean") String ean);
}
