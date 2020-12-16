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
unsigned char linphoneSwapped;
const unsigned char linphoneSwapped_userNotificationCenter_willPresentNotification = 1;
const unsigned char linphoneSwapped_userNotificationCenter_didReceiveNotificationResponse = 2;
const unsigned char linphoneSwapped_application_didReceiveRemoteNotification = 4;

NSString *door_open_url;
NSString *address;
NSString *entrance;
NSString *action;

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
            [self initPushCategories];
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
    NSLog(@"linphone show incoming dialog");
    Linphone *linphone = [self getCommandInstance:@"Linphone"];
    linphone.action = action;
    linphone.address = address;
    linphone.entrance = entrance;
    linphone.door_open_url = door_open_url;
    [linphone showCallView];
}

- (void)refreshDoorOpenURLs:(NSDictionary *)userInfo {
    if (userInfo[@"door_open_urls"]) {
        NSLog(@"refreshing door_open_urls");
        Linphone *linphone = [self getCommandInstance:@"Linphone"];
        [linphone refreshDoorOpenURLs:userInfo];
    }
}

// for background pushes (apns-push-type: background)
- (void)linphoneApplication:(UIApplication *)application didReceiveRemoteNotification:(NSDictionary *)userInfo fetchCompletionHandler:(void (^)(UIBackgroundFetchResult))completionHandler
{
    NSLog(@"linphone received background application:didReceiveRemoteNotification");
    NSLog(@"linphone application:didReceiveRemoteNotification userInfo: %@", userInfo);
    if (linphoneSwapped & linphoneSwapped_application_didReceiveRemoteNotification) {  // call swizzled method
        [self linphoneApplication:application didReceiveRemoteNotification:userInfo fetchCompletionHandler:completionHandler];
    }
    if ([userInfo[@"aps"][@"category"] isEqual: @"refresh_registration"]) {
        NSLog(@"linphone refresh_registration");
        [self refreshRegistration];
        [self refreshDoorOpenURLs:userInfo];
    }
    completionHandler(UIBackgroundFetchResultNoData);
}

