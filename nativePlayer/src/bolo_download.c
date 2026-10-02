#include "bolo_download.h"
#include <libavformat/avformat.h>
#include <libavutil/avutil.h>
#include <libavutil/mathematics.h>
#include <libavutil/intreadwrite.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <stdio.h>
#include <errno.h>
#include <string.h>

struct bolo_download {
    atomic_int canceled;
    atomic_int progress;
    char error[256];
};

static int interrupted(void *opaque) {
    return atomic_load(&((bolo_download *)opaque)->canceled);
}

static int read_packet(AVFormatContext *input, AVPacket *packet, int stream) {
    int result;
    while ((result = av_read_frame(input, packet)) >= 0) {
        if (packet->stream_index == stream) return packet->flags & AV_PKT_FLAG_CORRUPT ? AVERROR_INVALIDDATA : 0;
        av_packet_unref(packet);
    }
    return result;
}

// hvc1 requires complete parameter sets in hvcC, not in media samples.
static int check_hevc_parameter_sets(const AVCodecParameters *par, const uint8_t *nal, int nal_size) {
    const uint8_t *data = par->extradata;
    int size = par->extradata_size, offset = 23, present = 0, matched = 0;
    if (!data || size < 23 || data[0] != 1) return AVERROR_INVALIDDATA;
    for (int i = 0; i < data[22]; ++i) {
        if (size - offset < 3) return AVERROR_INVALIDDATA;
        int type = data[offset] & 63;
        int count = AV_RB16(data + offset + 1);
        offset += 3;
        for (int j = 0; j < count; ++j) {
            if (size - offset < 2) return AVERROR_INVALIDDATA;
            int length = AV_RB16(data + offset);
            offset += 2;
            if (length < 2 || length > size - offset || ((data[offset] >> 1) & 63) != type)
                return AVERROR_INVALIDDATA;
            if (type >= 32 && type <= 34) {
                present |= 1 << (type - 32);
                if (nal && length == nal_size && !memcmp(data + offset, nal, length)) matched = 1;
            }
            offset += length;
        }
    }
    return offset == size && present == 7 && (!nal || matched) ? 0 : AVERROR_INVALIDDATA;
}

static int prepare_hevc_packet(const AVCodecParameters *par, AVPacket *packet) {
    size_t extra_size = 0;
    const uint8_t *extra = av_packet_get_side_data(packet, AV_PKT_DATA_NEW_EXTRADATA, &extra_size);
    // A changed configuration cannot be discarded while retaining the original hvcC.
    if (extra && (extra_size != par->extradata_size || memcmp(extra, par->extradata, extra_size)))
        return AVERROR_INVALIDDATA;
    int result = av_packet_make_writable(packet);
    if (result < 0) return result;
    int length_size = (par->extradata[21] & 3) + 1;
    int offset = 0, written = 0;
    while (offset < packet->size) {
        if (packet->size - offset < length_size) return AVERROR_INVALIDDATA;
        int start = offset;
        uint32_t length = 0;
        for (int i = 0; i < length_size; ++i) length = (length << 8) | packet->data[offset++];
        if (length < 2 || length > (uint32_t)(packet->size - offset)) return AVERROR_INVALIDDATA;
        int type = (packet->data[offset] >> 1) & 63;
        if (type >= 32 && type <= 34) {
            result = check_hevc_parameter_sets(par, packet->data + offset, (int)length);
            if (result < 0) return result;
        } else {
            int count = length_size + (int)length;
            memmove(packet->data + written, packet->data + start, count);
            written += count;
        }
        offset += (int)length;
    }
    if (!written) return AVERROR_INVALIDDATA;
    av_shrink_packet(packet, written);
    return 0;
}

bolo_download *bolo_download_create(void) {
    bolo_download *job = calloc(1, sizeof(*job));
    if (job) {
        atomic_init(&job->canceled, 0);
        atomic_init(&job->progress, 0);
    }
    return job;
}

