package com.junsun.ambientlight;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Gestore Bluetooth Low Energy (BLE) per il controllo locale diretto del blaster IR Tuya CB3S (S06CB3S).
 * Permette di inviare comandi IR istantaneamente anche a Hotspot o Internet spento.
 */
public class TuyaBleManager {

    private static final String TAG = "TuyaBleManager";

    // UUID ufficiali del servizio Tuya Dual-Mode BLE & IR
    public static final UUID TUYA_SERVICE_UUID = UUID.fromString("0000fd50-0000-1000-8000-00805f9b34fb");
    public static final UUID TUYA_ALT_SERVICE_UUID = UUID.fromString("00001910-0000-1000-8000-00805f9b34fb");

    // Characteristic Write & Notify
    public static final UUID TUYA_CHAR_WRITE = UUID.fromString("00000001-0000-1000-8000-00805f9b34fb");
    public static final UUID TUYA_CHAR_NOTIFY = UUID.fromString("00000002-0000-1000-8000-00805f9b34fb");
    public static final UUID TUYA_ALT_CHAR_WRITE = UUID.fromString("0000fd51-0000-1000-8000-00805f9b34fb");

    public interface BleCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public interface BleStateListener {
        void onConnected(String deviceName, String mac);
        void onDisconnected();
        void onScanning();
    }

    private final Context context;
    private final AmbientConfig config;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic writeCharacteristic;

    private boolean isScanning = false;
    private boolean isConnected = false;
    private BleStateListener stateListener;

    public TuyaBleManager(Context context, AmbientConfig config) {
        this.context = context.getApplicationContext();
        this.config = config;
        initBluetooth();
    }

    public void setStateListener(BleStateListener listener) {
        this.stateListener = listener;
    }

