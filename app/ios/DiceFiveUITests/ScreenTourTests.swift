import XCTest

/// Opens each screen from the menu on a fresh install and saves a screenshot of it - the simulator's counterpart of the
/// `render` skill, run by .github/workflows/ios-release.yml. It finds everything by the text and labels VoiceOver
/// reads, so a screen it can't reach is also a screen VoiceOver may not reach.
///
/// Screenshots go to `$SCREENSHOT_DIR` (xcodebuild passes `TEST_RUNNER_SCREENSHOT_DIR` through), named
/// `NN-<screen>.png`, and are attached to the test result as well.
final class ScreenTourTests: XCTestCase {
    private let app = XCUIApplication()
    private var shot = 0

    override func setUp() {
        continueAfterFailure = true
        app.launch()
    }

    func testScreenTour() {
        XCTAssertTrue(element("Settings").waitForExistence(timeout: 30), "The menu never appeared")
        screenshot("menu")

        for label in ["Achievements", "Styles", "Leaderboard", "Statistics", "Rules", "Settings"] {
            visitFromMenu(label)
        }

        let play = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == 'Play' OR label == 'New Game'")).firstMatch
        guard tap(play, "the menu's Play button") else { return }
        _ = element("Start Game").waitForExistence(timeout: 10)
        sleep(1)
        screenshot("setup")
        guard tap(element("Start Game"), "Start Game") else { return }
        let cup = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'Dice cup'")).firstMatch
        XCTAssertTrue(cup.waitForExistence(timeout: 15), "The game board never appeared")
        screenshot("game")
    }

    private func visitFromMenu(_ label: String) {
        guard tap(element(label), "the menu's \(label)") else { return }
        // Let the screen's entry transition finish, and give Styles' previews (the heaviest drawing) time to appear.
        _ = element("Back").waitForExistence(timeout: 10)
        sleep(label == "Styles" ? 5 : 1)
        screenshot(label.lowercased())
        _ = tap(element("Back"), "Back on \(label)")
        XCTAssertTrue(element("Settings").waitForExistence(timeout: 10), "Back from \(label) didn't reach the menu")
    }

    private func element(_ label: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    /// Taps the element once it exists; records a failure and a screenshot of what was on screen instead if it never
    /// appears.
    private func tap(_ element: XCUIElement, _ what: String) -> Bool {
        guard element.waitForExistence(timeout: 10) else {
            XCTFail("Couldn't find \(what)")
            screenshot("missing-" + what.replacingOccurrences(of: " ", with: "-").lowercased())
            return false
        }
        element.tap()
        return true
    }

    private func screenshot(_ name: String) {
        shot += 1
        let fileName = String(format: "%02d-%@", shot, name)
        let image = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: image)
        attachment.name = fileName
        attachment.lifetime = .keepAlways
        add(attachment)
        if let dir = ProcessInfo.processInfo.environment["SCREENSHOT_DIR"], !dir.isEmpty {
            let url = URL(fileURLWithPath: dir).appendingPathComponent(fileName + ".png")
            try? image.pngRepresentation.write(to: url)
        }
    }
}
