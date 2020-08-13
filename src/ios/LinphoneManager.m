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
#import <AudioToolbox/AudioToolbox.h>
#import <AVFoundation/AVFoundation.h>
#import <UserNotifications/UserNotifications.h>

@import Firebase;

#include "linphone/factory.h"
#include "linphone/linphonecore_utils.h"

#include "LinphoneManager.h"
#include "Log.h"
#include "Utils.h"

#import "ProductModuleName-Swift.h"

static LinphoneCore *theLinphoneCore = nil;
static LinphoneManager *theLinphoneManager = nil;

//NSString *const kLinphoneMsgNotificationAppGroupId = @"group.org.linphone.phone.msgNotification";
NSString *const kLinphoneMsgNotificationAppGroupId = @"test group id";
NSString *const LINPHONERC_APPLICATION_KEY = @"app";
NSString *const kLinphoneCoreUpdate = @"LinphoneCoreUpdate";
NSString *const kLinphoneCallUpdate = @"LinphoneCallUpdate";
NSString *const kLinphoneRegistrationUpdate = @"LinphoneRegistrationUpdate";
NSString *const kLinphoneGlobalStateUpdate = @"LinphoneGlobalStateUpdate";
NSString *const kLinphoneConfiguringStateUpdate = @"LinphoneConfiguringStateUpdate";

extern void libmsamr_init(MSFactory *factory);
extern void libmsx264_init(MSFactory *factory);
extern void libmsopenh264_init(MSFactory *factory);
extern void libmssilk_init(MSFactory *factory);
extern void libmswebrtc_init(MSFactory *factory);
extern void libmscodec2_init(MSFactory *factory);

@implementation LinphoneManager

+ (BOOL)runningOnIpad {
    return ([[UIDevice currentDevice] userInterfaceIdiom] == UIUserInterfaceIdiomPad);
}

+ (NSString *)getUserAgent {
    return
        [NSString stringWithFormat:@"LinphoneIphone/%@ (Linphone/%s; Apple %@/%@)",
         [[NSBundle mainBundle] objectForInfoDictionaryKey:(NSString *)kCFBundleVersionKey],
         linphone_core_get_version(), [UIDevice currentDevice].systemName,
         [UIDevice currentDevice].systemVersion];
}

+ (LinphoneManager *)instance {
    @synchronized(self) {
        if (theLinphoneManager == nil) {
            theLinphoneManager = [[LinphoneManager alloc] init];
        }
    }
    return theLinphoneManager;
}

#pragma mark - Lifecycle Functions

- (id)init {
    if ((self = [super init])) {

        _sounds.vibrate = kSystemSoundID_Vibrate;

        _pushDict = [[NSMutableDictionary alloc] init];
        _database = NULL;
        _conf = FALSE;
        pushCallIDs = [[NSMutableArray alloc] init];
        [self renameDefaultSettings];
        [self copyDefaultSettings];
        [self overrideDefaultSettings];

        // set default values for first boot
        if ([self lpConfigStringForKey:@"debugenable_preference"] == nil) {
#ifdef DEBUG
            [self lpConfigSetInt:1 forKey:@"debugenable_preference"];
#else
            [self lpConfigSetInt:0 forKey:@"debugenable_preference"];
#endif
        }

        // by default if handle_content_encoding is not set, we use plain text for debug purposes only
        if ([self lpConfigStringForKey:@"handle_content_encoding" inSection:@"misc"] == nil) {
#ifdef DEBUG
            [self lpConfigSetString:@"none" forKey:@"handle_content_encoding" inSection:@"misc"];
#else
            [self lpConfigSetString:@"conflate" forKey:@"handle_content_encoding" inSection:@"misc"];
#endif
        }

    }
    return self;
}

- (void)renameDefaultSettings {
    // rename .linphonerc to linphonerc to ease debugging: when downloading
    // containers from MacOSX, Finder do not display hidden files leading
    // to useless painful operations to display the .linphonerc file
    NSString *src = [LinphoneManager documentFile:@".linphonerc"];
    NSString *dst = [LinphoneManager documentFile:@"linphonerc"];
    NSFileManager *fileManager = [NSFileManager defaultManager];
    NSError *fileError = nil;
    if ([fileManager fileExistsAtPath:src]) {
        if ([fileManager fileExistsAtPath:dst]) {
            [fileManager removeItemAtPath:src error:&fileError];
            LOGW(@"%@ already exists, simply removing %@ %@", dst, src,
                 fileError ? fileError.localizedDescription : @"successfully");
        } else {
            [fileManager moveItemAtPath:src toPath:dst error:&fileError];
            LOGI(@"%@ moving to %@ %@", dst, src, fileError ? fileError.localizedDescription : @"successfully");
        }
    }
}

#pragma mark - Linphone Core Functions

+ (LinphoneCore *)getLc {
    if (theLinphoneCore == nil) {
        @throw([NSException exceptionWithName:@"LinphoneCoreException"
            reason:@"Linphone core not initialized yet"
            userInfo:nil]);
    }
    return theLinphoneCore;
}

+ (BOOL)isLcInitialized {
    if (theLinphoneCore == nil) {
        return NO;
    }
    return YES;
}

