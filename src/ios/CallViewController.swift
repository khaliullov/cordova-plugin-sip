//
//  CallViewController.swift
//  cordova-plugin-sip
//
//  Created by Leandr on 2020-07-12.
//

import AVFoundation
import Foundation
import linphonesw


class CallViewController: UIViewController {
    var label: UILabel!
    @IBOutlet public var remoteVideoView: UIView!
    var lc: Core?
    var acceptButton: UIButton? = nil
    var declineButton: UIButton? = nil
    var unlockButton: UIButton? = nil

    @objc public func setCore(core: OpaquePointer) {
        lc = Core.getSwiftObject(cObject: core)
        //lc?.addDelegate(delegate: manager)
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        acceptButton?.isEnabled = true
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        NSLog("call dialog hidden");
        if (self.isBeingDismissed) {
            self.hangUp()
        }
    }

    @objc public func hangUp() {
        var cObject = LinphoneManager.getLc()
        lc = Core.getSwiftObject(cObject: cObject!)
        let call: Call? = self.lc?.currentCall
        if (call != nil) {
            if (call?.state == .OutgoingProgress || call?.state == .Connected || call?.state == .StreamsRunning) {
                try? call?.terminate()
            } else {
                let reason: Reason = .Declined
                try? call?.decline(reason: reason)
            }
        }
        NSLog("hang up")
    }
    @objc public func pickUp() {
        var cObject = LinphoneManager.getLc()
        lc = Core.getSwiftObject(cObject: cObject!)
        let call: Call? = self.lc?.currentCall
        if (call != nil) {
            // preview Call
            
            
            // accept Call
            var callParams: CallParams? = call?.params
            callParams?.audioDirection = .SendRecv
            callParams?.videoDirection = .RecvOnly
            callParams?.audioEnabled = true
            callParams?.videoEnabled = true  // true
            NSLog("accepting..")
            do {
                try call?.acceptWithParams(params: callParams)
            } catch {
                NSLog("Fuck \(error)")
            }
            //NSLog("updating..")
            //try? call?.acceptUpdate(params: callParams!)
            NSLog("params..")
            call?.params = callParams
            //isAnswered = true
            //NSLog("button..")
            acceptButton?.isEnabled = false
        }
        NSLog("pick up")
    }
    @objc public func unlock() {
        NSLog("unlock")
    }

    func initButtons() {
        NSLog("searching for buttons");
        //for case let subview as UIView in self.view.subviews {
        if ((acceptButton == nil) || (declineButton == nil) || (unlockButton == nil)) {
        for view in view.subviews as [UIView] {
            if let button = view as? UIButton {
                NSLog("found button")
                NSLog(button.restorationIdentifier!)
                if (button.restorationIdentifier! == "decline") {
                    declineButton = button
                    declineButton!.removeTarget(self, action: #selector(hangUp), for: UIControl.Event.touchUpInside)
                    declineButton!.addTarget(self, action: #selector(hangUp), for: UIControl.Event.touchUpInside)
                } else if (button.restorationIdentifier! == "accept") {
                    acceptButton = button
                    acceptButton!.removeTarget(self, action: #selector(pickUp), for: UIControl.Event.touchUpInside)
                    acceptButton!.addTarget(self, action: #selector(pickUp), for: UIControl.Event.touchUpInside)
                } else if (button.restorationIdentifier! == "unlock") {
                    unlockButton = button
                    unlockButton!.removeTarget(self, action: #selector(unlock), for: UIControl.Event.touchUpInside)
                    unlockButton!.addTarget(self, action: #selector(unlock), for: UIControl.Event.touchUpInside)
                }
            }
        }            //button.setTitleForAllStates("")
        NSLog("end of searching for buttons");
        } else {
            NSLog("Buttons already found");
        }
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        self.initButtons()
        return;
    }
}
