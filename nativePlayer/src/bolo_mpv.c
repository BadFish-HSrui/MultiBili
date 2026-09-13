#include "bolo_mpv.h"
#include <mpv/client.h>
#include <mpv/render_gl.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <math.h>

struct bolo_mpv {
    mpv_handle *player;
    mpv_render_context *render;
    atomic_int dirty;
    int64_t generation, expected_entry, playing_entry, seek_request;
    int64_t seek_reply;
    int64_t seek_started_ns;
    double seek_target;
    int report_eof;
    int loaded, needs_audio;
    int merge_audio_channels;
};

static const char *properties[] = {
    NULL, NULL, "time-pos", "duration", "seekable", "pause",
    "paused-for-cache", "seeking", "eof-reached"
};

static int option(bolo_mpv *p, const char *name, const char *value) {
    int result = mpv_set_option_string(p->player, name, value);
    if (result < 0) fprintf(stderr, "Bolo mpv option %s: %s\n", name, mpv_error_string(result));
    return result;
}

bolo_mpv *bolo_mpv_create(const char *platform) {
    bolo_mpv *p = calloc(1, sizeof(*p));
    if (!p) return NULL;
    p->player = mpv_create();
    p->expected_entry = p->playing_entry = -1;
    atomic_init(&p->dirty, 0);
    if (!p->player) { free(p); return NULL; }
    const char *opts[][2] = {
        {"config", "no"}, {"terminal", "no"},
        {"input-default-bindings", "no"}, {"input-vo-keyboard", "no"},
        {"osd-level", "0"}, {"sid", "no"},
        {"sub-auto", "no"}, {"audio-file-auto", "no"},
        {"idle", "yes"}, {"keep-open", "yes"}, {"pause", "yes"},
        {"audio-pitch-correction", "yes"}, {"volume-max", "200"},
        {"ad-lavc-downmix", "no"},
        {"audio-fallback-to-null", "no"}, {"stop-playback-on-init-failure", "yes"},
        {"gapless-audio", "no"}, {"tls-verify", "yes"},
    };
    for (unsigned i = 0; i < sizeof(opts) / sizeof(opts[0]); ++i)
        if (option(p, opts[i][0], opts[i][1]) < 0) goto fail;
    if (strcmp(platform, "android") == 0) {
        if (option(p, "vo", "gpu") < 0 || option(p, "gpu-context", "android") < 0 ||
            option(p, "hwdec", "mediacodec,mediacodec-copy") < 0 ||
            option(p, "ao", "audiotrack") < 0) goto fail;
    } else {
        int ios = strcmp(platform, "ios") == 0;
        // EAGL 直接映射 VideoToolbox 帧可能失败（-6683）；保留硬解并回读后上传纹理。
        if (option(p, "vo", "libmpv") < 0 ||
            option(p, "hwdec", ios ? "videotoolbox-copy" : "auto") < 0) goto fail;
        if (ios && option(p, "ao", "audiounit") < 0) goto fail;
    }
    if (mpv_initialize(p->player) < 0) goto fail;
    return p;
fail:
    mpv_terminate_destroy(p->player);
    free(p);
    return NULL;
}

static mpv_node *field(mpv_node *node, const char *key) {
    if (!node || node->format != MPV_FORMAT_NODE_MAP) return NULL;
    for (int i = 0; i < node->u.list->num; ++i)
        if (strcmp(node->u.list->keys[i], key) == 0) return &node->u.list->values[i];
    return NULL;
}

static int flag(mpv_node *node, const char *key) {
    mpv_node *v = field(node, key);
    return v && v->format == MPV_FORMAT_FLAG && v->u.flag;
}

