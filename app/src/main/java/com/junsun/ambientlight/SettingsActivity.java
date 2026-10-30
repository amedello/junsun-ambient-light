package com.junsun.ambientlight;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class SettingsActivity extends AppCompatActivity {

    private AmbientConfig config;
    private TuyaApiClient tuyaClient;

    private EditText etClientId, etSecret, etDeviceId, etRemoteId;
    private Spinner spRegion;
    private CheckBox cbDemoMode;

    private final String[] regions = {"Central Europe (eu)", "Western Europe (we)", "America (us)", "China (cn)", "India (in)"};
    private final String[] regionCodes = {"eu", "we", "us", "cn", "in"};

    // I 20 Tasti esatti del telecomando DreamColor
    private final String[] learnableKeys = {
            "Power ON/OFF [⏻]",
            "Luminosità + [☼+]",
            "Luminosità - [☼-]",
            "♫ Ritmo Musica",
            "Modalità Successiva [MODE +]",
            "Modalità Precedente [MODE -]",
            "Velocità + [SPEED +]",
            "Velocità - [SPEED -]",
            "Rosso [R]",
            "Verde [G]",
            "Blu [B]",
            "Arancione",
            "Smeraldo (Verde Chiaro)",
            "Blu Indaco (Notte)",
            "Giallo",
            "Ciano (Azzurro)",
            "Magenta (Viola)",
            "IC SET",
            "Pixel +",
            "Pixel -"
    };

    private final String[] configActionKeys = {
            AmbientConfig.BTN_POWER,
            AmbientConfig.BTN_BRIGHT_UP,
            AmbientConfig.BTN_BRIGHT_DOWN,
            AmbientConfig.BTN_MUSIC,
            AmbientConfig.BTN_MODE_UP,
            AmbientConfig.BTN_MODE_DOWN,
            AmbientConfig.BTN_SPEED_UP,
            AmbientConfig.BTN_SPEED_DOWN,
            AmbientConfig.BTN_COLOR_R,
            AmbientConfig.BTN_COLOR_G,
            AmbientConfig.BTN_COLOR_B,
            AmbientConfig.BTN_COLOR_ORANGE,
            AmbientConfig.BTN_COLOR_MINT,
            AmbientConfig.BTN_COLOR_INDIGO,
            AmbientConfig.BTN_COLOR_YELLOW,
            AmbientConfig.BTN_COLOR_CYAN,
            AmbientConfig.BTN_COLOR_PURPLE,
            AmbientConfig.BTN_IC_SET,
            AmbientConfig.BTN_IC_PLUS,
            AmbientConfig.BTN_IC_MINUS
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        config = new AmbientConfig(this);
        tuyaClient = new TuyaApiClient(config);

        initViews();
        loadSavedConfig();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
    }

    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btnBack);
        MaterialButton btnSave = findViewById(R.id.btnSave);
        MaterialButton btnTestApi = findViewById(R.id.btnTestApi);
        MaterialButton btnLearnIrDialog = findViewById(R.id.btnLearnIrDialog);

        etClientId = findViewById(R.id.etClientId);
        etSecret = findViewById(R.id.etSecret);
        etDeviceId = findViewById(R.id.etDeviceId);
        etRemoteId = findViewById(R.id.etRemoteId);
        spRegion = findViewById(R.id.spRegion);
        cbDemoMode = findViewById(R.id.cbDemoMode);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, regions);
        spRegion.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveConfig());
        btnTestApi.setOnClickListener(v -> testConnection());
        btnLearnIrDialog.setOnClickListener(v -> showLearnDialog());
    }

    private void loadSavedConfig() {
        etClientId.setText(config.getClientId());
        etSecret.setText(config.getSecret());
        etDeviceId.setText(config.getDeviceId());
        etRemoteId.setText(config.getRemoteId());

        String savedRegion = config.getRegion();
        for (int i = 0; i < regionCodes.length; i++) {
            if (regionCodes[i].equalsIgnoreCase(savedRegion)) {
                spRegion.setSelection(i);
                break;
            }
        }

        cbDemoMode.setChecked(config.isDemoMode());
    }

    private void saveConfig() {
        config.setClientId(etClientId.getText().toString().trim());
        config.setSecret(etSecret.getText().toString().trim());
        config.setDeviceId(etDeviceId.getText().toString().trim());
        config.setRemoteId(etRemoteId.getText().toString().trim());

        int selectedRegionIdx = spRegion.getSelectedItemPosition();
        if (selectedRegionIdx >= 0 && selectedRegionIdx < regionCodes.length) {
            config.setRegion(regionCodes[selectedRegionIdx]);
        }

        config.setDemoMode(cbDemoMode.isChecked());

        Toast.makeText(this, "Configurazione salvata con successo!", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void testConnection() {
        saveDraftConfig();
        Toast.makeText(this, "Verifica connessione Tuya in corso...", Toast.LENGTH_SHORT).show();

        tuyaClient.sendRemoteKey(config.getKeyMapping(AmbientConfig.BTN_POWER, "power"), new TuyaApiClient.ApiCallback() {
            @Override
            public void onSuccess(String result) {
                Toast.makeText(SettingsActivity.this, "Connessione Riuscita! " + result, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(SettingsActivity.this, "Errore Connessione: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showLearnDialog() {
        saveDraftConfig();

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_learn_ir);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        Spinner spKeyToLearn = dialog.findViewById(R.id.spKeyToLearn);
        TextView tvLearnStatus = dialog.findViewById(R.id.tvLearnStatus);
        ProgressBar pbLearning = dialog.findViewById(R.id.pbLearning);
        MaterialButton btnStartLearn = dialog.findViewById(R.id.btnStartLearn);
        MaterialButton btnCloseDialog = dialog.findViewById(R.id.btnCloseDialog);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, learnableKeys);
        spKeyToLearn.setAdapter(adapter);

        btnStartLearn.setOnClickListener(v -> {
            stopPolling();
            int selectedKeyIndex = spKeyToLearn.getSelectedItemPosition();
            String keyLabel = learnableKeys[selectedKeyIndex];

            pbLearning.setVisibility(View.VISIBLE);
            tvLearnStatus.setText("Avvio ascolto sull'emettitore Tuya...");
            btnStartLearn.setEnabled(false);

            tuyaClient.startLearning(new TuyaApiClient.ApiCallback() {
                @Override
                public void onSuccess(String result) {
                    tvLearnStatus.setText("In ascolto! Punta il telecomando e premi '" + keyLabel + "' ORA...");
                    startPollingForCode(selectedKeyIndex, tvLearnStatus, pbLearning, btnStartLearn);
                }

                @Override
                public void onError(String message) {
                    pbLearning.setVisibility(View.GONE);
                    btnStartLearn.setEnabled(true);
                    tvLearnStatus.setText("Errore avvio ascolto: " + message);
                }
            });
        });

        btnCloseDialog.setOnClickListener(v -> {
            stopPolling();
            dialog.dismiss();
        });

        dialog.setOnDismissListener(d -> stopPolling());
        dialog.show();
    }

    private void startPollingForCode(int keyIndex, TextView tvStatus, ProgressBar progressBar, MaterialButton btnStart) {
        final int[] attempts = {0};
        final int maxAttempts = 18; // ~20 secondi

        pollRunnable = new Runnable() {
            @Override
            public void run() {
                attempts[0]++;
                tuyaClient.fetchLearnedCode(new TuyaApiClient.ApiCallback() {
                    @Override
                    public void onSuccess(String code) {
                        progressBar.setVisibility(View.GONE);
                        btnStart.setEnabled(true);

                        String actionKey = configActionKeys[keyIndex];
                        config.setKeyMapping(actionKey, code);

                        tvStatus.setText("Segnale catturato e associato a " + learnableKeys[keyIndex] + "!");
                        Toast.makeText(SettingsActivity.this, "Tasto memorizzato: " + learnableKeys[keyIndex], Toast.LENGTH_SHORT).show();
                        stopPolling();
                    }

                    @Override
                    public void onError(String message) {
                        if (attempts[0] < maxAttempts) {
                            tvStatus.setText("In attesa del segnale dal telecomando... (" + (maxAttempts - attempts[0]) + "s)");
                            handler.postDelayed(pollRunnable, 1200);
                        } else {
                            progressBar.setVisibility(View.GONE);
                            btnStart.setEnabled(true);
                            tvStatus.setText("Tempo scaduto. Nessun segnale rilevato. Riprova puntando verso l'emettitore.");
                            stopPolling();
                        }
                    }
                });
            }
        };

        handler.postDelayed(pollRunnable, 1500);
    }

    private void stopPolling() {
        if (pollRunnable != null) {
            handler.removeCallbacks(pollRunnable);
            pollRunnable = null;
        }
    }

    private void saveDraftConfig() {
        config.setClientId(etClientId.getText().toString().trim());
        config.setSecret(etSecret.getText().toString().trim());
        config.setDeviceId(etDeviceId.getText().toString().trim());
        config.setRemoteId(etRemoteId.getText().toString().trim());
        int selectedRegionIdx = spRegion.getSelectedItemPosition();
        if (selectedRegionIdx >= 0 && selectedRegionIdx < regionCodes.length) {
            config.setRegion(regionCodes[selectedRegionIdx]);
        }
        config.setDemoMode(cbDemoMode.isChecked());
    }
}
