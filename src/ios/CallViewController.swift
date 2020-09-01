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
    private var oldIdleTimer: Bool = false
    @objc public var unlockButton: UIButton? = nil
    @objc public var addressLabel: UILabel? = nil
    @objc public var displayNameLabel: UILabel? = nil
    @objc public var doorOpenURL: String? = nil

    @objc public func setCore(core: OpaquePointer) {
        lc = Core.getSwiftObject(cObject: core)
        //lc?.addDelegate(delegate: manager)
    }

    @objc public func resetButtons() {
        acceptButton?.isEnabled = true
        declineButton?.isEnabled = true
        unlockButton?.isEnabled = false
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        self.oldIdleTimer = UIApplication.shared.isIdleTimerDisabled
        UIApplication.shared.isIdleTimerDisabled = true
        self.resetButtons()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        UIApplication.shared.isIdleTimerDisabled = self.oldIdleTimer
        NSLog("call dialog hidden");
        if (self.isBeingDismissed) {
            self.hangUp()
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let bounds: CGRect = self.view.bounds;
        NSLog("aligning remote view")

        var remoteViewFrame: CGRect = self.remoteVideoView.frame
        if (bounds.height > bounds.width) { // vertical
            let maxWidth: Int = Int(bounds.width - 20)
            let maxHeight: Int = Int((bounds.height - 20) / 2)
            var calculatedHeight: Int = Int(4 * maxWidth / 3)
            var calculatedWidth: Int = Int(3 * maxHeight / 4)
            if calculatedWidth > maxWidth {
                calculatedWidth = maxWidth
                calculatedHeight = Int(3 * maxWidth / 4)
            } else {
                calculatedHeight = maxHeight
                calculatedWidth = Int(4 * maxHeight / 3)
            }
            remoteViewFrame.size.height = CGFloat(calculatedHeight)
            remoteViewFrame.size.width = CGFloat(calculatedWidth)
            self.remoteVideoView.frame = remoteViewFrame
            self.remoteVideoView.center = CGPoint(x: (bounds.width) / 2, y: (bounds.height - 20) / 4)
            self.addressLabel?.center = CGPoint(x: (bounds.width) / 2, y: (bounds.height - remoteViewFrame.size.height - 20) / 2 + remoteViewFrame.size.height - 60)
            self.displayNameLabel?.center = CGPoint(x: (bounds.width) / 2, y: (bounds.height - remoteViewFrame.size.height - 20) / 2 + remoteViewFrame.size.height - 40)
            let offset: CGFloat = bounds.width / 3
            self.declineButton?.center = CGPoint(x: (bounds.width) / 2 - offset, y: bounds.height - offset)
            self.unlockButton?.center = CGPoint(x: (bounds.width) / 2, y: bounds.height - offset)
            self.acceptButton?.center = CGPoint(x: (bounds.width) / 2 + offset, y: bounds.height - offset)
        } else { // horizontal
            remoteViewFrame.size.height = bounds.height - 60
            remoteViewFrame.size.width = 4 * remoteViewFrame.size.height / 3
            self.remoteVideoView.frame = remoteViewFrame
            self.remoteVideoView.center = CGPoint(x: 10 + self.remoteVideoView.frame.width / 2, y: 10 + self.remoteVideoView.frame.height / 2)
            self.addressLabel?.center = CGPoint(x: 10 + self.remoteVideoView.frame.width / 2, y: bounds.height - 40)
            self.displayNameLabel?.center = CGPoint(x: 10 + self.remoteVideoView.frame.width / 2, y: bounds.height - 20)
            let offset: CGFloat = (bounds.height - 40) / 3
            self.declineButton?.center = CGPoint(x: 5 * bounds.width / 6, y: (bounds.height - 20) / 2 + offset)
            self.unlockButton?.center = CGPoint(x: 5 * bounds.width / 6, y: (bounds.height - 20) / 2)
            self.acceptButton?.center = CGPoint(x: 5 * bounds.width / 6, y: (bounds.height - 20) / 2 - offset)
        }
    }

    @objc public func hangUp() {
        var cObject = LinphoneManager.getLc()
        lc = Core.getSwiftObject(cObject: cObject!)
        let call: Call? = self.lc?.currentCall
        if (call != nil) {
            if (acceptButton!.isEnabled) {
                let reason: Reason = .NotAnswered
                try? call?.decline(reason: reason)
            } else {
                try? call?.terminate()
            }
        }
        NSLog("hang up")
    }
    @objc public func pickUp() {
        var cObject = LinphoneManager.getLc()
        lc = Core.getSwiftObject(cObject: cObject!)
        let call: Call? = self.lc?.currentCall
        if (call != nil) {
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
            acceptButton?.isEnabled = false
        }
        NSLog("pick up")
    }

    func showToast(message : String, backgroundColor: UIColor) {

        let toastLabel = UILabel(frame: CGRect(x: self.view.frame.size.width/2 - 75, y: self.view.frame.size.height-100, width: 150, height: 35))
        toastLabel.backgroundColor = backgroundColor
        toastLabel.textColor = UIColor.white
        toastLabel.font = .systemFont(ofSize: 12.0)
        toastLabel.textAlignment = .center;
        toastLabel.text = message
        toastLabel.alpha = 1.0
        toastLabel.layer.cornerRadius = 10;
        toastLabel.clipsToBounds  =  true
        self.view.addSubview(toastLabel)
        UIView.animate(withDuration: 4.0, delay: 0.1, options: .curveEaseOut, animations: {
             toastLabel.alpha = 0.0
        }, completion: {(isCompleted) in
            toastLabel.removeFromSuperview()
        })
    }

    func show_toast(opened: Bool) {
        var status: String = "Ошибка"
        var color: UIColor = UIColor.red.withAlphaComponent(0.6)
        if (opened) {
            status = "Дверь открыта"
            color = UIColor.green.withAlphaComponent(0.6)
        }
        self.showToast(message: status, backgroundColor: color)
    }

    @objc public func unlock() {
        if (self.doorOpenURL != nil) {
            let url = URL(string: self.doorOpenURL!)!
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            NSURLConnection.sendAsynchronousRequest(request, queue: OperationQueue.main) {(response, data, error) in
                let httpResponse = response as! HTTPURLResponse
                if (httpResponse.statusCode == 200) {
                    guard let data = data else { return }
                    do{
                        let jsonResponse = try JSONSerialization.jsonObject(with:
                            data, options: []) as! [String : Any]
                        guard let status = jsonResponse["status"] as? Bool else {
                            self.show_toast(opened: false)
                            return
                        }
                        self.show_toast(opened: status)
                      } catch let parsingError {
                         print("Error", parsingError)
                        self.show_toast(opened: false)
                    }
                } else {
                    self.show_toast(opened: false)
                }
            }
        }   
        NSLog("unlock")
    }

    func initControls() {
        //for case let subview as UIView in self.view.subviews {
        if ((acceptButton == nil) || (declineButton == nil) || (unlockButton == nil)) {
            for view in view.subviews as [UIView] {
                if let button = view as? UIButton {
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
                if let label = view as? UILabel {
                    if (label.restorationIdentifier! == "address") {
                        addressLabel = label
                    } else if (label.restorationIdentifier! == "displayName") {
                        displayNameLabel = label
                    }
                }
            }
        }
    }

    @objc func closeCallView() {
        self.dismiss(animated: true, completion: {
            self.hangUp()
        })
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        self.initControls()

        // TODO refactor this!!!
        let backBtn = UIBarButtonItem()
        var scale: CGFloat = 0.5
        var image: UIImage = UIImage.scale(image: UIImage(asset: .back)!, by: scale)!
        backBtn.image = image
        backBtn.tintColor = UIColor.Theme.white
        backBtn.action = #selector(closeCallView)
        backBtn.target = self
        navigationItem.leftBarButtonItem = backBtn
        let label = UILabel()
        label.font = .systemFont(ofSize: 17)
        label.textColor = UIColor.Theme.white
        label.textAlignment = .center
        label.text = "Домофон";
        navigationItem.titleView = label

        return;
    }
}
