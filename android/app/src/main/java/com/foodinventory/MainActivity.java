package com.foodinventory;

import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.foodinventory.databinding.ActivityMainBinding;
import com.foodinventory.model.InventoryEntry;

public class MainActivity extends AppCompatActivity implements MqttManager.ScreenCommandListener {

    private static final String PREFS_NAME        = "foodinventory_prefs";
    private static final String KEY_BASE_URL       = "base_url";
    private static final String KEY_MQTT_ENABLED   = "mqtt_enabled";
    private static final String KEY_MQTT_HOST      = "mqtt_host";
    private static final String KEY_MQTT_PORT      = "mqtt_port";
    private static final String KEY_MQTT_TLS       = "mqtt_tls";
    private static final String KEY_MQTT_SKIP_CERT = "mqtt_skip_cert";
    private static final String KEY_MQTT_USERNAME  = "mqtt_username";
    private static final String KEY_MQTT_PASSWORD  = "mqtt_password";
    private static final String KEY_MQTT_PREFIX    = "mqtt_topic_prefix";

    private static final long ADD_TIMEOUT_MS = 5 * 60 * 1000L;

    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    private SharedPreferences prefs;
    private CountDownTimer addModeTimer;
    private final Handler cardHideHandler = new Handler(Looper.getMainLooper());
    private final Runnable cardHideRunnable = () -> binding.cardProduct.setVisibility(View.GONE);

    private PowerManager.WakeLock screenWakeLock;
    private DevicePolicyManager dpm;
    private ComponentName adminComponent;

