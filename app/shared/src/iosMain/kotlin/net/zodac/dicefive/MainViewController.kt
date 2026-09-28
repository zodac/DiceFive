package net.zodac.dicefive

import androidx.compose.ui.window.ComposeUIViewController
import net.zodac.dicefive.device.IosAppContainer
import net.zodac.dicefive.device.IosPlatformServices
import net.zodac.dicefive.device.TransientMessageHost
import net.zodac.dicefive.ui.DiceFiveApp
import platform.UIKit.UIViewController

/**
 * The whole app as a UIKit view controller - the iOS counterpart of `MainActivity`, and what the
 * (still to be written) Xcode project's Swift entry point hosts, e.g. from SwiftUI:
 *
 * ```swift
 * struct ContentView: UIViewControllerRepresentable {
 *     func makeUIViewController(context: Context) -> UIViewController { MainViewControllerKt.MainViewController() }
 *     func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
 * }
 * ```
 */
@Suppress("FunctionName") // Named like the view controller it returns, as Kotlin/Native iOS entry points conventionally are.
fun MainViewController(): UIViewController {
    val platform = IosPlatformServices()
    return ComposeUIViewController {
        TransientMessageHost(platform) {
            DiceFiveApp(container = IosAppContainer.instance, platform = platform)
        }
    }
}
