//
//  AppDelegate+notification.m
//  pushtest
//
//  Created by Robert Easterday on 10/26/12.
//
//

#import "AppDelegate+linphone.h"
#import "LinphoneManager.h"
#import <objc/runtime.h>

@implementation AppDelegate (linphone)

// its dangerous to override a method from within a category.
// Instead we will use method swizzling. we set this up in the load call.
+ (void)load
{
    Method original, swizzled;
    
    original = class_getInstanceMethod(self, @selector(init));
    swizzled = class_getInstanceMethod(self, @selector(swizzled_init));
    method_exchangeImplementations(original, swizzled);
}

- (AppDelegate *)swizzled_init
{
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(initLinphoneCore:)
                                                 name:@"UIApplicationDidFinishLaunchingNotification" object:nil];
    
    // This actually calls the original init method over in AppDelegate. Equivilent to calling super
    // on an overrided method, this is not recursive, although it appears that way. neat huh?
    return [self swizzled_init];
}

- (void)initLinphoneCore:(NSNotification *)notification
{
    NSLog(@"initLinphoneCore");
    [[LinphoneManager instance] launchLinphoneCore];
    //[[LinphoneManager instance] initLinphoneCore];
    //[[LinphoneManager instance] setFirewallPolicy:@"PolicyNoFirewall"];
}

- (void)dealloc
{
    NSLog(@"dealloc");
    [[LinphoneManager instance] destroyLinphoneCore];
}

@end