#pragma mark - Logs Functions handlers
static void linphone_iphone_log_user_info(struct _LinphoneCore *lc, const char *message) {
    linphone_iphone_log_handler(NULL, ORTP_MESSAGE, message, NULL);
}
static void linphone_iphone_log_user_warning(struct _LinphoneCore *lc, const char *message) {
    linphone_iphone_log_handler(NULL, ORTP_WARNING, message, NULL);
}

#pragma mark - Call State Functions

- (void)localNotifContinue:(NSTimer *)timer {
    UILocalNotification *notif = [timer userInfo];
    if (notif) {
        LOGI(@"cancelling/presenting local notif");
        [[UIApplication sharedApplication] cancelAllLocalNotifications];
        [[UIApplication sharedApplication] presentLocalNotificationNow:notif];
    }
}

- (void)userNotifContinue:(NSTimer *)timer {
    UNNotificationContent *content = [timer userInfo];
    if (content && [UIApplication sharedApplication].applicationState != UIApplicationStateActive) {
        LOGI(@"cancelling/presenting user notif");
        UNNotificationRequest *req =
            [UNNotificationRequest requestWithIdentifier:@"call_request" content:content trigger:NULL];
        [[UNUserNotificationCenter currentNotificationCenter] addNotificationRequest:req
         withCompletionHandler:^(NSError *_Nullable error) {
                // Enable or disable features based on authorization.
                if (error) {
                    LOGD(@"Error while adding notification request :");
                    LOGD(error.description);
                }
            }];
    }
}

#pragma mark - Global state change

static void linphone_iphone_global_state_changed(LinphoneCore *lc, LinphoneGlobalState gstate, const char *message) {
    [(__bridge LinphoneManager *)linphone_core_cbs_get_user_data(linphone_core_get_current_callbacks(lc)) onGlobalStateChanged:gstate withMessage:message];
}

- (void)onGlobalStateChanged:(LinphoneGlobalState)state withMessage:(const char *)message {
    LOGI(@"onGlobalStateChanged: %d (message: %s)", state, message);

    NSDictionary *dict = [NSDictionary
                  dictionaryWithObjectsAndKeys:[NSNumber numberWithInt:state], @"state",
                  [NSString stringWithUTF8String:message ? message : ""], @"message", nil];

    // dispatch the notification asynchronously
    dispatch_async(dispatch_get_main_queue(), ^(void) {
        if (theLinphoneCore && linphone_core_get_global_state(theLinphoneCore) != LinphoneGlobalOff)
            [NSNotificationCenter.defaultCenter postNotificationName:kLinphoneGlobalStateUpdate object:self userInfo:dict];
    });
}

- (void)globalStateChangedNotificationHandler:(NSNotification *)notif {
    if ((LinphoneGlobalState)[[[notif userInfo] valueForKey:@"state"] integerValue] == LinphoneGlobalOn) {
        [self finishCoreConfiguration];
    }
}

#pragma mark - Configuring status changed

static void linphone_iphone_configuring_status_changed(LinphoneCore *lc, LinphoneConfiguringState status, const char *message) {
    [(__bridge LinphoneManager *)linphone_core_cbs_get_user_data(linphone_core_get_current_callbacks(lc)) onConfiguringStatusChanged:status withMessage:message];
}

- (void)onConfiguringStatusChanged:(LinphoneConfiguringState)status withMessage:(const char *)message {
    LOGI(@"onConfiguringStatusChanged: %s %@", linphone_configuring_state_to_string(status), message ? [NSString stringWithFormat:@"(message: %s)", message] : @"");
    NSDictionary *dict = [NSDictionary dictionaryWithObjectsAndKeys:[NSNumber numberWithInt:status], @"state", [NSString stringWithUTF8String:message ? message : ""], @"message", nil];

    // dispatch the notification asynchronously
    dispatch_async(dispatch_get_main_queue(), ^(void) {
            [NSNotificationCenter.defaultCenter postNotificationName:kLinphoneConfiguringStateUpdate object:self userInfo:dict];
        });
}

#pragma mark - Registration State Functions

