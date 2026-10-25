import SwiftUI

struct ContentView: View {
    @ObservedObject var store: AppStore

    var body: some View {
        NavigationStack {
            Group {
                if store.isLoading {
                    ProgressView("Loading ZUKO TV...")
                } else if let error = store.errorMessage {
                    VStack(spacing: 16) {
                        Text("Unable to load content")
                            .font(.headline)
                        Text(error)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                        Button("Retry") {
                            Task { await store.loadHome() }
                        }
                    }
                    .padding()
                } else {
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 20) {
                            ForEach(store.rows) { row in
                                VStack(alignment: .leading, spacing: 12) {
                                    Text(row.title)
                                        .font(.headline)
                                        .padding(.horizontal)

                                    ScrollView(.horizontal, showsIndicators: false) {
                                        HStack(spacing: 12) {
                                            ForEach(row.movies) { movie in
                                                NavigationLink(destination: DetailView(movie: movie)) {
                                                    VStack(alignment: .leading, spacing: 8) {
                                                        AsyncImage(url: movie.posterURL) { image in
                                                            image
                                                                .resizable()
                                                                .scaledToFill()
                                                        } placeholder: {
                                                            Color.gray.opacity(0.25)
                                                        }
                                                        .frame(width: 120, height: 180)
                                                        .clipShape(RoundedRectangle(cornerRadius: 12))

                                                        Text(movie.title)
                                                            .font(.caption)
                                                            .foregroundStyle(.primary)
                                                            .lineLimit(2)
                                                            .frame(width: 120, alignment: .leading)
                                                    }
                                                }
                                            }
                                        }
                                        .padding(.horizontal)
                                    }
                                }
                            }
                        }
                        .padding(.top)
                    }
                }
            }
            .navigationTitle("ZUKO TV")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        Task { await store.loadHome() }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                }
            }
        }
        .task {
            await store.loadHome()
        }
    }
}
