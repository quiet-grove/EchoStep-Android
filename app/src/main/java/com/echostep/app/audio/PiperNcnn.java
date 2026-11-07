package com.echostep.app.audio;

/** JNI bridge to ncnn-android-piper (CPU inference). */
public final class PiperNcnn {
    static {
        System.loadLibrary("piperncnn");
    }

    private PiperNcnn() {
    }

    /** Loads both en and zh ncnn models from a directory containing the downloaded files. */
    public static native boolean loadModelDir(String modelDir);

    /** langId: 0 = English, 1 = Chinese. Writes a 22.05 kHz mono 16-bit WAV. */
    public static native boolean synthesizeToFile(String text, int langId, int speakerId,
                                                  double lengthScale, String outPath);

    public static native boolean isLoaded();
}
