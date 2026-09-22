#import <AppKit/AppKit.h>
#import <MediaPlayer/MediaPlayer.h>
#include <jni.h>

#define JNI(name) Java_tv_hsrui_bolo_player_session_BoloDesktopSystemMediaNative_##name

@interface BoloSystemMediaBridge : NSObject
@property(nonatomic) JavaVM *vm;
@property(nonatomic) jobject target;
@property(nonatomic) jmethodID callback;
@property(nonatomic) BOOL closed;
@property(nonatomic, copy) NSString *mediaId;
@property(nonatomic, strong) NSMutableArray *commands;
@property(nonatomic, strong) NSMutableArray *tokens;
@property(nonatomic, strong) MPMediaItemArtwork *artwork;
@end

static __weak BoloSystemMediaBridge *activeBridge;

@implementation BoloSystemMediaBridge
- (void)emit:(int)action position:(int64_t)position value:(double)value {
    if (_closed || activeBridge != self || !_mediaId.length) return;
    JNIEnv *env = NULL;
    BOOL attached = (*_vm)->GetEnv(_vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK;
    if (attached && (*_vm)->AttachCurrentThread(_vm, (void **)&env, NULL) != JNI_OK) return;
    NSUInteger length = _mediaId.length;
    jchar *characters = malloc(MAX(length, 1) * sizeof(jchar));
    [_mediaId getCharacters:characters range:NSMakeRange(0, length)];
    jstring media = (*env)->NewString(env, characters, (jsize)length);
    free(characters);
    (*env)->CallVoidMethod(env, _target, _callback, (jlong)(__bridge void *)self,
                          (jint)action, media, (jlong)position, (jdouble)value);
    (*env)->DeleteLocalRef(env, media);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionDescribe(env); (*env)->ExceptionClear(env); }
    if (attached) (*_vm)->DetachCurrentThread(_vm);
}
- (void)bind:(MPRemoteCommand *)command action:(int)action {
    __weak BoloSystemMediaBridge *weakSelf = self;
    id token = [command addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *event) {
        int64_t position = 0;
        if ([event isKindOfClass:MPChangePlaybackPositionCommandEvent.class])
            position = (int64_t)(((MPChangePlaybackPositionCommandEvent *)event).positionTime * 1000.0);
        dispatch_async(dispatch_get_main_queue(), ^{
            BoloSystemMediaBridge *bridge = weakSelf;
            if (bridge && !bridge.closed && activeBridge == bridge) [bridge emit:action position:position value:0];
        });
        return MPRemoteCommandHandlerStatusSuccess;
    }];
    [_commands addObject:command];
    [_tokens addObject:token];
    command.enabled = NO;
}
@end

static NSString *string(JNIEnv *env, jstring value) {
    if (!value) return @"";
    const jchar *chars = (*env)->GetStringChars(env, value, NULL);
    NSString *result = [[NSString alloc] initWithCharacters:chars length:(NSUInteger)(*env)->GetStringLength(env, value)];
    (*env)->ReleaseStringChars(env, value, chars);
    return result;
}
static void main_sync(dispatch_block_t block) {
    if (NSThread.isMainThread) block(); else dispatch_sync(dispatch_get_main_queue(), block);
}

JNIEXPORT jlong JNICALL JNI(create)(JNIEnv *env, jobject self, jobject window) {
    BoloSystemMediaBridge *bridge = [BoloSystemMediaBridge new];
    JavaVM *vm = NULL;
    (*env)->GetJavaVM(env, &vm);
    bridge.vm = vm;
    bridge.target = (*env)->NewGlobalRef(env, self);
    jclass type = (*env)->GetObjectClass(env, self);
    bridge.callback = (*env)->GetMethodID(env, type, "onCommand", "(JILjava/lang/String;JD)V");
    (*env)->DeleteLocalRef(env, type);
    if (!bridge.callback) { (*env)->DeleteGlobalRef(env, bridge.target); return 0; }
    bridge.mediaId = @"";
    bridge.commands = [NSMutableArray new];
    bridge.tokens = [NSMutableArray new];
    main_sync(^{
        activeBridge = bridge;
        MPRemoteCommandCenter *center = MPRemoteCommandCenter.sharedCommandCenter;
        [bridge bind:center.playCommand action:0];
        [bridge bind:center.pauseCommand action:1];
        [bridge bind:center.togglePlayPauseCommand action:2];
        [bridge bind:center.changePlaybackPositionCommand action:3];
        [bridge bind:center.previousTrackCommand action:5];
        [bridge bind:center.nextTrackCommand action:6];
        [bridge bind:center.stopCommand action:7];
        center.skipBackwardCommand.enabled = NO;
        center.skipForwardCommand.enabled = NO;
    });
    return (jlong)(intptr_t)CFBridgingRetain(bridge);
}

