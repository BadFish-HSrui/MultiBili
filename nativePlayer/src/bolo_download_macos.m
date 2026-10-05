#import <Foundation/Foundation.h>
#include <string.h>

char *bolo_download_macos_directory(void) {
    @autoreleasepool {
        NSURL *url = [[NSFileManager defaultManager] URLForDirectory:NSDownloadsDirectory
            inDomain:NSUserDomainMask appropriateForURL:nil create:YES error:nil];
        return url.path ? strdup(url.path.UTF8String) : NULL;
    }
}
