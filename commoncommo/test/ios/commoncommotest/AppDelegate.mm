//
//  AppDelegate.m
//  commoncommotest
//
//  Created by Jeff Downs on 11/16/15.
//  Copyright © 2015 Jeff Downs. All rights reserved.
//

#import "AppDelegate.h"
#import "DetailViewController.h"
#import "threads/Mutex.hh"
#import "libxml/tree.h"
#import "openssl/ssl.h"
#import "curl/curl.h"

namespace {
    PGSC::Mutex *cryptoMutexes;
    void thread_lock(int mode, int type, const char *file, int line)
    {
        if (mode & CRYPTO_LOCK)
            cryptoMutexes[type].lock();
        else
            cryptoMutexes[type].unlock();
    }
    unsigned long thread_id(void)
    {
        unsigned long ret = (unsigned long)pthread_self();
        return ret;
    }
}

@interface AppDelegate () <UISplitViewControllerDelegate>

@end

@implementation AppDelegate


- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    // Override point for customization after application launch.
    UISplitViewController *splitViewController = (UISplitViewController *)self.window.rootViewController;
    UINavigationController *navigationController = [splitViewController.viewControllers lastObject];
    navigationController.topViewController.navigationItem.leftBarButtonItem = splitViewController.displayModeButtonItem;
    splitViewController.delegate = self;
    xmlInitParser();
    SSL_load_error_strings();
    OpenSSL_add_ssl_algorithms();
    cryptoMutexes = new PGSC::Mutex[CRYPTO_num_locks()];
    CRYPTO_set_id_callback(thread_id);
    CRYPTO_set_locking_callback(thread_lock);
    curl_global_init(CURL_GLOBAL_NOTHING);
    
    return YES;
}

- (void)applicationWillResignActive:(UIApplication *)application {
    // Sent when the application is about to move from active to inactive state. This can occur for certain types of temporary interruptions (such as an incoming phone call or SMS message) or when the user quits the application and it begins the transition to the background state.
    // Use this method to pause ongoing tasks, disable timers, and throttle down OpenGL ES frame rates. Games should use this method to pause the game.
}

- (void)applicationDidEnterBackground:(UIApplication *)application {
    // Use this method to release shared resources, save user data, invalidate timers, and store enough application state information to restore your application to its current state in case it is terminated later.
    // If your application supports background execution, this method is called instead of applicationWillTerminate: when the user quits.
}

- (void)applicationWillEnterForeground:(UIApplication *)application {
    // Called as part of the transition from the background to the inactive state; here you can undo many of the changes made on entering the background.
}

- (void)applicationDidBecomeActive:(UIApplication *)application {
    // Restart any tasks that were paused (or not yet started) while the application was inactive. If the application was previously in the background, optionally refresh the user interface.
}

- (void)applicationWillTerminate:(UIApplication *)application {
    // Called when the application is about to terminate. Save data if appropriate. See also applicationDidEnterBackground:.
    CRYPTO_set_locking_callback(NULL);
    CRYPTO_set_id_callback(NULL);
    delete[] cryptoMutexes;
    xmlCleanupParser();
}

#pragma mark - Split view

- (BOOL)splitViewController:(UISplitViewController *)splitViewController collapseSecondaryViewController:(UIViewController *)secondaryViewController ontoPrimaryViewController:(UIViewController *)primaryViewController {
    if ([secondaryViewController isKindOfClass:[UINavigationController class]] && [[(UINavigationController *)secondaryViewController topViewController] isKindOfClass:[DetailViewController class]] && ([(DetailViewController *)[(UINavigationController *)secondaryViewController topViewController] detailItem] == nil)) {
        // Return YES to indicate that we have handled the collapse by doing nothing; the secondary controller will be discarded.
        return YES;
    } else {
        return NO;
    }
}

@end
