package com.junsun.ambientlight;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Log.d(TAG, "Ricevuto evento di sistema/risveglio: " + action);

        // Se l'autoradio si riavvia o si risveglia dallo Standby ACC
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)
                || Intent.ACTION_POWER_CONNECTED.equals(action)
                || Intent.ACTION_SCREEN_ON.equals(action)) {

            // Se l'hotspot è spento, tenta di riattivarlo subito
            if (!HotspotManager.isHotspotActive(context)) {
                Log.d(TAG, "Hotspot disattivo al risveglio, tento auto-attivazione...");
                HotspotManager.tryEnableHotspot(context, (isEnabled, message) -> {
                    Log.d(TAG, "Esito riattivazione hotspot al boot: " + isEnabled + " (" + message + ")");
                });
            }
        }
    }
}
