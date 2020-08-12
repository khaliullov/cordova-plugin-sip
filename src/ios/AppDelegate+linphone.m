//
//  AppDelegate+notification.m
//  pushtest
//
//  Created by Robert Easterday on 10/26/12.
//
//

#import "AppDelegate+linphone.h"
#import "LinphoneManager.h"
#import "Linphone.h"
#import <objc/runtime.h>

@import Firebase;
@import FirebaseInstanceID;


NSString *const pushPluginApplicationDidBecomeActiveNotification = @"pushPluginApplicationDidBecomeActiveNotification";


@implementation AppDelegate (linphone)

BOOL fromPush;

- (id) getCommandInstance:(NSString*)className
{
    return [self.viewController getCommandInstance:className];
}

// its dangerous to override a method from within a category.
// Instead we will use method swizzling. we set this up in the load call.
+ (void)load
{
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        Class class = [self class];

        SEL originalSelector = @selector(init);
        SEL swizzledSelector = @selector(swizzled_init);

        Method original = class_getInstanceMethod(class, originalSelector);
        Method swizzled = class_getInstanceMethod(class, swizzledSelector);

        BOOL didAddMethod =
        class_addMethod(class,
                        originalSelector,
                        method_getImplementation(swizzled),
                        method_getTypeEncoding(swizzled));

        if (didAddMethod) {
            class_replaceMethod(class,
                                swizzledSelector,
                                method_getImplementation(original),
                                method_getTypeEncoding(original));
        } else {
            method_exchangeImplementations(original, swizzled);
        }
    });
}

- (AppDelegate *)swizzled_init
{
    UNUserNotificationCenter *center = [UNUserNotificationCenter currentNotificationCenter];
    center.delegate = self;

    [[NSNotificationCenter defaultCenter]addObserver:self selector:@selector(onApplicationDidBecomeActive:) name:UIApplicationDidBecomeActiveNotification object:nil];

    [[NSNotificationCenter defaultCenter]addObserver:self selector:@selector(onApplicationDidEnterBackground:) name:UIApplicationDidEnterBackgroundNotification object:nil];

    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(initLinphoneCore:) name:@"UIApplicationDidFinishLaunchingNotification" object:nil];

    // This actually calls the original init method over in AppDelegate. Equivilent to calling super
    // on an overrided method, this is not recursive, although it appears that way. neat huh?
    return [self swizzled_init];
}

- (void)onApplicationDidBecomeActive:(NSNotification *)notification
{
    NSLog(@"onApplicationDidBecomeActive");
    [[LinphoneManager instance] becomeActive];
    NSLog(@"Entered foreground");
    if (fromPush) {
        [self showIncomingDialog];
        NSLog(@"opened from push notification");
    }
}

- (void)onApplicationDidEnterBackground:(NSNotification *)notification
{
    NSLog(@"onApplicationDidEnterBackground");
    [[LinphoneManager instance] enterBackgroundMode];
    NSLog(@"Entered background");
}

//  FCM refresh token
//  Unclear how this is testable under normal circumstances
- (void)onTokenRefresh {
#if !TARGET_IPHONE_SIMULATOR
    // A rotation of the registration tokens is happening, so the app needs to request a new token.
    NSLog(@"The FCM registration token needs to be changed.");
    [[FIRInstanceID instanceID] instanceIDWithHandler:^(FIRInstanceIDResult * _Nullable result, NSError * _Nullable error) {
        if (error != nil) {
            NSLog(@"Error fetching remote instance ID: %@", error);
        } else {
            NSLog(@"Remote instance ID token: %@", result.token);
            NSData* token = [result.token dataUsingEncoding:NSUTF8StringEncoding];
            [[LinphoneManager instance] setRemoteNotificationToken:token];
        }
    }];
    // [self initRegistration];
#endif
}

// contains error info
- (void)sendDataMessageFailure:(NSNotification *)notification {
    NSLog(@"sendDataMessageFailure");
}

- (void)sendDataMessageSuccess:(NSNotification *)notification {
    NSLog(@"sendDataMessageSuccess");
}

- (void)didSendDataMessageWithID:messageID {
    NSLog(@"didSendDataMessageWithID");
}

- (void)willSendDataMessageWithID:messageID error:error {
    NSLog(@"willSendDataMessageWithID");
}

- (void)didDeleteMessagesOnServer {
    NSLog(@"didDeleteMessagesOnServer");
    // Some messages sent to this device were deleted on the GCM server before reception, likely
    // because the TTL expired. The client should notify the app server of this, so that the app
    // server can resend those messages.
}

- (void)showIncomingDialog {
    Linphone *linphone = [self getCommandInstance:@"Linphone"];
    [linphone showCallView];
}