int bolo_mpv_load(bolo_mpv *p, const char *video, const char *audio, double start, int64_t generation, const char *user_agent, const char *referrer) {
    for (int i = BOLO_POSITION; i <= BOLO_EOF; ++i)
        mpv_unobserve_property(p->player, ((uint64_t)p->generation << 8) | i);
    p->generation = generation;
    p->loaded = 0;
    p->expected_entry = -1;
    p->needs_audio = audio && *audio;
    p->seek_request = 0;
    p->seek_reply = 0;
    p->report_eof = 0;
    int paused = 1;
    int r = mpv_set_property(p->player, "pause", MPV_FORMAT_FLAG, &paused);
    if (r < 0) return r;
    r = mpv_set_property_string(p->player, "user-agent", user_agent);
    if (r < 0) return r;
    r = mpv_set_property_string(p->player, "referrer", referrer);
    if (r < 0) return r;
    mpv_node audio_item = {.format = MPV_FORMAT_STRING, .u.string = (char *)audio};
    mpv_node_list audio_list = {.num = p->needs_audio ? 1 : 0, .values = &audio_item};
    mpv_node audio_files = {.format = MPV_FORMAT_NODE_ARRAY, .u.list = &audio_list};
    r = mpv_set_property(p->player, "options/audio-files", MPV_FORMAT_NODE, &audio_files);
    if (r < 0) return r;
    char start_text[64];
    snprintf(start_text, sizeof(start_text), "%.3f", start);
    for (char *c = start_text; *c; ++c) if (*c == ',') *c = '.';
    r = mpv_set_property_string(p->player, "options/start", start_text);
    if (r < 0) return r;
    mpv_node args[] = {
        {.format = MPV_FORMAT_STRING, .u.string = "loadfile"},
        {.format = MPV_FORMAT_STRING, .u.string = (char *)video},
        {.format = MPV_FORMAT_STRING, .u.string = "replace"},
    };
    mpv_node_list list = {.num = 3, .values = args};
    mpv_node command = {.format = MPV_FORMAT_NODE_ARRAY, .u.list = &list};
    mpv_node result = {0};
    r = mpv_command_node(p->player, &command, &result);
    mpv_node *id = field(&result, "playlist_entry_id");
    if (r >= 0 && id && id->format == MPV_FORMAT_INT64) p->expected_entry = id->u.int64;
    else if (r >= 0) r = MPV_ERROR_GENERIC;
    mpv_free_node_contents(&result);
    return r;
}

int bolo_mpv_pause(bolo_mpv *p, int paused) {
    return mpv_set_property(p->player, "pause", MPV_FORMAT_FLAG, &paused);
}
int bolo_mpv_speed(bolo_mpv *p, double speed) {
    return mpv_set_property(p->player, "speed", MPV_FORMAT_DOUBLE, &speed);
}
int bolo_mpv_volume(bolo_mpv *p, double volume) {
    return mpv_set_property(p->player, "volume", MPV_FORMAT_DOUBLE, &volume);
}
static int apply_audio_merge(bolo_mpv *p, int enabled) {
    int r = mpv_set_property_string(p->player, "audio-normalize-downmix", enabled ? "yes" : "no");
    if (r < 0) return r;
    r = mpv_set_property_string(p->player, "audio-channels", enabled ? "mono" : "auto-safe");
    if (r < 0) return r;
    const char *args[] = {"af", enabled ? "add" : "remove",
                         enabled ? "@bolo-mono:format=channels=mono" : "@bolo-mono", NULL};
    return mpv_command(p->player, args);
}
int bolo_mpv_merge_audio_channels(bolo_mpv *p, int enabled) {
    enabled = !!enabled;
    if (p->merge_audio_channels == enabled) return 0;
    int r = apply_audio_merge(p, enabled);
    if (r >= 0) p->merge_audio_channels = enabled;
    else apply_audio_merge(p, p->merge_audio_channels);
    return r;
}
char *bolo_mpv_info(bolo_mpv *p) {
    if (p->expected_entry < 0) return NULL;
    char *info = mpv_get_property_string(p->player, "bolo-playback-info");
    if (!info) return NULL;
    const char *format = "{\"generation\":%lld,\"expectedEntry\":%lld,\"info\":%s}";
    int size = snprintf(NULL, 0, format, (long long)p->generation, (long long)p->expected_entry, info);
    char *result = size < 0 ? NULL : malloc((size_t)size + 1);
    if (result) snprintf(result, (size_t)size + 1, format,
                         (long long)p->generation, (long long)p->expected_entry, info);
    mpv_free(info);
    return result;
}
void bolo_mpv_info_free(char *info) { free(info); }
int bolo_mpv_seek(bolo_mpv *p, double seconds, int64_t request) {
    p->seek_request = request;
    p->seek_reply = 0;
    p->seek_target = seconds;
    p->seek_started_ns = mpv_get_time_ns(p->player);
    mpv_node args[] = {
        {.format = MPV_FORMAT_STRING, .u.string = "seek"},
        {.format = MPV_FORMAT_DOUBLE, .u.double_ = seconds},
        {.format = MPV_FORMAT_STRING, .u.string = "absolute+exact"},
    };
    mpv_node_list list = {.num = 3, .values = args};
    mpv_node cmd = {.format = MPV_FORMAT_NODE_ARRAY, .u.list = &list};
    return mpv_command_node_async(p->player, (uint64_t)request, &cmd);
}
int bolo_mpv_stop(bolo_mpv *p) {
    p->loaded = 0;
    p->expected_entry = -1;
    const char *args[] = {"stop", NULL};
    return mpv_command(p->player, args);
}

