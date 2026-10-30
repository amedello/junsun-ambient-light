package com.junsun.ambientlight;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TuyaApiClient {

    private static final String TAG = "TuyaApiClient";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public interface ApiCallback {
        void onSuccess(String result);
        void onError(String message);
    }

    private final AmbientConfig config;
    private final OkHttpClient httpClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String cachedToken = null;
    private long tokenExpireTime = 0;

    public TuyaApiClient(AmbientConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    private String getBaseUrl() {
        String region = config.getRegion();
        switch (region) {
            case "we":
                return "https://openapi.tuyawe.com";
            case "us":
                return "https://openapi.tuyaus.com";
            case "cn":
                return "https://openapi.tuyacn.com";
            case "in":
                return "https://openapi.tuyain.com";
            case "eu":
            default:
                return "https://openapi.tuyaeu.com";
        }
    }

    /**
     * Invia un comando al ricevitore IR:
     * - Se keyName inizia con "RAW:" o è una stringa lunga Base64, usa l'endpoint /send-ir per inviare il codice grezzo appreso.
     * - Altrimenti invia il nome del tasto al telecomando mappato (/remotes/{id}/command).
     */
    public void sendRemoteKey(final String keyName, final ApiCallback callback) {
        if (config.isDemoMode()) {
            Log.d(TAG, "[DEMO MODE] Simulating IR Command: " + keyName);
            if (callback != null) {
                mainHandler.post(() -> callback.onSuccess("Simulazione: tasto '" + (keyName.startsWith("RAW:") ? "IR Appreso" : keyName) + "' inviato con successo"));
            }
            return;
        }

        executor.execute(() -> {
            try {
                String token = getOrRefreshToken();
                if (token == null) {
                    postError(callback, "Impossibile ottenere il token Tuya");
                    return;
                }

                String deviceId = config.getDeviceId();
                String remoteId = config.getRemoteId();

                String path;
                JSONObject bodyJson = new JSONObject();

                if (!remoteId.isEmpty()) {
                    // Endpoint ufficiale per telecomandi virtuali DIY (sub-device):
                    // invia il comando di trasmissione con codice "key" e ID tasto salvato
                    path = "/v1.0/devices/" + remoteId + "/commands";
                    JSONArray commands = new JSONArray();
                    JSONObject cmd = new JSONObject();
                    cmd.put("code", "key");
                    cmd.put("value", keyName);
                    commands.put(cmd);
                    bodyJson.put("commands", commands);
                } else {
                    boolean isRawIr = keyName.startsWith("RAW:") || keyName.length() > 35;
                    if (isRawIr) {
                        path = "/v1.0/infrareds/" + deviceId + "/send-ir";
                        String rawCode = keyName.startsWith("RAW:") ? keyName.substring(4) : keyName;
                        bodyJson.put("code", rawCode);
                    } else {
                        path = "/v1.0/infrareds/" + deviceId + "/send-keys";
                        bodyJson.put("key", keyName);
                    }
                }

                String bodyStr = bodyJson.toString();
                long timestamp = System.currentTimeMillis();
                String sign = calculateBusinessSign(config.getClientId(), config.getSecret(), token, timestamp, "POST", path, bodyStr);

                Request request = new Request.Builder()
                        .url(getBaseUrl() + path)
                        .post(RequestBody.create(bodyStr, JSON))
                        .addHeader("client_id", config.getClientId())
                        .addHeader("access_token", token)
                        .addHeader("sign", sign)
                        .addHeader("t", String.valueOf(timestamp))
                        .addHeader("sign_method", "HMAC-SHA256")
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    String respBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "Tuya Response (" + response.code() + "): " + respBody);

                    if (response.isSuccessful()) {
                        JSONObject json = new JSONObject(respBody);
                        boolean success = json.optBoolean("success", false);
                        if (success) {
                            postSuccess(callback, "Comando inviato con successo!");
                        } else {
                            String msg = json.optString("msg", "Errore risposta Tuya");
                            postError(callback, msg);
                        }
                    } else {
                        postError(callback, "Errore HTTP " + response.code() + ": " + respBody);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending remote key", e);
                postError(callback, "Eccezione: " + e.getMessage());
            }
        });
    }

    /**
     * Mette l'emettitore Tuya in modalità ascolto/apprendimento IR.
     */
    public void startLearning(final ApiCallback callback) {
        if (config.isDemoMode()) {
            postSuccess(callback, "Modalità apprendimento avviata (Simulazione)");
            return;
        }

        executor.execute(() -> {
            try {
                String token = getOrRefreshToken();
                if (token == null) {
                    postError(callback, "Token Tuya non valido");
                    return;
                }

                String deviceId = config.getDeviceId();
                String path = "/v1.0/infrareds/" + deviceId + "/learning-codes";

                long timestamp = System.currentTimeMillis();
                String sign = calculateBusinessSign(config.getClientId(), config.getSecret(), token, timestamp, "POST", path, "{}");

                Request request = new Request.Builder()
                        .url(getBaseUrl() + path)
                        .post(RequestBody.create("{}", JSON))
                        .addHeader("client_id", config.getClientId())
                        .addHeader("access_token", token)
                        .addHeader("sign", sign)
                        .addHeader("t", String.valueOf(timestamp))
                        .addHeader("sign_method", "HMAC-SHA256")
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    String respBody = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        JSONObject json = new JSONObject(respBody);
                        if (json.optBoolean("success", false)) {
                            postSuccess(callback, "Emettitore in ascolto! Premi il tasto sul telecomando fisico.");
                        } else {
                            postError(callback, json.optString("msg", "Errore attivazione apprendimento"));
                        }
                    } else {
                        postError(callback, "Errore HTTP " + response.code() + ": " + respBody);
                    }
                }
            } catch (Exception e) {
                postError(callback, "Errore: " + e.getMessage());
            }
        });
    }

    /**
     * Recupera il codice IR appena catturato dall'emettitore Tuya.
     */
    public void fetchLearnedCode(final ApiCallback callback) {
        if (config.isDemoMode()) {
            postSuccess(callback, "RAW:DEMO_LEARNED_CODE_" + System.currentTimeMillis());
            return;
        }

        executor.execute(() -> {
            try {
                String token = getOrRefreshToken();
                if (token == null) {
                    postError(callback, "Token Tuya non valido");
                    return;
                }

                String deviceId = config.getDeviceId();
                String path = "/v1.0/infrareds/" + deviceId + "/learning-codes";

                long timestamp = System.currentTimeMillis();
                String sign = calculateBusinessSign(config.getClientId(), config.getSecret(), token, timestamp, "GET", path, "");

                Request request = new Request.Builder()
                        .url(getBaseUrl() + path)
                        .get()
                        .addHeader("client_id", config.getClientId())
                        .addHeader("access_token", token)
                        .addHeader("sign", sign)
                        .addHeader("t", String.valueOf(timestamp))
                        .addHeader("sign_method", "HMAC-SHA256")
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    String respBody = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        JSONObject json = new JSONObject(respBody);
                        if (json.optBoolean("success", false)) {
                            Object resultObj = json.opt("result");
                            String code = null;
                            if (resultObj instanceof JSONObject) {
                                code = ((JSONObject) resultObj).optString("code", null);
                            } else if (resultObj instanceof String) {
                                code = (String) resultObj;
                            }
                            if (code != null && !code.isEmpty() && !code.equalsIgnoreCase("null")) {
                                postSuccess(callback, "RAW:" + code);
                            } else {
                                postError(callback, "Nessun segnale ancora ricevuto");
                            }
                        } else {
                            postError(callback, json.optString("msg", "Codice non pronto"));
                        }
                    } else {
                        postError(callback, "Errore HTTP " + response.code());
                    }
                }
            } catch (Exception e) {
                postError(callback, "Errore: " + e.getMessage());
            }
        });
    }

    private synchronized String getOrRefreshToken() throws Exception {
        long now = System.currentTimeMillis();
        if (cachedToken != null && now < tokenExpireTime) {
            return cachedToken;
        }

        String clientId = config.getClientId();
        String secret = config.getSecret();
        if (clientId.isEmpty() || secret.isEmpty()) {
            return null;
        }

        String path = "/v1.0/token?grant_type=1";
        long timestamp = now;
        String sign = calculateTokenSign(clientId, secret, timestamp);

        Request request = new Request.Builder()
                .url(getBaseUrl() + path)
                .get()
                .addHeader("client_id", clientId)
                .addHeader("sign", sign)
                .addHeader("t", String.valueOf(timestamp))
                .addHeader("sign_method", "HMAC-SHA256")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                Log.e(TAG, "Token request failed: " + respBody);
                return null;
            }

            JSONObject json = new JSONObject(respBody);
            if (json.optBoolean("success", false)) {
                JSONObject result = json.getJSONObject("result");
                cachedToken = result.getString("access_token");
                int expireTimeSec = result.optInt("expire_time", 7200);
                tokenExpireTime = System.currentTimeMillis() + ((expireTimeSec - 300) * 1000L);
                Log.i(TAG, "Tuya token obtained successfully!");
                return cachedToken;
            } else {
                Log.e(TAG, "Tuya token error msg: " + json.optString("msg"));
                return null;
            }
        }
    }

    private static String calculateTokenSign(String clientId, String secret, long timestamp) {
        String contentHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        String stringToSign = "GET\n" + contentHash + "\n\n/v1.0/token?grant_type=1";
        String signPayload = clientId + timestamp + stringToSign;
        return hmacSha256(signPayload, secret);
    }

    private static String calculateBusinessSign(String clientId, String secret, String token, long timestamp, String method, String url, String body) {
        String contentHash = sha256Hex(body);
        String stringToSign = method + "\n" + contentHash + "\n" + "\n" + url;
        String signPayload = clientId + token + timestamp + stringToSign;
        return hmacSha256(signPayload, secret);
    }

    private static String sha256Hex(String input) {
        if (input == null || input.isEmpty()) {
            return "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"; // Empty string SHA-256
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().toUpperCase();
        } catch (Exception e) {
            return "";
        }
    }

    private void postSuccess(ApiCallback callback, String msg) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(msg));
        }
    }

    private void postError(ApiCallback callback, String msg) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(msg));
        }
    }
}
