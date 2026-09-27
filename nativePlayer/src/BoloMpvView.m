#import "BoloMpvView.h"
#import <QuartzCore/CAEAGLLayer.h>
#import <OpenGLES/ES3/gl.h>
#import <OpenGLES/EAGL.h>
#include <dlfcn.h>
#include <stdatomic.h>

@implementation BoloMpvView {
    // UIKit、定时器及提交状态只在 Main 访问。
    CADisplayLink *_displayLink;
    dispatch_queue_t _renderQueue;
    BOOL _suspended, _closed, _frameQueued, _resize;
    NSUInteger _generation;
    atomic_bool _renderAllowed;
    // 以下资源只在专用串行队列访问；每个任务设置并清除当前 EAGL context。
    bolo_mpv *_player;
    EAGLContext *_context;
    GLuint _framebuffer, _color;
    GLint _width, _height;
    BOOL _prepared;
}
+ (Class)layerClass { return CAEAGLLayer.class; }
- (instancetype)initWithPlayer:(int64_t)player {
    self = [super initWithFrame:CGRectZero];
    if (self) {
        _player = (bolo_mpv *)(intptr_t)player;
        _renderQueue = dispatch_queue_create("tv.hsrui.bolo.mpv.render", dispatch_queue_attr_make_with_qos_class(DISPATCH_QUEUE_SERIAL, QOS_CLASS_USER_INITIATED, 0));
        atomic_init(&_renderAllowed, false);
        self.opaque = YES;
        self.backgroundColor = UIColor.blackColor;
        self.contentScaleFactor = UIScreen.mainScreen.scale;
        CAEAGLLayer *layer = (CAEAGLLayer *)self.layer;
        layer.opaque = YES;
        layer.drawableProperties = @{kEAGLDrawablePropertyRetainedBacking: @NO,
                                     kEAGLDrawablePropertyColorFormat: kEAGLColorFormatRGBA8};
        _resize = YES;
        // 页面退出和异步 close 期间，backend 可能已从 controller 移除。
        // 原生宿主独立监听，确保这些实例也在进入后台前排空 GL。
        [NSNotificationCenter.defaultCenter addObserver:self selector:@selector(willResignActive:)
                                                  name:UIApplicationWillResignActiveNotification object:nil];
    }
    return self;
}
static void *get_proc(void *unused, const char *name) { return dlsym(RTLD_DEFAULT, name); }
- (BOOL)prepareOnRenderQueue {
    NSAssert(!NSThread.isMainThread, @"EAGL rendering must not run on Main");
    if (_prepared) return YES;
    if (!_player) return NO;
    _context = [[EAGLContext alloc] initWithAPI:kEAGLRenderingAPIOpenGLES3];
    if (!_context || ![EAGLContext setCurrentContext:_context]) return NO;
    if (bolo_mpv_render_create(_player, get_proc, NULL) < 0) {
        [EAGLContext setCurrentContext:nil];
        _context = nil;
        return NO;
    }
    glGenFramebuffers(1, &_framebuffer);
    glGenRenderbuffers(1, &_color);
    _prepared = YES;
    return YES;
}
- (void)prepareWithCompletion:(void (^)(BOOL))completion {
    NSAssert(NSThread.isMainThread, @"Video host belongs to Main");
    if (_closed || UIApplication.sharedApplication.applicationState == UIApplicationStateBackground) {
        completion(NO);
        return;
    }
    _suspended = NO;
    NSUInteger generation = ++_generation;
    atomic_store(&_renderAllowed, true);
    dispatch_async(_renderQueue, ^{
        @autoreleasepool {
            BOOL ready = atomic_load(&self->_renderAllowed) && [self prepareOnRenderQueue];
            [EAGLContext setCurrentContext:nil];
            dispatch_async(dispatch_get_main_queue(), ^{
                BOOL current = generation == self->_generation && !self->_closed && !self->_suspended;
                if (ready && current) {
                    self->_resize = YES;
                    if (!self->_displayLink) {
                        self->_displayLink = [CADisplayLink displayLinkWithTarget:self selector:@selector(drawFrame:)];
                        self->_displayLink.preferredFramesPerSecond = 60;
                        [self->_displayLink addToRunLoop:NSRunLoop.mainRunLoop forMode:NSRunLoopCommonModes];
                    }
                }
                completion(ready && current);
            });
        }
    });
}
- (void)layoutSubviews { [super layoutSubviews]; _resize = YES; }
- (void)drawFrame:(CADisplayLink *)link {
    NSAssert(NSThread.isMainThread, @"Video host belongs to Main");
    if (_closed || _suspended || _frameQueued || !self.window || CGRectIsEmpty(self.bounds)) return;
    BOOL resize = _resize;
    _resize = NO;
    _frameQueued = YES;
    CAEAGLLayer *layer = (CAEAGLLayer *)self.layer;
    dispatch_async(_renderQueue, ^{
        @autoreleasepool {
            if (atomic_load(&self->_renderAllowed) && self->_prepared) {
                [self renderLayer:layer resize:resize];
                [EAGLContext setCurrentContext:nil];
            }
            dispatch_async(dispatch_get_main_queue(), ^{ self->_frameQueued = NO; });
        }
    });
}
- (void)renderLayer:(CAEAGLLayer *)layer resize:(BOOL)resize {
    NSAssert(!NSThread.isMainThread, @"EAGL rendering must not run on Main");
    BOOL dirty = bolo_mpv_render_dirty(_player);
    if (!dirty && !resize) return;
    [EAGLContext setCurrentContext:_context];
    glBindFramebuffer(GL_FRAMEBUFFER, _framebuffer);
    glBindRenderbuffer(GL_RENDERBUFFER, _color);
    if (resize) {
        [_context renderbufferStorage:GL_RENDERBUFFER fromDrawable:layer];
        glGetRenderbufferParameteriv(GL_RENDERBUFFER, GL_RENDERBUFFER_WIDTH, &_width);
        glGetRenderbufferParameteriv(GL_RENDERBUFFER, GL_RENDERBUFFER_HEIGHT, &_height);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, _color);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            dispatch_async(dispatch_get_main_queue(), ^{ self->_resize = YES; });
            return;
        }
    }
    bolo_mpv_render(_player, _framebuffer, _width, _height, 1);
    if (!atomic_load(&_renderAllowed)) return;
    glBindRenderbuffer(GL_RENDERBUFFER, _color);
    [_context presentRenderbuffer:GL_RENDERBUFFER];
}
- (void)stopSubmittingFrames {
    NSAssert(NSThread.isMainThread, @"Video host belongs to Main");
    ++_generation;
    _suspended = YES;
    atomic_store(&_renderAllowed, false);
    [_displayLink invalidate];
    _displayLink = nil;
}
- (void)releaseOnRenderQueue {
    NSAssert(!NSThread.isMainThread, @"EAGL cleanup must not run on Main");
    if (_prepared) {
        [EAGLContext setCurrentContext:_context];
        bolo_mpv_render_free(_player);
        glDeleteFramebuffers(1, &_framebuffer);
        glDeleteRenderbuffers(1, &_color);
        glFinish();
        [EAGLContext setCurrentContext:nil];
        _prepared = NO;
        _context = nil;
        _framebuffer = _color = 0;
        _width = _height = 0;
    }
}
- (void)willResignActive:(NSNotification *)notification { [self suspendRendering]; }
- (void)suspendRendering {
    [self stopSubmittingFrames];
    // iOS 后台禁止 GL：仅此生命周期边界等待清理完成。不能 dispatch_sync，
    // 因为 GCD 可能在调用线程内联执行同步 block，令 GL 再次落到 Main。
    dispatch_semaphore_t drained = dispatch_semaphore_create(0);
    dispatch_async(_renderQueue, ^{
        @autoreleasepool {
            if (self->_player) bolo_mpv_video_enabled(self->_player, 0);
            [self releaseOnRenderQueue];
        }
        dispatch_semaphore_signal(drained);
    });
    dispatch_semaphore_wait(drained, DISPATCH_TIME_FOREVER);
}
- (void)closeWithCompletion:(void (^)(void))completion {
    [self stopSubmittingFrames];
    _closed = YES;
    dispatch_async(_renderQueue, ^{
        @autoreleasepool {
            [self releaseOnRenderQueue];
            self->_player = NULL;
            dispatch_async(dispatch_get_main_queue(), ^{
                [NSNotificationCenter.defaultCenter removeObserver:self];
                [self removeFromSuperview];
                completion();
            });
        }
    });
}
- (void)dealloc { [NSNotificationCenter.defaultCenter removeObserver:self]; }
@end
