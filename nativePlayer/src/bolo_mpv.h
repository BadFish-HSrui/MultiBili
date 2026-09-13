#pragma once
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct bolo_mpv bolo_mpv;
typedef struct {
    int type;
    int64_t generation;
    int64_t request;
    int error;
    double value;
} bolo_mpv_event;

enum {
    BOLO_LOADED = 1, BOLO_POSITION, BOLO_DURATION, BOLO_SEEKABLE,
    BOLO_PAUSED, BOLO_BUFFERING, BOLO_SEEKING, BOLO_EOF,
    BOLO_ERROR, BOLO_SEEK_REPLY, BOLO_RESTART, BOLO_OVERFLOW
};

bolo_mpv *bolo_mpv_create(const char *platform);
int bolo_mpv_load(bolo_mpv *, const char *video, const char *audio, double start, int64_t generation, const char *user_agent, const char *referrer);
int bolo_mpv_pause(bolo_mpv *, int paused);
int bolo_mpv_speed(bolo_mpv *, double speed);
int bolo_mpv_volume(bolo_mpv *, double volume);
int bolo_mpv_merge_audio_channels(bolo_mpv *, int enabled);
int bolo_mpv_seek(bolo_mpv *, double seconds, int64_t request);
int bolo_mpv_stop(bolo_mpv *);
int bolo_mpv_poll(bolo_mpv *, bolo_mpv_event *event);
char *bolo_mpv_info(bolo_mpv *);
void bolo_mpv_info_free(char *);
void bolo_mpv_destroy(bolo_mpv *);
int bolo_mpv_surface(bolo_mpv *, int64_t surface);
int bolo_mpv_surface_size(bolo_mpv *, int width, int height);
int bolo_mpv_ca_file(bolo_mpv *, const char *path);
int bolo_mpv_render_create(bolo_mpv *, void *(*get_proc)(void *, const char *), void *context);
int bolo_mpv_render_dirty(bolo_mpv *);
int bolo_mpv_render(bolo_mpv *, int fbo, int width, int height, int flip);
void bolo_mpv_render_free(bolo_mpv *);

#ifdef __cplusplus
}
#endif
