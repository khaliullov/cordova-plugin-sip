#!/usr/bin/env node

'use strict';

module.exports = function (context) {
    let linphoneSdkVersion = '4.4.0';  // auto replaced with value from plugin.xml

    if (context.opts.plugin.platform != 'ios') {
        console.info('iOS platform has not been added.');
        return;
    }

    const xcode = require('xcode'),
        fs = require('fs'),
        path = require('path'),
        deferral = require('q').defer(),
        projectRoot = context.opts.projectRoot,
        ConfigParser = require('cordova-common').ConfigParser,
        config = new ConfigParser(path.join(context.opts.projectRoot, 'config.xml')),
        appName = config.name();

    const xcodeProjectDir = appName + '.xcodeproj';
    const xcodeProjectPath = path.join(projectRoot, 'platforms', 'ios',
        xcodeProjectDir, 'project.pbxproj');
    const bridgingHeader = path.join(projectRoot, 'platforms', 'ios',
        appName, 'Bridging-Header.h');
    let bridgingHeaderContent = fs.readFileSync(bridgingHeader, 'utf8');
    let bridgingHeaderModified = false;
    const headers = ['@import UIKit;', '@import linphone;', '#import "Log.h"', '#import "LinphoneManager.h"'];
    for (let headerIndex in headers) {
        if (bridgingHeaderContent.search(headers[headerIndex]) < 0) {
            bridgingHeaderModified = true;
            bridgingHeaderContent += headers[headerIndex] + "\n";
        }
    }
    if (bridgingHeaderModified) {
        fs.writeFileSync(bridgingHeader, bridgingHeaderContent);
    }
    const plugin = path.join(context.opts.plugin.dir, 'plugin.xml');
    const pluginContent = fs.readFileSync(plugin, 'utf8');
    const linphoneSdkMatch = pluginContent.match('<pod name="linphone-sdk" spec="[^\\d]*(\\d+\\.\\d+\\.\\d+)');
    if ((linphoneSdkMatch) && (linphoneSdkMatch.length > 1)) {
        linphoneSdkVersion = linphoneSdkMatch[1];
    }
    console.info('Linphone SDK version: %s', linphoneSdkVersion);

    if (!fs.existsSync(xcodeProjectPath)) {
        console.info('xcode project was not found.');
        return;
    }

    let xcodeProject = xcode.project(xcodeProjectPath);
    xcodeProject.parseSync();

    const mpbxNativeTargetSection = xcodeProject.pbxNativeTargetSection();
    let buildConfigurationList = '';
    for (let nativeTargetKey in mpbxNativeTargetSection) {
        var value = mpbxNativeTargetSection[nativeTargetKey];
        if (!(typeof value === 'string')) {  // skipping comments
            buildConfigurationList = value.buildConfigurationList;
        }
    }
    const buildConfigurations = xcodeProject.pbxXCConfigurationList()[buildConfigurationList].buildConfigurations.map(function (obj) {
        return obj.value;
    });
    const mXCBuildConfigurationSections = xcodeProject.pbxXCBuildConfigurationSection();

    //create the new BuildConfig
    let newBuildConfig = {};
    for (let configKey in mXCBuildConfigurationSections) {
        var value = mXCBuildConfigurationSections[configKey];
        if (!(typeof value === 'string') && (buildConfigurations.includes(configKey))) {  // skipping comments & non-native targets
            if (configKey.name == 'Debug') {
                value.buildSettings['GCC_PREPROCESSOR_DEFINITIONS'] = '\'$(inherited) DEBUG=1\'';
            } else {
                value.buildSettings['GCC_PREPROCESSOR_DEFINITIONS'] = '\'$(inherited)\'';
            }
            value.buildSettings['OTHER_SWIFT_FLAGS'] = '\'$(inherited)\'';
            value.buildSettings['SWIFT_OBJC_INTERFACE_HEADER_NAME'] = '\'ProductModuleName-Swift.h\'';
            value.buildSettings['SWIFT_VERSION'] = '5.0';
            value.buildSettings['OTHER_CFLAGS'] = ['\'-DBCTBX_LOG_DOMAIN=\"\\\\\"ios\\\\\"\"\'',
                '\'-DCHECK_VERSION_UPDATE=FALSE\'', '\'-DENABLE_QRCODE=FALSE\'',
                '\'-DENABLE_SMS_INVITE=FALSE\'', '\'$(inherited)\'',
                '\'-DLINPHONE_SDK_VERSION=\"\\\\\"' + linphoneSdkVersion + '\\\\\"\"\''];
        }
        newBuildConfig[configKey] = value;
    }

    //Change BuildConfigs
    xcodeProject.hash.project.objects['XCBuildConfiguration'] = newBuildConfig

    fs.writeFile(xcodeProject.filepath, xcodeProject.writeSync(), 'utf8', function (err) {
        if (err) {
            deferral.reject(err);
            return;
        }
        console.info('finished writing xcodeproj');
        deferral.resolve();
    });

    return deferral.promise;
}
