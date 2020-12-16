
#include <sstream>
#import <Foundation/Foundation.h>
#include "util/IO2.h"

#define DEBUG_THIS_SOURCE 0

using namespace TAK::Engine;
using namespace TAK::Engine::Util;

NSDictionary *rootsDictionary() {
    static dispatch_once_t onceToken = 0;
    static NSDictionary<NSString *, NSString *> *roots = nil;
    dispatch_once(&onceToken, ^{
        NSBundle *mainBundle = [NSBundle mainBundle];
        NSFileManager *fileManager = [NSFileManager defaultManager];
        NSURL *directoryURL = [fileManager URLsForDirectory:NSDocumentDirectory inDomains:NSUserDomainMask].lastObject;
        NSString *dataRoot = [directoryURL.path stringByDeletingLastPathComponent];
        NSString *tempRoot = NSTemporaryDirectory();
        roots = @{@"bundle":mainBundle.resourcePath,
                  @"data":dataRoot,
                  @"temp":tempRoot};
    });
    return roots;
}

TAKErr TAK::Engine::Util::File_getStoragePath(Port::String &path) NOTHROWS {
    @autoreleasepool {
        NSString *pathString = [[NSString alloc] initWithUTF8String:path];
        NSDictionary *roots = rootsDictionary();
        __block NSString *mountName = nil;
        __block NSUInteger length = 0;
        [roots enumerateKeysAndObjectsUsingBlock:^(NSString * _Nonnull name, NSString *  _Nonnull root, BOOL * _Nonnull stop) {
            if ([pathString hasPrefix:root]) {
                mountName = name;
                length = root.length;
                *stop = YES;
            }
        }];
        
        if (mountName) {
            pathString = [[NSString alloc] initWithFormat:@"%@:%@", mountName, [pathString substringFromIndex:length]];
            path = pathString.UTF8String;
        } else {
            std::stringstream ss;
            for (NSString *root in roots.allValues) {
                ss << "\t" << root.UTF8String << "\n";
            }
            atakmap::util::Logger::log(atakmap::util::Logger::Debug, "failed to find storage path for %s\nWith roots:\n%s", path.get(), ss.str().c_str());
        }
    }
    return TE_Ok;
}

TAKErr TAK::Engine::Util::File_getRuntimePath(Port::String &path) NOTHROWS {
    @autoreleasepool {
        NSString *pathString = [[NSString alloc] initWithUTF8String:path];
        
#if DEBUG_THIS_SOURCE
        NSLog(@"resolving runtime path for %@", pathString);
#endif
        
        NSDictionary *roots = rootsDictionary();
        __block NSString *foundRoot = nil;
        __block NSUInteger length = 0;
        [roots enumerateKeysAndObjectsUsingBlock:^(NSString * _Nonnull name, NSString *  _Nonnull root, BOOL * _Nonnull stop) {
            NSString *namePrefix = [[NSString alloc] initWithFormat:@"%@:", name];
            if ([pathString hasPrefix:namePrefix]) {
                foundRoot = root;
                length = namePrefix.length;
                *stop = YES;
            }
        }];
        
        if (foundRoot) {
            pathString = [[NSString alloc] initWithFormat:@"%@%@", foundRoot, [pathString substringFromIndex:length]];
            
#if DEBUG_THIS_SOURCE
            NSFileManager *fileManager = [NSFileManager defaultManager];
            if (![fileManager fileExistsAtPath:pathString]) {
                NSLog(@"no file exists at path %@", pathString);
            }
#endif
            
            path = pathString.UTF8String;
        }  else {
            atakmap::util::Logger::log(atakmap::util::Logger::Debug, "failed to find runtime path for %s", path.get());
        }
    }
    return TE_Ok;
}
