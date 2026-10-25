import SwiftUI

struct DetailView: View {
    let movie: Movie
    @StateObject private var detailStore = DetailStore()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                if let url = movie.backdropURL ?? movie.posterURL {
                    AsyncImage(url: url) { image in
                        image.resizable().scaledToFill()
                    } placeholder: {
                        Color.gray.opacity(0.25)
                    }
                    .frame(height: 260)
                    .frame(maxWidth: .infinity)
                    .clipped()
                }

                VStack(alignment: .leading, spacing: 12) {
                    Text(movie.title)
                        .font(.largeTitle)
                        .fontWeight(.bold)

                    HStack(spacing: 12) {
                        if let year = movie.releaseYear { Text(year) }
                        if let runtime = movie.runtimeLabel { Text(runtime) }
                        if let rating = movie.ratingString { Text("★ \(rating)") }
                    }
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                    if let genres = movie.genres, !genres.isEmpty {
                        Text(genres.map { $0.name }.joined(separator: ", "))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }

                    if let overview = movie.overview, !overview.isEmpty {
                        Text(overview)
                            .font(.body)
                            .foregroundStyle(.primary)
                    }

                    NavigationLink(destination: PlayerView(movie: movie)) {
                        Label("Play", systemImage: "play.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                }
                .padding(.horizontal)

                if let detail = detailStore.movie {
                    if !detail.castNames.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Cast")
                                .font(.headline)
                            Text(detail.castNames.joined(separator: ", "))
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.horizontal)
                    }

                    if let similar = detail.similar, !similar.results.isEmpty {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("More like this")
                                .font(.headline)
                                .padding(.horizontal)

                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 12) {
                                    ForEach(similar.results) { item in
                                        NavigationLink(destination: DetailView(movie: item)) {
                                            VStack(alignment: .leading, spacing: 8) {
                                                AsyncImage(url: item.posterURL) { image in
                                                    image.resizable().scaledToFill()
                                                } placeholder: {
                                                    Color.gray.opacity(0.25)
                                                }
                                                .frame(width: 120, height: 180)
                                                .clipShape(RoundedRectangle(cornerRadius: 12))

                                                Text(item.title)
                                                    .font(.caption)
                                                    .foregroundStyle(.primary)
                                                    .frame(width: 120, alignment: .leading)
                                                    .lineLimit(2)
                                            }
                                        }
                                    }
                                }
                                .padding(.horizontal)
                            }
                        }
                    }
                }
            }
        }
        .task {
            await detailStore.loadDetails(movieID: movie.id)
        }
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct PlayerView: View {
    let movie: Movie
    @StateObject private var playerController: StreamPlayerController

    init(movie: Movie) {
        self.movie = movie
        _playerController = StateObject(wrappedValue: StreamPlayerController(movie: movie))
    }

    var body: some View {
        ZStack {
            if playerController.isLoading {
                ProgressView("Loading stream...")
            } else if let error = playerController.errorMessage {
                VStack(spacing: 12) {
                    Text("Playback unavailable")
                        .font(.headline)
                    Text(error)
                        .foregroundStyle(.secondary)
                }
            } else {
                VideoPlayer(player: playerController.player)
                    .ignoresSafeArea()
            }
        }
        .task {
            await playerController.loadStream(from: movie)
        }
    }
}
