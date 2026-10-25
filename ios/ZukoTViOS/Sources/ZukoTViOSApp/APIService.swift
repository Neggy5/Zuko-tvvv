import Foundation
import SwiftUI
import AVKit

@MainActor
final class AppStore: ObservableObject {
    @Published var rows: [HomeRow] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let api = APIService()

    func loadHome() async {
        isLoading = true
        errorMessage = nil

        do {
            rows = try await api.fetchHomeRows()
        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }
}

final class APIService {
    private let session: URLSession
    private let baseURL: URL

    init() {
        let config = URLSessionConfiguration.default
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        session = URLSession(configuration: config)

        let envBase = ProcessInfo.processInfo.environment["TMDB_PROXY_BASE"] ?? "https://athletic-consideration-production-c842.up.railway.app/api/tmdb/3/"
        baseURL = URL(string: envBase) ?? URL(string: "https://athletic-consideration-production-c842.up.railway.app/api/tmdb/3/")!
    }

    func fetchHomeRows() async throws -> [HomeRow] {
        let endpoints = [
            ("Trending this week", "trending/movie/week"),
            ("Popular", "movie/popular"),
            ("Top rated", "movie/top_rated"),
            ("Now playing", "movie/now_playing"),
            ("Coming soon", "movie/upcoming"),
        ]

        var result: [HomeRow] = []
        for (title, endpoint) in endpoints {
            let list = try await fetchList(endpoint: endpoint)
            if !list.results.isEmpty {
                result.append(HomeRow(id: title, title: title, movies: list.results))
            }
        }

        if result.isEmpty {
            throw URLError(.resourceUnavailable)
        }

        return result
    }

    func fetchMovieDetails(movieID: Int) async throws -> Movie {
        let url = baseURL
            .appendingPathComponent("movie")
            .appendingPathComponent(String(movieID))
            .appendingQueryItems([
                URLQueryItem(name: "language", value: "en-US"),
                URLQueryItem(name: "append_to_response", value: "credits,similar")
            ])

        let request = URLRequest(url: url)
        let (data, response) = try await session.data(for: request)

        guard let http = response as? HTTPURLResponse,
              (200..<300).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }

        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return try decoder.decode(Movie.self, from: data)
    }

    private func fetchList(endpoint: String) async throws -> MovieListResponse {
        let url = baseURL
            .appendingPathComponent(endpoint)
            .appendingQueryItems([
                URLQueryItem(name: "language", value: "en-US")
            ])

        let request = URLRequest(url: url)
        let (data, response) = try await session.data(for: request)

        guard let http = response as? HTTPURLResponse,
              (200..<300).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }

        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return try decoder.decode(MovieListResponse.self, from: data)
    }
}

private extension URL {
    func appendingQueryItems(_ items: [URLQueryItem]) -> URL {
        var components = URLComponents(url: self, resolvingAgainstBase: false)
        components?.queryItems = items
        return components?.url ?? self
    }
}

extension Movie {
    var releaseYear: String? {
        guard let releaseDate, releaseDate.count >= 4 else { return nil }
        return String(releaseDate.prefix(4))
    }

    var runtimeLabel: String? {
        guard let runtime, runtime > 0 else { return nil }
        let hours = runtime / 60
        let minutes = runtime % 60
        if hours > 0 {
            return "\(hours)h \(minutes)m"
        }
        return "\(minutes)m"
    }

    var ratingString: String? {
        guard let voteAverage else { return nil }
        return String(format: "%.1f", voteAverage)
    }

    var castNames: [String] {
        (credits?.cast ?? []).compactMap { $0.name }.prefix(5).map { $0 }
    }
}

@MainActor
final class DetailStore: ObservableObject {
    @Published var movie: Movie?
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let api = APIService()

    init(movie: Movie? = nil) {
        self.movie = movie
    }

    func loadDetails(movieID: Int) async {
        isLoading = true
        errorMessage = nil

        do {
            movie = try await api.fetchMovieDetails(movieID: movieID)
        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }
}

@MainActor
final class StreamPlayerController: ObservableObject {
    @Published var isLoading = true
    @Published var errorMessage: String?

    let player: AVPlayer
    private let movie: Movie

    init(movie: Movie) {
        self.movie = movie
        self.player = AVPlayer()
    }

    func loadStream() async {
        isLoading = true
        errorMessage = nil

        do {
            let streamURL = try await resolveStreamURL(for: movie)
            let item = AVPlayerItem(url: streamURL)
            player.replaceCurrentItem(with: item)
            player.play()
        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }

    private func resolveStreamURL(for movie: Movie) async throws -> URL {
        let fallback = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
        guard let url = URL(string: fallback) else { throw URLError(.badURL) }
        return url
    }
}
