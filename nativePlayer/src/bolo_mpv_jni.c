#include "bolo_mpv.h"
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#ifdef __ANDROID__
#include <libavcodec/jni.h>
#elif defined(_WIN32)
#include <windows.h>
#else
#include <dlfcn.h>
#endif

typedef struct {
    bolo_mpv *core;
    jobject surface, streams;
    jmethodID stream_open, stream_read, stream_seek, stream_size, stream_cancel, stream_close;
} java_player;
static JavaVM *java_vm;
#define PLAYER(value) ((java_player *)(intptr_t)(value))
#define JNI(name) Java_tv_hsrui_bolo_player_base_BoloMpvNative_##name

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    java_vm = vm;
#ifdef __ANDROID__
    if (av_jni_set_java_vm(vm, NULL) < 0) return JNI_ERR;
#endif
    return JNI_VERSION_1_6;
}

static JNIEnv *stream_env(int *attached) {
    JNIEnv *env = NULL;
    *attached = 0;
    jint result = (*java_vm)->GetEnv(java_vm, (void **)&env, JNI_VERSION_1_6);
    if (result == JNI_EDETACHED) {
        if ((*java_vm)->AttachCurrentThread(java_vm, (void **)&env, NULL) != JNI_OK) return NULL;
        *attached = 1;
    } else if (result != JNI_OK) return NULL;
    return env;
}
static int stream_exception(JNIEnv *env) {
    if (!(*env)->ExceptionCheck(env)) return 0;
    (*env)->ExceptionClear(env);
    return 1;
}
static int64_t java_stream_open(void *context, const char *uri) {
    java_player *p = context;
    int attached;
    JNIEnv *env = stream_env(&attached);
    if (!env) return 0;
    jstring text = (*env)->NewStringUTF(env, uri);
    jlong result = text ? (*env)->CallLongMethod(env, p->streams, p->stream_open, text) : 0;
    if (stream_exception(env)) result = 0;
    if (text) (*env)->DeleteLocalRef(env, text);
    if (attached) (*java_vm)->DetachCurrentThread(java_vm);
    return result;
}
static int64_t java_stream_read(void *context, int64_t id, char *buffer, uint64_t size) {
    java_player *p = context;
    int attached;
    JNIEnv *env = stream_env(&attached);
    if (!env) return -1;
    jsize capacity = (jsize)(size < 65536 ? size : 65536);
    jbyteArray bytes = (*env)->NewByteArray(env, capacity);
    jint result = bytes ? (*env)->CallIntMethod(env, p->streams, p->stream_read, (jlong)id, bytes) : -1;
    if (stream_exception(env) || result > capacity) result = -1;
    if (result > 0) (*env)->GetByteArrayRegion(env, bytes, 0, result, (jbyte *)buffer);
    if (stream_exception(env)) result = -1;
    if (bytes) (*env)->DeleteLocalRef(env, bytes);
    if (attached) (*java_vm)->DetachCurrentThread(java_vm);
    return result;
}
static int64_t java_stream_seek(void *context, int64_t id, int64_t offset) {
    java_player *p = context;
    int attached;
    JNIEnv *env = stream_env(&attached);
    if (!env) return -1;
    jlong result = (*env)->CallLongMethod(env, p->streams, p->stream_seek, (jlong)id, (jlong)offset);
    if (stream_exception(env)) result = -1;
    if (attached) (*java_vm)->DetachCurrentThread(java_vm);
    return result;
}
static int64_t java_stream_size(void *context, int64_t id) {
    java_player *p = context;
    int attached;
    JNIEnv *env = stream_env(&attached);
    if (!env) return -1;
    jlong result = (*env)->CallLongMethod(env, p->streams, p->stream_size, (jlong)id);
    if (stream_exception(env)) result = -1;
    if (attached) (*java_vm)->DetachCurrentThread(java_vm);
    return result;
}
static void java_stream_end(void *context, int64_t id, int close) {
    java_player *p = context;
    int attached;
    JNIEnv *env = stream_env(&attached);
    if (!env) return;
    (*env)->CallVoidMethod(env, p->streams, close ? p->stream_close : p->stream_cancel, (jlong)id);
    stream_exception(env);
    if (attached) (*java_vm)->DetachCurrentThread(java_vm);
}
static void java_stream_cancel(void *context, int64_t id) { java_stream_end(context, id, 0); }
static void java_stream_close(void *context, int64_t id) { java_stream_end(context, id, 1); }

