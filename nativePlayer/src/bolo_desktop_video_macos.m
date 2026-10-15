#define GL_SILENCE_DEPRECATION
#import <AppKit/AppKit.h>
#import <QuartzCore/QuartzCore.h>
#import <OpenGL/OpenGL.h>
#import <OpenGL/gl3.h>
#include "bolo_desktop_video.h"
#include <jawt.h>
#include <jawt_md.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <dlfcn.h>
#include <time.h>

@class BoloDesktopVideoBridge;
@interface BoloDesktopVideoLayer : CAOpenGLLayer
@property(nonatomic, weak) BoloDesktopVideoBridge *bridge;
@property(nonatomic, strong) NSMutableDictionary<NSValue *, NSNumber *> *readFramebuffers;
@property(nonatomic) BOOL redraw;
@property(nonatomic) BOOL presentingCompletedFrame;
@end

@interface BoloDesktopVideoBridge : NSObject {
@public
    pthread_mutex_t mutex;
    pthread_cond_t condition;
    pthread_t worker;
    bool started, requested, visible, redraw, presentationQueued;
    atomic_bool closed, failed;
    int width, height, front, pending;
    GLuint textures[3], buffers[3];
    int widths[3], heights[3];
    GLsync written[3], read[3];
    CGLPixelFormatObj format;
    CGLContextObj context;
    bolo_mpv *player;
    JavaVM *vm;
    jobject target;
    jmethodID callback;
    void *jawtLibrary;
    jboolean (JNICALL *getAWT)(JNIEnv *, JAWT *);
    int64_t frameIntervalNs, nextRequestNs;
}
@property(nonatomic, strong) CALayer *root;
@property(nonatomic, strong) BoloDesktopVideoLayer *video;
@end

static int64_t monotonic_ns(void) {
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return (int64_t)now.tv_sec * 1000000000 + now.tv_nsec;
}
static void main_sync(dispatch_block_t block) {
    if (NSThread.isMainThread) block(); else dispatch_sync(dispatch_get_main_queue(), block);
}
static void emit(BoloDesktopVideoBridge *bridge, int status, const char *message) {
    if (atomic_load(&bridge->closed)) return;
    JNIEnv *env = NULL;
    bool attached = (*bridge->vm)->GetEnv(bridge->vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK;
    if (attached && (*bridge->vm)->AttachCurrentThread(bridge->vm, (void **)&env, NULL) != JNI_OK) return;
    jstring text = message ? (*env)->NewStringUTF(env, message) : NULL;
    (*env)->CallVoidMethod(env, bridge->target, bridge->callback, (jlong)(__bridge void *)bridge, status, text);
    if (text) (*env)->DeleteLocalRef(env, text);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionDescribe(env); (*env)->ExceptionClear(env); }
    if (attached) (*bridge->vm)->DetachCurrentThread(bridge->vm);
}
static void fail(BoloDesktopVideoBridge *bridge, const char *message) {
    if (!atomic_exchange(&bridge->failed, true)) emit(bridge, -1, message);
}
static bool signaled(GLsync fence) {
    if (!fence) return true;
    GLenum status = glClientWaitSync(fence, 0, 0);
    return status == GL_ALREADY_SIGNALED || status == GL_CONDITION_SATISFIED;
}

@implementation BoloDesktopVideoBridge
- (instancetype)init {
    if ((self = [super init])) {
        pthread_mutex_init(&mutex, NULL); pthread_cond_init(&condition, NULL);
        atomic_init(&closed, false); atomic_init(&failed, false);
        front = pending = -1;
        frameIntervalNs = 1000000000 / 60;
    }
    return self;
}
- (void)dealloc {
    if (context) CGLReleaseContext(context);
    if (format) CGLReleasePixelFormat(format);
    if (jawtLibrary) dlclose(jawtLibrary);
    pthread_cond_destroy(&condition); pthread_mutex_destroy(&mutex);
}
@end

