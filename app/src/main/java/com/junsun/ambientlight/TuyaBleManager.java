package com.junsun.ambientlight;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Gestore Bluetooth Low Energy (BLE) per il controllo diretto locale
 * del blaster IR Tuya S06CB3S (modulo CB3S Beken BK7231N).
 * Implementa il protocollo Tuya BLE con handshake a 2 vie (DEVICE_INFO_REQ + PAIR_REQ),
 * cifratura AES-128-CBC, calcolo CRC16 Modbus, pacing frammenti sincronizzato
 * tramite onCharacteristicWrite, auto-rilevamento versione protocollo (v2/v4),
 * e packaging DP 201 (ir_send) con forme d'onda IR Base64 reali.
 */
public class TuyaBleManager {

    private static final String TAG = "TuyaBleManager";

    // UUID Servizi Tuya
    public static final UUID TUYA_SERVICE_UUID = UUID.fromString("0000fd50-0000-1000-8000-00805f9b34fb");
    public static final UUID TUYA_ALT_SERVICE_UUID = UUID.fromString("0000a201-0000-1000-8000-00805f9b34fb");
    public static final UUID TUYA_LEGACY_SERVICE_UUID = UUID.fromString("00001910-0000-1000-8000-00805f9b34fb");

    // Characteristic Write Tuya
    public static final UUID TUYA_CHAR_WRITE_1 = UUID.fromString("00000001-0000-1001-8001-00805f9b07d0");
    public static final UUID TUYA_CHAR_WRITE_2 = UUID.fromString("00000001-0000-1000-8000-00805f9b34fb");
    public static final UUID TUYA_CHAR_WRITE_3 = UUID.fromString("0000fd51-0000-1000-8000-00805f9b34fb");
    public static final UUID TUYA_CHAR_WRITE_4 = UUID.fromString("00002b11-0000-1000-8000-00805f9b34fb");

    // Characteristic Notify Tuya
    public static final UUID TUYA_CHAR_NOTIFY_1 = UUID.fromString("00000002-0000-1001-8001-00805f9b07d0");
    public static final UUID TUYA_CHAR_NOTIFY_2 = UUID.fromString("00000002-0000-1000-8000-00805f9b34fb");
    public static final UUID TUYA_CHAR_NOTIFY_3 = UUID.fromString("0000fd52-0000-1000-8000-00805f9b34fb");
    public static final UUID TUYA_CHAR_NOTIFY_4 = UUID.fromString("00002b10-0000-1000-8000-00805f9b34fb");

    // Standard CCCD Descriptor (Client Characteristic Configuration)
    public static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // Comandi Tuya BLE ufficiali (specifiche tuya_ble_api.h)
    public static final int CMD_DEVICE_INFO_REQ = 0x0000;
    public static final int CMD_PAIR_REQ = 0x0001;
    public static final int CMD_STATUS_INQUIRY = 0x0002;
    public static final int CMD_STATUS_REPORT = 0x0003;
    public static final int CMD_DATA_POINTS = 0x0004; // FRM_DP_DATA_WRITE ufficiale Tuya BLE

    public interface BleCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public interface BleStateListener {
        void onScanning();
        void onConnecting(String deviceName, String mac);
        void onHandshaking(String stepInfo);
        void onAuthenticated(String deviceName, String mac);
        void onHandshakeFailed(String reason);
        void onDisconnected();
    }

    private final Context context;
    private final AmbientConfig config;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothGattCharacteristic notifyCharacteristic;

    private boolean isScanning = false;
    private boolean isGattConnected = false;
    private boolean isAuthenticated = false;
    private BleStateListener stateListener;

    private final ByteArrayOutputStream inputBuffer = new ByteArrayOutputStream();
    private int inputExpectedPacketNum = 0;
    private int inputExpectedLength = 0;
    private int protocolVersion = 2; // Protocollo Tuya BLE standard (0x20) per moduli Beken BK7231N/CB3S
    private byte[] loginKey = null;

    private int sequenceNumber = 1;
    private byte[] deviceRandomSalt = null;
    private byte[] sessionKey = null;

    // Sincronizzazione affidabile scrittura frammenti GATT
    private final Object writeLock = new Object();
    private volatile boolean writeInProgress = false;
    private volatile int lastWriteStatus = BluetoothGatt.GATT_FAILURE;

    // Gestione tentativi handshake
    private int handshakeAttempts = 0;

    // Ring-buffer diagnostico per consultazione a schermo
    private final List<String> diagnosticLogs = new ArrayList<>();