- (void)onRegister:(LinphoneCore *)lc
cfg:(LinphoneProxyConfig *)cfg
state:(LinphoneRegistrationState)state
message:(const char *)cmessage {
    const char* status = linphone_registration_state_to_string(state);
    LOGI(@"New registration state: %s (message: %s)", status, cmessage);
    NSLog(@"New registration state: %s (message: %s)", status, cmessage);

    LinphoneReason reason = linphone_proxy_config_get_error(cfg);
    NSString *message = nil;
    switch (reason) {
    case LinphoneReasonBadCredentials:
        message = NSLocalizedString(@"Bad credentials, check your account settings", nil);
        break;
    case LinphoneReasonNoResponse:
        message = NSLocalizedString(@"No response received from remote", nil);
        break;
    case LinphoneReasonUnsupportedContent:
        message = NSLocalizedString(@"Unsupported content", nil);
        break;
    case LinphoneReasonIOError:
        message = NSLocalizedString(
                        @"Cannot reach the server: either it is an invalid address or it may be temporary down.", nil);
        break;

    case LinphoneReasonUnauthorized:
        message = NSLocalizedString(@"Operation is unauthorized because missing credential", nil);
        break;
    case LinphoneReasonNoMatch:
        message = NSLocalizedString(@"Operation could not be executed by server or remote client because it "
                        @"didn't have any context for it",
                        nil);
        break;
    case LinphoneReasonMovedPermanently:
        message = NSLocalizedString(@"Resource moved permanently", nil);
        break;
    case LinphoneReasonGone:
        message = NSLocalizedString(@"Resource no longer exists", nil);
        break;
    case LinphoneReasonTemporarilyUnavailable:
        message = NSLocalizedString(@"Temporarily unavailable", nil);
        break;
    case LinphoneReasonAddressIncomplete:
        message = NSLocalizedString(@"Address incomplete", nil);
        break;
    case LinphoneReasonNotImplemented:
        message = NSLocalizedString(@"Not implemented", nil);
        break;
    case LinphoneReasonBadGateway:
        message = NSLocalizedString(@"Bad gateway", nil);
        break;
    case LinphoneReasonServerTimeout:
        message = NSLocalizedString(@"Server timeout", nil);
        break;
    case LinphoneReasonNotAcceptable:
    case LinphoneReasonDoNotDisturb:
    case LinphoneReasonDeclined:
    case LinphoneReasonNotFound:
    case LinphoneReasonNotAnswered:
    case LinphoneReasonBusy:
    case LinphoneReasonNone:
    case LinphoneReasonUnknown:
        message = NSLocalizedString(@"Unknown error", nil);
        break;
    }

    // Post event
    NSDictionary *dict =
        [NSDictionary dictionaryWithObjectsAndKeys:[NSNumber numberWithInt:state], @"state", @(cmessage), @"cmessage", @(status), @"status",
         [NSValue valueWithPointer:cfg], @"cfg", message, @"message", nil];
    [NSNotificationCenter.defaultCenter postNotificationName:kLinphoneRegistrationUpdate object:self userInfo:dict];
}

static void linphone_iphone_registration_state(LinphoneCore *lc, LinphoneProxyConfig *cfg,
                           LinphoneRegistrationState state, const char *message) {
    NSLog(@"linphone_iphone_registration_state");
    [(__bridge LinphoneManager *)linphone_core_cbs_get_user_data(linphone_core_get_current_callbacks(lc)) onRegister:lc cfg:cfg state:state message:message];
}

#pragma mark - Call State Functions

- (void)onCall:(LinphoneCore *)lc
call:(LinphoneCall *)call
state:(LinphoneCallState)cstate
message:(const char *)cmessage {
    NSLog(@"message: %s", cmessage);
    // Post event
    NSDictionary *dict =
        [NSDictionary dictionaryWithObjectsAndKeys:[NSNumber numberWithInt:cstate], @"state", nil];
    [NSNotificationCenter.defaultCenter postNotificationName:kLinphoneCallUpdate object:self userInfo:dict];
}

static void linphone_iphone_call_state(LinphoneCore *lc, LinphoneCall *call, LinphoneCallState cstate, const char *message) {
    NSLog(@"linphone_iphone_call_state");
    [(__bridge LinphoneManager *)linphone_core_cbs_get_user_data(linphone_core_get_current_callbacks(lc)) onCall:lc call:call state:cstate message:message];
}

#pragma mark -

// scheduling loop
- (void)iterate {
    linphone_core_iterate(theLinphoneCore);
}

/** Should be called once per linphone_core_new() */
- (void)finishCoreConfiguration {
    NSLog(@"finish");
    //Force keep alive to workaround push notif on chat message
    linphone_core_enable_keep_alive([LinphoneManager getLc], true);

    // get default config from bundle
    NSString *device = [[NSMutableString alloc] initWithString:[NSString stringWithFormat:@"%@iOS/%@ (%@) LinphoneSDK",
                                    [NSBundle.mainBundle objectForInfoDictionaryKey:@"CFBundleDisplayName"],
                                    [[NSBundle mainBundle] objectForInfoDictionaryKey:@"CFBundleShortVersionString"],
                                    [[UIDevice currentDevice] name]]];

    linphone_core_set_user_agent(theLinphoneCore, device.UTF8String, LINPHONE_SDK_VERSION);

    NSString *path = [LinphoneManager bundleFile:@"nowebcamCIF.jpg"];
    if (path) {
        const char *imagePath = [path UTF8String];
        LOGI(@"Using '%s' as source image for no webcam", imagePath);
        linphone_core_set_static_picture(theLinphoneCore, imagePath);
    }

    LOGI(@"Linphone [%s] started on [%s]", linphone_core_get_version(), [[UIDevice currentDevice].model UTF8String]);

    // Post event
    NSDictionary *dict = [NSDictionary dictionaryWithObject:[NSValue valueWithPointer:theLinphoneCore] forKey:@"core"];

    [NSNotificationCenter.defaultCenter postNotificationName:kLinphoneCoreUpdate
     object:LinphoneManager.instance
     userInfo:dict];
}

static BOOL libStarted = FALSE;

