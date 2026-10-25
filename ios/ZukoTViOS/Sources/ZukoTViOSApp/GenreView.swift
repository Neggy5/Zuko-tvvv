import SwiftUI

struct TVShowsView: View {
    @StateObject private var store = TvShowsStore()

    var body: some View {
        Group {
            if store.isLoading {
                ProgressView("Loading TV shows...")
            } else if let error = store.errorMessage {
                VStack(spacing: 12) {
                    Text("Could not load shows")
                        .font(.headline)
                    Text(error)
                        .foregroundStyle(.secondary)
                }
                .padding()
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 18) {
                        ForEach(store.shows) { show in
                            VStack(alignment: .leading, spacing: 12) {
                                Text(show.name)
                                    .font(.headline)
                                    .padding(.horizontal)

                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 12) {
                                        AsyncImage(url: show.posterURL) { image in
                                            image.resizable().scaledToFill()
                                        } placeholder: {
                                            Color.gray.opacity(0.25)
                                        }
                                        .frame(width: 120, height: 180)
                                        .clipShape(RoundedRectangle(cornerRadius: 12))

                                        VStack(alignment: .leading, spacing: 8) {
                                            if let year = show.releaseYear { Text(year) }
                                            if let overview = show.overview, !overview.isEmpty {
                                                Text(overview)
                                                    .font(.caption)
                                                    .foregroundStyle(.secondary)
                                                    .lineLimit(3)
                                                    .frame(maxWidth: 220, alignment: .leading)
                                            }
                                        }
                                        .padding(.trailing)
                                    }
                                    .padding(.horizontal)
                                }
                            }
                        }
                    }
                }
            }
        }
        .task {
            await store.load()
        }
        .navigationTitle("TV Shows")
    }
}
