import SwiftUI

@main
struct ZukoTViOSApp: App {
    @StateObject private var viewModel = HomeViewModel()

    var body: some Scene {
        WindowGroup {
            ContentView(viewModel: viewModel)
        }
    }
}
