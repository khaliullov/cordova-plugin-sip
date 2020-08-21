#import "Linphone.h"
#import <Cordova/CDV.h>
#import <AudioToolbox/AudioToolbox.h>

#import "ProductModuleName-Swift.h"

@implementation Linphone

@synthesize call ;
@synthesize lc;
NSString *loginCallBackID ;
NSString *callCallBackID ;
static bool_t isspeaker=FALSE;
static Linphone *theLinhone;
static UIView *remoteView;
static CallViewController *callViewController;

- (void)acceptCall:(CDVInvokedUrlCommand*)command {
    NSLog(@"accept call");
    lc = LinphoneManager.getLc;
    call = linphone_core_get_current_call(lc);
    LinphoneCallParams *lcallParams = linphone_core_create_call_params(lc, call);
        if (!lcallParams) {
            return;
        }

        linphone_call_params_enable_video(lcallParams, TRUE);
    linphone_call_params_enable_audio(lcallParams, FALSE);

    //[self.commandDelegate runInBackground:^{
        linphone_core_accept_call_with_params(lc, call, lcallParams);
        //linphone_core_accept_early_media_with_params(lc, call, lcallParams);
        linphone_call_params_destroy(lcallParams);
    //}];
    
    callCallBackID = command.callbackId;

}

- (UIViewController*) topMostController
{
    UIViewController *topController = [UIApplication sharedApplication].keyWindow.rootViewController;

    while (topController.presentedViewController) {
        topController = topController.presentedViewController;
    }

    return topController;
}

- (void)showCallView {
    if (!callViewController) {
        UIStoryboard *storyboard = [UIStoryboard storyboardWithName:@"Linphone" bundle:nil];
       callViewController = [storyboard instantiateViewControllerWithIdentifier:@"CallViewController"];
        lc = LinphoneManager.getLc;
        //sipViewController.setCore(lc);
    }
    if ([[UIApplication sharedApplication] applicationState] != UIApplicationStateActive) {
        NSLog(@"won't open call dialog: application is not active");
        return;
    }
    self->lc = LinphoneManager.getLc;
    linphone_core_ensure_registered(self->lc);
    self->call = linphone_core_get_current_call(self->lc);
    if (self->call == nil) {
        if (linphone_core_get_calls_nb(self->lc) == 1) {
            self->call = (LinphoneCall *)linphone_core_get_calls(self->lc)->data;
            NSLog(@"call was found using helper");
        } else {
            linphone_core_refresh_registers(self->lc);
            NSLog(@"failed to open call dialog: call not found. on line: %i", linphone_core_get_calls_nb(self->lc));
            return;
        }
    }
    UIViewController *topMostController = [self topMostController];
    if (callViewController.isBeingPresented || topMostController == callViewController) {
        NSLog(@"call dialog is already opened");
    } else {
        if ([self viewController] != topMostController) {
            NSLog(@"closing all modal windows");
            [[self viewController] dismissViewControllerAnimated:false completion:nil];
        }
        NSLog(@"presenting call dialog");
        [self.viewController presentViewController: callViewController animated:YES completion:^{
            NSLog(@"incall window opened");
            if (linphone_call_get_dir(self->call) == LinphoneCallIncoming) {
                dispatch_async(dispatch_get_main_queue(), ^{
                    LinphoneCallParams *lcallParams = linphone_core_create_call_params(self->lc, self->call);
                    if (lcallParams) {
                        //return;
                        linphone_call_params_enable_audio(lcallParams, FALSE);
                        linphone_call_params_enable_video(lcallParams, TRUE);
                        //linphone_call_params_set_video_direction(lcallParams, LinphoneMediaDirectionRecvOnly);
                        linphone_call_params_set_audio_direction(lcallParams, LinphoneMediaDirectionInactive);
                        NSLog(@"accept early media");
                        linphone_core_accept_early_media_with_params(self->lc, self->call, lcallParams);
                        linphone_call_params_unref(lcallParams);
                    }
                });
            }
        }];
    }
    if (!remoteView) {
        remoteView = [[UIView alloc] initWithFrame:CGRectMake(0,0,320,240)];
        remoteView.backgroundColor=[UIColor blackColor];
        [callViewController.view addSubview:remoteView];
        linphone_core_set_native_video_window_id(lc, (__bridge void *)remoteView);
        NSLog(@"remote view added");
    }
}

- (void)onCallStateChanged:(NSNotification *)notif {
    NSDictionary *dict = notif.userInfo;
    NSLog(@"onCallStateChanged");
    LinphoneCallState state = [[dict objectForKey:@"state"] intValue];
    CDVPluginResult* pluginResult = nil;
    if (state == LinphoneCallError) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Error"];
        linphone_call_unref(call);
        call = NULL;
    }
    else if (state == LinphoneCallConnected) {
        //pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Connected"];
    }
    else if (state == LinphoneCallReleased) {
        //call = NULL;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"End"];
        [self.viewController dismissViewControllerAnimated:YES completion:nil];
    }
    else if (state == LinphoneCallIncomingReceived) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Incoming"];
        self.showCallView;
    }
    if (pluginResult) {
        [theLinhone.commandDelegate sendPluginResult:pluginResult callbackId:callCallBackID];
    }
}

