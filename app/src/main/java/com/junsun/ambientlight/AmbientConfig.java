package com.junsun.ambientlight;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

public class AmbientConfig {

    private static final String PREF_NAME = "junsun_ambient_pref";

    public static final String KEY_CLIENT_ID = "tuya_client_id";
    public static final String KEY_SECRET = "tuya_secret";
    public static final String KEY_DEVICE_ID = "tuya_device_id";
    public static final String KEY_REMOTE_ID = "tuya_remote_id";
    public static final String KEY_REGION = "tuya_region";
    public static final String KEY_DEMO_MODE = "demo_mode";

    public static final String KEY_MAP_PREFIX = "map_key_";

    // 20 Tasti esatti del telecomando DreamColor 20-Key
    public static final String BTN_POWER = "power";
    public static final String BTN_BRIGHT_UP = "bright_up";
    public static final String BTN_BRIGHT_DOWN = "bright_down";
    public static final String BTN_SPEED_UP = "speed_up";
    public static final String BTN_SPEED_DOWN = "speed_down";
    public static final String BTN_MODE_UP = "mode_up";
    public static final String BTN_MODE_DOWN = "mode_down";
    public static final String BTN_MUSIC = "music";
    public static final String BTN_IC_SET = "ic_set";
    public static final String BTN_IC_PLUS = "ic_plus";
    public static final String BTN_IC_MINUS = "ic_minus";

    // 9 Colori
    public static final String BTN_COLOR_R = "color_r";
    public static final String BTN_COLOR_G = "color_g";
    public static final String BTN_COLOR_B = "color_b";
    public static final String BTN_COLOR_ORANGE = "color_orange";
    public static final String BTN_COLOR_MINT = "color_mint";
    public static final String BTN_COLOR_INDIGO = "color_indigo";
    public static final String BTN_COLOR_YELLOW = "color_yellow";
    public static final String BTN_COLOR_CYAN = "color_cyan";
    public static final String BTN_COLOR_PURPLE = "color_purple";

    public static final String DEFAULT_CLIENT_ID = "se44kduxj4egf5rnsts3";
    public static final String DEFAULT_SECRET = "81152d6ed95943ce89568630f6fee2db";
    public static final String DEFAULT_DEVICE_ID = "bfd6492033f7bc99770az4";
    public static final String DEFAULT_REMOTE_ID = "bf970d329d9f1ec596tkhn";
    public static final String DEFAULT_REGION = "eu";

    private final SharedPreferences prefs;

    public AmbientConfig(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // Migrazione automatica: sovrascrive le vecchie preferenze vuote o la modalità demo
        if (prefs.getInt("config_ver", 0) < 6 || prefs.getString(KEY_CLIENT_ID, "").isEmpty()) {
            prefs.edit()
                    .putString(KEY_CLIENT_ID, DEFAULT_CLIENT_ID)
                    .putString(KEY_SECRET, DEFAULT_SECRET)
                    .putString(KEY_DEVICE_ID, DEFAULT_DEVICE_ID)
                    .putString(KEY_REMOTE_ID, DEFAULT_REMOTE_ID)
                    .putString(KEY_REGION, DEFAULT_REGION)
                    .putBoolean(KEY_DEMO_MODE, false)
                    .putInt("config_ver", 6)
                    .putString(KEY_MAP_PREFIX + BTN_POWER, "1793134114025")
                    .putString(KEY_MAP_PREFIX + BTN_BRIGHT_UP, "1793134137302")
                    .putString(KEY_MAP_PREFIX + BTN_BRIGHT_DOWN, "1793134154399")
                    .putString(KEY_MAP_PREFIX + BTN_SPEED_UP, "1793134165630")
                    .putString(KEY_MAP_PREFIX + BTN_SPEED_DOWN, "1793134180389")
                    .putString(KEY_MAP_PREFIX + BTN_MODE_UP, "1793134207636")
                    .putString(KEY_MAP_PREFIX + BTN_MODE_DOWN, "1793134218493")
                    .putString(KEY_MAP_PREFIX + BTN_IC_SET, "1793134235176")
                    .putString(KEY_MAP_PREFIX + BTN_IC_PLUS, "1793134245984")
                    .putString(KEY_MAP_PREFIX + BTN_IC_MINUS, "1793134253791")
                    .putString(KEY_MAP_PREFIX + BTN_MUSIC, "1793134264487")
                    .putString(KEY_MAP_PREFIX + BTN_COLOR_R, "1793134280706")
                    .putString(KEY_MAP_PREFIX + BTN_COLOR_G, "1793134286500")
                    .putString(KEY_MAP_PREFIX + BTN_COLOR_B, "1793134291547")
                    .putString(KEY_MAP_PREFIX + BTN_COLOR_ORANGE, "1793134299962")
                    .putString(KEY_MAP_PREFIX + BTN_COLOR_MINT, "1793134308235")
                    .putString(KEY_MAP_PREFIX + BTN_COLOR_INDIGO, "1793134313870")
                    .putString(KEY_MAP_PREFIX + BTN_COLOR_YELLOW, "1793134324949")
                    .putString(KEY_MAP_PREFIX + BTN_COLOR_CYAN, "1793134336186")
                    .putString(KEY_MAP_PREFIX + BTN_COLOR_PURPLE, "1793134342016")
                    .apply();
        }
    }

