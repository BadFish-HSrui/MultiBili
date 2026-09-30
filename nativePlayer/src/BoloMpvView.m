#import "BoloMpvView.h"
#import <QuartzCore/CAEAGLLayer.h>
#import <OpenGLES/ES3/gl.h>
#import <OpenGLES/EAGL.h>
#include <dlfcn.h>
#include <stdatomic.h>
#include <math.h>

@implementation BoloMpvView {
    // UIKit、定时器及提交状态只在 Main 访问。
    CADisplayLink *_displayLink;
    dispatch_queue_t _renderQueue;
    BOOL _active, _background, _suspended, _closed, _frameQueued, _preparing, _redraw;
    CGSize _pixelSize;
    void (^_prepareCompletion)(BOOL);
    void (^_closeCompletion)(void);
    NSUInteger _generation;
    atomic_bool _renderAllowed;
    // 以下资源只在专用串行队列访问；每个任务设置并清除当前 EAGL context。
    bolo_mpv *_player;
    EAGLContext *_context;
    GLuint _framebuffer, _color;
    GLint _width, _height;
    GLint _requestedWidth, _requestedHeight;
    BOOL _prepared, _drawableValid, _needsRedraw;
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
        _active = UIApplication.sharedApplication.applicationState == UIApplicationStateActive;
        _background = UIApplication.sharedApplication.applicationState == UIApplicationStateBackground;
        _suspended = !_active;
        _redraw = YES;
        // 页面退出和异步 close 期间，backend 可能已从 controller 移除。
        // 原生宿主独立监听，确保这些实例也在进入后台前排空 GL。
        [NSNotificationCenter.defaultCenter addObserver:self selector:@selector(willResignActive:)
                                                  name:UIApplicationWillResignActiveNotification object:nil];
        [NSNotificationCenter.defaultCenter addObserver:self selector:@selector(didEnterBackground:)
                                                  name:UIApplicationDidEnterBackgroundNotification object:nil];
        [NSNotificationCenter.defaultCenter addObserver:self selector:@selector(didBecomeActive:)
                                                  name:UIApplicationDidBecomeActiveNotification object:nil];
    }
    return self;
}
static void *get_proc(void *unused, const char *name) { return dlsym(RTLD_DEFAULT, name); }
- (BOOL)prepareOnRenderQueue {
    NSAssert(!NSThread.isMainThread, @"EAGL rendering must not run on Main");
    if (_prepared) {
        bolo_mpv_render_resume(_player);
        return YES;
    }
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
    _needsRedraw = YES;
    return YES;
}
- (void)prepareWithCompletion:(void (^)(BOOL))completion {
    NSAssert(NSThread.isMainThread, @"Video host belongs to Main");
    if (_closed) {
        completion(NO);
        return;
    }
    // backend 的新准备代次替换旧回调；失活期间仅保存请求，不重启 GL。
    _prepareCompletion = [completion copy];
    [self prepareIfActive];
}
- (void)prepareIfActive {
    if (_closed || !_active || _preparing || !_prepareCompletion ||
        UIApplication.sharedApplication.applicationState != UIApplicationStateActive) return;
    _preparing = YES;
    _suspended = NO;
    NSUInteger generation = ++_generation;
    atomic_store(&_renderAllowed, true);
    dispatch_async(_renderQueue, ^{
        @autoreleasepool {
            BOOL ready = atomic_load(&self->_renderAllowed) && [self prepareOnRenderQueue];
            [EAGLContext setCurrentContext:nil];
            dispatch_async(dispatch_get_main_queue(), ^{
                BOOL current = generation == self->_generation && !self->_closed && !self->_suspended;
                if (!current) return;
                self->_preparing = NO;
                if (ready) {
                    self->_redraw = YES;
                    if (!self->_displayLink) {
                        self->_displayLink = [CADisplayLink displayLinkWithTarget:self selector:@selector(drawFrame:)];
                        self->_displayLink.preferredFramesPerSecond = 60;
                        [self->_displayLink addToRunLoop:NSRunLoop.mainRunLoop forMode:NSRunLoopCommonModes];
                    }
                }
                void (^completion)(BOOL) = self->_prepareCompletion;
                self->_prepareCompletion = nil;
                if (completion) completion(ready);
            });
        }
    });
}
- (void)layoutSubviews {
    [super layoutSubviews];
    CGSize size = CGSizeMake(ceil(self.bounds.size.width * self.contentScaleFactor),
                             ceil(self.bounds.size.height * self.contentScaleFactor));
    if (!CGSizeEqualToSize(size, _pixelSize)) {
        _pixelSize = size;
        _redraw = YES;
    }
}
- (void)drawFrame:(CADisplayLink *)link {
    NSAssert(NSThread.isMainThread, @"Video host belongs to Main");
    if (_closed || !_active || _suspended || _frameQueued || !self.window || CGRectIsEmpty(self.bounds) ||
        UIApplication.sharedApplication.applicationState != UIApplicationStateActive) return;
    GLint width = (GLint)ceil(self.bounds.size.width * self.contentScaleFactor);
    GLint height = (GLint)ceil(self.bounds.size.height * self.contentScaleFactor);
    BOOL redraw = _redraw;
    _redraw = NO;
    _frameQueued = YES;
    CAEAGLLayer *layer = (CAEAGLLayer *)self.layer;
    dispatch_async(_renderQueue, ^{
        @autoreleasepool {
            if (atomic_load(&self->_renderAllowed) && self->_prepared) {
                [self renderLayer:layer width:width height:height redraw:redraw];
                [EAGLContext setCurrentContext:nil];
            }
            dispatch_async(dispatch_get_main_queue(), ^{ self->_frameQueued = NO; });
        }
    });
}
- (void)renderLayer:(CAEAGLLayer *)layer width:(GLint)width height:(GLint)height redraw:(BOOL)redraw {
    NSAssert(!NSThread.isMainThread, @"EAGL rendering must not run on Main");
    BOOL resize = !_drawableValid || width != _requestedWidth || height != _requestedHeight;
    BOOL dirty = bolo_mpv_render_dirty(_player);
    _needsRedraw |= dirty || redraw || resize;
    if (!_needsRedraw || width <= 0 || height <= 0) return;
    if (![EAGLContext setCurrentContext:_context]) return;
    // 输出准备完成仅允许恢复视频轨；有效视频帧到来前保留上次已显示的画面。
    if (!bolo_mpv_render_frame_ready(_player) || !atomic_load(&_renderAllowed)) return;
    glBindFramebuffer(GL_FRAMEBUFFER, _framebuffer);
    glBindRenderbuffer(GL_RENDERBUFFER, _color);
    if (resize) {
        _drawableValid = NO;
        if (![_context renderbufferStorage:GL_RENDERBUFFER fromDrawable:layer]) return;
        glGetRenderbufferParameteriv(GL_RENDERBUFFER, GL_RENDERBUFFER_WIDTH, &_width);
        glGetRenderbufferParameteriv(GL_RENDERBUFFER, GL_RENDERBUFFER_HEIGHT, &_height);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, _color);
        if (_width <= 0 || _height <= 0 || glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) return;
        _requestedWidth = width;
        _requestedHeight = height;
        _drawableValid = YES;
    }
    if (bolo_mpv_render(_player, _framebuffer, _width, _height, 1) < 0) return;
    if (!atomic_load(&_renderAllowed)) return;
    glBindRenderbuffer(GL_RENDERBUFFER, _color);
    if ([_context presentRenderbuffer:GL_RENDERBUFFER]) _needsRedraw = NO;
    else _drawableValid = NO;
}
- (void)stopSubmittingFrames {
    NSAssert(NSThread.isMainThread, @"Video host belongs to Main");
    ++_generation;
    _suspended = YES;
    _preparing = NO;
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
        _requestedWidth = _requestedHeight = 0;
        _drawableValid = NO;
        _needsRedraw = YES;
    }
}
- (void)willResignActive:(NSNotification *)notification { [self suspendRendering]; }
- (void)didBecomeActive:(NSNotification *)notification {
    _background = NO;
    _active = YES;
    if (_closed) [self finishClose];
    else [self prepareIfActive];
}
- (void)didEnterBackground:(NSNotification *)notification {
    if (_background) return;
    _background = YES;
    _active = NO;
    [self stopSubmittingFrames];
    [self drainRendering];
}
- (void)suspendRendering {
    _active = NO;
    [self stopSubmittingFrames];
    if (!_background) [self drainRendering];
}
- (void)drainRendering {
    // 保留已准备的资源，只排空 GPU；不申请额外后台执行时间。
    // 包含进入后台前已从 controller 移除、但仍在异步关闭的宿主。
    // iOS 后台禁止 GL：生命周期边界必须等待队列排空。不能 dispatch_sync，
    // 因为 GCD 可能在调用线程内联执行同步 block，令 GL 再次落到 Main。
    dispatch_semaphore_t drained = dispatch_semaphore_create(0);
    dispatch_async(_renderQueue, ^{
        @autoreleasepool {
            if (self->_prepared) {
                [EAGLContext setCurrentContext:self->_context];
                glFinish();
                [EAGLContext setCurrentContext:nil];
            }
        }
        dispatch_semaphore_signal(drained);
    });
    dispatch_semaphore_wait(drained, DISPATCH_TIME_FOREVER);
}
- (void)closeWithCompletion:(void (^)(void))completion {
    [self stopSubmittingFrames];
    _closed = YES;
    void (^completionPending)(BOOL) = _prepareCompletion;
    _prepareCompletion = nil;
    if (completionPending) completionPending(NO);
    _closeCompletion = [completion copy];
    // 后台只能停止 CPU 播放资源；Render API / EAGL 的最终清理等到前台。
    // 调用方挂起等待此 completion 后才可销毁 mpv，不能先释放裸指针。
    if (!_background && UIApplication.sharedApplication.applicationState != UIApplicationStateBackground)
        [self finishClose];
}
- (void)finishClose {
    if (!_closeCompletion) return;
    void (^completion)(void) = _closeCompletion;
    _closeCompletion = nil;
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