- (void)launchLinphoneCore {

    if (libStarted) {
        LOGE(@"Liblinphone is already initialized!");
        return;
    }

    libStarted = TRUE;

    signal(SIGPIPE, SIG_IGN);

    // create linphone core
    [self createLinphoneCore];

    if ([UIApplication sharedApplication].applicationState == UIApplicationStateBackground) {
        // go directly to bg mode
        [self enterBackgroundMode];
    }
}

- (void)startLinphoneCore {
    linphone_core_start([LinphoneManager getLc]);
    [CoreManager.instance startIterateTimer];
}

- (void)createLinphoneCore {
    if (theLinphoneCore != nil) {
        LOGI(@"linphonecore is already created");
        return;
    }

    // Set audio assets
    NSString *ring =
        ([LinphoneManager bundleFile:[self lpConfigStringForKey:@"local_ring" inSection:@"sound"].lastPathComponent]
         ?: [LinphoneManager bundleFile:@"notes_of_the_optimistic.caf"])
        .lastPathComponent;
    NSString *ringback =
        ([LinphoneManager bundleFile:[self lpConfigStringForKey:@"remote_ring" inSection:@"sound"].lastPathComponent]
         ?: [LinphoneManager bundleFile:@"ringback.wav"])
        .lastPathComponent;
    NSString *hold =
        ([LinphoneManager bundleFile:[self lpConfigStringForKey:@"hold_music" inSection:@"sound"].lastPathComponent]
         ?: [LinphoneManager bundleFile:@"hold.mkv"])
        .lastPathComponent;
    [self lpConfigSetString:[LinphoneManager bundleFile:ring] forKey:@"local_ring" inSection:@"sound"];
    [self lpConfigSetString:[LinphoneManager bundleFile:ringback] forKey:@"remote_ring" inSection:@"sound"];
    [self lpConfigSetString:[LinphoneManager bundleFile:hold] forKey:@"hold_music" inSection:@"sound"];

    LinphoneFactory *factory = linphone_factory_get();
    LinphoneCoreCbs *cbs = linphone_factory_create_core_cbs(factory);
    linphone_core_cbs_set_registration_state_changed(cbs, linphone_iphone_registration_state);
    linphone_core_cbs_set_call_state_changed(cbs, linphone_iphone_call_state);
    linphone_core_cbs_set_configuring_status(cbs, linphone_iphone_configuring_status_changed);
    linphone_core_cbs_set_global_state_changed(cbs, linphone_iphone_global_state_changed);
    //linphone_core_cbs_set_notify_received(cbs, linphone_iphone_notify_received);
    linphone_core_cbs_set_user_data(cbs, (__bridge void *)(self));

    theLinphoneCore = linphone_factory_create_shared_core_with_config(factory, _configDb, NULL, [kLinphoneMsgNotificationAppGroupId UTF8String], true);
    linphone_core_add_callbacks(theLinphoneCore, cbs);
    LCSipTransports data = {0, 0, 0, 0};
    data.udp_port = [@0 intValue];
    data.tcp_port = [@0 intValue];
    data.dtls_port = [@0 intValue];
    data.tls_port = [@0 intValue];
    linphone_core_set_sip_transports(theLinphoneCore, &data);
    linphone_core_enable_ipv6(theLinphoneCore, FALSE);

    //[CallManager.instance setCoreWithCore:theLinphoneCore];
    [CoreManager.instance setCoreWithCore:theLinphoneCore];
    //[ConfigManager.instance setDbWithDb:_configDb];

    // linphone_core_set_network_reachable(theLinphoneCore, TRUE);
    linphone_core_start(theLinphoneCore);

    // Let the core handle cbs
    linphone_core_cbs_unref(cbs);

    LOGI(@"Create linphonecore %p", theLinphoneCore);

    // Load plugins if available in the linphone SDK - otherwise these calls will do nothing
    MSFactory *f = linphone_core_get_ms_factory(theLinphoneCore);
    libmssilk_init(f);
    libmsamr_init(f);
    libmsx264_init(f);
    libmsopenh264_init(f);
    libmswebrtc_init(f);
    libmscodec2_init(f);

    linphone_core_reload_ms_plugins(theLinphoneCore, NULL);

    /* Use the rootca from framework, which is already set*/
    //linphone_core_set_root_ca(theLinphoneCore, [LinphoneManager bundleFile:@"rootca.pem"].UTF8String);
    linphone_core_set_user_certificates_path(theLinphoneCore, [LinphoneManager cacheDirectory].UTF8String);

    /* The core will call the linphone_iphone_configuring_status_changed callback when the remote provisioning is loaded
       (or skipped).
       Wait for this to finish the code configuration */

    [NSNotificationCenter.defaultCenter addObserver:self
     selector:@selector(globalStateChangedNotificationHandler:)
     name:kLinphoneGlobalStateUpdate
     object:nil];

    /*call iterate once immediately in order to initiate background connections with sip server or remote provisioning
     * grab, if any */
    [self iterate];
    // start scheduler
    [CoreManager.instance startIterateTimer];
}