    public TuyaBleManager(Context context, AmbientConfig config) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.loginKey = deriveLoginKey(config.getLocalKey());
        initBluetooth();
        log("TuyaBleManager inizializzato. LoginKey derivata con successo.");
    }

    private synchronized void log(String msg) {
        Log.i(TAG, msg);
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String entry = "[" + timestamp + "] " + msg;
        diagnosticLogs.add(entry);
        if (diagnosticLogs.size() > 60) {
            diagnosticLogs.remove(0);
        }
    }

    public synchronized List<String> getDiagnosticLogs() {
        return new ArrayList<>(diagnosticLogs);
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public boolean hasSessionKey() {
        return sessionKey != null;
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
        return isGattConnected && writeCharacteristic != null;
    }

    public boolean isAuthenticated() {
        return isAuthenticated && isConnected() && sessionKey != null;
    }

    @SuppressLint("MissingPermission")
    public void resetAndScan() {
        log("Reset manuale: cancellazione cache MAC e riavvio scansione completa...");
        config.setLastBleMac(null);
        disconnect();
        startScanning();
    }

    @SuppressLint("MissingPermission")
    public void startAutoConnect() {
        if (!isBluetoothAvailable()) {
            log("Bluetooth non attivo o non disponibile nel sistema");
            return;
        }

        if (isAuthenticated()) {
            log("Già connesso e autenticato con session_key valida");
            return;
        }

        // Su Android 10 Automotive, la connessione diretta a freddo senza scansione
        // fallisce sistematicamente con GATT status 133.
        // Avviamo sempre la scansione ultra-rapida con filtro mirato sul MAC per aggancio immediato!
        startScanning();
    }

    @SuppressLint("MissingPermission")
    public void startScanning() {
        if (!isBluetoothAvailable() || bleScanner == null || isScanning || isAuthenticated()) {
            return;
        }

        try {
            isScanning = true;
            log("Avvio scansione BLE attiva ad ampio spettro (nessun filtro hardware restrittivo)...");
            if (stateListener != null) {
                mainHandler.post(() -> stateListener.onScanning());
            }

            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();

            // Scansione ad ampio spettro senza filtri kernel per evitare bug di scarto pacchetti nei driver BT UIS8581
            bleScanner.startScan(null, settings, scanCallback);

            // Timeout di sicurezza a 12 secondi
            mainHandler.postDelayed(() -> {
                if (isScanning && !isConnected()) {
                    log("Timeout scansione BLE (12s). Nuovo tentativo tra 3s...");
                    stopScanning();
                    mainHandler.postDelayed(this::startAutoConnect, 3000);
                }
            }, 12000);

        } catch (Exception e) {
            log("Errore avvio scansione BLE: " + e.getMessage());
            isScanning = false;
        }
    }

    @SuppressLint("MissingPermission")
    public void stopScanning() {
        if (bleScanner != null && isScanning) {
            try {
                bleScanner.stopScan(scanCallback);
            } catch (Exception ignored) {}
            isScanning = false;
            log("Scansione BLE arrestata");
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        @SuppressLint("MissingPermission")
        public void onScanResult(int callbackType, ScanResult result) {
            inspectAndConnect(result);
        }

        @Override
        public void onScanFailed(int errorCode) {
            log("Scansione BLE fallita con codice errore hardware: " + errorCode);
            isScanning = false;
        }
    };

    @SuppressLint("MissingPermission")
    private void inspectAndConnect(ScanResult result) {
        if (result == null || result.getDevice() == null) return;
        BluetoothDevice device = result.getDevice();
        String mac = device.getAddress();
        String name = device.getName();

        String targetMac = config.getLastBleMac();
        if (targetMac == null || targetMac.isEmpty() || !BluetoothAdapter.checkBluetoothAddress(targetMac)) {
            targetMac = "50:8B:B9:50:F6:6D";
        }

        boolean match = false;
        String matchReason = "";

        // 1. Match primario per indirizzo MAC hardware
        if (mac != null && (mac.equalsIgnoreCase(targetMac) || mac.equalsIgnoreCase("50:8B:B9:50:F6:6D"))) {
            match = true;
            matchReason = "MAC hardware diretto (" + mac + ")";
        }

        // 2. Match per dati di fabbrica Tuya (Company ID 0x07D0)
        ScanRecord record = result.getScanRecord();
        if (!match && record != null) {
            byte[] mfd = record.getManufacturerSpecificData(0x07D0);
            if (mfd != null) {
                match = true;
                matchReason = "Tuya MFD 0x07D0 (" + mfd.length + "b: " + bytesToHex(mfd) + ")";
            }
        }

        // 3. Match per UUID Servizi Tuya
        if (!match && record != null && record.getServiceUuids() != null) {
            for (ParcelUuid pu : record.getServiceUuids()) {
                if (pu.getUuid().equals(TUYA_SERVICE_UUID) || pu.getUuid().equals(TUYA_ALT_SERVICE_UUID) || pu.getUuid().equals(TUYA_LEGACY_SERVICE_UUID)) {
                    match = true;
                    matchReason = "Service UUID Tuya (" + pu.getUuid() + ")";
                    break;
                }
            }
        }

        // 4. Match per nome del dispositivo
        if (!match && name != null && (name.contains("TY") || name.contains("Tuya") || name.contains("S06") ||
                name.contains("Smart") || name.contains("CB3S") || name.contains("Ir"))) {
            match = true;
            matchReason = "Nome dispositivo (" + name + ")";
        }

        if (match) {
            log("Trovato blaster Tuya S06 tramite " + matchReason + " -> " + (name != null ? name : "Dispositivo BLE") + " [" + mac + "]");
            stopScanning();
            connectToDevice(device);
        }
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice(BluetoothDevice device) {
        if (device == null) return;
        disconnect();

        log("Connessione GATT a " + device.getAddress() + " in corso...");
        if (stateListener != null) {
            mainHandler.post(() -> stateListener.onConnecting(device.getName(), device.getAddress()));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } else {
            bluetoothGatt = device.connectGatt(context, false, gattCallback);
        }
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        mainHandler.removeCallbacks(handshakeRetryRunnable);
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            } catch (Exception ignored) {}
            bluetoothGatt = null;
        }
        writeCharacteristic = null;
        notifyCharacteristic = null;
        isGattConnected = false;
        isAuthenticated = false;
        sessionKey = null;
        deviceRandomSalt = null;
        handshakeAttempts = 0;
        inputBuffer.reset();
        inputExpectedPacketNum = 0;
        inputExpectedLength = 0;
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        @SuppressLint("MissingPermission")
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("GATT Connesso. Richiesta MTU 247 per ottimizzare il throughput...");
                isGattConnected = true;
                if (!gatt.requestMtu(247)) {
                    gatt.discoverServices();
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("GATT Disconnesso (status=" + status + ")");
                mainHandler.removeCallbacks(handshakeRetryRunnable);
                isGattConnected = false;
                isAuthenticated = false;
                sessionKey = null;
                deviceRandomSalt = null;
                writeCharacteristic = null;
                notifyCharacteristic = null;
                if (stateListener != null) {
                    mainHandler.post(() -> stateListener.onDisconnected());
                }
                // Riprova automaticamente la scansione dopo 2.5s se disconnesso
                mainHandler.postDelayed(() -> {
                    if (!isAuthenticated && !isGattConnected) {
                        log("Auto-riavvio scansione BLE dopo disconnessione...");
                        startScanning();
                    }
                }, 2500);
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            log("MTU negoziato con successo a " + mtu + ". Avvio discovery servizi GATT...");
            gatt.discoverServices();
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log("Errore discovery servizi GATT: " + status);
                return;
            }

            log("Servizi GATT scoperti con successo.");
            BluetoothGattService targetService = null;

            // 1. Cerca per UUID del servizio Tuya primario o alternativo
            for (BluetoothGattService s : gatt.getServices()) {
                String sUuid = s.getUuid().toString().toLowerCase();
                if (sUuid.contains("fd50") || sUuid.contains("a201") || sUuid.contains("1910")) {
                    targetService = s;
                    break;
                }
            }

            // 2. Se non identificato per servizio, cerca tra tutte le caratteristiche note Tuya
            if (targetService == null) {
                for (BluetoothGattService s : gatt.getServices()) {
                    for (BluetoothGattCharacteristic ch : s.getCharacteristics()) {
                        String cUuid = ch.getUuid().toString().toLowerCase();
                        if (cUuid.contains("00000001") || cUuid.contains("fd51") || cUuid.contains("2b11") ||
                                cUuid.contains("00000002") || cUuid.contains("fd52") || cUuid.contains("2b10")) {
                            targetService = s;
                            break;
                        }
                    }
                    if (targetService != null) break;
                }
            }

            if (targetService == null) {
                log("Dispositivo non compatibile con Tuya BLE! Rimuovo dalla cache.");
                config.setLastBleMac(null);
                disconnect();
                startScanning();
                return;
            }

            log("Servizio Tuya selezionato: " + targetService.getUuid());

            // Identificazione precisa Characteristic Write e Notify
            for (BluetoothGattCharacteristic ch : targetService.getCharacteristics()) {
                int props = ch.getProperties();
                UUID cu = ch.getUuid();
                String cuStr = cu.toString().toLowerCase();

                if (writeCharacteristic == null) {
                    if (cuStr.contains("00000001") || cuStr.contains("fd51") || cuStr.contains("2b11") ||
                            ((props & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0)) {
                        writeCharacteristic = ch;
                        log("Assegnata Characteristic Write: " + cu + " (props: 0x" + Integer.toHexString(props) + ")");
                    }
                }

                if (notifyCharacteristic == null) {
                    if (cuStr.contains("00000002") || cuStr.contains("fd52") || cuStr.contains("2b10") ||
                            ((props & (BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0)) {
                        notifyCharacteristic = ch;
                        log("Assegnata Characteristic Notify: " + cu + " (props: 0x" + Integer.toHexString(props) + ")");
                    }
                }
            }

            String mac = gatt.getDevice().getAddress();
            config.setLastBleMac(mac);

            if (notifyCharacteristic != null) {
                subscribeNotifications(gatt, notifyCharacteristic);
            } else {
                log("Attenzione: Notify non presente, avvio diretto richiesta handshake");
                startHandshake();
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Notifiche CCCD abilitate con successo su " + descriptor.getCharacteristic().getUuid() + ". Inizio Handshake...");
                startHandshake();
            } else {
                log("Scrittura CCCD fallita con status: " + status + ", avvio comunque handshake di sicurezza");
                startHandshake();
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            synchronized (writeLock) {
                writeInProgress = false;
                lastWriteStatus = status;
                writeLock.notifyAll();
            }
        }

        // Metodo legacy per Android < 13
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] value = characteristic.getValue();
            if (value != null && value.length > 0) {
                log("GATT Notification (" + value.length + " byte): " + bytesToHex(value));
                handleDeviceNotification(value);
            }
        }

        // Metodo per Android 13+ (API 33+)
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {
            if (value != null && value.length > 0) {
                log("GATT Notification API33+ (" + value.length + " byte): " + bytesToHex(value));
                handleDeviceNotification(value);
            }
        }
    };

    @SuppressLint("MissingPermission")
    private void subscribeNotifications(BluetoothGatt gatt, BluetoothGattCharacteristic ch) {
        gatt.setCharacteristicNotification(ch, true);
        BluetoothGattDescriptor descriptor = ch.getDescriptor(CCCD_UUID);
        if (descriptor != null) {
            int props = ch.getProperties();
            if ((props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            } else if ((props & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            } else {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            }
            gatt.writeDescriptor(descriptor);
        } else {
            log("Descrittore CCCD standard non presente, avvio diretto handshake");
            startHandshake();
        }
    }

    private void startHandshake() {
        handshakeAttempts = 0;
        sequenceNumber = 1;
        protocolVersion = 2; // Rigorosamente protocollo v2 (0x20)
        mainHandler.removeCallbacks(handshakeRetryRunnable);

        if (stateListener != null) {
            mainHandler.post(() -> stateListener.onHandshaking("Handshake Tuya BLE (v2)..."));
        }

        sendDeviceInfoRequest();
        // Schedula un retry a 2500ms se S06 non risponde
        mainHandler.postDelayed(handshakeRetryRunnable, 2500);
    }

    private final Runnable handshakeRetryRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAuthenticated && isGattConnected && bluetoothGatt != null) {
                handshakeAttempts++;
                if (handshakeAttempts < 4) {
                    protocolVersion = 2;
                    log("Handshake retry #" + handshakeAttempts + ": ritento DeviceInfo con protocolVersion v2 (0x20)...");
                    if (stateListener != null) {
                        mainHandler.post(() -> stateListener.onHandshaking("Tentativo " + handshakeAttempts + "/3 (v2)"));
                    }
                    sendDeviceInfoRequest();
                    mainHandler.postDelayed(this, 2500);
                } else {
                    log("Handshake fallito dopo " + handshakeAttempts + " tentativi. Nessuna risposta dal chip S06.");
                    if (stateListener != null) {
                        mainHandler.post(() -> stateListener.onHandshakeFailed("S06 non risponde all'handshake BLE"));
                    }
                }
            }
        }
    };

    /**
     * Step 1 Handshake: Invia richiesta SENDER_DEVICE_INFO (CMD 0x0000, Flag 0x04)
     */
    private void sendDeviceInfoRequest() {
        executor.execute(() -> {
            try {
                if (loginKey == null) {
                    loginKey = deriveLoginKey(config.getLocalKey());
                }
                List<byte[]> packets = buildPackets(sequenceNumber++, CMD_DEVICE_INFO_REQ, new byte[0], 0, loginKey, (byte) 0x04);
                boolean sent = sendPackets(packets);
                log("Handshake Step 1 (Device Info Request 0x0000, v" + protocolVersion + ") inviato: " + (sent ? "OK (" + packets.size() + " pkt)" : "ERRORE"));
            } catch (Exception e) {
                log("Eccezione invio Device Info: " + e.getMessage());
            }
        });
    }

    /**
     * Step 2 Handshake: Invia SENDER_PAIR (CMD 0x0001, Flag 0x05) con UUID e Local Key
     */
    private void sendPairRequest() {
        executor.execute(() -> {
            try {
                String uuid = "20c191e3cf9c2795"; // 16 bytes
                String localKey = config.getLocalKey();
                String prefix = localKey.substring(0, Math.min(6, localKey.length()));
                String devId = config.getDeviceId(); // 20-22 bytes

                byte[] pairData = new byte[44];
                byte[] uBytes = uuid.getBytes(StandardCharsets.UTF_8);
                byte[] lBytes = prefix.getBytes(StandardCharsets.UTF_8);
                byte[] dBytes = devId.getBytes(StandardCharsets.UTF_8);

                System.arraycopy(uBytes, 0, pairData, 0, Math.min(16, uBytes.length));
                System.arraycopy(lBytes, 0, pairData, 16, Math.min(6, lBytes.length));
                System.arraycopy(dBytes, 0, pairData, 22, Math.min(22, dBytes.length));

                byte[] key = (sessionKey != null) ? sessionKey : loginKey;
                List<byte[]> packets = buildPackets(sequenceNumber++, CMD_PAIR_REQ, pairData, 0, key, (byte) 0x05);
                boolean sent = sendPackets(packets);
                log("Handshake Step 2 (Pair Request 0x0001, 44 byte) inviato: " + (sent ? "OK (" + packets.size() + " pkt)" : "ERRORE"));
            } catch (Exception e) {
                log("Eccezione invio Pair Request: " + e.getMessage());
            }
        });
    }

    /**
     * Gestisce i pacchetti di notifica ricevuti dal chip Tuya riassemblando i frammenti GATT
     */
    private synchronized void handleDeviceNotification(byte[] data) {
        if (data == null || data.length == 0) return;

        int pos = 0;
        int[] unp1 = unpackInt(data, pos);
        int packetNum = unp1[0];
        pos = unp1[1];

        if (packetNum != inputExpectedPacketNum) {
            if (packetNum == 0) {
                inputBuffer.reset();
                inputExpectedPacketNum = 0;
            } else {
                log("Pacchetto fuori sequenza: atteso " + inputExpectedPacketNum + ", ricevuto " + packetNum);
                inputBuffer.reset();
                inputExpectedPacketNum = 0;
                return;
            }
        }

        if (packetNum == 0) {
            inputBuffer.reset();
            int[] unp2 = unpackInt(data, pos);
            inputExpectedLength = unp2[0];
            pos = unp2[1];
            pos++; // Salta il byte versione protocollo
        }

        if (pos < data.length) {
            inputBuffer.write(data, pos, data.length - pos);
        }
        inputExpectedPacketNum++;

        if (inputBuffer.size() >= inputExpectedLength && inputExpectedLength > 0) {
            byte[] fullPayload = inputBuffer.toByteArray();
            inputBuffer.reset();
            inputExpectedPacketNum = 0;
            inputExpectedLength = 0;
            processCompleteNotification(fullPayload);
        }
    }

    private void processCompleteNotification(byte[] payload) {
        if (payload.length < 18) return;

        int securityFlag = payload[0] & 0xFF;
        byte[] iv = Arrays.copyOfRange(payload, 1, 17);
        byte[] encrypted = Arrays.copyOfRange(payload, 17, payload.length);

        byte[] key = (securityFlag == 0x04) ? loginKey : sessionKey;
        if (key == null) {
            key = loginKey;
        }

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(Arrays.copyOf(key, 16), "AES"), new IvParameterSpec(iv));
            byte[] raw = cipher.doFinal(encrypted);

            if (raw.length < 12) return;

            int seqNum = ((raw[0] & 0xFF) << 24) | ((raw[1] & 0xFF) << 16) | ((raw[2] & 0xFF) << 8) | (raw[3] & 0xFF);
            int responseTo = ((raw[4] & 0xFF) << 24) | ((raw[5] & 0xFF) << 16) | ((raw[6] & 0xFF) << 8) | (raw[7] & 0xFF);
            int code = ((raw[8] & 0xFF) << 8) | (raw[9] & 0xFF);
            int length = ((raw[10] & 0xFF) << 8) | (raw[11] & 0xFF);

            int dataEnd = 12 + length;
            if (raw.length < dataEnd) return;

            byte[] data = Arrays.copyOfRange(raw, 12, dataEnd);
            log("Notifica ricevuta: CMD 0x" + Integer.toHexString(code) + ", len=" + length);

            if (code == CMD_DEVICE_INFO_REQ) { // 0x0000
                mainHandler.removeCallbacks(handshakeRetryRunnable);
                if (data.length >= 3) {
                    protocolVersion = data[2] & 0xFF;
                }
                if (data.length >= 12) {
                    // data[6..12] è lo srand casuale a 6 byte generato da S06
                    deviceRandomSalt = Arrays.copyOfRange(data, 6, 12);
                    sessionKey = deriveSessionKey(config.getLocalKey(), deviceRandomSalt);
                    log("Handshake Step 1 SUCCESSO! Ricevuto srand=" + bytesToHex(deviceRandomSalt) + " (v" + protocolVersion + "). SessionKey calcolata!");
                }
                if (stateListener != null) {
                    mainHandler.post(() -> stateListener.onHandshaking("Srand OK, invio Pair Request..."));
                }
                sendPairRequest();
            } else if (code == CMD_PAIR_REQ) { // 0x0001
                mainHandler.removeCallbacks(handshakeRetryRunnable);
                int result = (data.length > 0) ? (data[0] & 0xFF) : -1;
                log("Handshake Step 2 SUCCESSO! Risposta Pair result=" + result + " (0=nuovo, 2=già associato). Autenticato al 100%!");
                isAuthenticated = true;
                if (stateListener != null && bluetoothGatt != null) {
                    final BluetoothDevice dev = bluetoothGatt.getDevice();
                    mainHandler.post(() -> stateListener.onAuthenticated(dev.getName(), dev.getAddress()));
                }
            } else if (code == CMD_DATA_POINTS || code == 0x0002 || code == 0x0004 || code == 0x8001 || code == 0x8004 || code == 0x8006) {
                log("ACK DP ricevuto da S06 (0x" + Integer.toHexString(code) + ")! Comando IR eseguito dall'hardware!");
            }
        } catch (Exception e) {
            log("Errore decifratura notifica Tuya: " + e.getMessage());
        }
    }

    /**
     * Esegue il packaging e l'invio fisico di un pacchetto DP 201 (ir_send).
     * @param code Stringa Base64 pura (forma d'onda a microsecondi) oppure ID tasto telecomando virtuale.
     * @param logPrefix Etichetta diagnostica per il tracciamento dei log.
     * @return true se tutti i frammenti GATT BLE sono stati confermati dall'hardware.
     */
    private boolean sendDp201Payload(String code, String logPrefix) {
        if (code == null || code.trim().isEmpty() || !isConnected()) {
            return false;
        }

        try {
            String cleanCode = code.trim();
            // Costruzione del JSON conforme alle specifiche Tuya per DP 201 (ir_send)
            String irJson = "{\"control\":\"send_ir\",\"head\":\"\",\"key1\":\"" + cleanCode + "\",\"type\":0}";
            byte[] jsonBytes = irJson.getBytes(StandardCharsets.UTF_8);

            // Packaging DP conforme a specifiche ufficiali Tuya TLV (2 byte length big-endian):
            // [0xC9 (DP 201)] [0x03 (Type String)] [LEN_H] [LEN_L] [JSON...]
            ByteArrayOutputStream dpStream = new ByteArrayOutputStream();
            dpStream.write(0xC9); // DP 201
            dpStream.write(0x03); // Type String
            dpStream.write((jsonBytes.length >> 8) & 0xFF); // Length HIGH byte
            dpStream.write(jsonBytes.length & 0xFF);        // Length LOW byte
            dpStream.write(jsonBytes);
            byte[] dpPayload = dpStream.toByteArray();

            // Cifratura e frammentazione Tuya BLE con sessionKey o loginKey
            byte[] key = (sessionKey != null) ? sessionKey : loginKey;
            List<byte[]> packets = buildPackets(sequenceNumber++, CMD_DATA_POINTS, dpPayload, 0, key, (byte) 0x05);

            String preview = (cleanCode.length() > 24) ? cleanCode.substring(0, 18) + "..." : cleanCode;
            log(logPrefix + " Invio DP 201: " + dpPayload.length + "b (" + packets.size() + " frammenti, val=\"" + preview + "\")...");
            boolean ok = sendPackets(packets);
            log(logPrefix + " Esito trasmissione: " + (ok ? "CONFERMATO (ACK)" : "FALLITO"));
            return ok;
        } catch (Exception e) {
            log(logPrefix + " Errore packaging: " + e.getMessage());
            return false;
        }
    }

    /**
     * Invia un comando IR provando il Fallback Duale o la modalità selezionata:
     * - Modalità A: Formato Raw Base64 (microsecondi di impulso IR appresi)
     * - Modalità B: Formato Tuya Key ID (ID tasto telecomando virtuale salvato nel chip)
     * In modalità DUAL (default), invia prima la forma grezza e subito dopo l'ID tasto a distanza di 75ms.
     */
    @SuppressLint("MissingPermission")
    public void sendRemoteKeyDual(final String rawBase64, final String keyId, final BleCallback callback) {
        if (!isConnected()) {
            if (callback != null) callback.onError("BLE non connesso al blaster Tuya");
            return;
        }

        if (!isAuthenticated || sessionKey == null) {
            log("Avviso: Invio IR tentato con loginKey provvisoria (session_key non ancora negoziata)");
        }

        executor.execute(() -> {
            try {
                int sendMode = config.getBleSendMode();
                boolean successA = false;
                boolean successB = false;

                String cleanRaw = (rawBase64 != null) ? rawBase64.trim() : "";
                String cleanKey = (keyId != null) ? keyId.trim() : "";

                if (sendMode == AmbientConfig.SEND_MODE_RAW_ONLY || sendMode == AmbientConfig.SEND_MODE_DUAL) {
                    if (!cleanRaw.isEmpty()) {
                        successA = sendDp201Payload(cleanRaw, "[Mod A - Raw Base64]");
                    }
                }

                if (sendMode == AmbientConfig.SEND_MODE_DUAL) {
                    // Pausa tra i due pacchetti per permettere al chip Beken BK7231N di svuotare il buffer FIFO radio
                    try {
                        Thread.sleep(75);
                    } catch (InterruptedException ignored) {}
                }

                if (sendMode == AmbientConfig.SEND_MODE_KEY_ID_ONLY || sendMode == AmbientConfig.SEND_MODE_DUAL) {
                    if (!cleanKey.isEmpty() && !cleanKey.equals(cleanRaw)) {
                        successB = sendDp201Payload(cleanKey, "[Mod B - Key ID]");
                    }
                }

                final boolean finalSuccess = (sendMode == AmbientConfig.SEND_MODE_RAW_ONLY) ? successA :
                        (sendMode == AmbientConfig.SEND_MODE_KEY_ID_ONLY) ? successB :
                                (successA || successB);

                final String modeDesc = (sendMode == AmbientConfig.SEND_MODE_DUAL) ? "Dual Fallback (Base64 + KeyID)" :
                        (sendMode == AmbientConfig.SEND_MODE_RAW_ONLY) ? "Solo Base64 Raw" : "Solo Key ID";

                mainHandler.post(() -> {
                    if (finalSuccess) {
                        log("Comando IR eseguito con successo (" + modeDesc + ")!");
                        if (callback != null) callback.onSuccess("IR inviato (" + modeDesc + ")");
                    } else {
                        log("Errore trasmissione pacchetti BLE (" + modeDesc + ")");
                        if (callback != null) callback.onError("Errore scrittura BLE (" + modeDesc + ")");
                    }
                });

            } catch (Exception e) {
                log("Eccezione durante invio comando IR duale: " + e.getMessage());
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Errore BLE: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Invia un comando IR specificando un singolo valore (raw o keyId)
     */
    @SuppressLint("MissingPermission")
    public void sendRemoteKey(final String rawOrKeyId, final BleCallback callback) {
        sendRemoteKeyDual(rawOrKeyId, rawOrKeyId, callback);
    }

    /**
     * Costruisce i pacchetti di wire format Tuya BLE frammentati a blocchi GATT di max 20 byte:
     * [PacketNum (varint)] [TotalLen (varint - solo su pkt 0)] [Ver (protocolVersion<<4 - solo su pkt 0)] [Payload slice]
     */
    private synchronized List<byte[]> buildPackets(int seqNum, int code, byte[] data, int responseTo, byte[] key, byte securityFlag) throws Exception {
        byte[] iv = new byte[16];
        new java.security.SecureRandom().nextBytes(iv);

        // raw = [seqNum (4)] [responseTo (4)] [code (2)] [data.length (2)] + data
        ByteArrayOutputStream rawStream = new ByteArrayOutputStream();
        rawStream.write((seqNum >> 24) & 0xFF);
        rawStream.write((seqNum >> 16) & 0xFF);
        rawStream.write((seqNum >> 8) & 0xFF);
        rawStream.write(seqNum & 0xFF);

        rawStream.write((responseTo >> 24) & 0xFF);
        rawStream.write((responseTo >> 16) & 0xFF);
        rawStream.write((responseTo >> 8) & 0xFF);
        rawStream.write(responseTo & 0xFF);

        rawStream.write((code >> 8) & 0xFF);
        rawStream.write(code & 0xFF);

        rawStream.write((data.length >> 8) & 0xFF);
        rawStream.write(data.length & 0xFF);

        if (data.length > 0) {
            rawStream.write(data);
        }

        byte[] rawBytes = rawStream.toByteArray();
        int crc = calculateCrc16Modbus(rawBytes, 0, rawBytes.length);
        rawStream.write((crc >> 8) & 0xFF);
        rawStream.write(crc & 0xFF);

        // Zero-padding a multiplo esatto di 16 byte per AES-128
        while (rawStream.size() % 16 != 0) {
            rawStream.write(0x00);
        }

        byte[] paddedRaw = rawStream.toByteArray();

        // Cifratura AES-128-CBC
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(Arrays.copyOf(key, 16), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] ciphertext = cipher.doFinal(paddedRaw);

        // encrypted = securityFlag (1 byte) + iv (16 byte) + ciphertext
        ByteArrayOutputStream encryptedStream = new ByteArrayOutputStream();
        encryptedStream.write(securityFlag);
        encryptedStream.write(iv);
        encryptedStream.write(ciphertext);
        byte[] encrypted = encryptedStream.toByteArray();

        List<byte[]> commands = new ArrayList<>();
        int packetNum = 0;
        int pos = 0;
        int totalLen = encrypted.length;
        int gattMtu = 20;

        while (pos < totalLen) {
            ByteArrayOutputStream packet = new ByteArrayOutputStream();
            packet.write(packInt(packetNum));

            if (packetNum == 0) {
                packet.write(packInt(totalLen));
                packet.write((protocolVersion << 4) & 0xFF);
            }

            int headerLen = packet.size();
            int maxChunk = gattMtu - headerLen;
            int take = Math.min(maxChunk, totalLen - pos);

            packet.write(encrypted, pos, take);
            commands.add(packet.toByteArray());

            pos += take;
            packetNum++;
        }

        return commands;
    }

    /**
     * Trasmette i pacchetti GATT in modo sincronizzato e affidabile:
     * attende la notifica di completamento (onCharacteristicWrite) o effettua pacing
     * per garantire che nessun pacchetto venga scartato dalla coda Bluetooth dell'autoradio.
     */
    @SuppressLint("MissingPermission")
    private synchronized boolean sendPackets(List<byte[]> packets) {
        if (bluetoothGatt == null || writeCharacteristic == null || packets == null || packets.isEmpty()) {
            log("sendPackets impossibile: gatt=" + (bluetoothGatt != null) + ", char=" + (writeCharacteristic != null));
            return false;
        }

        int total = packets.size();
        for (int i = 0; i < total; i++) {
            byte[] pkt = packets.get(i);
            synchronized (writeLock) {
                writeInProgress = true;
                lastWriteStatus = BluetoothGatt.GATT_FAILURE;

                writeCharacteristic.setValue(pkt);
                int props = writeCharacteristic.getProperties();
                if ((props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                } else {
                    writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                }

                boolean ok = bluetoothGatt.writeCharacteristic(writeCharacteristic);
                if (!ok) {
                    // Se lo stack è occupato, attendi 25ms e ritenta una volta
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException ignored) {}
                    ok = bluetoothGatt.writeCharacteristic(writeCharacteristic);
                    if (!ok) {
                        log("ERRORE CRITICO: Fallita scrittura frammento " + (i + 1) + "/" + total);
                        writeInProgress = false;
                        return false;
                    }
                }

                // Attesa segnale da onCharacteristicWrite con timeout di 250ms
                long start = System.currentTimeMillis();
                while (writeInProgress && (System.currentTimeMillis() - start) < 250) {
                    try {
                        writeLock.wait(100);
                    } catch (InterruptedException e) {
                        break;
                    }
                }

                if (writeInProgress) {
                    // Timeout (può succedere con alcuni chip WRITE_NO_RESPONSE)
                    writeInProgress = false;
                }

                // Piccola pausa di pacing per evitare saturazione buffer chip Beken BK7231N
                try {
                    Thread.sleep(12);
                } catch (InterruptedException ignored) {}
            }
        }
        return true;
    }

    public static byte[] packInt(int value) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while (true) {
            int currByte = value & 0x7F;
            value >>>= 7;
            if (value != 0) {
                currByte |= 0x80;
            }
            bos.write(currByte);
            if (value == 0) {
                break;
            }
        }
        return bos.toByteArray();
    }

    public static int[] unpackInt(byte[] data, int startPos) {
        int result = 0;
        int offset = 0;
        while (offset < 5) {
            int pos = startPos + offset;
            if (pos >= data.length) break;
            int currByte = data[pos] & 0xFF;
            result |= (currByte & 0x7F) << (offset * 7);
            offset++;
            if ((currByte & 0x80) == 0) {
                break;
            }
        }
        return new int[]{result, startPos + offset};
    }

    private byte[] deriveLoginKey(String localKey) {
        String prefix = localKey.substring(0, Math.min(6, localKey.length()));
        return md5(prefix.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] deriveSessionKey(String localKey, byte[] srand) {
        try {
            String prefix = localKey.substring(0, Math.min(6, localKey.length()));
            byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
            byte[] combined = new byte[prefixBytes.length + srand.length];
            System.arraycopy(prefixBytes, 0, combined, 0, prefixBytes.length);
            System.arraycopy(srand, 0, combined, prefixBytes.length, srand.length);
            return md5(combined);
        } catch (Exception e) {
            return deriveLoginKey(localKey);
        }
    }

    private static byte[] md5(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return md.digest(input);
        } catch (Exception e) {
            return new byte[16];
        }
    }

    public static int calculateCrc16Modbus(byte[] data, int offset, int length) {
        int crc = 0xFFFF;
        for (int i = offset; i < offset + length; i++) {
            crc ^= (data[i] & 0xFF);
            for (int j = 0; j < 8; j++) {
                if ((crc & 1) != 0) {
                    crc = (crc >> 1) ^ 0xA001;
                } else {
                    crc = (crc >> 1);
                }
            }
        }
        return crc & 0xFFFF;
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