int bolo_mpv_poll(bolo_mpv *p, bolo_mpv_event *out) {
    for (int count = 0; count < 128; ++count) {
        mpv_event *e = mpv_wait_event(p->player, 0);
        if (e->event_id == MPV_EVENT_NONE) {
            // 在最新命令回执之后读取当前媒体的位置，避免用队列中的旧 time-pos 确认 seek。
            if (p->loaded && p->seek_reply && p->seek_reply == p->seek_request) {
                int seeking = 1;
                int paused = 1;
                double position = -1;
                double speed = 1;
                mpv_get_property(p->player, "pause", MPV_FORMAT_FLAG, &paused);
                mpv_get_property(p->player, "speed", MPV_FORMAT_DOUBLE, &speed);
                double advance = paused ? 0 : (mpv_get_time_ns(p->player) - p->seek_started_ns) / 1e9 * speed;
                if (mpv_get_property(p->player, "seeking", MPV_FORMAT_FLAG, &seeking) >= 0 && !seeking &&
                    mpv_get_property(p->player, "time-pos", MPV_FORMAT_DOUBLE, &position) >= 0 &&
                    position >= p->seek_target - 0.25 && position <= p->seek_target + advance + 0.25) {
                    *out = (bolo_mpv_event){.type = BOLO_POSITION, .generation = p->generation,
                        .request = p->seek_reply, .value = position};
                    p->seek_reply = 0;
                    mpv_get_property(p->player, "eof-reached", MPV_FORMAT_FLAG, &p->report_eof);
                    return 1;
                }
            }
            if (p->report_eof) {
                p->report_eof = 0;
                *out = (bolo_mpv_event){.type = BOLO_EOF, .generation = p->generation, .value = 1};
                return 1;
            }
            return 0;
        }
        *out = (bolo_mpv_event){.generation = p->generation};
        switch (e->event_id) {
        case MPV_EVENT_START_FILE:
            p->playing_entry = ((mpv_event_start_file *)e->data)->playlist_entry_id;
            break;
        case MPV_EVENT_FILE_LOADED: {
            if (p->playing_entry != p->expected_entry) break;
            p->loaded = 1;
            mpv_node tracks = {0};
            int audio_ok = !p->needs_audio, video_ok = 0;
            if (mpv_get_property(p->player, "track-list", MPV_FORMAT_NODE, &tracks) >= 0 &&
                tracks.format == MPV_FORMAT_NODE_ARRAY) {
                for (int i = 0; i < tracks.u.list->num; ++i) {
                    mpv_node *track = &tracks.u.list->values[i];
                    mpv_node *type = field(track, "type");
                    if (!flag(track, "selected") || !type || type->format != MPV_FORMAT_STRING) continue;
                    if (!strcmp(type->u.string, "video")) video_ok = 1;
                    if (!strcmp(type->u.string, "audio") && flag(track, "external")) audio_ok = 1;
                }
            }
            mpv_free_node_contents(&tracks);
            if (!audio_ok || !video_ok) {
                out->type = BOLO_ERROR;
                out->error = MPV_ERROR_LOADING_FAILED;
                out->value = !audio_ok ? 2 : 1;
                return 1;
            }
            for (int i = BOLO_POSITION; i <= BOLO_EOF; ++i)
                mpv_observe_property(p->player, ((uint64_t)p->generation << 8) | i,
                    properties[i], i <= BOLO_DURATION ? MPV_FORMAT_DOUBLE : MPV_FORMAT_FLAG);
            out->type = BOLO_LOADED;
            return 1;
        }
        case MPV_EVENT_PROPERTY_CHANGE: {
            if (!p->loaded) break;
            mpv_event_property *prop = e->data;
            if (!prop->data) break;
            out->generation = e->reply_userdata >> 8;
            out->type = e->reply_userdata & 255;
            if (prop->format == MPV_FORMAT_DOUBLE) out->value = *(double *)prop->data;
            else if (prop->format == MPV_FORMAT_FLAG) out->value = *(int *)prop->data;
            else break;
            return 1;
        }
        case MPV_EVENT_COMMAND_REPLY:
            if (e->error >= 0 && e->reply_userdata == (uint64_t)p->seek_request)
                p->seek_reply = p->seek_request;
            out->type = BOLO_SEEK_REPLY;
            out->request = e->reply_userdata;
            out->error = e->error;
            return 1;
        case MPV_EVENT_PLAYBACK_RESTART:
            if (!p->loaded || p->playing_entry != p->expected_entry) break;
            out->type = BOLO_RESTART;
            out->request = p->seek_request;
            out->value = -1;
            mpv_get_property(p->player, "time-pos", MPV_FORMAT_DOUBLE, &out->value);
            return 1;
        case MPV_EVENT_END_FILE: {
            mpv_event_end_file *end = e->data;
            if (end->playlist_entry_id != p->expected_entry) break;
            if (end->reason == MPV_END_FILE_REASON_ERROR) {
                out->type = BOLO_ERROR;
                out->error = end->error;
                return 1;
            }
            break;
        }
        case MPV_EVENT_QUEUE_OVERFLOW:
            out->type = BOLO_OVERFLOW;
            return 1;
        default: break;
        }
    }
    return 0;
}

