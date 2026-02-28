package com.foodinventory;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.foodinventory.databinding.ActivityMainBinding;
import com.foodinventory.model.InventoryEntry;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "foodinventory_prefs";
    private static final String KEY_BASE_URL = "base_url";
    private static final long ADD_TIMEOUT_MS = 5 * 60 * 1000L; // 5 minutes

    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    private SharedPreferences prefs;
    private CountDownTimer addModeTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        // Load saved URL
        String savedUrl = prefs.getString(KEY_BASE_URL, "");
        viewModel.setBaseUrl(savedUrl);

        // Show settings on first launch if no URL configured
        if (savedUrl.isEmpty()) {
            showSettingsDialog();
        }

        setupModeButtons();
        setupHidInput();
        observeViewModel();
    }

    // Re-capture focus when window gains focus (e.g. after dialog closes)
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            requestBarcodeInputFocus();
        }
    }

    private void setupModeButtons() {
        selectMode(MainViewModel.Mode.REMOVE); // default

        binding.btnAdd.setOnClickListener(v -> selectMode(MainViewModel.Mode.ADD));
        binding.btnRemove.setOnClickListener(v -> selectMode(MainViewModel.Mode.REMOVE));
        binding.btnSettings.setOnClickListener(v -> showSettingsDialog());
    }

    private void selectMode(MainViewModel.Mode mode) {
        // Cancel any running countdown first
        if (addModeTimer != null) {
            addModeTimer.cancel();
            addModeTimer = null;
        }

        viewModel.setMode(mode);

        if (mode == MainViewModel.Mode.ADD) {
            binding.btnAdd.setAlpha(1.0f);
            binding.btnRemove.setAlpha(0.4f);
            binding.tvStatus.setText(R.string.status_add);
            startAddModeTimer();
        } else {
            binding.btnAdd.setAlpha(0.4f);
            binding.btnRemove.setAlpha(1.0f);
            binding.btnAdd.setText(R.string.btn_add); // reset any countdown text
            binding.timerProgress.setVisibility(View.GONE);
            binding.tvStatus.setText(R.string.status_remove);
        }
        // Clear last result when switching modes
        binding.cardProduct.setVisibility(View.GONE);
    }

    private void startAddModeTimer() {
        binding.timerProgress.setMax(300);
        binding.timerProgress.setProgress(300);
        binding.timerProgress.setVisibility(View.VISIBLE);

        addModeTimer = new CountDownTimer(ADD_TIMEOUT_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000);
                int minutes = secondsLeft / 60;
                int seconds = secondsLeft % 60;
                binding.btnAdd.setText(String.format("ADD  %d:%02d", minutes, seconds));
                binding.timerProgress.setProgress(secondsLeft);
            }

            @Override
            public void onFinish() {
                selectMode(MainViewModel.Mode.REMOVE);
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (addModeTimer != null) {
            addModeTimer.cancel();
        }
    }

    private void setupHidInput() {
        EditText et = binding.etBarcodeInput;
        // Prevent soft keyboard from appearing
        et.setShowSoftInputOnFocus(false);

        et.setOnEditorActionListener((v, actionId, event) -> {
            boolean isDone = actionId == EditorInfo.IME_ACTION_DONE;
            boolean isEnter = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (isDone || isEnter) {
                String ean = et.getText().toString().trim();
                et.setText("");
                if (!ean.isEmpty()) {
                    viewModel.scan(ean);
                }
                return true;
            }
            return false;
        });

        // Also handle hardware Enter key
        et.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                String ean = et.getText().toString().trim();
                et.setText("");
                if (!ean.isEmpty()) {
                    viewModel.scan(ean);
                }
                return true;
            }
            return false;
        });
    }

    private void requestBarcodeInputFocus() {
        binding.etBarcodeInput.requestFocus();
        // Suppress soft keyboard explicitly
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(binding.etBarcodeInput.getWindowToken(), 0);
        }
    }

    private void observeViewModel() {
        viewModel.getLoading().observe(this, loading -> {
            binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        });

        viewModel.getLastEntry().observe(this, this::showProduct);

        viewModel.getEntryDeleted().observe(this, ean -> {
            binding.cardProduct.setVisibility(View.GONE);
            String msg = getString(R.string.msg_removed, ean);
            binding.tvStatus.setText(msg);
            requestBarcodeInputFocus();
        });

        viewModel.getErrorMessage().observe(this, message -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            binding.tvStatus.setText(message);
            requestBarcodeInputFocus();
        });
    }

    private void showProduct(InventoryEntry entry) {
        binding.cardProduct.setVisibility(View.VISIBLE);

        String name = entry.product != null ? entry.product.name : getString(R.string.unknown_product);
        binding.tvProductName.setText(name);

        String category = "";
        if (entry.product != null && entry.product.category != null) {
            // Strip "en:" prefix used by Open Food Facts (e.g. "en:pasta" → "pasta")
            category = entry.product.category.replaceFirst("^[a-z]{2}:", "");
        }
        binding.tvCategory.setText(category);
        binding.tvCategory.setVisibility(category.isEmpty() ? View.GONE : View.VISIBLE);

        binding.tvQuantity.setText(getString(R.string.qty_label, entry.quantity));

        if (entry.expiryDate != null && !entry.expiryDate.isEmpty()) {
            binding.tvExpiry.setText(getString(R.string.expiry_label, entry.expiryDate));
            binding.tvExpiry.setVisibility(View.VISIBLE);
        } else {
            binding.tvExpiry.setVisibility(View.GONE);
        }

        if (entry.product != null && entry.product.imageUrl != null) {
            binding.ivProductImage.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(entry.product.imageUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(binding.ivProductImage);
        } else {
            binding.ivProductImage.setVisibility(View.GONE);
        }

        binding.tvStatus.setText(
                viewModel.getMode() == MainViewModel.Mode.ADD
                        ? getString(R.string.status_add)
                        : getString(R.string.status_remove)
        );

        requestBarcodeInputFocus();
    }

    private void showSettingsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        EditText etUrl = dialogView.findViewById(R.id.etBackendUrl);
        String current = prefs.getString(KEY_BASE_URL, "");
        etUrl.setText(current);
        etUrl.setHint("http://192.168.1.x:8080");

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_title)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String url = etUrl.getText().toString().trim();
                    prefs.edit().putString(KEY_BASE_URL, url).apply();
                    ApiClient.reset();
                    viewModel.setBaseUrl(url);
                    requestBarcodeInputFocus();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> requestBarcodeInputFocus())
                .show();
    }
}
