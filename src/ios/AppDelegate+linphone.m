//
//  AppDelegate+linphone.m
//  cordova-plugin-sip
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


@implementation AppDelegate (linphone)

BOOL linphoneFromPush;
BOOL linphoneSwapped;

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
        NSLog(@"linphone: installing swizzle");
        Class class = [self class];

        SEL originalSelector = @selector(init);
        SEL swizzledSelector = @selector(linphone_swizzled_init);

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

- (AppDelegate *)linphone_swizzled_init
{
    NSLog(@"linphone swizzled init");
    UNUserNotificationCenter *center = [UNUserNotificationCenter currentNotificationCenter];
    center.delegate = self;

    [[NSNotificationCenter defaultCenter]addObserver:self selector:@selector(linphoneOnApplicationDidBecomeActive:) name:UIApplicationDidBecomeActiveNotification object:nil];

    [[NSNotificationCenter defaultCenter]addObserver:self selector:@selector(linphoneOnApplicationDidEnterBackground:) name:UIApplicationDidEnterBackgroundNotification object:nil];

    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(initLinphoneCore:) name:@"UIApplicationDidFinishLaunchingNotification" object:nil];

    // This actually calls the original init method over in AppDelegate. Equivilent to calling super
    // on an overrided method, this is not recursive, although it appears that way. neat huh?
    return [self linphone_swizzled_init];
}

- (void)clearNotifications {
    [[UNUserNotificationCenter currentNotificationCenter] getDeliveredNotificationsWithCompletionHandler:^(NSArray<UNNotification *> * _Nonnull notifications) {
        NSMutableArray <NSString *> *identifiersToRemove = [@[] mutableCopy];
        for (UNNotification *notification in notifications) {
            if ([notification.request.content.categoryIdentifier isEqualToString:@"incoming_call"]) {
                [identifiersToRemove addObject:notification.request.identifier];
            }
        }
        [[UNUserNotificationCenter currentNotificationCenter] removeDeliveredNotificationsWithIdentifiers:identifiersToRemove];
    }];
}

- (void)linphoneOnApplicationDidBecomeActive:(NSNotification *)notification
{
    NSLog(@"linphone onApplicationDidBecomeActive");
    [[LinphoneManager instance] becomeActive];
    NSLog(@"linphone Entered foreground");
    [self clearNotifications];
    if (linphoneFromPush) {
        linphoneFromPush = FALSE;
        [self showIncomingDialog];
        NSLog(@"linphone opened from push notification");
    }
}

- (void)linphoneOnApplicationDidEnterBackground:(NSNotification *)notification
{
    NSLog(@"linphone onApplicationDidEnterBackground");
    [[LinphoneManager instance] enterBackgroundMode];
    NSLog(@"Entered background");
}

//  FCM refresh token
//  Unclear how this is testable under normal circumstances
- (void)linphoneOnTokenRefresh {
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
- (void)linphoneSendDataMessageFailure:(NSNotification *)notification {
    NSLog(@"sendDataMessageFailure");
}

- (void)linphoneSendDataMessageSuccess:(NSNotification *)notification {
    NSLog(@"sendDataMessageSuccess");
}

- (void)linphoneDidSendDataMessageWithID:messageID {
    NSLog(@"didSendDataMessageWithID");
}

- (void)linphoneWillSendDataMessageWithID:messageID error:error {
    NSLog(@"willSendDataMessageWithID");
}

- (void)linphoneDidDeleteMessagesOnServer {
    NSLog(@"didDeleteMessagesOnServer");
    // Some messages sent to this device were deleted on the GCM server before reception, likely
    // because the TTL expired. The client should notify the app server of this, so that the app
    // server can resend those messages.
}

- (void)showIncomingDialog {
    Linphone *linphone = [self getCommandInstance:@"Linphone"];
    [linphone showCallView];
}

- (void)linphoneApplication:(UIApplication *)application didReceiveRemoteNotification:(NSDictionary *)userInfo fetchCompletionHandler:(void (^)(UIBackgroundFetchResult))completionHandler
{
    NSLog(@"linphone Received Remote notification");
    if (linphoneSwapped) {  // call swizzled method
        [self linphoneApplication:application didReceiveRemoteNotification:userInfo fetchCompletionHandler:completionHandler];
    }
    if ([userInfo[@"aps"][@"category"] isEqual: @"incoming_call"]) {
        NSLog(@"incoming call");
        if(application.applicationState != UIApplicationStateActive) {
            linphoneFromPush = TRUE;
        } else {
            [self showIncomingDialog];
            NSLog(@"received push notification while foreground");
            linphoneFromPush = FALSE;
        }
    } else {
        linphoneFromPush = FALSE;
    }
    completionHandler(UIBackgroundFetchResultNoData);
}

- (void)linphoneSetupPushHandlers
{
    // this = self;
    if ([[[UIApplication sharedApplication] delegate] respondsToSelector:@selector(application:didReceiveRemoteNotification:fetchCompletionHandler:)]) {
        NSLog(@"linphone swapping listener");
        Method original, swizzled;
        original = class_getInstanceMethod([self class], @selector(linphoneApplication:didReceiveRemoteNotification:fetchCompletionHandler:));
        swizzled = class_getInstanceMethod([[[UIApplication sharedApplication] delegate] class], @selector(application:didReceiveRemoteNotification:fetchCompletionHandler:));
        method_exchangeImplementations(original, swizzled);
        linphoneSwapped = TRUE;
    } else {
        NSLog(@"linphone adding listener");
        class_addMethod([[[UIApplication sharedApplication] delegate] class], @selector(application:didReceiveRemoteNotification:fetchCompletionHandler:), class_getMethodImplementation([self class], @selector(linphoneApplication:didReceiveRemoteNotification:fetchCompletionHandler:)), nil);
        linphoneSwapped = FALSE;
    }
}

- (void)initPushNotifications {
    dispatch_async(dispatch_get_main_queue(), ^{
        if ([FIRApp defaultApp] == nil) {
            NSLog(@"linphone configuring Firebase");
            [FIRApp configure];
        }
        [[NSNotificationCenter defaultCenter]
         addObserver:self selector:@selector(linphoneOnTokenRefresh)
         name:kFIRInstanceIDTokenRefreshNotification object:nil];

        [[NSNotificationCenter defaultCenter]
         addObserver:self selector:@selector(linphoneSendDataMessageFailure:)
         name:FIRMessagingSendErrorNotification object:nil];

        [[NSNotificationCenter defaultCenter]
         addObserver:self selector:@selector(linphoneSendDataMessageSuccess:)
         name:FIRMessagingSendSuccessNotification object:nil];

        [[NSNotificationCenter defaultCenter]
         addObserver:self selector:@selector(linphoneDidDeleteMessagesOnServer)
         name:FIRMessagingMessagesDeletedNotification object:nil];
        [self linphoneSetupPushHandlers];
    });
    if (![self linphonePermissionState]) {
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

- (BOOL)linphonePermissionState
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
    linphoneFromPush = FALSE;
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
    [[NSNotificationCenter defaultCenter] removeObserver:self name:UIApplicationDidFinishLaunchingNotification object:nil];

    [[LinphoneManager instance] destroyLinphoneCore];
}

@end
