package com.junsun.ambientlight;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HotspotManager {

    private static final String TAG = "HotspotManager";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface HotspotCallback {
        void onResult(boolean isEnabled, String message);
    }

    /**
     * Verifica se l'Hotspot Wi-Fi (Tethering) dello stereo è attualmente attivo.
     */
    public static boolean isHotspotActive(Context context) {
        if (context == null) return false;
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                Method method = wifiManager.getClass().getDeclaredMethod("getWifiApState");
                method.setAccessible(true);
                int state = (Integer) method.invoke(wifiManager);
                // 12 = WIFI_AP_STATE_ENABLING, 13 = WIFI_AP_STATE_ENABLED
                return state == 12 || state == 13;
            }
        } catch (Exception e) {
            try {
                WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null) {
                    Method method = wifiManager.getClass().getDeclaredMethod("isWifiApEnabled");
                    method.setAccessible(true);
                    return (Boolean) method.invoke(wifiManager);
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    /**
     * Verifica se lo stereo ha una connessione Internet attiva (4G SIM o Wi-Fi).
     */
    public static boolean isInternetConnected(Context context) {
        if (context == null) return false;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                return activeNetwork != null && activeNetwork.isConnected();
            }
        } catch (Exception e) {
            Log.e(TAG, "Errore verifica connessione internet", e);
        }
        return false;
    }

    /**
     * Conta quanti dispositivi (es. il blaster Tuya) sono attualmente collegati all'Hotspot leggendo la tabella ARP.
     */
    public static int getConnectedClientsCount() {
        int count = 0;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader("/proc/net/arp"));
            String line;
            while ((line = br.readLine()) != null) {
                String[] splitted = line.split(" +");
                if (splitted.length >= 4) {
                    String ip = splitted[0];
                    String flags = splitted[2];
                    String mac = splitted[3];
                    if (flags.matches("0x[26]") && !mac.equals("00:00:00:00:00:00") && !ip.equalsIgnoreCase("IP")) {
                        count++;
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if (br != null) br.close();
            } catch (Exception ignored) {}
        }
        return count;
    }

    /**
     * Tenta di forzare l'attivazione dell'Hotspot via shell di sistema (su / cmd) o reflection.
     */
    public static void tryEnableHotspot(final Context context, final HotspotCallback callback) {
        executor.execute(() -> {
            boolean success = false;
            String message = "";

            // Se è già attivo, non serve forzare
            if (isHotspotActive(context)) {
                postCallback(callback, true, "Hotspot già attivo");
                return;
            }

            // Metodo 1: Shell command su Android con 'cmd connectivity start-tethering' o 'su'
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "cmd connectivity start-tethering wifi"});
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    Thread.sleep(1500);
                    if (isHotspotActive(context)) {
                        postCallback(callback, true, "Hotspot attivato via root/su");
                        return;
                    }
                }
            } catch (Exception ignored) {}

            try {
                Process process = Runtime.getRuntime().exec("cmd connectivity start-tethering wifi");
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    Thread.sleep(1500);
                    if (isHotspotActive(context)) {
                        postCallback(callback, true, "Hotspot attivato via shell");
                        return;
                    }
                }
            } catch (Exception ignored) {}

            // Metodo 2: Reflection su WifiManager.setWifiApEnabled(null, true)
            try {
                WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null) {
                    Method method = wifiManager.getClass().getMethod("setWifiApEnabled", null, boolean.class);
                    method.invoke(wifiManager, null, true);
                    Thread.sleep(1500);
                    if (isHotspotActive(context)) {
                        postCallback(callback, true, "Hotspot attivato con successo");
                        return;
                    }
                }
            } catch (Exception ignored) {}

            // Se i metodi programmatici falliscono per restrizioni di sicurezza Android,
            // restituisce esito negativo in modo che l'app possa aprire le impostazioni Hotspot al tocco
            postCallback(callback, isHotspotActive(context), isHotspotActive(context) ? "Attivo" : "Richiede attivazione manuale nelle impostazioni");
        });
    }

    private static void postCallback(final HotspotCallback callback, final boolean success, final String msg) {
        if (callback != null) {
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(success, msg));
        }
    }

    /**
     * Apre direttamente la schermata di sistema 'Hotspot e Tethering' dello stereo Junsun.
     */
    public static void openHotspotSettings(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent();
            intent.setAction("android.settings.TETHER_SETTINGS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            try {
                Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception ex) {
                try {
                    Intent intent = new Intent(Settings.ACTION_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception ignored) {}
            }
        }
    }
}