    public String getClientId() {
        String val = prefs.getString(KEY_CLIENT_ID, DEFAULT_CLIENT_ID);
        return (val == null || val.trim().isEmpty()) ? DEFAULT_CLIENT_ID : val.trim();
    }
    public void setClientId(String val) { prefs.edit().putString(KEY_CLIENT_ID, val.trim()).apply(); }

    public String getSecret() {
        String val = prefs.getString(KEY_SECRET, DEFAULT_SECRET);
        return (val == null || val.trim().isEmpty()) ? DEFAULT_SECRET : val.trim();
    }
    public void setSecret(String val) { prefs.edit().putString(KEY_SECRET, val.trim()).apply(); }

    public String getDeviceId() {
        String val = prefs.getString(KEY_DEVICE_ID, DEFAULT_DEVICE_ID);
        return (val == null || val.trim().isEmpty()) ? DEFAULT_DEVICE_ID : val.trim();
    }
    public void setDeviceId(String val) { prefs.edit().putString(KEY_DEVICE_ID, val.trim()).apply(); }

    public String getRemoteId() {
        String val = prefs.getString(KEY_REMOTE_ID, DEFAULT_REMOTE_ID);
        return (val == null || val.trim().isEmpty()) ? DEFAULT_REMOTE_ID : val.trim();
    }
    public void setRemoteId(String val) { prefs.edit().putString(KEY_REMOTE_ID, val.trim()).apply(); }

    public String getRegion() {
        String val = prefs.getString(KEY_REGION, DEFAULT_REGION);
        return (val == null || val.trim().isEmpty()) ? DEFAULT_REGION : val.trim().toLowerCase();
    }
    public void setRegion(String val) { prefs.edit().putString(KEY_REGION, val.trim().toLowerCase()).apply(); }

    public boolean isDemoMode() {
        return prefs.getBoolean(KEY_DEMO_MODE, false);
    }
    public void setDemoMode(boolean val) { prefs.edit().putBoolean(KEY_DEMO_MODE, val).apply(); }

    public int getLastColor() { return prefs.getInt("last_color", Color.parseColor("#00E5FF")); }
    public void setLastColor(int color) { prefs.edit().putInt("last_color", color).apply(); }

    public boolean getLastPowerState() { return prefs.getBoolean("last_power", true); }
    public void setLastPowerState(boolean power) { prefs.edit().putBoolean("last_power", power).apply(); }

    public int getLastBrightness() { return prefs.getInt("last_brightness", 80); }
    public void setLastBrightness(int brightness) { prefs.edit().putInt("last_brightness", brightness).apply(); }

    public boolean isLastMusicMode() { return prefs.getBoolean("last_music_mode", false); }
    public void setLastMusicMode(boolean val) { prefs.edit().putBoolean("last_music_mode", val).apply(); }

    public String getKeyMapping(String actionKey, String defaultTuyaKey) {
        String preconfiguredDefault = defaultTuyaKey;
        switch (actionKey) {
            case BTN_POWER: preconfiguredDefault = "1793134114025"; break;
            case BTN_BRIGHT_UP: preconfiguredDefault = "1793134137302"; break;
            case BTN_BRIGHT_DOWN: preconfiguredDefault = "1793134154399"; break;
            case BTN_SPEED_UP: preconfiguredDefault = "1793134165630"; break;
            case BTN_SPEED_DOWN: preconfiguredDefault = "1793134180389"; break;
            case BTN_MODE_UP: preconfiguredDefault = "1793134207636"; break;
            case BTN_MODE_DOWN: preconfiguredDefault = "1793134218493"; break;
            case BTN_IC_SET: preconfiguredDefault = "1793134235176"; break;
            case BTN_IC_PLUS: preconfiguredDefault = "1793134245984"; break;
            case BTN_IC_MINUS: preconfiguredDefault = "1793134253791"; break;
            case BTN_MUSIC: preconfiguredDefault = "1793134264487"; break;
            case BTN_COLOR_R: preconfiguredDefault = "1793134280706"; break;
            case BTN_COLOR_G: preconfiguredDefault = "1793134286500"; break;
            case BTN_COLOR_B: preconfiguredDefault = "1793134291547"; break;
            case BTN_COLOR_ORANGE: preconfiguredDefault = "1793134299962"; break;
            case BTN_COLOR_MINT: preconfiguredDefault = "1793134308235"; break;
            case BTN_COLOR_INDIGO: preconfiguredDefault = "1793134313870"; break;
            case BTN_COLOR_YELLOW: preconfiguredDefault = "1793134324949"; break;
            case BTN_COLOR_CYAN: preconfiguredDefault = "1793134336186"; break;
            case BTN_COLOR_PURPLE: preconfiguredDefault = "1793134342016"; break;
        }

        String saved = prefs.getString(KEY_MAP_PREFIX + actionKey, preconfiguredDefault);
        // Se il valore salvato è un vecchio testo finto (es. "power", "red"), usa il codice ID reale
        if (saved == null || saved.equals("power") || saved.equals("red") || saved.equals("green")
                || saved.equals("blue") || saved.equals("bright+") || saved.equals("bright-")
                || saved.equals("speed+") || saved.equals("speed-") || saved.equals("music")
                || saved.equals("mode+") || saved.equals("mode-") || saved.equals("fade") || saved.equals("strobe")) {
            return preconfiguredDefault;
        }
        return saved;
    }

    public void setKeyMapping(String actionKey, String tuyaKey) {
        prefs.edit().putString(KEY_MAP_PREFIX + actionKey, tuyaKey.trim()).apply();
    }
}
