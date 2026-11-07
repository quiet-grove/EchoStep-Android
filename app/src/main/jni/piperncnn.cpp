#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <cstdio>
#include <cstdint>
#include <vector>

#include <platform.h>

#include "piper.h"

#define LOG_TAG "PiperNcnn"

static Piper* g_en = nullptr;
static Piper* g_zh = nullptr;
static ncnn::Mutex g_lock;

static bool write_wav(const char* path, const std::vector<short>& pcm, int sample_rate) {
    FILE* fp = fopen(path, "wb");
    if (!fp) return false;
    const uint32_t data_size = (uint32_t)(pcm.size() * 2);
    const uint32_t riff_size = 36 + data_size;
    const uint16_t channels = 1;
    const uint16_t bits = 16;
    const uint32_t byte_rate = sample_rate * channels * bits / 8;
    const uint16_t block_align = channels * bits / 8;
    fwrite("RIFF", 1, 4, fp);
    fwrite(&riff_size, 4, 1, fp);
    fwrite("WAVEfmt ", 1, 8, fp);
    uint32_t fmt_size = 16;
    uint16_t audio_format = 1;
    fwrite(&fmt_size, 4, 1, fp);
    fwrite(&audio_format, 2, 1, fp);
    fwrite(&channels, 2, 1, fp);
    fwrite(&sample_rate, 4, 1, fp);
    fwrite(&byte_rate, 4, 1, fp);
    fwrite(&block_align, 2, 1, fp);
    fwrite(&bits, 2, 1, fp);
    fwrite("data", 1, 4, fp);
    fwrite(&data_size, 4, 1, fp);
    if (data_size) fwrite(pcm.data(), 1, data_size, fp);
    fclose(fp);
    return true;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_echostep_app_audio_PiperNcnn_loadModelDir(JNIEnv* env, jobject, jstring dir) {
    const char* c_dir = env->GetStringUTFChars(dir, nullptr);
    if (!c_dir) return JNI_FALSE;
    bool ok = false;
    {
        ncnn::MutexLockGuard guard(g_lock);
        if (chdir(c_dir) == 0) {
            delete g_en;
            delete g_zh;
            g_en = new Piper;
            g_zh = new Piper;
            int en = g_en->load("en", false);
            int zh = g_zh->load("zh", false);
            ok = en == 0 && zh == 0;
            __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "load en=%d zh=%d", en, zh);
        } else {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "chdir failed: %s", c_dir);
        }
    }
    env->ReleaseStringUTFChars(dir, c_dir);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_echostep_app_audio_PiperNcnn_synthesizeToFile(JNIEnv* env, jobject, jstring text,
                                                       jint langid, jint speakerid,
                                                       jdouble length_scale, jstring out_path) {
    if (!text || !out_path) return JNI_FALSE;
    const char* c_text = env->GetStringUTFChars(text, nullptr);
    const char* c_path = env->GetStringUTFChars(out_path, nullptr);
    bool ok = false;
    {
        ncnn::MutexLockGuard guard(g_lock);
        Piper* piper = langid == 1 ? g_zh : g_en;
        if (piper && c_text && c_path) {
            std::vector<short> pcm;
            float noise_scale = langid == 1 ? 0.667f : 0.333f;
            float noise_scale_w = langid == 1 ? 0.8f : 0.333f;
            piper->synthesize(c_text, (int)speakerid, noise_scale,
                              (float)length_scale, noise_scale_w, pcm);
            if (!pcm.empty()) ok = write_wav(c_path, pcm, 22050);
        }
    }
    if (c_text) env->ReleaseStringUTFChars(text, c_text);
    if (c_path) env->ReleaseStringUTFChars(out_path, c_path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_echostep_app_audio_PiperNcnn_isLoaded(JNIEnv*, jobject) {
    ncnn::MutexLockGuard guard(g_lock);
    return (g_en && g_zh) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
