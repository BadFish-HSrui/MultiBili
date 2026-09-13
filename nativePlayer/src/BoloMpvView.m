#import "BoloMpvView.h"
#import <QuartzCore/CAEAGLLayer.h>
#import <OpenGLES/ES3/gl.h>
#import <OpenGLES/EAGL.h>
#include <dlfcn.h>

@implementation BoloMpvView {
    bolo_mpv *_player;
    EAGLContext *_context;
    CADisplayLink *_displayLink;
    GLuint _framebuffer, _color;
    GLint _width, _height;
    BOOL _prepared, _suspended, _resize;
}
+ (Class)layerClass { return CAEAGLLayer.class; }
- (instancetype)initWithPlayer:(int64_t)player {
    self = [super initWithFrame:CGRectZero];
    if (self) {
        _player = (bolo_mpv *)(intptr_t)player;
        self.opaque = YES;
        self.backgroundColor = UIColor.blackColor;
        self.contentScaleFactor = UIScreen.mainScreen.scale;
        CAEAGLLayer *layer = (CAEAGLLayer *)self.layer;
        layer.opaque = YES;
        layer.drawableProperties = @{kEAGLDrawablePropertyRetainedBacking: @NO,
                                     kEAGLDrawablePropertyColorFormat: kEAGLColorFormatRGBA8};
        _resize = YES;
    }
    return self;
}
static void *get_proc(void *unused, const char *name) { return dlsym(RTLD_DEFAULT, name); }
- (BOOL)prepare {
    NSAssert(NSThread.isMainThread, @"EAGL output belongs to main thread");
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
    [self resumeRendering];
    return YES;
}
- (void)layoutSubviews { [super layoutSubviews]; _resize = YES; }
- (void)drawFrame:(CADisplayLink *)link {
    if (!_prepared || _suspended || !self.window || CGRectIsEmpty(self.bounds)) return;
    BOOL dirty = bolo_mpv_render_dirty(_player);
    if (!dirty && !_resize) return;
    [EAGLContext setCurrentContext:_context];
    glBindFramebuffer(GL_FRAMEBUFFER, _framebuffer);
    glBindRenderbuffer(GL_RENDERBUFFER, _color);
    if (_resize) {
        [_context renderbufferStorage:GL_RENDERBUFFER fromDrawable:(CAEAGLLayer *)self.layer];
        glGetRenderbufferParameteriv(GL_RENDERBUFFER, GL_RENDERBUFFER_WIDTH, &_width);
        glGetRenderbufferParameteriv(GL_RENDERBUFFER, GL_RENDERBUFFER_HEIGHT, &_height);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, _color);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) return;
        _resize = NO;
    }
    bolo_mpv_render(_player, _framebuffer, _width, _height, 1);
    glBindRenderbuffer(GL_RENDERBUFFER, _color);
    [_context presentRenderbuffer:GL_RENDERBUFFER];
}
- (void)suspendRendering {
    NSAssert(NSThread.isMainThread, @"EAGL output belongs to main thread");
    _suspended = YES;
    [_displayLink invalidate];
    _displayLink = nil;
    if (_prepared) {
        [EAGLContext setCurrentContext:_context];
        bolo_mpv_render_free(_player);
        glDeleteFramebuffers(1, &_framebuffer);
        glDeleteRenderbuffers(1, &_color);
        glFinish();
        [EAGLContext setCurrentContext:nil];
        _prepared = NO;
        _context = nil;
    }
}
- (void)resumeRendering {
    if (!_prepared || _displayLink) return;
    _suspended = NO;
    _resize = YES;
    _displayLink = [CADisplayLink displayLinkWithTarget:self selector:@selector(drawFrame:)];
    [_displayLink addToRunLoop:NSRunLoop.mainRunLoop forMode:NSRunLoopCommonModes];
}
- (void)close {
    [self suspendRendering];
    _prepared = NO;
    _player = NULL;
    _context = nil;
    [self removeFromSuperview];
}
@end