- (void)destroyLinphoneCore {
    [CoreManager.instance stopIterateTimer];
    // just in case
    [self removeCTCallCenterCb];

    if (theLinphoneCore != nil) { // just in case application terminate before linphone core initialization

        linphone_core_destroy(theLinphoneCore);
        LOGI(@"Destroy linphonecore %p", theLinphoneCore);
        theLinphoneCore = nil;

        // Post event
        NSDictionary *dict =
            [NSDictionary dictionaryWithObject:[NSValue valueWithPointer:theLinphoneCore] forKey:@"core"];
        [NSNotificationCenter.defaultCenter postNotificationName:kLinphoneCoreUpdate
         object:LinphoneManager.instance
         userInfo:dict];
    }
    libStarted = FALSE;
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

- (void)resetLinphoneCore {
    [self destroyLinphoneCore];
    [self createLinphoneCore];
}

static int comp_call_id(const LinphoneCall *call, const char *callid) {
    if (linphone_call_log_get_call_id(linphone_call_get_call_log(call)) == nil) {
        ms_error("no callid for call [%p]", call);
        return 1;
    }
    return strcmp(linphone_call_log_get_call_id(linphone_call_get_call_log(call)), callid);
}

- (void)acceptCallForCallId:(NSString *)callid {
    // first, make sure this callid is not already involved in a call
    const bctbx_list_t *calls = linphone_core_get_calls(theLinphoneCore);
    bctbx_list_t *call = bctbx_list_find_custom(calls, (bctbx_compare_func)comp_call_id, [callid UTF8String]);
    if (call != NULL) {
        const LinphoneVideoPolicy *video_policy = linphone_core_get_video_policy(theLinphoneCore);
        bool with_video = video_policy->automatically_accept;
        //[CallManager.instance acceptCallWithCall:(LinphoneCall *)call->data hasVideo:with_video];
        return;
    };
}

- (void)addPushCallId:(NSString *)callid {
    // first, make sure this callid is not already involved in a call
    const bctbx_list_t *calls = linphone_core_get_calls(theLinphoneCore);
    if (bctbx_list_find_custom(calls, (bctbx_compare_func)comp_call_id, [callid UTF8String])) {
        LOGW(@"Call id [%@] already handled", callid);
        return;
    };
    if ([pushCallIDs count] > 10 /*max number of pending notif*/)
        [pushCallIDs removeObjectAtIndex:0];

    [pushCallIDs addObject:callid];
}

- (BOOL)popPushCallID:(NSString *)callId {
    for (NSString *pendingNotif in pushCallIDs) {
        if ([pendingNotif compare:callId] == NSOrderedSame) {
            [pushCallIDs removeObject:pendingNotif];
            return TRUE;
        }
    }
    return FALSE;
}

- (BOOL)resignActive {
    linphone_core_stop_dtmf_stream(theLinphoneCore);

    return YES;
}

static int comp_call_state_paused(const LinphoneCall *call, const void *param) {
    return linphone_call_get_state(call) != LinphoneCallPaused;
}

- (void)startCallPausedLongRunningTask {
    pausedCallBgTask = [[UIApplication sharedApplication] beginBackgroundTaskWithExpirationHandler:^{
            LOGW(@"Call cannot be paused any more, too late");
            [[UIApplication sharedApplication] endBackgroundTask:pausedCallBgTask];
        }];
    LOGI(@"Long running task started, remaining [%@] because at least one call is paused",
         [LinphoneUtils intervalToString:[[UIApplication sharedApplication] backgroundTimeRemaining]]);
}

- (BOOL)enterBackgroundMode {
    NSLog(@"entering background...");
    linphone_core_enter_background([LinphoneManager getLc]);

    LinphoneProxyConfig *proxyCfg = linphone_core_get_default_proxy_config(theLinphoneCore);
    BOOL shouldEnterBgMode = FALSE;

    // handle proxy config if any
    if (proxyCfg) {
        BOOL pushNotifEnabled = linphone_proxy_config_is_push_notification_allowed(proxyCfg);
        NSLog(@"proxy config found: %i", pushNotifEnabled);
        if ([LinphoneManager.instance lpConfigBoolForKey:@"backgroundmode_preference"] || pushNotifEnabled) {
            if (floor(NSFoundationVersionNumber) <= NSFoundationVersionNumber_iOS_9_x_Max) {
                // For registration register
                [self refreshRegisters];
            }
        }

        if ([LinphoneManager.instance lpConfigBoolForKey:@"backgroundmode_preference"] && !pushNotifEnabled) {
            // Keep this!! Socket VoIP is deprecated after 9.0, but sometimes it's the only way to keep the phone background and receive the call. For example, when there is only local area network.
            // register keepalive
            if ([[UIApplication sharedApplication]
                 setKeepAliveTimeout:600 /*(NSTimeInterval)linphone_proxy_config_get_expires(proxyCfg)*/
                 handler:^{
                     LOGW(@"keepalive handler");
                     self->mLastKeepAliveDate = [NSDate date];
                     if (theLinphoneCore == nil) {
                         LOGW(@"It seems that Linphone BG mode was deactivated, just skipping");
                         return;
                     }
                     if (floor(NSFoundationVersionNumber) <= NSFoundationVersionNumber_iOS_9_x_Max) {
                         // For registration register
                         [self refreshRegisters];
                     }
                     linphone_core_iterate(theLinphoneCore);
                 }]) {
             LOGI(@"keepalive handler succesfully registered");
                 } else {
                     LOGI(@"keepalive handler cannot be registered");
                 }
            shouldEnterBgMode = TRUE;
        }
    }

    LinphoneCall *currentCall = linphone_core_get_current_call(theLinphoneCore);
    const bctbx_list_t *callList = linphone_core_get_calls(theLinphoneCore);
    if (!currentCall // no active call
        && callList  // at least one call in a non active state
        && bctbx_list_find_custom(callList, (bctbx_compare_func)comp_call_state_paused, NULL)) {
        [self startCallPausedLongRunningTask];
    }
    if (callList) // If at least one call exist, enter normal bg mode
        shouldEnterBgMode = TRUE;

    // Stop the video preview
    if (theLinphoneCore) {
        linphone_core_enable_video_preview(theLinphoneCore, FALSE);
        [self iterate];
    }
    linphone_core_stop_dtmf_stream(theLinphoneCore);

    LOGI(@"Entering [%s] bg mode", shouldEnterBgMode ? "normal" : "lite");
    if (!shouldEnterBgMode && floor(NSFoundationVersionNumber) <= NSFoundationVersionNumber_iOS_9_x_Max) {
        BOOL pushNotifEnabled = linphone_proxy_config_is_push_notification_allowed(proxyCfg);
        if (pushNotifEnabled) {
            LOGI(@"Keeping lc core to handle push");
            return YES;
        }
        return NO;
    }
    return YES;
}

- (void)becomeActive {
    NSLog(@"returning to foreground");
    linphone_core_enter_foreground([LinphoneManager getLc]);

    // enable presence
    if (floor(NSFoundationVersionNumber) <= NSFoundationVersionNumber_iOS_9_x_Max) {
        [self refreshRegisters];
    }
    if (pausedCallBgTask) {
        [[UIApplication sharedApplication] endBackgroundTask:pausedCallBgTask];
        pausedCallBgTask = 0;
    }
    if (incallBgTask) {
        [[UIApplication sharedApplication] endBackgroundTask:incallBgTask];
        incallBgTask = 0;
    }

    /*IOS specific*/
    linphone_core_start_dtmf_stream(theLinphoneCore);
    [AVCaptureDevice requestAccessForMediaType:AVMediaTypeVideo
     completionHandler:^(BOOL granted){
        }];

    /*start the video preview in case we are in the main view*/
    if (linphone_core_video_display_enabled(theLinphoneCore) && [self lpConfigBoolForKey:@"preview_preference"]) {
        linphone_core_enable_video_preview(theLinphoneCore, TRUE);
    }
    /*check last keepalive handler date*/
    if (mLastKeepAliveDate != Nil) {
        NSDate *current = [NSDate date];
        if ([current timeIntervalSinceDate:mLastKeepAliveDate] > 700) {
            NSString *datestr = [mLastKeepAliveDate description];
            LOGW(@"keepalive handler was called for the last time at %@", datestr);
        }
    }
}

- (void)refreshRegisters {
    NSLog(@"refreshing registers");
    linphone_core_refresh_registers(theLinphoneCore); // just to make sure REGISTRATION is up to date
}
    
- (void)copyDefaultSettings {
    NSString *src = [LinphoneManager bundleFile:@"linphonerc"];
    NSString *srcIpad = [LinphoneManager bundleFile:@"linphonerc~ipad"];
    if (IPAD && [[NSFileManager defaultManager] fileExistsAtPath:srcIpad]) {
        src = srcIpad;
    }
    NSString *dst = [LinphoneManager documentFile:@"linphonerc"];
    NSLog(@"copy default settings: %s", [dst UTF8String]);
    [LinphoneManager copyFile:src destination:dst override:FALSE ignore:FALSE];
}

- (void)overrideDefaultSettings {
    NSString *factory = [LinphoneManager bundleFile:@"linphonerc-factory"];
    NSString *factoryIpad = [LinphoneManager bundleFile:@"linphonerc-factory~ipad"];
    if (IPAD && [[NSFileManager defaultManager] fileExistsAtPath:factoryIpad]) {
        factory = factoryIpad;
    }
    NSString *confiFileName = [LinphoneManager documentFile:@"linphonerc"];
    _configDb = lp_config_new_with_factory([confiFileName UTF8String], [factory UTF8String]);
    //_configDb = linphone_config_new_for_shared_core(kLinphoneMsgNotificationAppGroupId.UTF8String, @"linphonerc".UTF8String, factory.UTF8String);
    lp_config_clean_entry(_configDb, "misc", "max_calls");
}

#pragma mark - Property Functions

- (void)setRemoteNotificationToken:(NSData *)remoteNotificationToken {
    if (remoteNotificationToken == _remoteNotificationToken) {
        return;
    }
    _remoteNotificationToken = remoteNotificationToken;

    [self configurePushTokenForProxyConfigs];
}

- (void)configurePushTokenForProxyConfigs {
    @try {
        const MSList *proxies = linphone_core_get_proxy_config_list(theLinphoneCore);
        while (proxies) {
            [self configurePushTokenForProxyConfig:proxies->data];
            proxies = proxies->next;
        }
    } @catch (NSException* e) {
        LOGW(@"%s: linphone core not ready yet, ignoring push token", __FUNCTION__);
    }

}

- (void)configurePushTokenForProxyConfig:(LinphoneProxyConfig *)proxyCfg {
    linphone_proxy_config_edit(proxyCfg);

    NSData *remoteTokenData = _remoteNotificationToken;
    BOOL pushNotifEnabled = linphone_proxy_config_is_push_notification_allowed(proxyCfg);
    NSLog(@"preparing for setting token");
    if ((remoteTokenData != nil) && pushNotifEnabled) {
        NSLog(@"setting token...");

        NSString* token = [[NSString alloc] initWithData:remoteTokenData encoding:NSUTF8StringEncoding];
        NSLog(token);

        // NSLocalizedString(@"IC_MSG", nil); // Fake for genstrings
        // NSLocalizedString(@"IM_MSG", nil); // Fake for genstrings
        // NSLocalizedString(@"IM_FULLMSG", nil); // Fake for genstrings
        NSString *ring =
            ([LinphoneManager bundleFile:[self lpConfigStringForKey:@"local_ring" inSection:@"sound"].lastPathComponent]
             ?: [LinphoneManager bundleFile:@"notes_of_the_optimistic.caf"])
            .lastPathComponent;

        NSString *timeout;
        if (floor(NSFoundationVersionNumber) > NSFoundationVersionNumber_iOS_9_x_Max) {
            timeout = @";pn-timeout=0";
        } else {
            timeout = @"";
        }

        
        NSString *appId = [[[FIRApp defaultApp] options] GCMSenderID];
        NSLog(@"GCMSenderID: %@", appId);

        NSString *params = [NSString
                    stringWithFormat:@"pn-type=firebase;pn-tok=%@;"
                            @"app-id=%@;pn-call-snd=%@%@;pn-silent=1",
                            token, appId, ring, timeout];

        LOGI(@"Proxy config %s configured for push notifications with contact: %@",
        linphone_proxy_config_get_identity(proxyCfg), params);
        linphone_proxy_config_set_contact_uri_parameters(proxyCfg, [params UTF8String]);
        linphone_proxy_config_set_contact_parameters(proxyCfg, NULL);
    } else {
        LOGI(@"Proxy config %s NOT configured for push notifications", linphone_proxy_config_get_identity(proxyCfg));
        // no push token:
        linphone_proxy_config_set_contact_uri_parameters(proxyCfg, NULL);
        linphone_proxy_config_set_contact_parameters(proxyCfg, NULL);
    }

    linphone_proxy_config_done(proxyCfg);
}

#pragma mark - Misc Functions

+ (NSString *)bundleFile:(NSString *)file {
    return [[NSBundle mainBundle] pathForResource:[file stringByDeletingPathExtension] ofType:[file pathExtension]];
}

+ (NSString *)documentFile:(NSString *)file {
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
    NSString *documentsPath = [paths objectAtIndex:0];
    return [documentsPath stringByAppendingPathComponent:file];
}

+ (NSString *)cacheDirectory {
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES);
    NSString *cachePath = [paths objectAtIndex:0];
    BOOL isDir = NO;
    NSError *error;
    // cache directory must be created if not existing
    if (![[NSFileManager defaultManager] fileExistsAtPath:cachePath isDirectory:&isDir] && isDir == NO) {
        [[NSFileManager defaultManager] createDirectoryAtPath:cachePath
                                  withIntermediateDirectories:NO
                                                   attributes:nil
                                                        error:&error];
    }
    return cachePath;
}

