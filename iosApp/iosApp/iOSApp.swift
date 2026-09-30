import SwiftUI
import UIKit
import ComposeApp

private final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
        UIInterfaceOrientationMask(rawValue: UInt(MainViewControllerKt.playerSupportedInterfaceOrientations(window: window)))
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
