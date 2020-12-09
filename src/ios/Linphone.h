#import <Cordova/CDV.h>
#import <AudioToolbox/AudioToolbox.h>
#include "linphone/linphonecore.h"

#include "LinphoneManager.h"

@interface Linphone : CDVPlugin{
    LinphoneCore *lc;
    LinphoneCall *call;
}

@property (nonatomic) LinphoneCore *lc;
@property (nonatomic) LinphoneCall *call;
@property (nonatomic) NSString *door_open_url;
@property (nonatomic) NSString *address;
@property (nonatomic) NSString *entrance;
@property (nonatomic) NSString *action;

- (void)refreshDoorOpenURLs:(NSDictionary *)userInfo;
- (void)ensureRegistered;
- (void)showCallView;
- (void)acceptCall:(CDVInvokedUrlCommand*)command;
- (void)listenCall:(CDVInvokedUrlCommand*)command;
- (void)login:(CDVInvokedUrlCommand*)command;
- (void)logout:(CDVInvokedUrlCommand*)command;
- (void)call:(CDVInvokedUrlCommand*)command;
- (void)videocall:(CDVInvokedUrlCommand*)command;
- (void)hangup:(CDVInvokedUrlCommand*)command;
- (void)toggleVideo:(CDVInvokedUrlCommand*)command;
- (void)toggleSpeaker:(CDVInvokedUrlCommand*)command;
- (void)toggleMute:(CDVInvokedUrlCommand*)command;
- (void)sendDtmf:(CDVInvokedUrlCommand*)command;

@end