JNIEXPORT void JNICALL JNI(update)(JNIEnv *env, jobject self, jlong handle, jstring mediaId,
    jstring title, jstring artist, jstring album, jbyteArray artwork, jboolean artworkChanged,
    jlong position, jlong duration, jdouble speed, jint status, jint commands) {
    BoloSystemMediaBridge *bridge = (__bridge BoloSystemMediaBridge *)(void *)(intptr_t)handle;
    NSString *media = string(env, mediaId), *name = string(env, title), *author = string(env, artist), *collection = string(env, album);
    NSData *data = nil;
    if (artwork) {
        jsize size = (*env)->GetArrayLength(env, artwork);
        jbyte *bytes = (*env)->GetByteArrayElements(env, artwork, NULL);
        data = [NSData dataWithBytes:bytes length:(NSUInteger)size];
        (*env)->ReleaseByteArrayElements(env, artwork, bytes, JNI_ABORT);
    }
    dispatch_async(dispatch_get_main_queue(), ^{
        if (bridge.closed || activeBridge != bridge) return;
        BOOL changed = ![bridge.mediaId isEqualToString:media];
        bridge.mediaId = media;
        if (changed || artworkChanged) bridge.artwork = nil;
        if (data) {
            NSImage *original = [[NSImage alloc] initWithData:data];
            if (original) {
                NSImage *image = [[NSImage alloc] initWithSize:NSMakeSize(512, 512)];
                [image lockFocus];
                [original drawInRect:NSMakeRect(0, 0, 512, 512) fromRect:NSZeroRect operation:NSCompositingOperationCopy fraction:1];
                [image unlockFocus];
                bridge.artwork = [[MPMediaItemArtwork alloc] initWithBoundsSize:image.size requestHandler:^NSImage *(CGSize size) { return image; }];
            }
        }
        MPRemoteCommandCenter *center = MPRemoteCommandCenter.sharedCommandCenter;
        center.playCommand.enabled = (commands & 1) != 0;
        center.pauseCommand.enabled = (commands & 2) != 0;
        center.togglePlayPauseCommand.enabled = (commands & 3) != 0;
        center.changePlaybackPositionCommand.enabled = (commands & 4) != 0;
        center.previousTrackCommand.enabled = (commands & 8) != 0;
        center.nextTrackCommand.enabled = (commands & 16) != 0;
        center.stopCommand.enabled = (commands & 3) != 0;
        MPNowPlayingInfoCenter *info = MPNowPlayingInfoCenter.defaultCenter;
        if (!media.length) { info.nowPlayingInfo = nil; info.playbackState = MPNowPlayingPlaybackStateStopped; return; }
        NSMutableDictionary *values = [@{
            MPMediaItemPropertyTitle: name,
            MPMediaItemPropertyPlaybackDuration: @(duration / 1000.0),
            MPNowPlayingInfoPropertyElapsedPlaybackTime: @(position / 1000.0),
            MPNowPlayingInfoPropertyPlaybackRate: @(status == 1 ? speed : 0),
            MPNowPlayingInfoPropertyDefaultPlaybackRate: @(speed),
            MPNowPlayingInfoPropertyExternalContentIdentifier: media,
            MPNowPlayingInfoPropertyMediaType: @(MPNowPlayingInfoMediaTypeVideo),
        } mutableCopy];
        NSCharacterSet *whitespace = NSCharacterSet.whitespaceAndNewlineCharacterSet;
        if ([author stringByTrimmingCharactersInSet:whitespace].length) values[MPMediaItemPropertyArtist] = author;
        if ([collection stringByTrimmingCharactersInSet:whitespace].length) values[MPMediaItemPropertyAlbumTitle] = collection;
        if (bridge.artwork) values[MPMediaItemPropertyArtwork] = bridge.artwork;
        info.nowPlayingInfo = values;
        info.playbackState = status == 1 ? MPNowPlayingPlaybackStatePlaying :
            (status == 0 || status == 4 || status == 5 ? MPNowPlayingPlaybackStateStopped : MPNowPlayingPlaybackStatePaused);
    });
}

JNIEXPORT void JNICALL JNI(destroy)(JNIEnv *env, jobject self, jlong handle) {
    BoloSystemMediaBridge *bridge = CFBridgingRelease((void *)(intptr_t)handle);
    main_sync(^{
        bridge.closed = YES;
        for (NSUInteger i = 0; i < bridge.commands.count; i++) [bridge.commands[i] removeTarget:bridge.tokens[i]];
        [bridge.commands removeAllObjects];
        [bridge.tokens removeAllObjects];
        if (activeBridge == bridge) {
            activeBridge = nil;
            MPNowPlayingInfoCenter.defaultCenter.playbackState = MPNowPlayingPlaybackStateStopped;
            MPNowPlayingInfoCenter.defaultCenter.nowPlayingInfo = nil;
        }
    });
    (*env)->DeleteGlobalRef(env, bridge.target);
    bridge.target = NULL;
}