@implementation BoloDesktopVideoLayer
- (CGLPixelFormatObj)copyCGLPixelFormatForDisplayMask:(uint32_t)mask {
    BoloDesktopVideoBridge *bridge = self.bridge;
    return bridge ? CGLRetainPixelFormat(bridge->format) : NULL;
}
- (CGLContextObj)copyCGLContextForPixelFormat:(CGLPixelFormatObj)format {
    BoloDesktopVideoBridge *bridge = self.bridge;
    if (!bridge || atomic_load(&bridge->closed)) return NULL;
    CGLContextObj context = NULL;
    if (CGLCreateContext(format, bridge->context, &context) != kCGLNoError)
        fail(bridge, "Cannot create video presentation context");
    return context;
}
- (BOOL)canDrawInCGLContext:(CGLContextObj)context pixelFormat:(CGLPixelFormatObj)format
              forLayerTime:(CFTimeInterval)time displayTime:(const CVTimeStamp *)displayTime {
    BoloDesktopVideoBridge *bridge = self.bridge;
    if (!bridge || atomic_load(&bridge->closed) || atomic_load(&bridge->failed)) return NO;
    pthread_mutex_lock(&bridge->mutex);
    // 合并显示刷新请求；mpv 的目标时间等待仅发生在后台渲染线程。
    int64_t now = monotonic_ns();
    // CA 的检查频率可能高于屏幕刷新率；完成帧通知也不能提高 mpv 请求频率。
    if (!self.presentingCompletedFrame && now + bridge->frameIntervalNs / 4 >= bridge->nextRequestNs) {
        bridge->requested = true; pthread_cond_signal(&bridge->condition);
        bridge->nextRequestNs += bridge->frameIntervalNs;
        if (bridge->nextRequestNs <= now) bridge->nextRequestNs = now + bridge->frameIntervalNs;
    }
    BOOL result = bridge->visible && (bridge->pending >= 0 || self.redraw);
    pthread_mutex_unlock(&bridge->mutex);
    return result;
}
- (void)drawInCGLContext:(CGLContextObj)context pixelFormat:(CGLPixelFormatObj)format
           forLayerTime:(CFTimeInterval)time displayTime:(const CVTimeStamp *)displayTime {
    BoloDesktopVideoBridge *bridge = self.bridge;
    if (!bridge || atomic_load(&bridge->closed) || atomic_load(&bridge->failed)) return;
    CGLSetCurrentContext(context);
    GLint target = 0, viewport[4];
    glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &target);
    glGetIntegerv(GL_VIEWPORT, viewport);
    pthread_mutex_lock(&bridge->mutex);
    int pending = bridge->pending;
    if (pending >= 0) {
        // 生产者已提交 fence；只在 GPU 队列等待，不阻塞 AppKit 等待 mpv 定时。
        glWaitSync(bridge->written[pending], 0, GL_TIMEOUT_IGNORED);
        glDeleteSync(bridge->written[pending]); bridge->written[pending] = NULL;
        bridge->front = pending; bridge->pending = -1;
    }
    int front = bridge->front;
    glDisable(GL_SCISSOR_TEST);
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, target);
    GLenum error = GL_NO_ERROR;
    if (front >= 0) {
        NSValue *key = [NSValue valueWithPointer:context];
        if (!_readFramebuffers) _readFramebuffers = [NSMutableDictionary new];
        GLuint framebuffer = _readFramebuffers[key].unsignedIntValue;
        if (!framebuffer) { glGenFramebuffers(1, &framebuffer); _readFramebuffers[key] = @(framebuffer); }
        glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
        glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, bridge->textures[front], 0);
        if (glCheckFramebufferStatus(GL_READ_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) {
            // CA 切换呈现上下文时，新 fence 仍须涵盖旧上下文尚未完成的读取。
            if (bridge->read[front]) glWaitSync(bridge->read[front], 0, GL_TIMEOUT_IGNORED);
            glBlitFramebuffer(0, 0, bridge->widths[front], bridge->heights[front],
                viewport[0], viewport[1], viewport[0] + viewport[2], viewport[1] + viewport[3], GL_COLOR_BUFFER_BIT, GL_LINEAR);
        } else error = GL_INVALID_FRAMEBUFFER_OPERATION;
        if (bridge->read[front]) glDeleteSync(bridge->read[front]);
        bridge->read[front] = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    } else {
        glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
    }
    glBindFramebuffer(GL_READ_FRAMEBUFFER, target);
    glFlush();
    if (!error) error = glGetError();
    pthread_mutex_unlock(&bridge->mutex);
    self.redraw = NO;
    [super drawInCGLContext:context pixelFormat:format forLayerTime:time displayTime:displayTime];
    if (error) fail(bridge, "Video presentation failed");
}
- (void)releaseCGLContext:(CGLContextObj)context {
    CGLContextObj previous = CGLGetCurrentContext();
    CGLLockContext(context); CGLSetCurrentContext(context);
    BoloDesktopVideoBridge *bridge = self.bridge;
    if (bridge) pthread_mutex_lock(&bridge->mutex);
    NSValue *key = [NSValue valueWithPointer:context];
    GLuint framebuffer = _readFramebuffers[key].unsignedIntValue;
    [_readFramebuffers removeObjectForKey:key];
    if (bridge) pthread_mutex_unlock(&bridge->mutex);
    if (framebuffer) glDeleteFramebuffers(1, &framebuffer);
    CGLSetCurrentContext(previous == context ? NULL : previous); CGLUnlockContext(context);
    [super releaseCGLContext:context];
}
@end

