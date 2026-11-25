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
    public static final String KEY_LOCAL_KEY = "tuya_local_key";
    public static final String KEY_LAST_BLE_MAC = "tuya_last_ble_mac";
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
    public static final String DEFAULT_LOCAL_KEY = "6[L|qWR5-B-|R~z/";
    public static final String DEFAULT_REGION = "eu";

    private final SharedPreferences prefs;

    public AmbientConfig(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // Migrazione automatica: sovrascrive le vecchie preferenze vuote o la modalità demo
        if (prefs.getInt("config_ver", 0) < 7 || prefs.getString(KEY_CLIENT_ID, "").isEmpty() || !prefs.contains(KEY_LOCAL_KEY)) {
            prefs.edit()
                    .putString(KEY_CLIENT_ID, DEFAULT_CLIENT_ID)
                    .putString(KEY_SECRET, DEFAULT_SECRET)
                    .putString(KEY_DEVICE_ID, DEFAULT_DEVICE_ID)
                    .putString(KEY_REMOTE_ID, DEFAULT_REMOTE_ID)
                    .putString(KEY_LOCAL_KEY, DEFAULT_LOCAL_KEY)
                    .putString(KEY_REGION, DEFAULT_REGION)
                    .putBoolean(KEY_DEMO_MODE, false)
                    .putInt("config_ver", 7)
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

    public String getLocalKey() {
        String val = prefs.getString(KEY_LOCAL_KEY, DEFAULT_LOCAL_KEY);
        return (val == null || val.trim().isEmpty()) ? DEFAULT_LOCAL_KEY : val.trim();
    }
    public void setLocalKey(String val) { prefs.edit().putString(KEY_LOCAL_KEY, val.trim()).apply(); }

    public String getLastBleMac() {
        return prefs.getString(KEY_LAST_BLE_MAC, null);
    }
    public void setLastBleMac(String val) {
        if (val == null) {
            prefs.edit().remove(KEY_LAST_BLE_MAC).apply();
        } else {
            prefs.edit().putString(KEY_LAST_BLE_MAC, val.trim()).apply();
        }
    }

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

    public static String getRawIrBase64(String actionKey) {
        switch (actionKey) {
            case BTN_POWER:
                return "KyNVEXACNgJwAoMGNgI2AnACNgI2AjYCNgKDBjYCgwY2AoMGNgKDBjYCgwY2AjYCNgKDBjYCgwY2AoMGNgKDBjYCgwY2AjYCNgKDBjYCgwY2AjYCNgI2AjYCgwY2AjYCNgI2AjYCgwY2AjYCcAI2AnACgwY2AoMGNgI2AnACgwY2AoMGNgJLjisjsAg2AvN3KyOwCDYCMPIrI7AIcALzdysjsAhwAvN3KyOwCHAC83c=";
            case BTN_BRIGHT_UP:
                return "NCNwETICMgIyAokGMgIyAnQCMgJ0AjICMgKJBjICiQYyAokGMgKJBjICiQYyAjICMgKJBjICiQYyAokGMgKJBjICiQYyAokGMgIyAnQCiQYyAokGMgIyAjICiQYyAjICMgIyAjICMgIyAokGMgIyAnQCMgJ0AokGMgIyAnQCiQYyAokGMgJJjjQjqggyAjDyNCOqCDICMPI0I6oIdALudjQjqgh0AjDyNCOqCHQC7nY=";
            case BTN_BRIGHT_DOWN:
                return "OSMxEXcC3gF3Al8GdwLeAXcC3gF3At4BdwJfBncCXwZ3Al8GdwJfBncCXwZ3At4BdwJfBncCXwZ3Al8GdwJfBncCXwZ3Al8GdwLeAXcCXwZ3At4BdwLeAXcCXwZ3At4BdwLeAXcC3gF3Al8GdwLeAXcCXwZ3Al8GdwLeAXcCXwZ3Al8GdwJsjTkjmwh3AjDyOSObCHcCMHU=";
            case BTN_SPEED_UP:
                return "TiMxEYQCqAHuAlMGhALVAYQC1QGEAtUBhAJTBoQCUwaEAlMGhAJTBoQCUwaEAtUBhAJTBoQCUwaEAlMGhAJTBoQCUwaEAtUBhAJTBoQC1QGEAlMGhAKoAYQCqAGEAqgBhAKoAYQCUwaEAtUBhAJTBoQC1QGEAlMGhAJTBoQCUwaEAlMGhAJPjU4jgQiEAjDyTiOBCIQCc3ZOI4EIhAJzdg==";
            case BTN_SPEED_DOWN:
                return "KyNTEXMC7AFzAlwGcwLsAXMC7AFzAuwBcwJcBnMCXAZzAlwGcwJcBnMCXAZzAuwBcwJcBnMCXAZzAlwGcwJcBnMCXAZzAuwBcwLsAXMC7AFzAlwGcwLsAXMC7AFzAuwBcwLsAXMCXAZzAlwGcwJcBnMC7AFzAlwGcwJcBnMCXAZzAlwGcwJEjSsjjAhzAjB1";
            case BTN_MODE_UP:
                return "HiNSEW0C9gFtAmEGbQL2AW0C9gFtAvYBbQJhBm0CYQZtAmEGbQJhBm0CYQZtAvYBbQJhBm0CYQZtAmEGbQJhBm0CYQZtAvYBbQJhBm0CYQZtAmEGbQL2AW0CYQZtAmEGbQL2AW0CYQZtAvYBbQL2AW0C9gFtAmEGbQL2AW0C9gFtAmEGbQJYjR4jiwhtAjB1";
            case BTN_MODE_DOWN:
                return "CyNUEXkC3AF5AlkGeQLcAXkC3AF5AtwBeQJZBnkCWQZ5AlkGeQJZBnkCWQZ5AtwBeQJZBnkCWQZ5AlkGeQJZBnkCWQZ5AlkGeQJZBnkC3AF5AlkGeQJZBnkC3AF5AlkGeQLcAXkC3AF5AtwBeQJZBnkC3AF5AtwBeQJZBnkC3AF5AlkGeQJXjQsjygh5AjB1";
            case BTN_IC_SET:
                return "GSNQEWcC7gFnAm8GZwLuAWcC7gFnAu4BZwJvBmcCbwZnAm8GZwJvBmcCbwZnAu4BZwJvBmcCbwZnAm8GZwJvBmcCbwZnAu4BZwJvBmcC7gFnAm8GZwLuAWcC7gFnAu4BZwJvBmcCbwZnAu4BZwJvBmcC7gFnAm8GZwJvBmcCbwZnAu4BZwJ0jRkjjAhnAjB1";
            case BTN_IC_PLUS:
                return "OyMxEYsCwwGLAlAGiwLDAYsCwwGLAsMBiwJQBosCUAaLAlAGiwJQBosCUAaLAsMBiwJQBosCUAaLAlAGiwJQBosCUAaLAsMBiwLDAYsCwwGLAsMBiwJQBosCwwGLAsMBiwLDAYsCUAaLAlAGiwJQBosCUAaLAsMBiwJQBosCUAaLAlAGiwK9gDsjjAiLAr2A";
            case BTN_IC_MINUS:
                return "9CJwETICMgIyApMGMgIyAjICMgJwAjICcAKTBjICkwYyApMGMgKTBjICkwYyAjICMgKTBjICkwYyApMGMgKTBjICkwYyApMGMgIyAjICMgIyApMGMgIyAjICMgIyAjICMgIyAjICMgIyApMGMgKTBjICMgIyApMGMgKTBjICkwYyApMGMgKKjfQiyAgyAjB1";
            case BTN_MUSIC:
                return "ESNzES4CLgIuApEGLgIuAi4CLgIuAi4CcQKRBi4CkQYuApEGLgKRBi4CkQYuAi4CLgKRBi4CkQYuApEGLgKRBi4CkQYuAi4CLgIuAi4CkQYuApEGLgKRBi4CLgIuApEGLgIuAnECkQYuApEGLgIuAnECLgJxAi4CcQKRBi4CLgJxApEGLgJrjREjrAguAjB1";
            case BTN_COLOR_R:
                return "HiNTEXQC3gF0AmEGdALeAXQC3gF0At4BdAJhBnQCYQZ0AmEGdAJhBnQCYQZ0At4BdAJhBnQCYQZ0AmEGdAJhBnQCYQZ0At4BdALeAXQCYQZ0AmEGdAJhBnQC3gF0At4BdALeAXQCYQZ0AmEGdALeAXQC3gF0At4BdAJhBnQCYQZ0AmEGdALMgB4jqwh0AsyA";
            case BTN_COLOR_G:
                return "3CKQES0CLQItAqUGLQItAi0CLQItAi0CLQKlBi0CpQYtAqUGLQKlBi0CpQYtAi0CLQKlBi0CpQYtAqUGLQKlBi0CpQYtAi0CLQKlBi0CLQItAi0CLQKlBi0CLQItAqUGLQItAi0CpQYtAi0CLQKlBi0CpQYtAi0CLQKlBi0CLQItAqUGLQIzjdwi6AgtAjB1";
            case BTN_COLOR_B:
                return "1CJwETACMAIwApEGMAIwAjACMAJwAjACMAKRBjACkQYwApEGMAKRBjACkQYwAjACMAKRBjACkQYwApEGMAKRBjACkQYwApEGMAKRBjACMAJwAjACcAKRBjACMAJwApEGMAIwAjACMAIwAjACMAKRBjACkQYwAjACMAKRBjACMAIwApEGMAIwdQ==";
            case BTN_COLOR_ORANGE:
                return "DCNyETECMQIxApIGMQIxAjECMQIxAjECMQKSBjECkgYxApwAewiSBjECkgYxAjECMQKSBjECkgYxApIGMQKSBjECkgYxAjECMQIxAjECkgYxApIGMQIxAnECMQJxAjECcQIxAjECkgYxApIGMQIxAjECMQIxApIGMQKSBjECkgYxApIGMQJIjgwjewgxAjB1";
            case BTN_COLOR_MINT:
                return "zSJzETUCNQI1AooGNQI1AjUCNQI1AjUCNQKKBjUCigY1AooGNQKKBjUCigY1AjUCcQKKBjUCigY1AooGNQKKBjUCigY1AjUCcQKKBjUCNQJxAooGNQKKBjUCNQI1AjUCNQI1AjUCigY1AjUCNQKKBjUCNQI1AjUCNQKKBjUCigY1AooGNQIwdQ==";
            case BTN_COLOR_INDIGO:
                return "EyNREXEC8wFxAlgGcQLzAXEC8wFxAvMBcQJYBnECWAZxAlgGcQJYBnECWAZxAvMBcQJYBnECWAZxAlgGcQJYBnECWAZxAlgGcQJYBnECWAZxAlgGcQJYBnEC8wFxAvMBcQLzAXEC8wFxAvMBcQLzAXEC8wFxAvMBcQJYBnECWAZxAlgGcQJNjhMjiwhxAjB1";
            case BTN_COLOR_YELLOW:
                return "CSNSEXACOQI5AoMGOQI5AjkCOQI5AjkCOQKDBjkCgwY5AoMGOQKDBjkCgwY5AjkCcAKDBjkCgwY5AoMGOQKDBjkCgwY5AoMGOQI5AnACOQI5AjkCOQI5AjkCOQI5AjkCOQI5AjkCOQI5AjkCOQI5AoMGOQKDBjkCgwY5AoMGOQKDBjkCgwY5AoMGOQKPjQkjqghwAjB1";
            case BTN_COLOR_CYAN:
                return "ICNUEf0B/QFxAlkGcQL9AXEC/QFxAv0BcQJZBnECWQZxAlkGcQJZBnECWQZxAv0BcQJZBnECWQZxAlkGcQJZBnECWQZxAv0BcQL9AXEC/QFxAv0BcQL9AXEC/QFxAv0BcQL9AXECWQZxAlkGcQJZBnECWQZxAlkGcQJZBnECWQZxAlkGcQJ1jSAjqwhxAjB1";
            case BTN_COLOR_PURPLE:
                return "+yJwEWICBAJiAmoGYgIEAmICBAJiAgQCYgJqBmICagZiAmoGYgJqBmICagZiAgQCYgJqBmICagZiAmoGYgJqBmICagZiAgQCYgJqBmICBAJiAgQCYgIEAmICBAJiAgQCYgIEAmICagZiAgQCYgJqBmICagZiAmoGYgJqBmICagZiAmoGYgJsjfsiqghiAjB1";
            default:
                return null;
        }
    }

    public void setKeyMapping(String actionKey, String tuyaKey) {
        prefs.edit().putString(KEY_MAP_PREFIX + actionKey, tuyaKey.trim()).apply();
    }
}