- (void)listenCall:(CDVInvokedUrlCommand*)command {
    theLinhone = self;
    callCallBackID = command.callbackId;
    [NSNotificationCenter.defaultCenter removeObserver:self
     name:@"LinphoneCallUpdate"
     object:nil];
    [NSNotificationCenter.defaultCenter addObserver:self
     selector:@selector(onCallStateChanged:)
     name:@"LinphoneCallUpdate"
     object:nil];
    NSLog(@"Listen call");
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Idle"];
    [pluginResult setKeepCallback:[NSNumber numberWithBool:YES]];
    [theLinhone.commandDelegate sendPluginResult:pluginResult callbackId:callCallBackID];
}

- (void)onRegistrationChanged:(NSNotification *)notif {
    NSDictionary *dict = notif.userInfo;
    NSLog(@"Status: %s", [dict[@"status"] UTF8String]);
    lc = LinphoneManager.getLc;
    NSString *result = nil;
    if ([dict[@"status"] isEqual: @"LinphoneRegistrationOk"]) {
        result = @"RegistrationSuccess";
    } else if ([dict[@"status"] isEqual: @"LinphoneRegistrationProgress"]) {
            result = @"RegistrationProgress";
    } else if ([dict[@"status"] isEqual: @"LinphoneRegistrationFailed"]) {
        result = @"RegistrationFailed";
    }
    if (result) {
        NSLog(@"sending result: %s", [result UTF8String]);
        NSLog(@"callback id: %s", [loginCallBackID UTF8String]);
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:result];
        [theLinhone.commandDelegate sendPluginResult:pluginResult callbackId:loginCallBackID];
    }
}

- (void)login:(CDVInvokedUrlCommand*)command {
    theLinhone = self;
    loginCallBackID = command.callbackId;
    self->lc = LinphoneManager.getLc;
    NSString* username = [command.arguments objectAtIndex:0];
    NSString* password = [command.arguments objectAtIndex:1];
    NSString* domain = [command.arguments objectAtIndex:2];
    NSString* sip = [@"sip:" stringByAppendingString:username];
    char* identity = (char*)[sip UTF8String];
    const char* sip_address = (const char*)[[@"sip:" stringByAppendingString:domain] UTF8String];

    [NSNotificationCenter.defaultCenter removeObserver:self name:@"LinphoneRegistrationUpdate" object:nil];
    [NSNotificationCenter.defaultCenter addObserver:self selector:@selector(onRegistrationChanged:) name:@"LinphoneRegistrationUpdate" object:nil];

    // check if there we already configured with such config
    const MSList *proxies = linphone_core_get_proxy_config_list(self->lc);
    while (proxies) {
        LinphoneProxyConfig *proxy_cfg = proxies->data;
        //linphone_proxy_config_get_identity_address(proxy_cfg);
        const char *existing_identity = linphone_proxy_config_get_identity(proxy_cfg);
        const char *existing_domain = linphone_proxy_config_get_server_addr(proxy_cfg);
        if (strcmp(existing_identity,  sip.UTF8String) == 0 && strcmp(existing_domain,  sip_address) == 0) {
            LinphoneRegistrationState state = linphone_proxy_config_get_state(proxy_cfg);
            const char* status = linphone_registration_state_to_string(state);
            NSDictionary *dict =
                [NSDictionary dictionaryWithObjectsAndKeys:[NSNumber numberWithInt:state], @"state", @(status), @"status", nil];
            [NSNotificationCenter.defaultCenter postNotificationName:@"LinphoneRegistrationUpdate" object:self userInfo:dict];
            return;  // do not reconnect! why?s
        }
        proxies = proxies->next;
    }

    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"RegistrationProgress"];
    [pluginResult setKeepCallback:[NSNumber numberWithBool:YES]];
    [theLinhone.commandDelegate sendPluginResult:pluginResult callbackId:loginCallBackID];
    //NSString* sip = [@"sip:" stringByAppendingString:[[username stringByAppendingString:@"@"] stringByAppendingString:domain]];

    linphone_core_clear_all_auth_info(lc);
    linphone_core_clear_proxy_config(lc);
    LinphoneProxyConfig *proxy_cfg = linphone_core_create_proxy_config(lc);
    LinphoneAddress *from = linphone_address_new(identity);
    
    /*create authentication structure from identity*/
    LinphoneAuthInfo *info = linphone_auth_info_new(linphone_address_get_username(from), NULL, (char*)[password UTF8String], NULL, (char*)[domain UTF8String], (char*)[domain UTF8String]);
    linphone_core_add_auth_info(lc, info); /*add authentication info to LinphoneCore*/
    
    // configure proxy entries
    linphone_proxy_config_set_identity(proxy_cfg, identity); /*set identity with user name and domain*/
    //const char* server_addr = linphone_address_get_domain(from); /*extract domain address from identity*/
    //NSLog(sip_address);
    //LinphoneAddress *proxy_address = linphone_address_new(sip_address);
    //const char* server_addr = (const char*)[proxy_address UTF8String];
    linphone_proxy_config_set_server_addr(proxy_cfg, sip_address); /* we assume domain = proxy server address*/
    linphone_proxy_config_enable_register(proxy_cfg, TRUE); /*activate registration for this proxy config*/
    linphone_address_destroy(from); /*release resource*/
    //linphone_address_destroy(proxy_address);
    linphone_proxy_config_set_push_notification_allowed(proxy_cfg, true);
    LinphoneManager* linphoneManager = [LinphoneManager instance];
    if ([linphoneManager remoteNotificationToken]) {
        NSLog(@"setting remote push token");
        [linphoneManager configurePushTokenForProxyConfig:proxy_cfg];
    }
    // TODO replace this by sending notifications all the time
    linphone_proxy_config_set_expires(proxy_cfg, 604800);
    NSLog(@"registration expires: %i", linphone_proxy_config_get_expires(proxy_cfg));
    linphone_core_add_proxy_config(lc, proxy_cfg); /*add proxy config to linphone core*/
    linphone_core_set_default_proxy(lc, proxy_cfg);
}