- (void)application:(UIApplication *)application didReceiveRemoteNotification:(NSDictionary *)userInfo fetchCompletionHandler:(void (^)(UIBackgroundFetchResult))completionHandler
{
    NSLog(@"Received Remote notification");
    if(application.applicationState != UIApplicationStateActive) {
        fromPush = TRUE;
    } else {
        [self showIncomingDialog];
        NSLog(@"received push notification while foreground");
        fromPush = FALSE;
    }
    completionHandler(UIBackgroundFetchResultNoData);
    //this.completionHandler = completionHandler;
    //[this dispatchPushEvent:userInfo];
    //[self application:application didReceiveRemoteNotification:userInfo fetchCompletionHandler:completionHandler];
}

- (void)setupPushHandlers
{
    // this = self;
    if ([[[UIApplication sharedApplication] delegate] respondsToSelector:@selector(application:didReceiveRemoteNotification:fetchCompletionHandler:)]) {
        NSLog(@"swapping listener");
        Method original, swizzled;
        original = class_getInstanceMethod([self class], @selector(application:didReceiveRemoteNotification:fetchCompletionHandler:));
        swizzled = class_getInstanceMethod([[[UIApplication sharedApplication] delegate] class], @selector(application:didReceiveRemoteNotification:fetchCompletionHandler:));
        method_exchangeImplementations(original, swizzled);
    } else {
        NSLog(@"adding listener");
        class_addMethod([[[UIApplication sharedApplication] delegate] class], @selector(application:didReceiveRemoteNotification:fetchCompletionHandler:), class_getMethodImplementation([self class], @selector(application:didReceiveRemoteNotification:fetchCompletionHandler:)), nil);
    }
}

- (void)initPushNotifications {
    if ([FIRApp defaultApp] == nil) {
        NSLog(@"configuring Firebase");
        [FIRApp configure];

        // TODO check if this works with other plugins with using Firebase
        [[NSNotificationCenter defaultCenter]
         addObserver:self selector:@selector(onTokenRefresh)
         name:kFIRInstanceIDTokenRefreshNotification object:nil];

        [[NSNotificationCenter defaultCenter]
         addObserver:self selector:@selector(sendDataMessageFailure:)
         name:FIRMessagingSendErrorNotification object:nil];

        [[NSNotificationCenter defaultCenter]
         addObserver:self selector:@selector(sendDataMessageSuccess:)
         name:FIRMessagingSendSuccessNotification object:nil];

        [[NSNotificationCenter defaultCenter]
         addObserver:self selector:@selector(didDeleteMessagesOnServer)
         name:FIRMessagingMessagesDeletedNotification object:nil];
        [self setupPushHandlers];
    }
    if (![self permissionState]) {
        NSLog(@"push notifications are not registered");
        if ([UNUserNotificationCenter class] != nil) {
          // iOS 10 or later
          // For iOS 10 display notification (sent via APNS)
          // [UNUserNotificationCenter currentNotificationCenter].delegate = self;
          UNAuthorizationOptions authOptions = UNAuthorizationOptionAlert | UNAuthorizationOptionSound | UNAuthorizationOptionBadge;
          [[UNUserNotificationCenter currentNotificationCenter]
              requestAuthorizationWithOptions:authOptions
              completionHandler:^(BOOL granted, NSError * _Nullable error) {
                // ...
                if (granted && !error) {
                    dispatch_async(dispatch_get_main_queue(), ^{
                           [[UIApplication sharedApplication] registerForRemoteNotifications];
                           NSLog(@"registered for remote push");
                       });
                } else {
                    NSLog(@"not granted for iOS");
                }
              }];
        } else {
            NSLog(@"not supported iOS version for notifications");
        }
    }
}

- (BOOL)permissionState
{
    if ([[UIApplication sharedApplication] respondsToSelector:@selector(isRegisteredForRemoteNotifications)])
    {
        return [[UIApplication sharedApplication] isRegisteredForRemoteNotifications];
    } else {
        return [[UIApplication sharedApplication] enabledRemoteNotificationTypes] != UIRemoteNotificationTypeNone;
    }
}

- (void)initLinphoneCore:(NSNotification *)notification
{
    fromPush = FALSE;
    [self initPushNotifications];
    NSLog(@"initLinphoneCore");
    [[LinphoneManager instance] launchLinphoneCore];
    //[[LinphoneManager instance] initLinphoneCore];
    //[[LinphoneManager instance] setFirewallPolicy:@"PolicyNoFirewall"];
}

- (void)dealloc
{
    NSLog(@"dealloc");
    [[NSNotificationCenter defaultCenter] removeObserver:self name:UIApplicationDidBecomeActiveNotification object:nil];
    [[NSNotificationCenter defaultCenter] removeObserver:self name:UIApplicationDidEnterBackgroundNotification object:nil];

    [[LinphoneManager instance] destroyLinphoneCore];
}

@end
