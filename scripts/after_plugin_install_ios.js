#!/usr/bin/env node

'use strict';
const ContextHelper = require('./ContextHelper');
const ProjectHelper = require('./ProjectHelper');
const LogHelper = require('./LogHelper');

const EXTENSION_TARGET_BUILD_SETTINGS = {
    Debug: {
        DEBUG_INFORMATION_FORMAT: 'dwarf',
        GCC_DYNAMIC_NO_PIC: 'NO',
        GCC_OPTIMIZATION_LEVEL:  0,
        GCC_PREPROCESSOR_DEFINITIONS: "(\n          \"DEBUG=1\",\n          \"$(inherited)\",\n        )",
        MTL_ENABLE_DEBUG_INFO: 'INCLUDE_SOURCE',
    },
    Release: {
        DEBUG_INFORMATION_FORMAT: '"dwarf-with-dsym"',
        ENABLE_NS_ASSERTIONS: 'NO',
        MTL_ENABLE_DEBUG_INFO: 'NO',
        VALIDATE_PRODUCT: 'YES',
    },
    Common: {
        ALWAYS_SEARCH_USER_PATHS: 'NO',
        CLANG_ANALYZER_NONNULL: 'YES',
        CLANG_ANALYZER_NUMBER_OBJECT_CONVERSION: 'YES_AGGRESSIVE',
        CLANG_CXX_LANGUAGE_STANDARD: '"gnu++14"',
        CLANG_CXX_LIBRARY: '"libc++"',
        CLANG_ENABLE_OBJC_WEAK: 'YES',
        CLANG_WARN_DIRECT_OBJC_ISA_USAGE: 'YES_ERROR',
        CLANG_WARN_DOCUMENTATION_COMMENTS: 'YES',
        CLANG_WARN_OBJC_ROOT_CLASS: 'YES_ERROR',
        CLANG_WARN_UNGUARDED_AVAILABILITY: 'YES_AGGRESSIVE',
        COPY_PHASE_STRIP: 'NO',
        GCC_C_LANGUAGE_STANDARD: 'gnu11',
        GCC_WARN_ABOUT_RETURN_TYPE: 'YES_ERROR',
        GCC_WARN_UNINITIALIZED_AUTOS: 'YES_AGGRESSIVE',
        IPHONEOS_DEPLOYMENT_TARGET: '10.0',
        MTL_ENABLE_DEBUG_INFO: 'NO',
        MTL_FAST_MATH: 'YES',
        SKIP_INSTALL: 'YES',
        TARGETED_DEVICE_FAMILY: '"1,2"',
        SWIFT_VERSION: '5.0',
        OTHER_SWIFT_FLAGS: '\'$(inherited)\'',
        SWIFT_OBJC_BRIDGING_HEADER: '\'$(PROJECT_DIR)/SIPNSE/SipNotificationService-Bridging-Header.h\'',
        INFOPLIST_FILE: '\'SIPNSE/SipNotificationService-Info.plist\'',
    },
};

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
        appName = config.name(),
        contextHelper = new ContextHelper(context),
        logHelper = new LogHelper(contextHelper.context),
        packageName = config.ios_CFBundleIdentifier();

    const extDir = 'SIPNSE';
    const extName = 'SipNotificationService';
    const extPath = path.join(projectRoot, 'platforms', 'ios', extDir);
    const extFiles = [
        `${extName}.swift`,
        `${extName}-Bridging-Header.h`,
        `${extName}-Info.plist`,
    ];

    const xcodeProjectDir = appName + '.xcodeproj';
    const xcodeProjectPath = path.join(projectRoot, 'platforms', 'ios',
        xcodeProjectDir, 'project.pbxproj');
    const bridgingHeader = path.join(projectRoot, 'platforms', 'ios',
        appName, 'Bridging-Header.h');
    let NoServiceExtensionYet = true;
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

    if (!fs.existsSync(extPath)) {
        console.log(`Adding ${extName} SIP Push Service Extension to ${appName}...`);
        fs.mkdirSync(extPath);
    } else {
        console.log(`${extName} already exists at ${extPath}`);
    }
    const extSourceDir = path.join(context.opts.plugin.dir, 'src', 'ios', extDir);
    extFiles.forEach(function (extFile) {
        let targetFile = `${extPath}/${extFile}`;
        fs.createReadStream(`${extSourceDir}/${extFile}`)
            .pipe(fs.createWriteStream(targetFile));
    });

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

    logHelper.debug('[addExtensionToProject]');
    const projectHelper = new ProjectHelper(xcodeProject);

    const existingServiceExtensions = projectHelper.getAppExtensionTargets();

    // Message user if another extension that is not ours is found
    if (existingServiceExtensions.find(x => projectHelper.unquote(x.name) !== extName)) {
        logHelper.warn('[addExtensionToProject] You already have a notification service extension.');
        NoServiceExtensionYet = false;
    }

    // Exit right there
    if (existingServiceExtensions.length) {
        logHelper.debug('[addExtensionToProject] existing service extension, exiting');
        NoServiceExtensionYet = false;
    }

    for (const environment of ['Debug', 'Release']) {
        const bundleIdentifier = projectHelper.getAppBundleIdentifier(environment) || contextHelper.bundleIdentifier;
        if (!bundleIdentifier) {
            logHelper.warn('[addExtensionToProject] Could not add notification service extension: missing product bundle identifier');
            NoServiceExtensionYet = false;
        }
    }

    if (NoServiceExtensionYet) {
        const target = xcodeProject.addTarget(extName, 'app_extension', extName);
        logHelper.debug('[addExtensionToProject] created target', extName);
        const group = xcodeProject.addPbxGroup(extFiles, extName, extDir);
        console.log('[addExtensionToProject] created group', group.uuid);
        // Add our group to the main group
        const mainGroupId = projectHelper.getProjectMainGroupId();
        if (!mainGroupId) throw  new Error('[addExtensionToProject] Could not find main group ID');

        xcodeProject.addToPbxGroup(group.uuid, mainGroupId);
        logHelper.debug('[addExtensionToProject] added group', group.uuid, 'to the main group', mainGroupId);

        // Get this build configurations for the app
        const appTargetKey = projectHelper.getAppTargetKey();
        const appBuildConfigurations = projectHelper.getTargetBuildConfigurations(appTargetKey);

        // Get the build configuration for the extension
        const buildConfigurations = projectHelper.getTargetBuildConfigurations(target.uuid);

        // Get uuids of other build configurations
        const otherBuildConfigurationKeys = projectHelper.getAllBuildConfigurations()
            .map(x => x.uuid)
            .filter(x => buildConfigurations.map(y => y.uuid).indexOf(x) === -1)
            .sort();

        const changeBuildConfigurationKey = (buildConfiguration) => {
            projectHelper.removeBuildConfigurationFromBuildConfigurationList(buildConfiguration.uuid, target.pbxNativeTarget.buildConfigurationList);
            projectHelper.removeBuildConfigurationByKey(buildConfiguration.uuid);
            buildConfiguration.uuid = projectHelper.project.generateUuid();
            let result;
            result = projectHelper.addBuildConfiguration(buildConfiguration.uuid, buildConfiguration.pbxXCBuildConfiguration);
            result = projectHelper.addBuildConfigurationToBuildConfigurationList(buildConfiguration.uuid, target.pbxNativeTarget.buildConfigurationList);
        };
        for (const buildConfiguration of buildConfigurations) {
            logHelper.debug('[addExtensionToProject] update build configuration', buildConfiguration.uuid);

            const environment = buildConfiguration.pbxXCBuildConfiguration.name;
            // const bundleIdentifier = projectHelper.getAppBundleIdentifier(environment) || contextHelper.bundleIdentifier;

            // Copy CODE_SIGN* entries
            const correspondingAppBuildConfiguration = appBuildConfigurations.find(x => x.pbxXCBuildConfiguration.name === environment);
            if (correspondingAppBuildConfiguration && correspondingAppBuildConfiguration.pbxXCBuildConfiguration.buildSettings) {
                for (const key in correspondingAppBuildConfiguration.pbxXCBuildConfiguration.buildSettings) {
                    if (key.startsWith("CODE_SIGN") || key === 'DEVELOPMENT_TEAM') {
                        logHelper.debug('[addExtensionToProject] Copying build setting', key, correspondingAppBuildConfiguration.pbxXCBuildConfiguration.buildSettings[key]);
                        buildConfiguration.pbxXCBuildConfiguration.buildSettings[key] = correspondingAppBuildConfiguration.pbxXCBuildConfiguration.buildSettings[key];
                    }
                }
            }

            // Copy other build settings
            Object.assign(buildConfiguration.pbxXCBuildConfiguration.buildSettings, EXTENSION_TARGET_BUILD_SETTINGS.Common);
            Object.assign(buildConfiguration.pbxXCBuildConfiguration.buildSettings, EXTENSION_TARGET_BUILD_SETTINGS[environment]);

            // Copy bundle identifier
            let extensionBundleIdentifier;
            extensionBundleIdentifier = `${packageName}.${extName}`;
            buildConfiguration.pbxXCBuildConfiguration.buildSettings.PRODUCT_BUNDLE_IDENTIFIER = extensionBundleIdentifier;

            // Make sure we're not first or last
            if (otherBuildConfigurationKeys.length) {
                while (buildConfiguration.uuid < otherBuildConfigurationKeys[0]
                || buildConfiguration.uuid > otherBuildConfigurationKeys[otherBuildConfigurationKeys.length - 1]) {
                    const oldKey = buildConfiguration.uuid;
                    changeBuildConfigurationKey(buildConfiguration);
                    logHelper.debug('[addExtensionToProject] Changed build configuration key from', oldKey, 'to', buildConfiguration.uuid);
                }
            }
        }

        // Make sure .swift files get compiled
        const buildPhaseFileKeys = group.pbxGroup.children
            .filter(x => {
                const f = projectHelper.getFileByKey(x.value);
                return f && f.path && projectHelper.unquote(f.path).endsWith('.swift');
            })
            .map(x => x.value);

        // Add build phase to compile files
        const buildPhase = projectHelper.addSourcesBuildPhase(buildPhaseFileKeys, target);
        logHelper.debug('[addExtensionToProject] added build phase', buildPhase.uuid);
        let podfileContents = fs.readFileSync(contextHelper.podfilePath, 'utf8');
        if (podfileContents.indexOf(ProjectHelper.PODFILE_SNIPPET) < 0) {
            logHelper.debug('[addExtensionToProject] adding snippet to Podfile', contextHelper.podfilePath);
            fs.writeFileSync(contextHelper.podfilePath, podfileContents + "\n" + ProjectHelper.PODFILE_SNIPPET);
            // contextHelper.runPodInstall();
        }
    }

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
