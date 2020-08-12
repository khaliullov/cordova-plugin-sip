//
//  AppDelegate+linphone.h
//  HelloCordova
//
//  Created by Thien on 5/3/16.
//
//

@import UserNotifications;

#import "AppDelegate.h"


extern NSString *const pushPluginApplicationDidBecomeActiveNotification;


@interface AppDelegate (linphone) <UNUserNotificationCenterDelegate>
@end