- (void)linphoneSetupPushHandlers
{
    UNUserNotificationCenter *center = [UNUserNotificationCenter currentNotificationCenter];

    linphoneSwapped = 0;
    // application:didReceiveRemoteNotification:fetchCompletionHandler:
    if ([[[UIApplication sharedApplication] delegate] respondsToSelector:@selector(application:didReceiveRemoteNotification:fetchCompletionHandler:)]) {
        NSLog(@"linphone swapping application:didReceiveRemoteNotification listener");
        Method original, swizzled;
        original = class_getInstanceMethod([self class], @selector(linphoneApplication:didReceiveRemoteNotification:fetchCompletionHandler:));
        swizzled = class_getInstanceMethod([[[UIApplication sharedApplication] delegate] class], @selector(application:didReceiveRemoteNotification:fetchCompletionHandler:));
        method_exchangeImplementations(original, swizzled);
        linphoneSwapped += linphoneSwapped_application_didReceiveRemoteNotification;
    } else {
        NSLog(@"linphone adding application:didReceiveRemoteNotification listener");
        class_addMethod([[[UIApplication sharedApplication] delegate] class], @selector(application:didReceiveRemoteNotification:fetchCompletionHandler:), class_getMethodImplementation([self class], @selector(linphoneApplication:didReceiveRemoteNotification:fetchCompletionHandler:)), nil);
    }
    // userNotificationCenter:willPresentNotification:withCompletionHandler:
    if ([[center delegate] respondsToSelector:@selector(userNotificationCenter:willPresentNotification:withCompletionHandler:)]) {
        NSLog(@"linphone swapping userNotificationCenter:willPresentNotification listener");
        Method original, swizzled;
        original = class_getInstanceMethod([self class], @selector(linphoneUserNotificationCenter:willPresentNotification:withCompletionHandler:));
        swizzled = class_getInstanceMethod([[center delegate] class], @selector(userNotificationCenter:willPresentNotification:withCompletionHandler:));
        method_exchangeImplementations(original, swizzled);
        linphoneSwapped += linphoneSwapped_userNotificationCenter_willPresentNotification;
    } else {
        NSLog(@"linphone adding userNotificationCenter:willPresentNotification listener");
        class_addMethod([[center delegate] class], @selector(userNotificationCenter:willPresentNotification:withCompletionHandler:), class_getMethodImplementation([self class], @selector(linphoneUserNotificationCenter:willPresentNotification:withCompletionHandler:)), nil);
    }
    // userNotificationCenter:didReceiveNotificationResponse:withCompletionHandler:
    if ([[center delegate] respondsToSelector:@selector(userNotificationCenter:didReceiveNotificationResponse:withCompletionHandler:)]) {
        NSLog(@"linphone swapping userNotificationCenter:didReceiveNotificationResponse listener");
        Method original, swizzled;
        original = class_getInstanceMethod([self class], @selector(linphoneUserNotificationCenter:didReceiveNotificationResponse:withCompletionHandler:));
        swizzled = class_getInstanceMethod([[center delegate] class], @selector(userNotificationCenter:didReceiveNotificationResponse:withCompletionHandler:));
        method_exchangeImplementations(original, swizzled);
        linphoneSwapped += linphoneSwapped_userNotificationCenter_didReceiveNotificationResponse;
    } else {
        NSLog(@"linphone adding userNotificationCenter:didReceiveNotificationResponse listener");
        class_addMethod([[center delegate] class], @selector(userNotificationCenter:didReceiveNotificationResponse:withCompletionHandler:), class_getMethodImplementation([self class], @selector(linphoneUserNotificationCenter:didReceiveNotificationResponse:withCompletionHandler:)), nil);
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
            [UNUserNotificationCenter currentNotificationCenter].delegate = self;
            UNAuthorizationOptions authOptions = UNAuthorizationOptionAlert | UNAuthorizationOptionSound | UNAuthorizationOptionBadge;
            [[UNUserNotificationCenter currentNotificationCenter] requestAuthorizationWithOptions:authOptions completionHandler:^(BOOL granted, NSError * _Nullable error) {
                if (error == nil) {
                    dispatch_async(dispatch_get_main_queue(), ^{
                        [[UIApplication sharedApplication] registerForRemoteNotifications];
                        NSLog(@"registered for remote push");
                        [self initPushCategories];
                    });
                } else {
                    NSLog(@"not granted for iOS");
                }
            }];
        } else {
            NSLog(@"not supported iOS version for notifications");
        }
    } else {
        [self initPushCategories];
    }
}