    private void initBluetooth() {
        BluetoothManager bm = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bm != null) {
            bluetoothAdapter = bm.getAdapter();
            if (bluetoothAdapter != null) {
                bleScanner = bluetoothAdapter.getBluetoothLeScanner();
            }
        }
    }

    public boolean isBluetoothAvailable() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public boolean isConnected() {
        return isConnected && bluetoothGatt != null && writeCharacteristic != null;
    }

    /**
     * Avvia la connessione automatica: prova prima l'ultimo MAC noto in cache,
     * altrimenti effettua una scansione BLE filtrata per servizio Tuya FD50.
     */
    @SuppressLint("MissingPermission")
    public void startAutoConnect() {
        if (!isBluetoothAvailable()) {
            Log.w(TAG, "Bluetooth non disponibile o disattivato");
            return;
        }

        if (isConnected()) {
            Log.d(TAG, "Già connesso al dispositivo BLE");
            return;
        }

        String lastMac = config.getLastBleMac();
        if (lastMac != null && BluetoothAdapter.checkBluetoothAddress(lastMac)) {
            Log.d(TAG, "Tentativo di connessione diretta all'ultimo MAC noto: " + lastMac);
            try {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(lastMac);
                connectToDevice(device);
                return;
            } catch (Exception e) {
                Log.w(TAG, "Errore connessione rapida al MAC cache, avvio scansione: " + e.getMessage());
            }
        }

        startScanning();
    }

    @SuppressLint("MissingPermission")
    public void startScanning() {
        if (!isBluetoothAvailable() || bleScanner == null || isScanning || isConnected()) {
            return;
        }

        try {
            isScanning = true;
            if (stateListener != null) {
                mainHandler.post(() -> stateListener.onScanning());
            }

            List<ScanFilter> filters = new ArrayList<>();
            // Filtro per Service UUID Tuya 0xFD50
            filters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(TUYA_SERVICE_UUID)).build());
            filters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(TUYA_ALT_SERVICE_UUID)).build());

            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();

            bleScanner.startScan(filters, settings, scanCallback);
            Log.d(TAG, "Scansione BLE Tuya avviata con filtri specifici");

            // Timeout scansione dopo 15 secondi per risparmiare risorse
            mainHandler.postDelayed(() -> {
                if (isScanning && !isConnected()) {
                    stopScanning();
                    // Se non ha trovato nulla con i filtri UUID, fa una breve scansione generica
                    startFallbackScan();
                }
            }, 15000);

        } catch (Exception e) {
            Log.e(TAG, "Errore durante l'avvio della scansione BLE: " + e.getMessage());
            isScanning = false;
        }
    }

    @SuppressLint("MissingPermission")
    private void startFallbackScan() {
        if (!isBluetoothAvailable() || bleScanner == null || isConnected() || isScanning) return;
        try {
            isScanning = true;
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            bleScanner.startScan(fallbackScanCallback);
            Log.d(TAG, "Scansione BLE fallback avviata");
            mainHandler.postDelayed(this::stopScanning, 10000);
        } catch (Exception e) {
            isScanning = false;
        }
    }

    @SuppressLint("MissingPermission")
    public void stopScanning() {
        if (bleScanner != null && isScanning) {
            try {
                bleScanner.stopScan(scanCallback);
                bleScanner.stopScan(fallbackScanCallback);
            } catch (Exception ignored) {}
            isScanning = false;
            Log.d(TAG, "Scansione BLE interrotta");
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        @SuppressLint("MissingPermission")
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device != null) {
                Log.i(TAG, "Trovato dispositivo Tuya BLE: " + device.getName() + " [" + device.getAddress() + "]");
                stopScanning();
                connectToDevice(device);
            }
        }
    };

    private final ScanCallback fallbackScanCallback = new ScanCallback() {
        @Override
        @SuppressLint("MissingPermission")
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device != null) {
                String name = device.getName();
                String mac = device.getAddress();
                if (name != null && (name.contains("TY") || name.contains("Tuya") || name.contains("S06") || name.contains("Smart") || name.contains("CB3S"))) {
                    Log.i(TAG, "Trovato dispositivo Tuya compatibile via fallback: " + name + " [" + mac + "]");
                    stopScanning();
                    connectToDevice(device);
                }
            }
        }
    };

    @SuppressLint("MissingPermission")
    private void connectToDevice(BluetoothDevice device) {
        if (device == null) return;
        disconnect();

        Log.d(TAG, "Connessione GATT a " + device.getAddress() + "...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } else {
            bluetoothGatt = device.connectGatt(context, false, gattCallback);
        }
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            } catch (Exception ignored) {}
            bluetoothGatt = null;
        }
        writeCharacteristic = null;
        isConnected = false;
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        @SuppressLint("MissingPermission")
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connesso al server GATT, avvio discovery servizi...");
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "Disconnesso dal server GATT");
                isConnected = false;
                writeCharacteristic = null;
                if (stateListener != null) {
                    mainHandler.post(() -> stateListener.onDisconnected());
                }
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (BluetoothGattService service : gatt.getServices()) {
                    UUID uuid = service.getUuid();
                    if (uuid.equals(TUYA_SERVICE_UUID) || uuid.equals(TUYA_ALT_SERVICE_UUID)) {
                        for (BluetoothGattCharacteristic ch : service.getCharacteristics()) {
                            if ((ch.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) {
                                writeCharacteristic = ch;
                                isConnected = true;
                                String mac = gatt.getDevice().getAddress();
                                String name = gatt.getDevice().getName();
                                config.setLastBleMac(mac);
                                Log.i(TAG, "Pronto! Characteristic Write identificata: " + ch.getUuid() + " su " + mac);
                                if (stateListener != null) {
                                    mainHandler.post(() -> stateListener.onConnected(name, mac));
                                }
                                return;
                            }
                        }
                    }
                }

                // Se non ha trovato UUID Tuya esatti, prova la prima caratteristica scrivibile
                for (BluetoothGattService service : gatt.getServices()) {
                    for (BluetoothGattCharacteristic ch : service.getCharacteristics()) {
                        if ((ch.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) {
                            writeCharacteristic = ch;
                            isConnected = true;
                            String mac = gatt.getDevice().getAddress();
                            String name = gatt.getDevice().getName();
                            config.setLastBleMac(mac);
                            if (stateListener != null) {
                                mainHandler.post(() -> stateListener.onConnected(name, mac));
                            }
                            return;
                        }
                    }
                }
            }
        }
    };

    /**
     * Invia un comando IR al dispositivo Tuya via Bluetooth LE con crittografia AES basata su local_key.
     */
    @SuppressLint("MissingPermission")
    public void sendRemoteKey(final String keyId, final BleCallback callback) {
        if (!isConnected()) {
            if (callback != null) callback.onError("BLE non connesso al blaster Tuya");
            return;
        }

        executor.execute(() -> {
            try {
                byte[] payload = buildTuyaBleIrPayload(keyId, config.getLocalKey());
                if (payload == null || payload.length == 0) {
                    mainHandler.post(() -> callback.onError("Errore creazione pacchetto BLE crittografato"));
                    return;
                }

                writeCharacteristic.setValue(payload);
                writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                boolean success = bluetoothGatt.writeCharacteristic(writeCharacteristic);

                mainHandler.post(() -> {
                    if (success) {
                        callback.onSuccess("Comando BLE inviato con successo!");
                    } else {
                        callback.onError("Fallita scrittura su characteristic BLE");
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Errore trasmissione BLE: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("Errore BLE: " + e.getMessage()));
            }
        });
    }

    /**
     * Costruisce il pacchetto Tuya BLE frame standard:
     * Header (0x55 0xAA) + SN + Command Type + Length + AES-128(LocalKey, Payload) + CRC
     */
    private byte[] buildTuyaBleIrPayload(String keyId, String localKey) {
        try {
            // Crea il JSON DP per l'emissione del tasto IR
            // Nel protocollo Tuya IR DP 201 invia il keyId registrato
            String jsonCmd = "{\"201\":\"" + keyId + "\"}";
            byte[] rawData = jsonCmd.getBytes(StandardCharsets.UTF_8);

            // Cifratura AES-128-ECB con local_key
            byte[] keyBytes = Arrays.copyOf(localKey.getBytes(StandardCharsets.UTF_8), 16);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(rawData);

            // Assemblaggio frame Tuya BLE
            int frameLen = 7 + encrypted.length;
            byte[] frame = new byte[frameLen];
            frame[0] = (byte) 0x55;
            frame[1] = (byte) 0xAA;
            frame[2] = 0x00; // Versione
            frame[3] = 0x06; // Comando DP Control
            frame[4] = (byte) ((encrypted.length >> 8) & 0xFF);
            frame[5] = (byte) (encrypted.length & 0xFF);
            System.arraycopy(encrypted, 0, frame, 6, encrypted.length);

            // Calcolo Checksum / CRC8 semplice Tuya
            int sum = 0;
            for (int i = 0; i < frameLen - 1; i++) {
                sum += (frame[i] & 0xFF);
            }
            frame[frameLen - 1] = (byte) (sum & 0xFF);

            return frame;
        } catch (Exception e) {
            Log.e(TAG, "Errore crittografia BLE: " + e.getMessage());
            return null;
        }
    }
}
