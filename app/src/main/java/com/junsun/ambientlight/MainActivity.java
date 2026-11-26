package com.junsun.ambientlight;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.junsun.ambientlight.views.CockpitView;

public class MainActivity extends AppCompatActivity {

    private AmbientConfig config;
    private TuyaApiClient tuyaClient;
    private TuyaBleManager bleManager;

    private CockpitView cockpitView;
    private TextView tvStatusBadge, tvBrightnessValue, tvMusicSub;
    private TextView tvHotspotBadge;
    private View layoutHotspotAlert;
    private MaterialButton btnQuickEnableHotspot;
    private MaterialButton btnPower;
    private LinearLayout btnMusicSync;
    private SeekBar sbBrightness;

    private boolean isPowerOn = true;
    private boolean isMusicMode = false;
    private int currentColor;
    private int currentBrightness = 80;

    private String bleStatusString = null;

    private final Handler hotspotMonitorHandler = new Handler(Looper.getMainLooper());
    private final Runnable hotspotMonitorRunnable = new Runnable() {
        @Override
        public void run() {
            checkAndRefreshHotspotStatus();
            hotspotMonitorHandler.postDelayed(this, 4000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        bypassHiddenApiRestrictions();
        super.onCreate(savedInstanceState);
        hideSystemUI();
        setContentView(R.layout.activity_main);

        config = new AmbientConfig(this);
        tuyaClient = new TuyaApiClient(config);
        bleManager = new TuyaBleManager(this, config);
        bleManager.setStateListener(new TuyaBleManager.BleStateListener() {
            @Override
            public void onScanning() {
                runOnUiThread(() -> {
                    bleStatusString = "⚡ BLE: RICERCA S06...";
                    checkAndRefreshHotspotStatus();
                });
            }

            @Override
            public void onConnecting(String deviceName, String mac) {
                runOnUiThread(() -> {
                    bleStatusString = "⚡ BLE: CONNETTO " + (deviceName != null ? deviceName : mac) + "...";
                    checkAndRefreshHotspotStatus();
                });
            }

            @Override
            public void onHandshaking(String stepInfo) {
                runOnUiThread(() -> {
                    bleStatusString = "⚡ BLE: " + stepInfo;
                    checkAndRefreshHotspotStatus();
                });
            }

            @Override
            public void onAuthenticated(String deviceName, String mac) {
                runOnUiThread(() -> {
                    bleStatusString = "⚡ BLE PRONTO (" + (deviceName != null ? deviceName : mac) + ")";
                    Toast.makeText(MainActivity.this, "✅ Tuya S06 Autenticato al 100% (Offline)", Toast.LENGTH_SHORT).show();
                    checkAndRefreshHotspotStatus();
                });
            }

            @Override
            public void onHandshakeFailed(String reason) {
                runOnUiThread(() -> {
                    bleStatusString = "⚠️ " + reason;
                    Toast.makeText(MainActivity.this, "Handshake BLE non completato: tocca ● per dettagli", Toast.LENGTH_LONG).show();
                    checkAndRefreshHotspotStatus();
                });
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    bleStatusString = null;
                    checkAndRefreshHotspotStatus();
                });
            }
        });

        checkBluetoothPermissions();

        currentColor = config.getLastColor();
        isPowerOn = config.getLastPowerState();
        currentBrightness = config.getLastBrightness();
        isMusicMode = config.isLastMusicMode();

        initViews();
        setupListeners();
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        updateStatusBadge();

        if (bleManager != null && !bleManager.isConnected()) {
            bleManager.startAutoConnect();
        }

        // Tenta auto-attivazione se spento
        if (!HotspotManager.isHotspotActive(this)) {
            HotspotManager.tryEnableHotspot(this, null);
        }

        hotspotMonitorHandler.removeCallbacks(hotspotMonitorRunnable);
        hotspotMonitorHandler.post(hotspotMonitorRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        hotspotMonitorHandler.removeCallbacks(hotspotMonitorRunnable);
        if (bleManager != null) {
            bleManager.stopScanning();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bleManager != null) {
            bleManager.disconnect();
        }
    }

    private void bypassHiddenApiRestrictions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                java.lang.reflect.Method forName = Class.class.getDeclaredMethod("forName", String.class);
                java.lang.reflect.Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
                Class<?> vmRuntimeClass = (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
                java.lang.reflect.Method getRuntime = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", (Object[]) null);
                java.lang.reflect.Method setHiddenApiExemptions = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "setHiddenApiExemptions", new Class<?>[]{String[].class});
                Object vmRuntime = getRuntime.invoke(null);
                setHiddenApiExemptions.invoke(vmRuntime, new Object[]{new String[]{"L"}});
            } catch (Throwable ignored) {}
        }
    }

    private void checkBluetoothPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != android.content.pm.PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.BLUETOOTH_SCAN,
                        android.Manifest.permission.BLUETOOTH_CONNECT
                }, 101);
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                }, 101);
            }
        }
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
    }

    private void initViews() {
        cockpitView = findViewById(R.id.cockpitView);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvHotspotBadge = findViewById(R.id.tvHotspotBadge);
        layoutHotspotAlert = findViewById(R.id.layoutHotspotAlert);
        btnQuickEnableHotspot = findViewById(R.id.btnQuickEnableHotspot);
        tvBrightnessValue = findViewById(R.id.tvBrightnessValue);
        tvMusicSub = findViewById(R.id.tvMusicSub);
        btnPower = findViewById(R.id.btnPower);
        btnMusicSync = findViewById(R.id.btnMusicSync);
        sbBrightness = findViewById(R.id.sbBrightness);

        tvStatusBadge.setOnClickListener(v -> showBleDiagnosticsDialog());
        tvHotspotBadge.setOnClickListener(v -> HotspotManager.openHotspotSettings(MainActivity.this));
        tvHotspotBadge.setOnLongClickListener(v -> {
            if (bleManager != null) {
                bleManager.resetAndScan();
                android.widget.Toast.makeText(MainActivity.this, "Reset BLE: cache azzerata e nuova scansione...", android.widget.Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        if (btnQuickEnableHotspot != null) {
            btnQuickEnableHotspot.setOnClickListener(v -> HotspotManager.openHotspotSettings(MainActivity.this));
        }

        ImageButton btnSettings = findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));

        sbBrightness.setProgress(currentBrightness);
        cockpitView.setAmbientColor(currentColor);
        cockpitView.setPowerState(isPowerOn);
        cockpitView.setBrightness(currentBrightness);
        cockpitView.setMusicMode(isMusicMode);
    }

    private void setupListeners() {
        // Master Power
        btnPower.setOnClickListener(v -> {
            isPowerOn = !isPowerOn;
            config.setLastPowerState(isPowerOn);
            updatePowerButtonUI();
            cockpitView.setPowerState(isPowerOn);

            sendTuyaCommand(AmbientConfig.BTN_POWER, "power");
        });

        // ♫ Music Sync
        btnMusicSync.setOnClickListener(v -> {
            isMusicMode = !isMusicMode;
            config.setLastMusicMode(isMusicMode);
            cockpitView.setMusicMode(isMusicMode);
            updateMusicButtonUI();

            sendTuyaCommand(AmbientConfig.BTN_MUSIC, "music");
        });

        // Dynamic Modes (Prev / Next)
        findViewById(R.id.btnModePrev).setOnClickListener(v -> {
            isMusicMode = false;
            cockpitView.setMusicMode(false);
            updateMusicButtonUI();
            sendTuyaCommand(AmbientConfig.BTN_MODE_DOWN, "mode-");
        });

        findViewById(R.id.btnModeNext).setOnClickListener(v -> {
            isMusicMode = false;
            cockpitView.setMusicMode(false);
            updateMusicButtonUI();
            sendTuyaCommand(AmbientConfig.BTN_MODE_UP, "mode+");
        });

        // Speed (+ / -)
        findViewById(R.id.btnSpeedDown).setOnClickListener(v -> sendTuyaCommand(AmbientConfig.BTN_SPEED_DOWN, "speed-"));
        findViewById(R.id.btnSpeedUp).setOnClickListener(v -> sendTuyaCommand(AmbientConfig.BTN_SPEED_UP, "speed+"));

        // 9 Colori Statici del Telecomando DreamColor
        findViewById(R.id.btnColorR).setOnClickListener(v -> selectColorPreset(Color.parseColor("#FF1744"), AmbientConfig.BTN_COLOR_R, "red"));
        findViewById(R.id.btnColorG).setOnClickListener(v -> selectColorPreset(Color.parseColor("#00E676"), AmbientConfig.BTN_COLOR_G, "green"));
        findViewById(R.id.btnColorB).setOnClickListener(v -> selectColorPreset(Color.parseColor("#2979FF"), AmbientConfig.BTN_COLOR_B, "blue"));

        findViewById(R.id.btnColorOrange).setOnClickListener(v -> selectColorPreset(Color.parseColor("#FF6D00"), AmbientConfig.BTN_COLOR_ORANGE, "orange"));
        findViewById(R.id.btnColorMint).setOnClickListener(v -> selectColorPreset(Color.parseColor("#00E5A3"), AmbientConfig.BTN_COLOR_MINT, "mint"));
        findViewById(R.id.btnColorIndigo).setOnClickListener(v -> selectColorPreset(Color.parseColor("#3D5AFE"), AmbientConfig.BTN_COLOR_INDIGO, "indigo"));

        findViewById(R.id.btnColorYellow).setOnClickListener(v -> selectColorPreset(Color.parseColor("#FFEA00"), AmbientConfig.BTN_COLOR_YELLOW, "yellow"));
        findViewById(R.id.btnColorCyan).setOnClickListener(v -> selectColorPreset(Color.parseColor("#00E5FF"), AmbientConfig.BTN_COLOR_CYAN, "cyan"));
        findViewById(R.id.btnColorPurple).setOnClickListener(v -> selectColorPreset(Color.parseColor("#D500F9"), AmbientConfig.BTN_COLOR_PURPLE, "purple"));

        // Dimming (+ / -)
        findViewById(R.id.btnDimDown).setOnClickListener(v -> {
            int newProgress = Math.max(10, sbBrightness.getProgress() - 15);
            sbBrightness.setProgress(newProgress);
            sendTuyaCommand(AmbientConfig.BTN_BRIGHT_DOWN, "bright-");
        });

        findViewById(R.id.btnDimUp).setOnClickListener(v -> {
            int newProgress = Math.min(100, sbBrightness.getProgress() + 15);
            sbBrightness.setProgress(newProgress);
            sendTuyaCommand(AmbientConfig.BTN_BRIGHT_UP, "bright+");
        });

        // Seekbar Dimming
        sbBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentBrightness = Math.max(10, progress);
                tvBrightnessValue.setText(currentBrightness + "%");
                cockpitView.setBrightness(currentBrightness);
                config.setLastBrightness(currentBrightness);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // IC SET Controls
        findViewById(R.id.btnIcMinus).setOnClickListener(v -> sendTuyaCommand(AmbientConfig.BTN_IC_MINUS, "ic-"));
        findViewById(R.id.btnIcSet).setOnClickListener(v -> sendTuyaCommand(AmbientConfig.BTN_IC_SET, "ic_set"));
        findViewById(R.id.btnIcPlus).setOnClickListener(v -> sendTuyaCommand(AmbientConfig.BTN_IC_PLUS, "ic+"));
    }

    private void selectColorPreset(int color, String actionKey, String defaultTuyaKey) {
        if (!isPowerOn) {
            isPowerOn = true;
            config.setLastPowerState(true);
            updatePowerButtonUI();
            cockpitView.setPowerState(true);
        }

        isMusicMode = false;
        config.setLastMusicMode(false);
        updateMusicButtonUI();

        currentColor = color;
        config.setLastColor(color);
        cockpitView.setAmbientColor(color);

        sendTuyaCommand(actionKey, defaultTuyaKey);
    }

    private void sendTuyaCommand(String actionKey, String defaultTuyaKey) {
        final String mappedKey = config.getKeyMapping(actionKey, defaultTuyaKey);
        final String rawIrBase64 = AmbientConfig.getRawIrBase64(actionKey);

        // Se BLE è autenticato e pronto (con session_key verificata), invia via BLE offline con Fallback Duale
        if (bleManager != null && bleManager.isAuthenticated()) {
            bleManager.sendRemoteKeyDual(rawIrBase64, mappedKey, new TuyaBleManager.BleCallback() {
                @Override
                public void onSuccess(String message) {
                    Toast.makeText(MainActivity.this, "⚡ " + message, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(MainActivity.this, "⚠️ Invio BLE: " + error, Toast.LENGTH_SHORT).show();
                    sendViaCloud(mappedKey);
                }
            });
            return;
        }

        // Se BLE è connesso ma non ancora autenticato (handshake in corso)
        if (bleManager != null && bleManager.isConnected()) {
            Toast.makeText(MainActivity.this, "Handshake BLE in corso... Inoltro via Wi-Fi", Toast.LENGTH_SHORT).show();
            sendViaCloud(mappedKey);
            return;
        }

        // Se BLE non è connesso, avvia auto-connect in background e invia via Cloud
        if (bleManager != null && !bleManager.isConnected()) {
            bleManager.startAutoConnect();
        }
        sendViaCloud(mappedKey);
    }

    private void showBleDiagnosticsDialog() {
        if (bleManager == null) return;
        List<String> logs = bleManager.getDiagnosticLogs();
        int currentSendMode = config.getBleSendMode();
        String sendModeName = (currentSendMode == AmbientConfig.SEND_MODE_DUAL) ? "Fallback Duale (Base64 + Key ID)" :
                (currentSendMode == AmbientConfig.SEND_MODE_RAW_ONLY ? "Solo Base64 Raw" : "Solo Key ID Tuya");

        StringBuilder sb = new StringBuilder();
        sb.append("Stato: ").append(bleManager.isAuthenticated() ? "● AUTENTICATO (100% OFFLINE)" : (bleManager.isConnected() ? "● CONNESSO (Handshake in corso)" : "● DISCONNESSO")).append("\n");
        sb.append("MAC Target: ").append(config.getLastBleMac() != null ? config.getLastBleMac() : "50:8B:B9:50:F6:6D").append("\n");
        sb.append("Modalità Invio: ").append(sendModeName).append("\n");
        sb.append("Versione Protocollo: v").append(bleManager.getProtocolVersion()).append(" (0x").append(Integer.toHexString(bleManager.getProtocolVersion() << 4)).append(")\n");
        sb.append("Session Key: ").append(bleManager.hasSessionKey() ? "CALCOLATA (OK)" : "NON PRESENTE").append("\n\n");
        sb.append("--- REGISTRO EVENTI BLE ---\n");
        if (logs.isEmpty()) {
            sb.append("(Nessun evento registrato)\n");
        } else {
            for (String l : logs) {
                sb.append(l).append("\n");
            }
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Diagnostica Tuya S06 BLE")
                .setMessage(sb.toString())
                .setPositiveButton("Chiudi", null)
                .setNegativeButton("Cambia Modalità", (dialog, which) -> showSendModeSelectionDialog())
                .setNeutralButton("Reset & Re-scan", (dialog, which) -> {
                    bleManager.resetAndScan();
                    Toast.makeText(this, "Reset BLE: nuova scansione avviata...", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showSendModeSelectionDialog() {
        final String[] options = new String[]{
                "1. Fallback Duale: Base64 + Key ID (Raccomandato)",
                "2. Solo Base64 Raw (Forma d'onda impulsi)",
                "3. Solo Key ID (Telecomando Virtuale Tuya)"
        };
        int current = config.getBleSendMode();
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Seleziona Modalità di Invio BLE")
                .setSingleChoiceItems(options, current, (dialog, which) -> {
                    config.setBleSendMode(which);
                    String selected = (which == AmbientConfig.SEND_MODE_DUAL) ? "Fallback Duale" :
                            (which == AmbientConfig.SEND_MODE_RAW_ONLY ? "Solo Base64" : "Solo Key ID");
                    Toast.makeText(this, "Impostato: " + selected, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    showBleDiagnosticsDialog();
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void sendViaCloud(String mappedKey) {
        tuyaClient.sendRemoteKey(mappedKey, new TuyaApiClient.ApiCallback() {
            @Override
            public void onSuccess(String result) {
                // Esecuzione silenziosa e fluida in stile OEM
            }

            @Override
            public void onError(String message) {
                if (bleManager == null || !bleManager.isConnected()) {
                    Toast.makeText(MainActivity.this, "Tuya: " + message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void updateUI() {
        updatePowerButtonUI();
        updateMusicButtonUI();
        updateStatusBadge();
        tvBrightnessValue.setText(currentBrightness + "%");
    }

    private void updatePowerButtonUI() {
        if (isPowerOn) {
            btnPower.setText("⏻  SPEGNI AMBIENT LIGHT");
            btnPower.setBackgroundColor(Color.parseColor("#FF1744"));
        } else {
            btnPower.setText("⏻  ACCENDI AMBIENT LIGHT");
            btnPower.setBackgroundColor(Color.parseColor("#00E676"));
        }
    }

    private void updateMusicButtonUI() {
        if (isMusicMode) {
            btnMusicSync.setBackgroundResource(R.drawable.bg_music_active);
            tvMusicSub.setText("ATTIVO • Segue il ritmo della musica");
            tvMusicSub.setTextColor(Color.parseColor("#00E5FF"));
        } else {
            btnMusicSync.setBackgroundResource(R.drawable.bg_mood_cyber);
            tvMusicSub.setText("Sincronizza con i bassi");
            tvMusicSub.setTextColor(Color.parseColor("#85A2C4"));
        }
    }

    private void updateStatusBadge() {
        checkAndRefreshHotspotStatus();
    }

    private void checkAndRefreshHotspotStatus() {
        boolean isHotspotOn = HotspotManager.isHotspotActive(this);
        boolean isInternetOn = HotspotManager.isInternetConnected(this);
        boolean isBleAuth = bleManager != null && bleManager.isAuthenticated();
        boolean isBleConnected = bleManager != null && bleManager.isConnected();

        if (tvHotspotBadge != null) {
            if (isBleAuth) {
                tvHotspotBadge.setText(bleStatusString != null ? bleStatusString : "⚡ TUYA BLE PRONTO");
                tvHotspotBadge.setTextColor(Color.parseColor("#00E676"));
                if (layoutHotspotAlert != null) layoutHotspotAlert.setVisibility(View.GONE);
            } else if (isBleConnected) {
                tvHotspotBadge.setText(bleStatusString != null ? bleStatusString : "⚡ TUYA BLE IN CONNESSIONE...");
                tvHotspotBadge.setTextColor(Color.parseColor("#00E5FF"));
                if (layoutHotspotAlert != null) layoutHotspotAlert.setVisibility(View.GONE);
            } else if (isHotspotOn) {
                int clients = HotspotManager.getConnectedClientsCount();
                if (clients > 0) {
                    tvHotspotBadge.setText("📡 HOTSPOT ATTIVO (" + clients + " DISP.)");
                } else {
                    tvHotspotBadge.setText("📡 HOTSPOT ATTIVO");
                }
                tvHotspotBadge.setTextColor(Color.parseColor("#00E5FF"));
                if (layoutHotspotAlert != null) layoutHotspotAlert.setVisibility(View.GONE);
            } else {
                tvHotspotBadge.setText(bleStatusString != null ? bleStatusString : "⚠️ OFFLINE (ATTIVA HOTSPOT O BLE)");
                tvHotspotBadge.setTextColor(Color.parseColor("#FF5252"));
                if (layoutHotspotAlert != null) layoutHotspotAlert.setVisibility(View.VISIBLE);
            }
        }

        if (tvStatusBadge != null) {
            if (config.isDemoMode()) {
                tvStatusBadge.setText("● DEMO");
                tvStatusBadge.setTextColor(Color.parseColor("#FFAB00"));
            } else if (isBleAuth) {
                tvStatusBadge.setText("● BLE AUTENTICATO");
                tvStatusBadge.setTextColor(Color.parseColor("#00E676"));
            } else if (isBleConnected) {
                tvStatusBadge.setText("● BLE HANDSHAKE");
                tvStatusBadge.setTextColor(Color.parseColor("#00E5FF"));
            } else if (isInternetOn) {
                tvStatusBadge.setText("● TUYA ONLINE");
                tvStatusBadge.setTextColor(Color.parseColor("#00E676"));
            } else {
                tvStatusBadge.setText("● RICERCA BLE...");
                tvStatusBadge.setTextColor(Color.parseColor("#FFAB00"));
            }
        }
    }
}