static void *get_proc(void *unused, const char *name) { return dlsym(RTLD_DEFAULT, name); }
static void *render_worker(void *opaque) {
    @autoreleasepool {
        BoloDesktopVideoBridge *bridge = (__bridge BoloDesktopVideoBridge *)opaque;
        pthread_setname_np("BoloMpv-NativeRender");
        CGLSetCurrentContext(bridge->context);
        bool initialized = false;
        bool ready = false;
        int previousWidth = 0, previousHeight = 0;
        while (!atomic_load(&bridge->closed) && !atomic_load(&bridge->failed)) {
            pthread_mutex_lock(&bridge->mutex);
            while (!bridge->requested && !atomic_load(&bridge->closed) && !atomic_load(&bridge->failed))
                pthread_cond_wait(&bridge->condition, &bridge->mutex);
            bridge->requested = false;
            int width = bridge->width, height = bridge->height, index = -1;
            bool redraw = bridge->redraw;
            if (bridge->visible && width > 0 && height > 0 && !atomic_load(&bridge->closed)) {
                for (int i = 0; i < 3; i++) {
                    if (i != bridge->front && i != bridge->pending && signaled(bridge->read[i])) { index = i; break; }
                }
            }
            if (index >= 0) {
                if (bridge->read[index]) glDeleteSync(bridge->read[index]);
                if (bridge->written[index]) glDeleteSync(bridge->written[index]);
                bridge->read[index] = bridge->written[index] = NULL;
                bridge->redraw = false;
            }
            GLenum syncError = glGetError();
            pthread_mutex_unlock(&bridge->mutex);
            if (syncError != GL_NO_ERROR) { fail(bridge, "Native video fence wait failed"); break; }
            if (index < 0) continue;
            if (!initialized) {
                if (bolo_mpv_render_create(bridge->player, get_proc, NULL) < 0) {
                    fail(bridge, "Cannot initialize native mpv renderer"); break;
                }
                initialized = true;
            }
            bool dirty = bolo_mpv_render_dirty(bridge->player);
            if (!dirty && !redraw && width == previousWidth && height == previousHeight) continue;
            if (!bridge->textures[index]) glGenTextures(1, &bridge->textures[index]);
            if (!bridge->buffers[index]) glGenFramebuffers(1, &bridge->buffers[index]);
            glBindTexture(GL_TEXTURE_2D, bridge->textures[index]);
            if (width != bridge->widths[index] || height != bridge->heights[index]) {
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
                bridge->widths[index] = width; bridge->heights[index] = height;
            }
            glBindFramebuffer(GL_FRAMEBUFFER, bridge->buffers[index]);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, bridge->textures[index], 0);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                fail(bridge, "Cannot allocate native video framebuffer"); break;
            }
            if (bolo_mpv_render(bridge->player, (int)bridge->buffers[index], width, height, 1) < 0) {
                fail(bridge, "Native video rendering failed"); break;
            }
            GLsync fence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0); glFlush();
            if (!fence || glGetError() != GL_NO_ERROR) {
                if (fence) glDeleteSync(fence);
                fail(bridge, "Native video synchronization failed"); break;
            }
            previousWidth = width; previousHeight = height;
            pthread_mutex_lock(&bridge->mutex);
            bridge->written[index] = fence; bridge->pending = index;
            bool notify = !bridge->presentationQueued;
            bridge->presentationQueued = true;
            pthread_mutex_unlock(&bridge->mutex);
            if (notify) dispatch_async(dispatch_get_main_queue(), ^{
                pthread_mutex_lock(&bridge->mutex); bridge->presentationQueued = false; pthread_mutex_unlock(&bridge->mutex);
                if (!atomic_load(&bridge->closed) && !atomic_load(&bridge->failed) && !bridge.video.hidden) {
                    // 完成帧立即交给图层，但不产生新的渲染请求；刷新回调仍负责节流。
                    bridge.video.presentingCompletedFrame = YES;
                    [bridge.video display];
                    bridge.video.presentingCompletedFrame = NO;
                }
            });
            if (!ready) { ready = true; emit(bridge, 1, NULL); }
        }
        if (initialized) bolo_mpv_render_free(bridge->player);
        // 关闭方先停用呈现回调；后台等待最后一次 GPU 读取结束后释放共享纹理。
        pthread_mutex_lock(&bridge->mutex);
        for (int i = 0; i < 3; i++) {
            if (bridge->read[i]) { glWaitSync(bridge->read[i], 0, GL_TIMEOUT_IGNORED); glDeleteSync(bridge->read[i]); bridge->read[i] = NULL; }
            if (bridge->written[i]) { glDeleteSync(bridge->written[i]); bridge->written[i] = NULL; }
        }
        bridge->front = bridge->pending = -1;
        glFinish();
        glDeleteTextures(3, bridge->textures); glDeleteFramebuffers(3, bridge->buffers);
        pthread_mutex_unlock(&bridge->mutex);
        CGLSetCurrentContext(NULL);
    }
    return NULL;
}

