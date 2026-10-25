import SwiftUI

struct SearchView: View {
    @StateObject private var store = SearchStore()
    @State private var localQuery = ""

    var body: some View {
        VStack {
            TextField("Search movies…", text: $localQuery)
                .textFieldStyle(.roundedBorder)
                .padding()
                .onSubmit {
                    Task {
                        await store.search(localQuery)
                    }
                }

            if store.isLoading {
                ProgressView("Searching...")
            } else if let error = store.errorMessage {
                Text(error)
                    .foregroundStyle(.red)
                    .padding()
            } else if store.results.isEmpty {
                Text("Type a movie title to search TMDB.")
                    .foregroundStyle(.secondary)
                    .padding()
            } else {
                List(store.results) { movie in
                    NavigationLink(destination: DetailView(movie: movie)) {
                        HStack(spacing: 12) {
                            AsyncImage(url: movie.posterURL) { image in
                                image.resizable().scaledToFill()
                            } placeholder: {
                                Color.gray.opacity(0.25)
                            }
                            .frame(width: 50, height: 75)
                            .clipShape(RoundedRectangle(cornerRadius: 8))

                            VStack(alignment: .leading, spacing: 4) {
                                Text(movie.title)
                                if let year = movie.releaseYear {
                                    Text(year)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
            }

            Spacer()
        }
        .navigationTitle("Search")
    }
}