    // Launcher for the system "Activate device admin" screen
    private final ActivityResultLauncher<Intent> adminRequestLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                // No-op: lockNow() will simply be unavailable if the user declined.
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Allow this activity to show on top of the keyguard and to turn the
        // screen on when it becomes visible (needed for MQTT screen-on command).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            //noinspection deprecation
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, FoodInventoryAdminReceiver.class);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        // FULL_WAKE_LOCK + ACQUIRE_CAUSES_WAKEUP powers the display on immediately.
        // SCREEN_BRIGHT_WAKE_LOCK is deprecated and ignored on modern Android.
        //noinspection deprecation
        screenWakeLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK
                        | PowerManager.ACQUIRE_CAUSES_WAKEUP
                        | PowerManager.ON_AFTER_RELEASE,
                "FoodInventory:ScreenWake");

        String savedUrl = prefs.getString(KEY_BASE_URL, "");
        viewModel.setBaseUrl(savedUrl);

        if (savedUrl.isEmpty()) {
            showSettingsDialog();
        }

        setupModeButtons();
        setupHidInput();
        observeViewModel();
        startMqtt();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (addModeTimer != null) addModeTimer.cancel();
        cardHideHandler.removeCallbacks(cardHideRunnable);
        MqttManager.getInstance().stop();
        stopMqttService();
    }

    // ── MqttManager.ScreenCommandListener ────────────────────────────────────

    @Override
    public void onScreenOn() {
        // Acquire the wake lock HERE, on the MQTT callback thread, before posting
        // to the UI thread. This closes the race window where the partial wake lock
        // was already released but the screen wake lock hasn't been acquired yet,
        // which could let the CPU sleep before the UI runnable executes.
        if (!screenWakeLock.isHeld()) {
            screenWakeLock.acquire(30_000); // 30 s safety cap; onResume releases it
        }

        runOnUiThread(() -> {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            // Dismiss the keyguard that lockNow() installed so the app is visible.
            // requestDismissKeyguard() succeeds when no PIN/pattern/password is set,
            // which is the common setup for a dedicated device.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
                if (km != null) km.requestDismissKeyguard(this, null);
            }

            // Bring the activity to front. setShowWhenLocked(true) (set in onCreate)
            // lets it display over the keyguard; setTurnScreenOn(true) lets the
            // window itself power the display on when it becomes visible.
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-assert keep-screen-on (may have been cleared by a screen-off command).
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // The activity is now visible — release the timed screen wake lock and let
        // FLAG_KEEP_SCREEN_ON take responsibility for keeping the display on.
        if (screenWakeLock != null && screenWakeLock.isHeld()) {
            screenWakeLock.release();
        }
    }

    @Override
    public void onScreenOff() {
        runOnUiThread(() -> {
            // Remove the keep-on flag first so the screen is free to turn off.
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            // Lock immediately if device admin is active; otherwise the screen
            // turns off after the normal OS timeout.
            if (dpm.isAdminActive(adminComponent)) {
                dpm.lockNow();
            } else {
                Toast.makeText(MainActivity.this,
                        "Screen-off: grant Device Administrator permission to lock immediately",
                        Toast.LENGTH_LONG).show();
                requestDeviceAdmin();
            }
            // MqttManager holds a partial CPU wake lock + WiFi lock so the app
            // remains reachable to receive the screen-on command.
        });
    }

    // ── Focus ─────────────────────────────────────────────────────────────────

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) requestBarcodeInputFocus();
    }

    // ── Mode buttons ──────────────────────────────────────────────────────────

    private void setupModeButtons() {
        selectMode(MainViewModel.Mode.REMOVE);
        binding.btnAdd.setOnClickListener(v -> selectMode(MainViewModel.Mode.ADD));
        binding.btnRemove.setOnClickListener(v -> selectMode(MainViewModel.Mode.REMOVE));
        binding.btnSettings.setOnClickListener(v -> showSettingsDialog());
    }

    private void selectMode(MainViewModel.Mode mode) {
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
            binding.btnAdd.setText(R.string.btn_add);
            binding.timerProgress.setVisibility(View.GONE);
            binding.tvStatus.setText(R.string.status_remove);
        }
        cardHideHandler.removeCallbacks(cardHideRunnable);
        binding.cardProduct.setVisibility(View.GONE);
    }

    private void startAddModeTimer() {
        binding.timerProgress.setMax(300);
        binding.timerProgress.setProgress(300);
        binding.timerProgress.setVisibility(View.VISIBLE);
        addModeTimer = new CountDownTimer(ADD_TIMEOUT_MS, 1000) {
            @Override public void onTick(long ms) {
                int s = (int) (ms / 1000);
                binding.btnAdd.setText(String.format("ADD  %d:%02d", s / 60, s % 60));
                binding.timerProgress.setProgress(s);
            }
            @Override public void onFinish() { selectMode(MainViewModel.Mode.REMOVE); }
        }.start();
    }

    // ── Barcode HID input ─────────────────────────────────────────────────────

    private void setupHidInput() {
        EditText et = binding.etBarcodeInput;
        et.setShowSoftInputOnFocus(false);
        et.setOnEditorActionListener((v, actionId, event) -> {
            boolean isDone  = actionId == EditorInfo.IME_ACTION_DONE;
            boolean isEnter = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (isDone || isEnter) { submitBarcode(et); return true; }
            return false;
        });
        et.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                submitBarcode(et);
                return true;
            }
            return false;
        });
    }

    private void submitBarcode(EditText et) {
        String ean = et.getText().toString().trim();
        et.setText("");
        if (!ean.isEmpty()) viewModel.scan(ean);
    }

    private void requestBarcodeInputFocus() {
        binding.etBarcodeInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(binding.etBarcodeInput.getWindowToken(), 0);
    }

    // ── ViewModel observers ───────────────────────────────────────────────────

    private void observeViewModel() {
        viewModel.getLoading().observe(this, loading ->
                binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE));

        viewModel.getLastEntry().observe(this, entry -> {
            showProduct(entry);
            String ean = entry.product != null ? entry.product.ean : null;
            if (ean != null) {
                if (viewModel.getMode() == MainViewModel.Mode.ADD) {
                    MqttManager.getInstance().publishAdd(ean);
                } else {
                    MqttManager.getInstance().publishRemove(ean);
                }
            }
        });

        viewModel.getEntryDeleted().observe(this, ean -> {
            binding.cardProduct.setVisibility(View.GONE);
            binding.tvStatus.setText(getString(R.string.msg_removed, ean));
            MqttManager.getInstance().publishRemove(ean);
            requestBarcodeInputFocus();
        });

        viewModel.getErrorMessage().observe(this, message -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            binding.tvStatus.setText(message);
            requestBarcodeInputFocus();
        });
    }

    // ── Product card ──────────────────────────────────────────────────────────

    private void showProduct(InventoryEntry entry) {
        binding.cardProduct.setVisibility(View.VISIBLE);
        cardHideHandler.removeCallbacks(cardHideRunnable);
        cardHideHandler.postDelayed(cardHideRunnable, 5_000);

        String name = entry.product != null ? entry.product.name : getString(R.string.unknown_product);
        binding.tvProductName.setText(name);

        String category = "";
        if (entry.product != null && entry.product.category != null) {
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

        binding.tvStatus.setText(viewModel.getMode() == MainViewModel.Mode.ADD
                ? getString(R.string.status_add) : getString(R.string.status_remove));

        requestBarcodeInputFocus();
    }

    // ── Settings dialog ───────────────────────────────────────────────────────

    private void showSettingsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_settings, null);

        // Backend
        EditText etUrl = dialogView.findViewById(R.id.etBackendUrl);
        etUrl.setText(prefs.getString(KEY_BASE_URL, ""));

        // MQTT
        SwitchCompat swEnabled  = dialogView.findViewById(R.id.swMqttEnabled);
        View layoutFields       = dialogView.findViewById(R.id.layoutMqttFields);
        SwitchCompat swTls      = dialogView.findViewById(R.id.swMqttTls);
        View layoutSkipCert     = dialogView.findViewById(R.id.layoutSkipCert);
        SwitchCompat swSkipCert = dialogView.findViewById(R.id.swMqttSkipCert);
        EditText etHost         = dialogView.findViewById(R.id.etMqttHost);
        EditText etPort         = dialogView.findViewById(R.id.etMqttPort);
        EditText etUsername     = dialogView.findViewById(R.id.etMqttUsername);
        EditText etPassword     = dialogView.findViewById(R.id.etMqttPassword);
        EditText etPrefix       = dialogView.findViewById(R.id.etMqttTopicPrefix);

        // Populate from prefs
        boolean mqttEnabled = prefs.getBoolean(KEY_MQTT_ENABLED, false);
        swEnabled.setChecked(mqttEnabled);
        layoutFields.setVisibility(mqttEnabled ? View.VISIBLE : View.GONE);

        etHost.setText(prefs.getString(KEY_MQTT_HOST, ""));
        int port = prefs.getInt(KEY_MQTT_PORT, 0);
        etPort.setText(port > 0 ? String.valueOf(port) : "");
        boolean tls = prefs.getBoolean(KEY_MQTT_TLS, false);
        swTls.setChecked(tls);
        layoutSkipCert.setVisibility(tls ? View.VISIBLE : View.GONE);
        swSkipCert.setChecked(prefs.getBoolean(KEY_MQTT_SKIP_CERT, false));
        etUsername.setText(prefs.getString(KEY_MQTT_USERNAME, ""));
        etPassword.setText(prefs.getString(KEY_MQTT_PASSWORD, ""));
        etPrefix.setText(prefs.getString(KEY_MQTT_PREFIX, "foodinventory/"));

        swEnabled.setOnCheckedChangeListener((btn, checked) ->
                layoutFields.setVisibility(checked ? View.VISIBLE : View.GONE));

        swTls.setOnCheckedChangeListener((btn, checked) -> {
            layoutSkipCert.setVisibility(checked ? View.VISIBLE : View.GONE);
            etPort.setHint(checked ? "8883" : "1883");
        });
        etPort.setHint(tls ? "8883" : "1883");

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_title)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String url = etUrl.getText().toString().trim();
                    prefs.edit().putString(KEY_BASE_URL, url).apply();
                    ApiClient.reset();
                    viewModel.setBaseUrl(url);

                    String portText = etPort.getText().toString().trim();
                    int portVal = portText.isEmpty() ? 0 : Integer.parseInt(portText);
                    prefs.edit()
                            .putBoolean(KEY_MQTT_ENABLED,   swEnabled.isChecked())
                            .putString (KEY_MQTT_HOST,      etHost.getText().toString().trim())
                            .putInt    (KEY_MQTT_PORT,      portVal)
                            .putBoolean(KEY_MQTT_TLS,       swTls.isChecked())
                            .putBoolean(KEY_MQTT_SKIP_CERT, swSkipCert.isChecked())
                            .putString (KEY_MQTT_USERNAME,  etUsername.getText().toString().trim())
                            .putString (KEY_MQTT_PASSWORD,  etPassword.getText().toString())
                            .putString (KEY_MQTT_PREFIX,    etPrefix.getText().toString().trim())
                            .apply();

                    MqttManager.getInstance().stop();
                    startMqtt();

                    // If MQTT is enabled and device admin hasn't been granted yet,
                    // prompt now so that screen-off commands can lock immediately.
                    if (swEnabled.isChecked() && !dpm.isAdminActive(adminComponent)) {
                        requestDeviceAdmin();
                    }

                    requestBarcodeInputFocus();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> requestBarcodeInputFocus())
                .show();
    }

    // ── Device admin ──────────────────────────────────────────────────────────

    private void requestDeviceAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        getString(R.string.admin_explanation));
        adminRequestLauncher.launch(intent);
    }

    // ── MQTT lifecycle ────────────────────────────────────────────────────────

    private void startMqtt() {
        MqttManager.Settings s = new MqttManager.Settings();
        s.enabled            = prefs.getBoolean(KEY_MQTT_ENABLED,   false);
        s.host               = prefs.getString (KEY_MQTT_HOST,      "");
        s.port               = prefs.getInt    (KEY_MQTT_PORT,      0);
        s.useTls             = prefs.getBoolean(KEY_MQTT_TLS,       false);
        s.skipCertValidation = prefs.getBoolean(KEY_MQTT_SKIP_CERT, false);
        s.username           = prefs.getString (KEY_MQTT_USERNAME,  "");
        s.password           = prefs.getString (KEY_MQTT_PASSWORD,  "");
        s.topicPrefix        = prefs.getString (KEY_MQTT_PREFIX,    "foodinventory/");
        MqttManager.getInstance().start(this, s, this);

        if (s.enabled) {
            // Foreground service keeps the process unrestricted after lockNow()
            // sends the activity to background, preventing Android from suspending
            // the MQTT network threads.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(new Intent(this, MqttForegroundService.class));
            } else {
                startService(new Intent(this, MqttForegroundService.class));
            }
        } else {
            stopMqttService();
        }

        // Ensure device admin is granted so lockNow() works for screen-off commands.
        if (s.enabled && !dpm.isAdminActive(adminComponent)) {
            requestDeviceAdmin();
        }
    }

    private void stopMqttService() {
        startService(new Intent(this, MqttForegroundService.class)
                .setAction(MqttForegroundService.ACTION_STOP));
    }
}
