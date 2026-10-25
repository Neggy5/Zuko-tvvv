import SwiftUI

struct GenreView: View {
    @StateObject private var store = GenresStore()

    var body: some View {
        Group {
            if store.isLoading {
                ProgressView("Loading genres...")
            } else if let error = store.errorMessage {
                VStack(spacing: 12) {
                    Text("Could not load genres")
                        .font(.headline)
                    Text(error)
                        .foregroundStyle(.secondary)
                }
                .padding()
            } else {
                List(store.genres) { genre in
                    Text(genre.name)
                }
            }
        }
        .task {
            await store.load()
        }
        .navigationTitle("Genres")
    }
}
