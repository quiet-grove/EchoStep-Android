package com.echostep.app.audio;

import android.content.Context;

import com.echostep.app.data.AppConfig;
import com.echostep.app.data.ListeningTest;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.List;

/** Offline fallback: ncnn Piper CPU synthesis, concatenated into one WAV. */
public final class LocalPiperTts {
    private static final int SAMPLE_RATE = 22050;

    private LocalPiperTts() {
    }

    public static File synthesize(Context context, ListeningTest test, AppConfig config, File output) throws Exception {
        File dir = PiperModelManager.directory(context);
        if (!PiperModelManager.isInstalled(context)) {
            throw new IllegalStateException("本地语音模型还没有下载");
        }
        if (!PiperNcnn.isLoaded() && !PiperNcnn.loadModelDir(dir.getAbsolutePath())) {
            throw new IllegalStateException("本地 Piper 模型加载失败");
        }
        File tempDir = new File(context.getCacheDir(), "piper_segments");
        if (!tempDir.exists()) tempDir.mkdirs();
        File raw = new File(tempDir, "exam.pcm");
        try (OutputStream pcmOut = new BufferedOutputStream(new FileOutputStream(raw))) {
            List<ExamAudioTextBuilder.SpeechSegment> segments = ExamAudioTextBuilder.segments(test, config);
            double lengthScale = 1.0 / Math.max(0.5, Math.min(1.5, config.speed));
            int index = 0;
            for (ExamAudioTextBuilder.SpeechSegment segment : segments) {
                int lang = "zh".equals(segment.lang) ? 1 : 0;
                for (int repeat = 0; repeat < segment.repeats; repeat++) {
                    File wav = new File(tempDir, "seg_" + (index++) + ".wav");
                    boolean ok = PiperNcnn.synthesizeToFile(segment.text, lang, 0, lengthScale, wav.getAbsolutePath());
                    if (!ok) throw new IllegalStateException("本地语音合成失败");
                    copyPcm(wav, pcmOut);
                    wav.delete();
                    appendSilence(pcmOut, repeat < segment.repeats - 1 ? 700 : segment.pauseMs);
                }
            }
        }
        writeWav(raw, output);
        raw.delete();
        return output;
    }

    private static void copyPcm(File wav, OutputStream output) throws Exception {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(wav)))) {
            byte[] header = new byte[44];
            input.readFully(header);
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
    }

    private static void appendSilence(OutputStream output, int millis) throws Exception {
        int bytes = SAMPLE_RATE * Math.max(0, millis) / 1000 * 2;
        byte[] zeros = new byte[Math.min(8192, Math.max(2, bytes))];
        int remaining = bytes;
        while (remaining > 0) {
            int n = Math.min(zeros.length, remaining);
            output.write(zeros, 0, n);
            remaining -= n;
        }
    }

    private static void writeWav(File raw, File output) throws Exception {
        long dataSize = raw.length();
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(raw)));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(output))) {
            int byteRate = SAMPLE_RATE * 2;
            writeAscii(out, "RIFF");
            writeInt(out, (int) (36 + dataSize));
            writeAscii(out, "WAVEfmt ");
            writeInt(out, 16);
            writeShort(out, 1);
            writeShort(out, 1);
            writeInt(out, SAMPLE_RATE);
            writeInt(out, byteRate);
            writeShort(out, 2);
            writeShort(out, 16);
            writeAscii(out, "data");
            writeInt(out, (int) dataSize);
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) out.write(buffer, 0, read);
        }
    }

    private static void writeAscii(OutputStream out, String text) throws Exception {
        out.write(text.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static void writeInt(OutputStream out, int value) throws Exception {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    private static void writeShort(OutputStream out, int value) throws Exception {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }
}
