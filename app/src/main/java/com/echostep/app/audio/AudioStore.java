package com.echostep.app.audio;

import android.content.Context;

import com.echostep.app.data.AppConfig;
import com.echostep.app.data.ListeningTest;

import java.io.File;

public final class AudioStore {
    private AudioStore() {
    }

    public static File directory(Context context) {
        File dir = new File(context.getCacheDir(), "exam_audio");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File fileFor(Context context, ListeningTest test, AppConfig config) {
        String key = test.id + "_" + settingsKey(config);
        return new File(directory(context), key + ".mp3");
    }

    public static File localFileFor(Context context, ListeningTest test, AppConfig config) {
        String key = test.id + "_" + settingsKey(config);
        return new File(directory(context), key + "_local.wav");
    }

    private static String settingsKey(AppConfig config) {
        String raw = String.format(java.util.Locale.US, "%d_%.2f_%d_%d_%d_%d",
                config.repeat, config.speed, config.readChinese ? 1 : 0,
                config.readOptions ? 1 : 0, config.soundcheck ? 1 : 0, 5);
        return Integer.toHexString(raw.hashCode());
    }

    public static void clear(Context context) {
        File[] files = directory(context).listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isFile()) file.delete();
        }
    }
}
