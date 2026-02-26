package com.foodinventory;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.foodinventory.model.AddRequest;
import com.foodinventory.model.ApiError;
import com.foodinventory.model.InventoryEntry;
import com.google.gson.Gson;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainViewModel extends ViewModel {

    public enum Mode { ADD, REMOVE }

    private final MutableLiveData<InventoryEntry> lastEntry = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    // Signals that the last remove deleted the entry (qty reached 0)
    private final MutableLiveData<String> entryDeleted = new MutableLiveData<>();

    private Mode mode = Mode.ADD;
    private String baseUrl = "";

    public LiveData<InventoryEntry> getLastEntry() { return lastEntry; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getEntryDeleted() { return entryDeleted; }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }

    public void setBaseUrl(String url) { this.baseUrl = url; }

    public void scan(String ean) {
        if (baseUrl.isEmpty()) {
            errorMessage.setValue("Backend URL not configured. Tap the settings icon.");
            return;
        }
        loading.setValue(true);
        ApiService api = ApiClient.getInstance(baseUrl);

        if (mode == Mode.ADD) {
            api.addProduct(new AddRequest(ean)).enqueue(new Callback<InventoryEntry>() {
                @Override
                public void onResponse(Call<InventoryEntry> call, Response<InventoryEntry> response) {
                    loading.postValue(false);
                    if (response.isSuccessful() && response.body() != null) {
                        lastEntry.postValue(response.body());
                    } else {
                        postError(response);
                    }
                }

                @Override
                public void onFailure(Call<InventoryEntry> call, Throwable t) {
                    loading.postValue(false);
                    errorMessage.postValue("Network error: " + t.getMessage());
                }
            });
        } else {
            api.removeProduct(ean).enqueue(new Callback<InventoryEntry>() {
                @Override
                public void onResponse(Call<InventoryEntry> call, Response<InventoryEntry> response) {
                    loading.postValue(false);
                    if (response.isSuccessful()) {
                        if (response.body() != null) {
                            // qty > 0 after decrement
                            lastEntry.postValue(response.body());
                        } else {
                            // 204: entry removed (qty reached 0)
                            entryDeleted.postValue(ean);
                        }
                    } else {
                        postError(response);
                    }
                }

                @Override
                public void onFailure(Call<InventoryEntry> call, Throwable t) {
                    loading.postValue(false);
                    errorMessage.postValue("Network error: " + t.getMessage());
                }
            });
        }
    }

    private void postError(Response<?> response) {
        try {
            String body = response.errorBody() != null ? response.errorBody().string() : "";
            ApiError err = new Gson().fromJson(body, ApiError.class);
            if (err != null && err.message != null) {
                errorMessage.postValue(err.message);
            } else {
                errorMessage.postValue("Server error " + response.code());
            }
        } catch (IOException e) {
            errorMessage.postValue("Server error " + response.code());
        }
    }
}