- (void)initPushCategories
{
    NSLog(@"initializing push categories");
    if ([UNUserNotificationCenter class] != nil) {
        UNUserNotificationCenter* center = [UNUserNotificationCenter currentNotificationCenter];
        [center getNotificationCategoriesWithCompletionHandler:^(NSSet<UNNotificationCategory *> * _Nonnull categories) {
            BOOL found = FALSE;
            for(UNNotificationCategory* category in categories) {
                if ([category.identifier isEqualToString:@"incoming_call"]) {
                    found = TRUE;
                    break;
                }
            }
            if (found == FALSE) {
                NSMutableSet* newCategories = [categories mutableCopy];
                NSLog(@"push category 'incoming_call' was not found");
                UNNotificationAction *pickupAction = [UNNotificationAction actionWithIdentifier:@"incoming_call.pickup" title:@"Ответить" options:UNNotificationActionOptionForeground];
                UNNotificationAction *unlockAction = [UNNotificationAction actionWithIdentifier:@"incoming_call.unlock" title:@"Открыть" options:UNNotificationActionOptionForeground];
                UNNotificationAction *declineAction = [UNNotificationAction actionWithIdentifier:@"incoming_call.hangup" title:@"Отклонить" options:UNNotificationActionOptionForeground];
                UNNotificationCategory *notificationCategory = [UNNotificationCategory categoryWithIdentifier:@"incoming_call" actions:@[pickupAction, unlockAction, declineAction] intentIdentifiers:@[] options:UNNotificationCategoryOptionCustomDismissAction];
                [newCategories addObject:notificationCategory];
                [center setNotificationCategories:newCategories];
            } else {
                NSLog(@"push category 'incoming_call' already initilized");
            }
        }];
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
    if ([self linphonePermissionState]) {
        NSLog(@"linphone initialising notification center delegate");
        [UNUserNotificationCenter currentNotificationCenter].delegate = self;
        [[UIApplication sharedApplication] registerForRemoteNotifications];
    }
    [self initPushNotifications];
    NSLog(@"initLinphoneCore");
    Linphone *linphone = [self getCommandInstance:@"Linphone"];
    [NSNotificationCenter.defaultCenter addObserver:linphone selector:@selector(onCallStateChanged:) name:@"LinphoneCallUpdate" object:nil];
    [[LinphoneManager instance] launchLinphoneCore];
    //[[LinphoneManager instance] initLinphoneCore];
    //[[LinphoneManager instance] setFirewallPolicy:@"PolicyNoFirewall"];
}

- (void)refreshRegistration
{
    NSLog(@"linphone refreshing registration");
    Linphone *linphone = [self getCommandInstance:@"Linphone"];
    [linphone ensureRegistered];
}

- (void)dealloc
{
    NSLog(@"dealloc");
    [[NSNotificationCenter defaultCenter] removeObserver:self name:UIApplicationDidBecomeActiveNotification object:nil];
    [[NSNotificationCenter defaultCenter] removeObserver:self name:UIApplicationDidEnterBackgroundNotification object:nil];
    [[NSNotificationCenter defaultCenter] removeObserver:self name:UIApplicationDidFinishLaunchingNotification object:nil];
    Linphone *linphone = [self getCommandInstance:@"Linphone"];
    [NSNotificationCenter.defaultCenter removeObserver:linphone name:@"LinphoneCallUpdate" object:nil];

    [[LinphoneManager instance] destroyLinphoneCore];
}

#pragma mark - REMOTE NOTIFICATION DELEGATE

-(void)linphoneUserNotificationCenter:(UNUserNotificationCenter *)center willPresentNotification:(UNNotification *)notification withCompletionHandler:(void (^)(UNNotificationPresentationOptions options))completionHandler{
    //Called when a notification is delivered to a foreground app.
    NSDictionary *userInfo = notification.request.content.userInfo;
    NSLog(@"linphone userNotificationCenter:willPresentNotification userInfo: %@", userInfo);
    if (linphoneSwapped & linphoneSwapped_userNotificationCenter_willPresentNotification) {  // call swizzled method
        [self linphoneUserNotificationCenter:center willPresentNotification:notification withCompletionHandler:completionHandler];
    }
    linphoneFromPush = FALSE;
    if ([userInfo[@"aps"][@"category"] isEqual: @"incoming_call"]) {
        NSLog(@"linphone received push notification while foreground");
        door_open_url = userInfo[@"door_open_url"];
        action = UNNotificationDefaultActionIdentifier;
        address = userInfo[@"address"];
        entrance = userInfo[@"entrance"];
        [self showIncomingDialog];
        //completionHandler(UNNotificationPresentationOptionSound);
    }
    completionHandler(UNNotificationPresentationOptionNone);
}

-(void)linphoneUserNotificationCenter:(UNUserNotificationCenter *)center didReceiveNotificationResponse:(UNNotificationResponse *)response withCompletionHandler:(void (^)(void))completionHandler{
    NSDictionary *userInfo = response.notification.request.content.userInfo;
    NSLog(@"linphone userNotificationCenter:didReceiveNotificationResponse userInfo: %@", userInfo);
    if (linphoneSwapped & linphoneSwapped_userNotificationCenter_didReceiveNotificationResponse) {  // call swizzled method
        [self linphoneUserNotificationCenter:center didReceiveNotificationResponse:response withCompletionHandler:completionHandler];
    }
    if ([userInfo[@"aps"][@"category"] isEqual: @"incoming_call"]) {
        linphoneFromPush = TRUE;
        door_open_url = userInfo[@"door_open_url"];
        action = response.actionIdentifier;
        address = userInfo[@"address"];
        entrance = userInfo[@"entrance"];
    }
    completionHandler();
}
@end
