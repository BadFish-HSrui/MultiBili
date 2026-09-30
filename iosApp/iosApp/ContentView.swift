import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    @Binding var statusBarHidden: Bool

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(onStatusBarHiddenChanged: { hidden in
            statusBarHidden = hidden.boolValue
        })
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    @State private var statusBarHidden = false

    var body: some View {
        let composeView = ComposeView(statusBarHidden: $statusBarHidden)
            .ignoresSafeArea()
        if UIDevice.current.userInterfaceIdiom == .pad {
            composeView.statusBarHidden(statusBarHidden)
        } else {
            composeView
        }
    }
}
