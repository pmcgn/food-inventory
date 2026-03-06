package com.foodinventory;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Minimal Device Admin receiver.
 * The only policy requested is force-lock, which lets the app call
 * DevicePolicyManager.lockNow() to turn the screen off immediately.
 */
public class FoodInventoryAdminReceiver extends DeviceAdminReceiver {
    @Override public void onEnabled(Context context, Intent intent) {}
    @Override public void onDisabled(Context context, Intent intent) {}
}