int bolo_download_run(bolo_download *job, const char *video, const char *audio, const char *output) {
    AVFormatContext *inputs[2] = {NULL, NULL}, *out = NULL;
    AVPacket *packets[2] = {NULL, NULL};
    int streams[2] = {0, 0}, ready[2] = {0, 0}, counts[2] = {0, 0};
    const char *paths[2] = {video, audio};
    int tracks = audio && *audio ? 2 : 1;
    int result = 0;
    int64_t duration = 0;
    if (!job || !video || !output) return AVERROR(EINVAL);
    for (int i = 0; i < tracks; ++i) {
        inputs[i] = avformat_alloc_context();
        if (!inputs[i]) { result = AVERROR(ENOMEM); goto finish; }
        inputs[i]->interrupt_callback = (AVIOInterruptCB){interrupted, job};
        AVDictionary *options = NULL;
        av_dict_set(&options, "protocol_whitelist", "file", 0);
        result = avformat_open_input(&inputs[i], paths[i], NULL, &options);
        av_dict_free(&options);
        if (result < 0) goto finish;
        if ((result = avformat_find_stream_info(inputs[i], NULL)) < 0) goto finish;
        result = av_find_best_stream(inputs[i], i == 0 ? AVMEDIA_TYPE_VIDEO : AVMEDIA_TYPE_AUDIO, -1, -1, NULL, 0);
        if (result < 0) goto finish;
        streams[i] = result;
        if (inputs[i]->duration != AV_NOPTS_VALUE && inputs[i]->duration > duration) duration = inputs[i]->duration;
        packets[i] = av_packet_alloc();
        if (!packets[i]) { result = AVERROR(ENOMEM); goto finish; }
    }
    result = avformat_alloc_output_context2(&out, NULL, "mp4", output);
    if (result < 0 || !out) { if (result >= 0) result = AVERROR(ENOMEM); goto finish; }
    out->interrupt_callback = (AVIOInterruptCB){interrupted, job};
    for (int i = 0; i < tracks; ++i) {
        AVStream *source = inputs[i]->streams[streams[i]];
        AVStream *target = avformat_new_stream(out, NULL);
        if (!target) { result = AVERROR(ENOMEM); goto finish; }
        if ((result = avcodec_parameters_copy(target->codecpar, source->codecpar)) < 0) goto finish;
        target->codecpar->codec_tag = 0;
        if (source->codecpar->codec_id == AV_CODEC_ID_HEVC) {
            if ((result = check_hevc_parameter_sets(source->codecpar, NULL, 0)) < 0) goto finish;
            target->codecpar->codec_tag = MKTAG('h', 'v', 'c', '1');
        }
        target->time_base = source->time_base;
        target->avg_frame_rate = source->avg_frame_rate;
        target->disposition = source->disposition;
        av_dict_copy(&target->metadata, source->metadata, 0);
    }
    if (interrupted(job)) { result = AVERROR_EXIT; goto finish; }
    if ((result = avio_open2(&out->pb, output, AVIO_FLAG_WRITE, &out->interrupt_callback, NULL)) < 0) goto finish;
    if ((result = avformat_write_header(out, NULL)) < 0) goto finish;
    for (int i = 0; i < tracks; ++i) {
        result = read_packet(inputs[i], packets[i], streams[i]);
        if (result < 0) goto finish;
        ready[i] = 1;
    }
    while (ready[0] || ready[1]) {
        if (interrupted(job)) { result = AVERROR_EXIT; goto finish; }
        int i = ready[0] ? 0 : 1;
        if (ready[0] && ready[1]) {
            int64_t a = packets[0]->dts == AV_NOPTS_VALUE ? packets[0]->pts : packets[0]->dts;
            int64_t b = packets[1]->dts == AV_NOPTS_VALUE ? packets[1]->pts : packets[1]->dts;
            if (av_compare_ts(a, inputs[0]->streams[streams[0]]->time_base,
                              b, inputs[1]->streams[streams[1]]->time_base) > 0) i = 1;
        }
        AVPacket *packet = packets[i];
        AVStream *source = inputs[i]->streams[streams[i]], *target = out->streams[i];
        if (source->codecpar->codec_id == AV_CODEC_ID_HEVC &&
            (result = prepare_hevc_packet(target->codecpar, packet)) < 0) goto finish;
        if (duration > 0 && packet->pts != AV_NOPTS_VALUE) {
            int64_t position = av_rescale_q(packet->pts, source->time_base, AV_TIME_BASE_Q);
            int value = (int)av_rescale(position < 0 ? 0 : position, 10000, duration);
            if (value > 9999) value = 9999;
            if (value > atomic_load(&job->progress)) atomic_store(&job->progress, value);
        }
        av_packet_rescale_ts(packet, source->time_base, target->time_base);
        packet->stream_index = i;
        packet->pos = -1;
        result = av_interleaved_write_frame(out, packet);
        if (result < 0) goto finish;
        ++counts[i];
        result = read_packet(inputs[i], packet, streams[i]);
        if (result == AVERROR_EOF) ready[i] = 0;
        else if (result < 0) goto finish;
    }
    if (interrupted(job)) { result = AVERROR_EXIT; goto finish; }
    for (int i = 0; i < tracks; ++i) if (!counts[i]) { result = AVERROR_INVALIDDATA; goto finish; }
    result = av_write_trailer(out);
    if (result >= 0 && out->pb && out->pb->error < 0) result = out->pb->error;
finish:
    for (int i = 0; i < 2; ++i) {
        av_packet_free(&packets[i]);
        avformat_close_input(&inputs[i]);
    }
    if (out) {
        if (out->pb) {
            int closed = avio_closep(&out->pb);
            if (result >= 0 && closed < 0) result = closed;
        }
        avformat_free_context(out);
    }
    if (result < 0) av_strerror(result, job->error, sizeof(job->error));
    else atomic_store(&job->progress, 10000);
    return result;
}

void bolo_download_cancel(bolo_download *job) { if (job) atomic_store(&job->canceled, 1); }
int bolo_download_progress(bolo_download *job) { return job ? atomic_load(&job->progress) : 0; }
const char *bolo_download_error(bolo_download *job) { return job ? job->error : "Allocation failed"; }
void bolo_download_destroy(bolo_download *job) { free(job); }
