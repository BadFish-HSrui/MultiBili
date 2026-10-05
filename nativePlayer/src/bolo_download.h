#pragma once
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct bolo_download bolo_download;
bolo_download *bolo_download_create(void);
int bolo_download_run(bolo_download *, const char *video, const char *audio, const char *output);
void bolo_download_cancel(bolo_download *);
int bolo_download_progress(bolo_download *);
const char *bolo_download_error(bolo_download *);
void bolo_download_destroy(bolo_download *);

#ifdef __cplusplus
}
#endif