static CALayer *window_layer(JNIEnv *env, jobject window, BoloDesktopVideoBridge *bridge) {
    JAWT awt = {.version = JAWT_VERSION_9};
    if (!window || !bridge->getAWT(env, &awt)) return nil;
    JAWT_DrawingSurface *surface = awt.GetDrawingSurface(env, window);
    if (!surface) return nil;
    CALayer *root = nil;
    jint lock = surface->Lock(surface);
    if (!(lock & JAWT_LOCK_ERROR)) {
        JAWT_DrawingSurfaceInfo *info = surface->GetDrawingSurfaceInfo(surface);
        if (info) {
            id<JAWT_SurfaceLayers> layers = (__bridge id<JAWT_SurfaceLayers>)info->platformInfo;
            root = layers.windowLayer;
            surface->FreeDrawingSurfaceInfo(info);
        }
        surface->Unlock(surface);
    }
    awt.FreeDrawingSurface(surface);
    return root;
}

void *bolo_desktop_video_create(JNIEnv *env, jobject target, jobject window, bolo_mpv *player, const char *jawt_path) {
    BoloDesktopVideoBridge *bridge = [BoloDesktopVideoBridge new];
    bridge->player = player;
    bridge->jawtLibrary = dlopen(jawt_path, RTLD_NOW | RTLD_LOCAL);
    if (!bridge->jawtLibrary) return NULL;
    bridge->getAWT = dlsym(bridge->jawtLibrary, "JAWT_GetAWT");
    if (!bridge->getAWT) return NULL;
    CALayer *root = window_layer(env, window, bridge);
    if (!root) return NULL;
    bridge.root = root;
    CGLPixelFormatAttribute attrs[] = {kCGLPFAOpenGLProfile, (CGLPixelFormatAttribute)kCGLOGLPVersion_3_2_Core,
        kCGLPFAAccelerated, kCGLPFAColorSize, (CGLPixelFormatAttribute)24,
        kCGLPFAAlphaSize, (CGLPixelFormatAttribute)8, (CGLPixelFormatAttribute)0};
    GLint count = 0;
    if (CGLChoosePixelFormat(attrs, &bridge->format, &count) != kCGLNoError ||
        CGLCreateContext(bridge->format, NULL, &bridge->context) != kCGLNoError) return NULL;
    (*env)->GetJavaVM(env, &bridge->vm);
    jclass type = (*env)->GetObjectClass(env, target);
    bridge->callback = (*env)->GetMethodID(env, type, "onNativeState", "(JILjava/lang/String;)V");
    (*env)->DeleteLocalRef(env, type);
    if (!bridge->callback) return NULL;
    bridge->target = (*env)->NewGlobalRef(env, target);
    if (!bridge->target) return NULL;
    if (pthread_create(&bridge->worker, NULL, render_worker, (__bridge void *)bridge) != 0) {
        (*env)->DeleteGlobalRef(env, bridge->target); bridge->target = NULL; return NULL;
    }
    bridge->started = true;
    main_sync(^{
        BoloDesktopVideoLayer *video = [BoloDesktopVideoLayer layer]; video.bridge = bridge;
        video.opaque = YES; video.zPosition = -1; video.anchorPoint = CGPointZero;
        CGColorSpaceRef colorspace = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
        video.colorspace = colorspace;
        CGColorSpaceRelease(colorspace);
        video.hidden = YES; video.redraw = YES;
        bridge.video = video;
        [root insertSublayer:video atIndex:0];
    });
    return (void *)CFBridgingRetain(bridge);
}

