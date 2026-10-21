import Shared
import SwiftUI

/// The iOS app: a window holding the shared Kotlin app (`MainViewController.kt` in app/shared's iosMain), edge to edge.
/// Compose pads for the safe areas itself.
@main
struct DiceFiveApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView().ignoresSafeArea()
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