+ (BOOL)copyFile:(NSString *)src destination:(NSString *)dst override:(BOOL)override ignore:(BOOL)ignore {
    NSFileManager *fileManager = NSFileManager.defaultManager;
    NSError *error = nil;
    if ([fileManager fileExistsAtPath:src] == NO) {
        if (!ignore)
            LOGE(@"Can't find \"%@\": %@", src, [error localizedDescription]);
        return FALSE;
    }
    if ([fileManager fileExistsAtPath:dst] == YES) {
        if (override) {
            [fileManager removeItemAtPath:dst error:&error];
            if (error != nil) {
                LOGE(@"Can't remove \"%@\": %@", dst, [error localizedDescription]);
                return FALSE;
            }
        } else {
            LOGW(@"\"%@\" already exists", dst);
            return FALSE;
        }
    }
    [fileManager copyItemAtPath:src toPath:dst error:&error];
    if (error != nil) {
        LOGE(@"Can't copy \"%@\" to \"%@\": %@", src, dst, [error localizedDescription]);
        return FALSE;
    }
    return TRUE;
}

#pragma mark - LPConfig Functions

- (void)lpConfigSetString:(NSString *)value forKey:(NSString *)key {
    [self lpConfigSetString:value forKey:key inSection:LINPHONERC_APPLICATION_KEY];
}

