#if defined(__linux__) && !defined(_GNU_SOURCE)
#define _GNU_SOURCE 1
#endif

#include "bolo_download.h"
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <stdio.h>
#ifdef _WIN32
#include <windows.h>
#include <shlobj.h>
#include <knownfolders.h>
#include <wchar.h>
#elif defined(__APPLE__)
char *bolo_download_macos_directory(void);
#else
#include <unistd.h>
#include <fcntl.h>
#include <sys/syscall.h>
#endif

#define JNI(name) Java_tv_hsrui_bolo_download_DownloadNative_##name
#define JOB(value) ((bolo_download *)(intptr_t)(value))

#ifdef _WIN32
static wchar_t *file_path(const char *path) {
    int size = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, path, -1, NULL, 0);
    if (size <= 0) return NULL;
    wchar_t *wide = calloc((size_t)size + 8, sizeof(wchar_t));
    if (!wide) return NULL;
    int offset = 0;
    if (strncmp(path, "\\\\?\\", 4) != 0) {
        int unc = strncmp(path, "\\\\", 2) == 0;
        wcscpy(wide, unc ? L"\\\\?\\UNC\\" : L"\\\\?\\");
        offset = unc ? 8 : 4;
        if (unc) path += 2;
    }
    if (!MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, path, -1, wide + offset, size)) {
        free(wide);
        return NULL;
    }
    return wide;
}
#endif

static char *text(JNIEnv *env, jbyteArray bytes) {
    if (!bytes) return NULL;
    jsize count = (*env)->GetArrayLength(env, bytes);
    char *value = calloc((size_t)count + 1, 1);
    if (value) (*env)->GetByteArrayRegion(env, bytes, 0, count, (jbyte *)value);
    return value;
}
static jbyteArray bytes(JNIEnv *env, const char *text) {
    if (!text) return NULL;
    jsize size = (jsize)strlen(text);
    jbyteArray result = (*env)->NewByteArray(env, size);
    if (result) (*env)->SetByteArrayRegion(env, result, 0, size, (const jbyte *)text);
    return result;
}
JNIEXPORT jlong JNICALL JNI(create)(JNIEnv *env, jobject self) { return (intptr_t)bolo_download_create(); }
JNIEXPORT jint JNICALL JNI(run)(JNIEnv *env, jobject self, jlong handle, jbyteArray video, jbyteArray audio, jbyteArray output) {
    char *v = text(env, video), *a = text(env, audio), *o = text(env, output);
    int result = v && o && (!audio || a) ? bolo_download_run(JOB(handle), v, a, o) : -12;
    free(v); free(a); free(o);
    return result;
}
JNIEXPORT void JNICALL JNI(cancel)(JNIEnv *env, jobject self, jlong handle) { bolo_download_cancel(JOB(handle)); }
JNIEXPORT jint JNICALL JNI(progress)(JNIEnv *env, jobject self, jlong handle) { return bolo_download_progress(JOB(handle)); }
JNIEXPORT jbyteArray JNICALL JNI(error)(JNIEnv *env, jobject self, jlong handle) { return bytes(env, bolo_download_error(JOB(handle))); }
JNIEXPORT void JNICALL JNI(destroy)(JNIEnv *env, jobject self, jlong handle) { bolo_download_destroy(JOB(handle)); }
// 0: published, 1: name already exists, negative: platform error. Never replace an existing entry.
JNIEXPORT jint JNICALL JNI(publishFile)(JNIEnv *env, jobject self, jbyteArray source, jbyteArray target) {
    char *s = text(env, source), *t = text(env, target);
    int result = -ENOMEM;
    if (!s || !t) goto finish;
#ifdef _WIN32
    wchar_t *sw = file_path(s), *tw = file_path(t);
    if (sw && tw) {
        if (MoveFileExW(sw, tw, MOVEFILE_WRITE_THROUGH)) result = 0;
        else { DWORD error = GetLastError(); result = error == ERROR_ALREADY_EXISTS || error == ERROR_FILE_EXISTS ? 1 : -(int)error; }
    }
    free(sw); free(tw);
#elif defined(__APPLE__)
    result = renamex_np(s, t, RENAME_EXCL) == 0 ? 0 : errno == EEXIST ? 1 : -errno;
#else
    result = syscall(SYS_renameat2, AT_FDCWD, s, AT_FDCWD, t, 1 /* RENAME_NOREPLACE */) == 0 ? 0 : errno == EEXIST ? 1 : -errno;
#endif
finish:
    free(s); free(t);
    return result;
}
JNIEXPORT jbyteArray JNICALL JNI(downloadsDirectory)(JNIEnv *env, jobject self) {
    char *directory = NULL;
#ifdef _WIN32
    HRESULT initialized = CoInitializeEx(NULL, COINIT_APARTMENTTHREADED);
    PWSTR wide = NULL;
    if (SUCCEEDED(SHGetKnownFolderPath(&FOLDERID_Downloads, KF_FLAG_CREATE, NULL, &wide))) {
        int size = WideCharToMultiByte(CP_UTF8, 0, wide, -1, NULL, 0, NULL, NULL);
        if (size > 0) {
            directory = malloc(size);
            if (directory) WideCharToMultiByte(CP_UTF8, 0, wide, -1, directory, size, NULL, NULL);
        }
        CoTaskMemFree(wide);
    }
    if (SUCCEEDED(initialized)) CoUninitialize();
#elif defined(__APPLE__)
    directory = bolo_download_macos_directory();
#endif
    jbyteArray result = bytes(env, directory);
    free(directory);
    return result;
}
