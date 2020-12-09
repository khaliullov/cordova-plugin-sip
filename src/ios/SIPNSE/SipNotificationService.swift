//
//  SipNotificationService.swift
//  SipNotificationService
//
//  Created by Leandr on 11/26/20.
//

import UserNotifications
import FirebaseMessaging

struct IntercomSetting: Decodable {
    let notdisturb: String
}

class SipNotificationService: UNNotificationServiceExtension {

    var contentHandler: ((UNNotificationContent) -> Void)?
    var bestAttemptContent: UNMutableNotificationContent?

    override func didReceive(_ request: UNNotificationRequest, withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void) {
        self.contentHandler = contentHandler
        bestAttemptContent = (request.content.mutableCopy() as? UNMutableNotificationContent)

        if let bestAttemptContent = bestAttemptContent {
            // Modify the notification content here...
            // group.bundle_id.sip
            let suiteName: String = "group." + Bundle.main.bundleIdentifier!.substring(to: Bundle.main.bundleIdentifier!.lastIndex(of: ".")!) + ".sip"
            let intercom_settings_raw: String? = UserDefaults(suiteName: suiteName)?.string(forKey: "intercom_settings")
            let decoder = JSONDecoder()
            if (intercom_settings_raw != nil) {
                do {
                    let intercom_settings: Dictionary<String, IntercomSetting> = try decoder.decode(Dictionary<String, IntercomSetting>.self, from: intercom_settings_raw!.data(using: .utf8)!)
                    for (doorphone_id, intercom_setting) in intercom_settings {
                        if (bestAttemptContent.userInfo["doorphone_id"] != nil && bestAttemptContent.userInfo["doorphone_id"] as! String == doorphone_id && intercom_setting.notdisturb == "1") {
                            // make it silent if user has disabled notification
                            bestAttemptContent.sound = nil
                            bestAttemptContent.title = "\(bestAttemptContent.title) [не беспокоить]"
                        }
                    }
                } catch {
                }
            }

            Messaging.serviceExtension().populateNotificationContent(bestAttemptContent, withContentHandler: contentHandler)
        }
    }
    
    override func serviceExtensionTimeWillExpire() {
        // Called just before the extension will be terminated by the system.
        // Use this as an opportunity to deliver your "best attempt" at modified content, otherwise the original push payload will be used.
        if let contentHandler = contentHandler, let bestAttemptContent =  bestAttemptContent {
            contentHandler(bestAttemptContent)
        }
    }

}