JNIEXPORT jint JNICALL JNI(setStreamCallbacks)(JNIEnv *env, jobject self, jlong handle, jobject streams) {
    java_player *p = PLAYER(handle);
    if (p->streams || !streams) return -1;
    jclass type = (*env)->GetObjectClass(env, streams);
    if (!type) return -1;
    p->stream_open = (*env)->GetMethodID(env, type, "open", "(Ljava/lang/String;)J");
    p->stream_read = (*env)->GetMethodID(env, type, "read", "(J[B)I");
    p->stream_seek = (*env)->GetMethodID(env, type, "seek", "(JJ)J");
    p->stream_size = (*env)->GetMethodID(env, type, "size", "(J)J");
    p->stream_cancel = (*env)->GetMethodID(env, type, "cancel", "(J)V");
    p->stream_close = (*env)->GetMethodID(env, type, "close", "(J)V");
    (*env)->DeleteLocalRef(env, type);
    if (stream_exception(env) || !p->stream_open || !p->stream_read || !p->stream_seek ||
        !p->stream_size || !p->stream_cancel || !p->stream_close) return -1;
    p->streams = (*env)->NewGlobalRef(env, streams);
    if (!p->streams) return -1;
    const bolo_mpv_stream_callbacks callbacks = {.open = java_stream_open, .read = java_stream_read,
        .seek = java_stream_seek, .size = java_stream_size, .cancel = java_stream_cancel, .close = java_stream_close};
    int result = bolo_mpv_set_stream_callbacks(p->core, &callbacks, p);
    if (result < 0) { (*env)->DeleteGlobalRef(env, p->streams); p->streams = NULL; }
    return result;
}
JNIEXPORT jlong JNICALL JNI(create)(JNIEnv *env, jobject self, jstring platform) {
    const char *name = (*env)->GetStringUTFChars(env, platform, NULL);
    java_player *p = calloc(1, sizeof(*p));
    if (p) p->core = bolo_mpv_create(name);
    (*env)->ReleaseStringUTFChars(env, platform, name);
    if (p && !p->core) { free(p); p = NULL; }
    return (intptr_t)p;
}
JNIEXPORT jint JNICALL JNI(load)(JNIEnv *env, jobject self, jlong handle, jstring video, jstring audio, jdouble start, jlong generation, jstring user_agent, jstring referrer) {
    const char *v = (*env)->GetStringUTFChars(env, video, NULL);
    const char *a = audio ? (*env)->GetStringUTFChars(env, audio, NULL) : NULL;
    const char *ua = (*env)->GetStringUTFChars(env, user_agent, NULL);
    const char *ref = (*env)->GetStringUTFChars(env, referrer, NULL);
    int r = bolo_mpv_load(PLAYER(handle)->core, v, a, start, generation, ua, ref);
    (*env)->ReleaseStringUTFChars(env, user_agent, ua);
    (*env)->ReleaseStringUTFChars(env, referrer, ref);
    if (a) (*env)->ReleaseStringUTFChars(env, audio, a);
    (*env)->ReleaseStringUTFChars(env, video, v);
    return r;
}
JNIEXPORT jint JNICALL JNI(videoEnabled)(JNIEnv *env, jobject self, jlong handle, jboolean enabled) {
    return bolo_mpv_video_enabled(PLAYER(handle)->core, enabled);
}
JNIEXPORT jint JNICALL JNI(pause)(JNIEnv *env, jobject self, jlong p, jboolean value) { return bolo_mpv_pause(PLAYER(p)->core, value); }
JNIEXPORT jint JNICALL JNI(speed)(JNIEnv *env, jobject self, jlong p, jdouble value) { return bolo_mpv_speed(PLAYER(p)->core, value); }
JNIEXPORT jint JNICALL JNI(volume)(JNIEnv *env, jobject self, jlong p, jdouble value) { return bolo_mpv_volume(PLAYER(p)->core, value); }
JNIEXPORT jint JNICALL JNI(loudness)(JNIEnv *env, jobject self, jlong p, jdouble gain, jboolean dynamic, jdouble target, jdouble range, jdouble peak) { return bolo_mpv_loudness(PLAYER(p)->core, gain, dynamic, target, range, peak); }
JNIEXPORT jint JNICALL JNI(mergeAudioChannels)(JNIEnv *env, jobject self, jlong p, jboolean enabled) { return bolo_mpv_merge_audio_channels(PLAYER(p)->core, enabled); }
JNIEXPORT jint JNICALL JNI(seek)(JNIEnv *env, jobject self, jlong p, jdouble value, jlong request) { return bolo_mpv_seek(PLAYER(p)->core, value, request); }
JNIEXPORT jint JNICALL JNI(stop)(JNIEnv *env, jobject self, jlong p) { return bolo_mpv_stop(PLAYER(p)->core); }
JNIEXPORT jint JNICALL JNI(surfaceSize)(JNIEnv *env, jobject self, jlong p, jint width, jint height) { return bolo_mpv_surface_size(PLAYER(p)->core, width, height); }
JNIEXPORT jint JNICALL JNI(displayFps)(JNIEnv *env, jobject self, jlong p, jdouble fps) { return bolo_mpv_display_fps(PLAYER(p)->core, fps); }
JNIEXPORT jint JNICALL JNI(caFile)(JNIEnv *env, jobject self, jlong handle, jstring path) {
    const char *value = (*env)->GetStringUTFChars(env, path, NULL);
    int result = bolo_mpv_ca_file(PLAYER(handle)->core, value);
    (*env)->ReleaseStringUTFChars(env, path, value);
    return result;
}
JNIEXPORT jdoubleArray JNICALL JNI(poll)(JNIEnv *env, jobject self, jlong p) {
    bolo_mpv_event event;
    if (!bolo_mpv_poll(PLAYER(p)->core, &event)) return NULL;
    jdouble values[] = {event.type, (double)event.generation, (double)event.request, event.error, event.value};
    jdoubleArray result = (*env)->NewDoubleArray(env, 5);
    if (result) (*env)->SetDoubleArrayRegion(env, result, 0, 5, values);
    return result;
}
JNIEXPORT jbyteArray JNICALL JNI(info)(JNIEnv *env, jobject self, jlong handle, jboolean include_diagnostics) {
    char *info = bolo_mpv_info(PLAYER(handle)->core, include_diagnostics);
    if (!info) return NULL;
    size_t size = strlen(info);
    jbyteArray result = size <= INT32_MAX ? (*env)->NewByteArray(env, (jsize)size) : NULL;
    if (result) (*env)->SetByteArrayRegion(env, result, 0, (jsize)size, (const jbyte *)info);
    bolo_mpv_info_free(info);
    return result;
}
JNIEXPORT jint JNICALL JNI(surface)(JNIEnv *env, jobject self, jlong handle, jobject surface) {
    java_player *p = PLAYER(handle);
    jobject next = surface ? (*env)->NewGlobalRef(env, surface) : NULL;
    int r = bolo_mpv_surface(p->core, (intptr_t)next);
    if (r < 0) { if (next) (*env)->DeleteGlobalRef(env, next); return r; }
    if (p->surface) (*env)->DeleteGlobalRef(env, p->surface);
    p->surface = next;
    return r;
}
#ifndef __ANDROID__
static void *get_proc(void *unused, const char *name) {
#ifdef _WIN32
    void *p = (void *)wglGetProcAddress(name);
    if (!p || p == (void *)1 || p == (void *)2 || p == (void *)3 || p == (void *)-1)
        p = (void *)GetProcAddress(GetModuleHandleA("opengl32.dll"), name);
    return p;
#elif defined(__APPLE__)
    return dlsym(RTLD_DEFAULT, name);
#else
    void *lib = dlopen("libGL.so.1", RTLD_LAZY | RTLD_LOCAL);
    if (!lib) return NULL;
    void *(*get)(const unsigned char *) = dlsym(lib, "glXGetProcAddressARB");
    void *p = get ? get((const unsigned char *)name) : dlsym(lib, name);
    dlclose(lib);
    return p;
#endif
}
#endif
JNIEXPORT jint JNICALL JNI(renderCreate)(JNIEnv *env, jobject self, jlong p) {
#ifdef __ANDROID__
    return -1;
#else
    return bolo_mpv_render_create(PLAYER(p)->core, get_proc, NULL);
#endif
}
JNIEXPORT jboolean JNICALL JNI(renderDirty)(JNIEnv *env, jobject self, jlong p) { return bolo_mpv_render_dirty(PLAYER(p)->core); }
JNIEXPORT jint JNICALL JNI(render)(JNIEnv *env, jobject self, jlong p, jint fbo, jint w, jint h, jboolean flip) {
    return bolo_mpv_render(PLAYER(p)->core, fbo, w, h, flip);
}
JNIEXPORT void JNICALL JNI(renderFree)(JNIEnv *env, jobject self, jlong p) { bolo_mpv_render_free(PLAYER(p)->core); }
JNIEXPORT void JNICALL JNI(destroy)(JNIEnv *env, jobject self, jlong handle) {
    java_player *p = PLAYER(handle);
    bolo_mpv_destroy(p->core);
    if (p->streams) (*env)->DeleteGlobalRef(env, p->streams);
    if (p->surface) (*env)->DeleteGlobalRef(env, p->surface);
    free(p);
}
