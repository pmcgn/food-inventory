package com.foodinventory;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Manages the MQTT connection for smarthome integration.
 *
 * Topics (relative to configured prefix, default "foodinventory/"):
 *   {prefix}scan/add    - published (EAN payload) when a product is added
 *   {prefix}scan/remove - published (EAN payload) when a product is removed
 *   {prefix}status      - retained "online"/"offline" (LWT = "offline")
 *   {prefix}screen      - subscribed; payload "on"/"off" controls screen
 */
public class MqttManager {

    private static final String TAG = "MqttManager";

    public interface ScreenCommandListener {
        void onScreenOn();
        void onScreenOff();
    }

    public static class Settings {
        public boolean enabled;
        public String host = "";
        public int port;           // 0 = use protocol default (1883 / 8883)
        public boolean useTls;
        public boolean skipCertValidation;
        public String username = "";
        public String password = "";
        public String topicPrefix = "foodinventory/";
    }

    private static MqttManager instance;

    private MqttAsyncClient client;
    private String prefix;          // normalised (always ends with /)
    private ScreenCommandListener screenListener;
    private WifiManager.WifiLock wifiLock;
    private PowerManager.WakeLock partialWakeLock;

    private MqttManager() {}

    public static MqttManager getInstance() {
        if (instance == null) instance = new MqttManager();
        return instance;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void start(Context ctx, Settings s, ScreenCommandListener listener) {
        if (!s.enabled) return;
        this.screenListener = listener;
        this.prefix = s.topicPrefix.endsWith("/") ? s.topicPrefix : s.topicPrefix + "/";

        // WiFi lock keeps the radio associated while the screen is off
        WifiManager wm = (WifiManager) ctx.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "FoodInventory:MqttWifi");
        wifiLock.acquire();

        // Partial wake lock acquired only when screen is turned off via MQTT
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        partialWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "FoodInventory:MqttCpu");

        String scheme = s.useTls ? "ssl" : "tcp";
        int port = s.port > 0 ? s.port : (s.useTls ? 8883 : 1883);
        String brokerUrl = scheme + "://" + s.host + ":" + port;

        try {
            client = new MqttAsyncClient(brokerUrl,
                    MqttAsyncClient.generateClientId(),
                    new MemoryPersistence());

            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setCleanSession(true);
            opts.setAutomaticReconnect(true);
            opts.setConnectionTimeout(30);
            opts.setKeepAliveInterval(60);

            // Last Will: mark phone as offline if connection drops unexpectedly
            opts.setWill(prefix + "status", "offline".getBytes(), 1, true);

            if (!s.username.isEmpty()) {
                opts.setUserName(s.username);
                if (!s.password.isEmpty()) {
                    opts.setPassword(s.password.toCharArray());
                }
            }

            if (s.useTls && s.skipCertValidation) {
                opts.setSocketFactory(buildTrustAllSocketFactory());
            }

            client.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    Log.i(TAG, "Connected to " + serverURI + " (reconnect=" + reconnect + ")");
                    publishRetained(prefix + "status", "online");
                    subscribeScreen();
                }

                @Override
                public void connectionLost(Throwable cause) {
                    Log.w(TAG, "Connection lost: " + (cause != null ? cause.getMessage() : "?"));
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    handleIncoming(topic, new String(message.getPayload()).trim());
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });

            client.connect(opts, null, new IMqttActionListener() {
                @Override public void onSuccess(IMqttToken t) {
                    Log.i(TAG, "connect() succeeded");
                }
                @Override public void onFailure(IMqttToken t, Throwable e) {
                    Log.e(TAG, "connect() failed: " + (e != null ? e.getMessage() : "?"));
                }
            });

        } catch (MqttException e) {
            Log.e(TAG, "MQTT setup error", e);
        }
    }

    public void stop() {
        if (client != null) {
            try {
                if (client.isConnected()) {
                    // Disable auto-reconnect so the disconnect below is final.
                    client.setCallback(null);

                    // Publish "offline" synchronously (wait up to 3 s) so the broker
                    // receives it before the TCP connection is torn down.
                    MqttMessage msg = new MqttMessage("offline".getBytes());
                    msg.setQos(1);
                    msg.setRetained(true);
                    IMqttDeliveryToken token = client.publish(prefix + "status", msg);
                    token.waitForCompletion(3_000);

                    client.disconnect();
                }
            } catch (MqttException e) {
                Log.e(TAG, "Graceful shutdown error", e);
            }
        }
        releaseWakeLocks();
        instance = null;
    }

    public boolean isRunning() {
        return client != null && client.isConnected();
    }

    // -------------------------------------------------------------------------
    // Publishing
    // -------------------------------------------------------------------------

    public void publishAdd(String ean) {
        publish(prefix + "scan/add", ean, false);
    }

    public void publishRemove(String ean) {
        publish(prefix + "scan/remove", ean, false);
    }

    private void publishRetained(String topic, String payload) {
        publish(topic, payload, true);
    }

    private void publish(String topic, String payload, boolean retain) {
        if (client == null || !client.isConnected()) return;
        try {
            MqttMessage msg = new MqttMessage(payload.getBytes());
            msg.setQos(1);
            msg.setRetained(retain);
            client.publish(topic, msg);
        } catch (MqttException e) {
            Log.e(TAG, "Publish failed [" + topic + "]: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Subscription & screen control
    // -------------------------------------------------------------------------

    private void subscribeScreen() {
        if (client == null || !client.isConnected()) return;
        try {
            client.subscribe(prefix + "screen", 1,
                    (topic, msg) -> handleIncoming(topic, new String(msg.getPayload()).trim()));
        } catch (MqttException e) {
            Log.e(TAG, "Subscribe error", e);
        }
    }

    private void handleIncoming(String topic, String payload) {
        if (!topic.equals(prefix + "screen") || screenListener == null) return;
        if ("on".equalsIgnoreCase(payload)) {
            // Release the CPU wake lock; screen FLAG_KEEP_SCREEN_ON will take over
            if (partialWakeLock != null && partialWakeLock.isHeld()) {
                partialWakeLock.release();
            }
            screenListener.onScreenOn();
        } else if ("off".equalsIgnoreCase(payload)) {
            // Acquire partial wake lock + WiFi lock to stay reachable with screen off
            if (partialWakeLock != null && !partialWakeLock.isHeld()) {
                partialWakeLock.acquire();
            }
            screenListener.onScreenOff();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void releaseWakeLocks() {
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        if (partialWakeLock != null && partialWakeLock.isHeld()) partialWakeLock.release();
    }

    @SuppressWarnings("TrustAllX509TrustManager")
    private javax.net.ssl.SSLSocketFactory buildTrustAllSocketFactory() {
        try {
            TrustManager[] trustAll = {new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            }};
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new SecureRandom());
            return sc.getSocketFactory();
        } catch (Exception e) {
            Log.e(TAG, "TrustAll SSL setup failed", e);
            return null;
        }
    }
}
