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

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.junsun.ambientlight.views.CockpitView;

public class MainActivity extends AppCompatActivity {

    private AmbientConfig config;
    private TuyaApiClient tuyaClient;

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
        super.onCreate(savedInstanceState);
        hideSystemUI();
        setContentView(R.layout.activity_main);

        config = new AmbientConfig(this);
        tuyaClient = new TuyaApiClient(config);

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

        tvHotspotBadge.setOnClickListener(v -> HotspotManager.openHotspotSettings(MainActivity.this));
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
        String mappedKey = config.getKeyMapping(actionKey, defaultTuyaKey);
        tuyaClient.sendRemoteKey(mappedKey, new TuyaApiClient.ApiCallback() {
            @Override
            public void onSuccess(String result) {
                // Esecuzione silenziosa e fluida in stile OEM, nessun popup invasivo
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MainActivity.this, "Errore Tuya: " + message, Toast.LENGTH_LONG).show();
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

        if (tvHotspotBadge != null) {
            if (isHotspotOn) {
                int clients = HotspotManager.getConnectedClientsCount();
                if (clients > 0) {
                    tvHotspotBadge.setText("📡 HOTSPOT ATTIVO (" + clients + " DISP.)");
                } else {
                    tvHotspotBadge.setText("📡 HOTSPOT ATTIVO");
                }
                tvHotspotBadge.setTextColor(Color.parseColor("#00E5FF"));
                if (layoutHotspotAlert != null) layoutHotspotAlert.setVisibility(View.GONE);
            } else {
                tvHotspotBadge.setText("⚠️ HOTSPOT SPENTO");
                tvHotspotBadge.setTextColor(Color.parseColor("#FF5252"));
                if (layoutHotspotAlert != null) layoutHotspotAlert.setVisibility(View.VISIBLE);
            }
        }

        if (tvStatusBadge != null) {
            if (config.isDemoMode()) {
                tvStatusBadge.setText("● DEMO");
                tvStatusBadge.setTextColor(Color.parseColor("#FFAB00"));
            } else if (!isInternetOn) {
                tvStatusBadge.setText("● OFFLINE");
                tvStatusBadge.setTextColor(Color.parseColor("#FF5252"));
            } else {
                tvStatusBadge.setText("● TUYA ONLINE");
                tvStatusBadge.setTextColor(Color.parseColor("#00E676"));
            }
        }
    }
}