- (void)lpConfigSetString:(NSString *)value forKey:(NSString *)key inSection:(NSString *)section {
    if (!key)
        return;
    lp_config_set_string(_configDb, [section UTF8String], [key UTF8String], value ? [value UTF8String] : NULL);
}

- (NSString *)lpConfigStringForKey:(NSString *)key {
    return [self lpConfigStringForKey:key withDefault:nil];
}

- (NSString *)lpConfigStringForKey:(NSString *)key withDefault:(NSString *)defaultValue {
    return [self lpConfigStringForKey:key inSection:LINPHONERC_APPLICATION_KEY withDefault:defaultValue];
}

- (NSString *)lpConfigStringForKey:(NSString *)key inSection:(NSString *)section {
    return [self lpConfigStringForKey:key inSection:section withDefault:nil];
}

- (NSString *)lpConfigStringForKey:(NSString *)key inSection:(NSString *)section withDefault:(NSString *)defaultValue {
    if (!key)
        return defaultValue;
    const char *value = lp_config_get_string(_configDb, [section UTF8String], [key UTF8String], NULL);
    return value ? [NSString stringWithUTF8String:value] : defaultValue;
}

- (void)lpConfigSetInt:(int)value forKey:(NSString *)key {
    [self lpConfigSetInt:value forKey:key inSection:LINPHONERC_APPLICATION_KEY];
}

