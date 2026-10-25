import SwiftUI

@main
struct ZukoTViOSApp: App {
    @StateObject private var store = AppStore()

    var body: some Scene {
        WindowGroup {
            ContentView(store: store)
        }
    }
}
