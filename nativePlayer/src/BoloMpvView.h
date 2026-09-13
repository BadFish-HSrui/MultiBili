#import <UIKit/UIKit.h>
#include "bolo_mpv.h"

NS_ASSUME_NONNULL_BEGIN
@interface BoloMpvView : UIView
- (instancetype)initWithPlayer:(int64_t)player;
- (void)prepareWithCompletion:(void (^)(BOOL ready))completion;
- (void)suspendRendering;
- (void)closeWithCompletion:(void (^)(void))completion;
@end
NS_ASSUME_NONNULL_END