- (void)lpConfigSetInt:(int)value forKey:(NSString *)key inSection:(NSString *)section {
    if (!key)
        return;
    lp_config_set_int(_configDb, [section UTF8String], [key UTF8String], (int)value);
}

- (int)lpConfigIntForKey:(NSString *)key {
    return [self lpConfigIntForKey:key withDefault:-1];
}

- (int)lpConfigIntForKey:(NSString *)key withDefault:(int)defaultValue {
    return [self lpConfigIntForKey:key inSection:LINPHONERC_APPLICATION_KEY withDefault:defaultValue];
}

- (int)lpConfigIntForKey:(NSString *)key inSection:(NSString *)section {
    return [self lpConfigIntForKey:key inSection:section withDefault:-1];
}

- (int)lpConfigIntForKey:(NSString *)key inSection:(NSString *)section withDefault:(int)defaultValue {
    if (!key)
        return defaultValue;
    return lp_config_get_int(_configDb, [section UTF8String], [key UTF8String], (int)defaultValue);
}

- (void)lpConfigSetBool:(BOOL)value forKey:(NSString *)key {
    [self lpConfigSetBool:value forKey:key inSection:LINPHONERC_APPLICATION_KEY];
}

- (void)lpConfigSetBool:(BOOL)value forKey:(NSString *)key inSection:(NSString *)section {
    [self lpConfigSetInt:(int)(value == TRUE) forKey:key inSection:section];
}

- (BOOL)lpConfigBoolForKey:(NSString *)key {
    return [self lpConfigBoolForKey:key withDefault:FALSE];
}

- (BOOL)lpConfigBoolForKey:(NSString *)key withDefault:(BOOL)defaultValue {
    return [self lpConfigBoolForKey:key inSection:LINPHONERC_APPLICATION_KEY withDefault:defaultValue];
}

- (BOOL)lpConfigBoolForKey:(NSString *)key inSection:(NSString *)section {
    return [self lpConfigBoolForKey:key inSection:section withDefault:FALSE];
}

- (BOOL)lpConfigBoolForKey:(NSString *)key inSection:(NSString *)section withDefault:(BOOL)defaultValue {
    if (!key)
        return defaultValue;
    int val = [self lpConfigIntForKey:key inSection:section withDefault:-1];
    return (val != -1) ? (val == 1) : defaultValue;
}

#pragma mark - GSM management

- (void)removeCTCallCenterCb {
    if (mCallCenter != nil) {
        LOGI(@"Removing CT call center listener [%p]", mCallCenter);
        mCallCenter.callEventHandler = NULL;
    }
    mCallCenter = nil;
}

- (BOOL)isCTCallCenterExist {
    return mCallCenter != nil;
}

- (void)setupGSMInteraction {

    [self removeCTCallCenterCb];
    mCallCenter = [[CTCallCenter alloc] init];
    LOGI(@"Adding CT call center listener [%p]", mCallCenter);
    __block __weak LinphoneManager *weakSelf = self;
    __block __weak CTCallCenter *weakCCenter = mCallCenter;
    mCallCenter.callEventHandler = ^(CTCall *call) {
        // post on main thread
        [weakSelf performSelectorOnMainThread:@selector(handleGSMCallInteration:)
         withObject:weakCCenter
         waitUntilDone:YES];
    };
}

- (void)handleGSMCallInteration:(id)cCenter {
    if (floor(NSFoundationVersionNumber) <= NSFoundationVersionNumber_iOS_9_x_Max) {
        CTCallCenter *ct = (CTCallCenter *)cCenter;
        // pause current call, if any
        LinphoneCall *call = linphone_core_get_current_call(theLinphoneCore);
        if ([ct currentCalls] != nil) {
            if (call) {
                LOGI(@"Pausing SIP call because GSM call");
                //CallManager.instance.speakerBeforePause = CallManager.instance.speakerEnabled;
                linphone_call_pause(call);
                [self startCallPausedLongRunningTask];
            } else if (linphone_core_is_in_conference(theLinphoneCore)) {
                LOGI(@"Leaving conference call because GSM call");
                linphone_core_leave_conference(theLinphoneCore);
                [self startCallPausedLongRunningTask];
            }
        } // else nop, keep call in paused state
    }
}

@end