int bolo_mpv_surface(bolo_mpv *p, int64_t surface) {
    return mpv_set_property(p->player, "wid", MPV_FORMAT_INT64, &surface);
}
int bolo_mpv_surface_size(bolo_mpv *p, int width, int height) {
    if (width <= 0 || height <= 0) return MPV_ERROR_INVALID_PARAMETER;
    char size[48];
    snprintf(size, sizeof(size), "%dx%d", width, height);
    return mpv_set_property_string(p->player, "android-surface-size", size);
}
int bolo_mpv_ca_file(bolo_mpv *p, const char *path) {
    return mpv_set_property_string(p->player, "tls-ca-file", path);
}
static void render_update(void *context) {
    bolo_mpv *p = context;
    atomic_store(&p->dirty, 1);
}
int bolo_mpv_render_create(bolo_mpv *p, void *(*get_proc)(void *, const char *), void *context) {
    mpv_opengl_init_params gl = {.get_proc_address = get_proc, .get_proc_address_ctx = context};
    mpv_render_param params[] = {
        {MPV_RENDER_PARAM_API_TYPE, MPV_RENDER_API_TYPE_OPENGL},
        {MPV_RENDER_PARAM_OPENGL_INIT_PARAMS, &gl}, {0, NULL}
    };
    int r = mpv_render_context_create(&p->render, p->player, params);
    if (r >= 0) {
        mpv_render_context_set_update_callback(p->render, render_update, p);
        atomic_store(&p->dirty, 1);
    }
    return r;
}
int bolo_mpv_render_dirty(bolo_mpv *p) { return atomic_exchange(&p->dirty, 0); }
int bolo_mpv_render(bolo_mpv *p, int fbo, int width, int height, int flip) {
    if (!p->render || width <= 0 || height <= 0) return 0;
    mpv_opengl_fbo target = {.fbo = fbo, .w = width, .h = height};
    mpv_render_param params[] = {
        {MPV_RENDER_PARAM_OPENGL_FBO, &target}, {MPV_RENDER_PARAM_FLIP_Y, &flip}, {0, NULL}
    };
    mpv_render_context_update(p->render);
    return mpv_render_context_render(p->render, params);
}
void bolo_mpv_render_free(bolo_mpv *p) {
    if (!p->render) return;
    mpv_render_context_set_update_callback(p->render, NULL, NULL);
    mpv_render_context_free(p->render);
    p->render = NULL;
}
void bolo_mpv_destroy(bolo_mpv *p) {
    if (!p) return;
    // 调用方必须先在原 GL 上下文释放 renderer，并停止事件读取。
    mpv_terminate_destroy(p->player);
    free(p);
}
