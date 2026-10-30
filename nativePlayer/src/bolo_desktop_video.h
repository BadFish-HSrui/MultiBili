#ifndef BOLO_DESKTOP_VIDEO_H
#define BOLO_DESKTOP_VIDEO_H

#include "bolo_mpv.h"
#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

#if defined(__APPLE__)
void *bolo_desktop_video_create(JNIEnv *, jobject target, jobject window, bolo_mpv *, const char *jawt_path);
int bolo_desktop_video_update(JNIEnv *, void *, jobject window, double x, double y,
    double width, double height, double scale, double refresh_rate, int visible);
void bolo_desktop_video_close(JNIEnv *, void *);
#elif defined(_WIN32)
void *bolo_desktop_video_windows_create(JNIEnv *, jlong hwnd);
void bolo_desktop_video_windows_update(JNIEnv *, void *, int x, int y, int width, int height, int visible,
    int clip_left, int clip_top, int clip_right, int clip_bottom);
int bolo_desktop_video_windows_begin(JNIEnv *, void *);
void bolo_desktop_video_windows_end(JNIEnv *, void *, int present);
void bolo_desktop_video_windows_close(void *);
#endif

#ifdef __cplusplus
}
#endif

#endif
