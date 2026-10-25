import SwiftUI

struct SettingsView: View {
    @AppStorage("zuko.darkMode") private var darkMode = true
    @AppStorage("zuko.streamBase") private var streamBase = "https://athletic-consideration-production-c842.up.railway.app"

    var body: some View {
        Form {
            Section("Appearance") {
                Toggle("Dark mode", isOn: $darkMode)
            }

            Section("Streaming") {
                TextField("Stream API base", text: $streamBase)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
            }

            Section("About") {
                Text("ZUKO TV iPhone port")
                Text("Backend-compatible with the Android API patterns.")
            }
        }
        .navigationTitle("Settings")
    }
}