- (void)logout:(CDVInvokedUrlCommand*)command {
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"OK"];
    lc = LinphoneManager.getLc;
    if(lc != NULL){
        LinphoneProxyConfig *proxy_cfg = linphone_core_create_proxy_config(lc);
        linphone_core_get_default_proxy(lc,&proxy_cfg); /* get default proxy config*/
        linphone_proxy_config_edit(proxy_cfg); /*start editing proxy configuration*/
        linphone_proxy_config_enable_register(proxy_cfg,FALSE); /*de-activate registration for this proxy config*/
        linphone_proxy_config_done(proxy_cfg); /*initiate REGISTER with expire = 0*/
        
        while(linphone_proxy_config_get_state(proxy_cfg) !=  LinphoneRegistrationCleared){
            linphone_core_iterate(lc); /*to make sure we receive call backs before shutting down*/
            ms_usleep(50000);
        }
        
        linphone_core_clear_all_auth_info(lc);
        linphone_core_clear_proxy_config(lc);
        //linphone_core_destroy(lc);
        
        call = NULL;
        //lc = NULL;
    }
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)setStunServer:(CDVInvokedUrlCommand*)command {
    NSString* stunServer = [[command arguments] objectAtIndex:0];
    lc = LinphoneManager.getLc;
    stunServer = @"stun4.l.google.com:19302";
    NSLog(stunServer);
    linphone_core_set_stun_server(lc, [stunServer UTF8String]);
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)disableStunServer:(CDVInvokedUrlCommand*)command {
    
}

- (void)call:(CDVInvokedUrlCommand*)command {
    callCallBackID = command.callbackId;
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"OK"];
    NSString* address = [command.arguments objectAtIndex:0];
    NSString* displayName = [command.arguments objectAtIndex:1];
    lc = LinphoneManager.getLc;
    call = linphone_core_invite(lc, (char *)[address UTF8String]);
    linphone_call_ref(call);
    self.showCallView;
}

- (void)videocall:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"OK"];
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)hangup:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"OK"];
    
    if(call && linphone_call_get_state(call) != LinphoneCallEnd){
        linphone_core_terminate_call(lc, call);
        linphone_call_unref(call);
    }
    call = NULL;
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}
- (void)toggleVideo:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"OK"];
    bool isenabled = FALSE;
    
    if (call != NULL && linphone_call_params_get_used_video_codec(linphone_call_get_current_params(call))) {
        if(isenabled){
            
        }else{
            
        }
    }
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)toggleSpeaker:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"OK"];
    if (call != NULL && linphone_call_get_state(call) != LinphoneCallEnd){
        isspeaker = !isspeaker;
        if (isspeaker) {
            UInt32 audioRouteOverride = kAudioSessionOverrideAudioRoute_Speaker;
            AudioSessionSetProperty(kAudioSessionProperty_OverrideAudioRoute, sizeof(audioRouteOverride),
                                    &audioRouteOverride);
            
        } else {
            UInt32 audioRouteOverride = kAudioSessionOverrideAudioRoute_None;
            AudioSessionSetProperty(kAudioSessionProperty_OverrideAudioRoute, sizeof(audioRouteOverride),
                                    &audioRouteOverride);
        }
    }
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)toggleMute:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"OK"];
    bool isenabled = FALSE;
    
    if(call && linphone_call_get_state(call) != LinphoneCallEnd){
        linphone_core_enable_mic(lc, isenabled);
    }
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)sendDtmf:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"OK"];
    NSString* dtmf = [command.arguments objectAtIndex:0];
    
    if(call && linphone_call_get_state(call) != LinphoneCallEnd){
        linphone_call_send_dtmf(call, [dtmf characterAtIndex:0]);
    }
    
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

@end
