/*
* Copyright (c) 2010-2020 Belledonne Communications SARL.
*
* This file is part of linphone-iphone
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

#ifndef SIP_Plugin_Linphone_Manager_h
#define SIP_Plugin_Linphone_Manager_h

#import <AudioToolbox/AudioToolbox.h>
#import <CoreTelephony/CTCallCenter.h>

#import <sqlite3.h>

#include "linphone/linphonecore.h"

/* NSString *const kLinphoneMsgNotificationAppGroupId;
NSString *const LINPHONERC_APPLICATION_KEY;
NSString *const kLinphoneCoreUpdate;
NSString *const kLinphoneGlobalStateUpdate;
NSString *const kLinphoneConfiguringStateUpdate; */

typedef struct _CallContext {
    LinphoneCall* call;
} CallContext;

typedef struct _LinphoneManagerSounds {
    SystemSoundID vibrate;
} LinphoneManagerSounds;

@interface LinphoneManager : NSObject {
    @private
        NSTimer* mIterateTimer;
            NSMutableArray*  pushCallIDs;

        UIBackgroundTaskIdentifier pausedCallBgTask;
        UIBackgroundTaskIdentifier incallBgTask;
        UIBackgroundTaskIdentifier pushBgTaskRefer;
        UIBackgroundTaskIdentifier pushBgTaskCall;
        UIBackgroundTaskIdentifier pushBgTaskMsg;
        CTCallCenter* mCallCenter;
        NSDate *mLastKeepAliveDate;
    @public
        CallContext currentCallContextBeforeGoingBackground;
}

+ (LinphoneManager*)instance;  // +
+ (LinphoneCore*) getLc;  // +
+ (BOOL)isLcInitialized;  // +
+ (NSString *)getUserAgent;  // +
- (void)resetLinphoneCore;  // +
- (void)launchLinphoneCore;  // +
- (void)destroyLinphoneCore;  // +
- (void)startLinphoneCore;
- (BOOL)resignActive;  // +
- (void)becomeActive;  // +
- (BOOL)enterBackgroundMode;  // +
- (void)addPushCallId:(NSString*) callid;  // +
- (void)configurePushTokenForProxyConfigs;
- (void)configurePushTokenForProxyConfig: (LinphoneProxyConfig*)cfg;
- (BOOL)popPushCallID:(NSString*) callId;  // +
- (void)acceptCallForCallId:(NSString*)callid;  // +
- (void)refreshRegisters;  //+

+ (BOOL)copyFile:(NSString*)src destination:(NSString*)dst override:(BOOL)override ignore:(BOOL)ignore;
+ (NSString*)bundleFile:(NSString*)file;
+ (NSString *)documentFile:(NSString *)file;
+ (NSString*)cacheDirectory;
+ (BOOL)copyFile:(NSString*)src destination:(NSString*)dst override:(BOOL)override ignore:(BOOL)ignore;

- (void)call:(const LinphoneAddress *)address;

- (void)lpConfigSetString:(NSString*)value forKey:(NSString*)key;
- (void)lpConfigSetString:(NSString *)value forKey:(NSString *)key inSection:(NSString *)section;
- (NSString *)lpConfigStringForKey:(NSString *)key;
- (NSString *)lpConfigStringForKey:(NSString *)key inSection:(NSString *)section;
- (NSString *)lpConfigStringForKey:(NSString *)key withDefault:(NSString *)value;
- (NSString *)lpConfigStringForKey:(NSString *)key inSection:(NSString *)section withDefault:(NSString *)value;

- (void)lpConfigSetInt:(int)value forKey:(NSString *)key;
- (void)lpConfigSetInt:(int)value forKey:(NSString *)key inSection:(NSString *)section;
- (int)lpConfigIntForKey:(NSString *)key;
- (int)lpConfigIntForKey:(NSString *)key inSection:(NSString *)section;
- (int)lpConfigIntForKey:(NSString *)key withDefault:(int)value;
- (int)lpConfigIntForKey:(NSString *)key inSection:(NSString *)section withDefault:(int)value;

- (void)lpConfigSetBool:(BOOL)value forKey:(NSString*)key;
- (void)lpConfigSetBool:(BOOL)value forKey:(NSString *)key inSection:(NSString *)section;
- (BOOL)lpConfigBoolForKey:(NSString *)key;
- (BOOL)lpConfigBoolForKey:(NSString *)key inSection:(NSString *)section;
- (BOOL)lpConfigBoolForKey:(NSString *)key withDefault:(BOOL)value;
- (BOOL)lpConfigBoolForKey:(NSString *)key inSection:(NSString *)section withDefault:(BOOL)value;

@property(strong, nonatomic) NSString *SSID;
@property (readonly) sqlite3* database;
@property(nonatomic, strong) NSData *remoteNotificationToken;
@property (readonly) LinphoneManagerSounds sounds;
@property (nonatomic, assign) BOOL bluetoothAvailable;
@property (copy) void (^silentPushCompletion)(UIBackgroundFetchResult);
@property (readonly) LpConfig *configDb;
@property BOOL conf;
@property NSDictionary *pushDict;

@end

#endif
