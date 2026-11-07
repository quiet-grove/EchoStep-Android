package com.echostep.app.audio;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads ncnn Piper models from hf-mirror into app-private storage.
 * Word dictionaries are small and ship in assets; the neural models are opt-in.
 */
public final class PiperModelManager {
    public static final String EN_URL = "https://hf-mirror.com/gyroing/PiperTTS-NCNN-Models/resolve/main/en_US-girl.zip";
    public static final String ZH_URL = "https://hf-mirror.com/gyroing/PiperTTS-NCNN-Models/resolve/main/zh_CN-huayan.zip";

    private static final String[] REQUIRED = {
            "en_dec.ncnn.param", "en_dec.ncnn.bin",
            "en_dp.ncnn.param", "en_dp.ncnn.bin",
            "en_flow.ncnn.param", "en_flow.ncnn.bin",
            "en_enc_p.ncnn.param", "en_enc_p.ncnn.bin",
            "en_emb_g.ncnn.param", "en_emb_g.ncnn.bin",
            "zh_dec.ncnn.param", "zh_dec.ncnn.bin",
            "zh_dp.ncnn.param", "zh_dp.ncnn.bin",
            "zh_flow.ncnn.param", "zh_flow.ncnn.bin",
            "zh_enc_p.ncnn.param", "zh_enc_p.ncnn.bin",
            "en-word_id.bin", "zh-word_id.bin"
    };

    public interface Progress {
        void onProgress(String message, int percent);
    }

    private PiperModelManager() {
    }

    public static File directory(Context context) {
        File dir = new File(context.getFilesDir(), "piper");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static boolean isInstalled(Context context) {
        File dir = directory(context);
        for (String name : REQUIRED) {
            File file = new File(dir, name);
            if (!file.isFile() || file.length() < 1024) return false;
        }
        return true;
    }

    public static long installedBytes(Context context) {
        long total = 0;
        File[] files = directory(context).listFiles();
        if (files != null) {
            for (File file : files) if (file.isFile()) total += file.length();
        }
        return total;
    }

    public static void delete(Context context) {
        File[] files = directory(context).listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isFile()) file.delete();
        }
    }

    public static void download(Context context, Progress progress) throws Exception {
        File dir = directory(context);
        List<File> tempFiles = new ArrayList<>();
        try {
            progress.onProgress("正在下载英文模型…", 1);
            File enZip = new File(context.getCacheDir(), "piper-en.zip");
            tempFiles.add(enZip);
            downloadFile(EN_URL, enZip, (pct) -> progress.onProgress("正在下载英文模型…", 1 + pct / 2));
            progress.onProgress("正在解压英文模型…", 52);
            unzipModels(enZip, dir, "en_");

            progress.onProgress("正在下载中文模型…", 53);
            File zhZip = new File(context.getCacheDir(), "piper-zh.zip");
            tempFiles.add(zhZip);
            downloadFile(ZH_URL, zhZip, (pct) -> progress.onProgress("正在下载中文模型…", 53 + pct / 2));
            progress.onProgress("正在解压中文模型…", 98);
            unzipModels(zhZip, dir, "zh_");

            copyAsset(context.getAssets(), "piper/en-word_id.bin", new File(dir, "en-word_id.bin"));
            copyAsset(context.getAssets(), "piper/zh-word_id.bin", new File(dir, "zh-word_id.bin"));
            if (!isInstalled(context)) {
                throw new IllegalStateException("模型文件不完整，请重试");
            }
            progress.onProgress("本地语音模型已就绪", 100);
        } catch (Exception error) {
            delete(context);
            throw error;
        } finally {
            for (File file : tempFiles) file.delete();
        }
    }

    private interface ByteProgress {
        void onProgress(int percent);
    }

    private static void downloadFile(String urlText, File target, ByteProgress progress) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(120000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "EchoStep-Android/1.0");
        connection.connect();
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("下载失败（HTTP " + code + "）");
        }
        long total = connection.getContentLengthLong();
        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
            byte[] buffer = new byte[64 * 1024];
            long done = 0;
            int read;
            int lastPercent = -1;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                done += read;
                if (total > 0) {
                    int percent = (int) Math.min(100, done * 100 / total);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        progress.onProgress(percent);
                    }
                }
            }
        } finally {
            connection.disconnect();
        }
        if (target.length() < 1024 * 1024) {
            throw new IllegalStateException("模型文件下载不完整");
        }
    }

    private static void unzipModels(File zipFile, File dir, String prefix) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new java.io.FileInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = new File(entry.getName()).getName();
                if (!entry.isDirectory() && name.startsWith(prefix) && name.contains(".ncnn.")) {
                    File target = new File(dir, name);
                    try (OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
                        byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = zip.read(buffer)) != -1) output.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static void copyAsset(AssetManager assets, String assetName, File target) throws Exception {
        try (InputStream input = new BufferedInputStream(assets.open(assetName));
             OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
    }
}