int bolo_desktop_video_update(JNIEnv *env, void *handle, jobject window, double x, double y,
    double width, double height, double scale, double refresh_rate, int visible) {
    BoloDesktopVideoBridge *bridge = (__bridge BoloDesktopVideoBridge *)handle;
    if (atomic_load(&bridge->closed) || atomic_load(&bridge->failed)) return -1;
    if (!isfinite(x) || !isfinite(y) || !isfinite(width) || !isfinite(height) || !isfinite(scale) ||
        !isfinite(refresh_rate) || refresh_rate < 1 || refresh_rate > 240 ||
        width < 0 || height < 0 || scale <= 0 || width * scale > INT_MAX || height * scale > INT_MAX) return -1;
    CALayer *root = window_layer(env, window, bridge);
    if (!root) return -1;
    int pixelsWide = (int)llround(width * scale), pixelsHigh = (int)llround(height * scale);
    pthread_mutex_lock(&bridge->mutex);
    bridge->width = pixelsWide; bridge->height = pixelsHigh;
    int64_t interval = (int64_t)llround(1000000000.0 / refresh_rate);
    if (bridge->frameIntervalNs != interval) { bridge->frameIntervalNs = interval; bridge->nextRequestNs = 0; }
    bridge->visible = visible && pixelsWide > 0 && pixelsHigh > 0;
    bridge->redraw = true; bridge->requested = true; pthread_cond_signal(&bridge->condition);
    pthread_mutex_unlock(&bridge->mutex);
    dispatch_async(dispatch_get_main_queue(), ^{
        if (atomic_load(&bridge->closed) || atomic_load(&bridge->failed)) return;
        [CATransaction begin]; [CATransaction setDisableActions:YES];
        if (bridge.root != root) {
            [bridge.video removeFromSuperlayer]; bridge.root = root;
            [root insertSublayer:bridge.video atIndex:0];
        }
        CGFloat convertedY = root.geometryFlipped ? y : root.bounds.size.height - y - height;
        bridge.video.frame = CGRectMake(x, convertedY, width, height); bridge.video.contentsScale = scale;
        bridge.video.hidden = !visible || pixelsWide <= 0 || pixelsHigh <= 0;
        bridge.video.asynchronous = !bridge.video.hidden;
        bridge.video.redraw = YES;
        [CATransaction commit];
        if (!bridge.video.hidden) [bridge.video setNeedsDisplay];
    });
    return 0;
}

void bolo_desktop_video_close(JNIEnv *env, void *handle) {
    BoloDesktopVideoBridge *bridge = CFBridgingRelease(handle);
    atomic_store(&bridge->closed, true);
    main_sync(^{ bridge.video.asynchronous = NO; bridge.video.hidden = YES; });
    pthread_mutex_lock(&bridge->mutex); pthread_cond_signal(&bridge->condition); pthread_mutex_unlock(&bridge->mutex);
    if (bridge->started) pthread_join(bridge->worker, NULL);
    main_sync(^{ [bridge.video removeFromSuperlayer]; bridge.video = nil; bridge.root = nil; });
    if (bridge->target) { (*env)->DeleteGlobalRef(env, bridge->target); bridge->target = NULL; }
}
