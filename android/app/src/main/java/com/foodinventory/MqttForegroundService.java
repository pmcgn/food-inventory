package com.foodinventory;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

/**
 * Minimal foreground service whose only job is to keep the process alive
 * after lockNow() turns the screen off.
 *
 * Without a foreground service, Android's background execution limits (API 26+)
 * suspend the app's threads shortly after the activity goes to background,
 * which drops the MQTT TCP connection even when wake locks are held.
 *
 * The service does not own the MQTT connection — MqttManager remains the
 * singleton that manages it. The service just prevents the process from being
 * restricted.
 */
public class MqttForegroundService extends Service {

    static final String ACTION_STOP = "com.foodinventory.MQTT_STOP";
    private static final String CHANNEL_ID = "mqtt_fg";
    private static final int NOTIF_ID = 1;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIF_ID, buildNotification());
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "MQTT", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("MQTT active")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }
}
