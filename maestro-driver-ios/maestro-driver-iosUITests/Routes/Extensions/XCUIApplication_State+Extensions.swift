import XCTest

extension XCUIApplication.State {
    func toString() -> String {
        switch self {
        case .notRunning:
            return "notRunning"
        case .runningBackground:
            return "runningBackground"
        case .runningBackgroundSuspended:
            return "runningBackgroundSuspended"
        case .runningForeground:
            return "runningForeground"
        case .unknown:
            return "unknown"
        @unknown default:
            return "unknown"
        }
    }
}
