package com.echostep.app.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppConfig {
    private static final String PREFS = "echostep";
    private final SharedPreferences preferences;

    public String apiKey;
    public String baseUrl;
    public String model;
    public boolean webSearch;
    public boolean readChinese;
    public boolean readOptions;
    public boolean soundcheck;
    public boolean modelPromptShown;
    public int repeat;
    public float speed;
    public String grade;
    public String book;
    public int unitIndex;
    public int count;
    public String lastTestId;

    private AppConfig(SharedPreferences preferences) {
        this.preferences = preferences;
        apiKey = preferences.getString("api_key", "");
        baseUrl = preferences.getString("base_url", "https://api.deepseek.com");
        model = preferences.getString("model", "deepseek-flash");
        webSearch = preferences.getBoolean("web_search", true);
        readChinese = preferences.getBoolean("read_chinese", true);
        readOptions = preferences.getBoolean("read_options", false);
        soundcheck = preferences.getBoolean("soundcheck", true);
        modelPromptShown = preferences.getBoolean("model_prompt_shown", false);
        repeat = preferences.getInt("repeat", 1);
        speed = preferences.getFloat("speed", 1.0f);
        grade = preferences.getString("grade", "八年级");
        book = preferences.getString("book", "上册");
        unitIndex = preferences.getInt("unit_index", 0);
        count = preferences.getInt("count", 30);
        lastTestId = preferences.getString("last_test_id", "");
    }

    public static AppConfig load(Context context) {
        return new AppConfig(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE));
    }

    public void save() {
        preferences.edit()
                .putString("api_key", apiKey == null ? "" : apiKey.trim())
                .putString("base_url", normalizeBase(baseUrl))
                .putString("model", model)
                .putBoolean("web_search", webSearch)
                .putBoolean("read_chinese", readChinese)
                .putBoolean("read_options", readOptions)
                .putBoolean("soundcheck", soundcheck)
                .putBoolean("model_prompt_shown", modelPromptShown)
                .putInt("repeat", repeat)
                .putFloat("speed", speed)
                .putString("grade", grade)
                .putString("book", book)
                .putInt("unit_index", unitIndex)
                .putInt("count", count)
                .putString("last_test_id", lastTestId)
                .apply();
    }

    public boolean hasKey() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    public String normalizedBase() {
        return normalizeBase(baseUrl);
    }

    public static String normalizeBase(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result.isEmpty() ? "https://api.deepseek.com" : result;
    }

    public String maskKey() {
        if (!hasKey()) {
            return "";
        }
        String key = apiKey.trim();
        if (key.length() <= 10) {
            return key.substring(0, 2) + "••" + key.substring(key.length() - 2);
        }
        return key.substring(0, 5) + "••••••" + key.substring(key.length() - 4);
    }
}
