#import <UIKit/UIKit.h>
#include "bolo_mpv.h"

NS_ASSUME_NONNULL_BEGIN
@interface BoloMpvView : UIView
- (instancetype)initWithPlayer:(int64_t)player;
- (BOOL)prepare;
- (void)suspendRendering;
- (void)resumeRendering;
- (void)close;
@end
NS_ASSUME_NONNULL_END
