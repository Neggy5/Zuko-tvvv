import Foundation
import AVKit

final class APIService {
    private let session: URLSession
    private let baseURL: URL

    init() {
        let config = URLSessionConfiguration.default
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        self.session = URLSession(configuration: config)

        let envBase = ProcessInfo.processInfo.environment["TMDB_PROXY_BASE"] ?? "https://athletic-consideration-production-c842.up.railway.app/api/tmdb/3/"
        self.baseURL = URL(string: envBase) ?? URL(string: "https://athletic-consideration-production-c842.up.railway.app/api/tmdb/3/")!
    }

    func fetchHomeRows() async throws -> [HomeRow] {
        let endpoints = [
            ("Trending this week", "trending/movie/week"),
            ("Popular", "movie/popular"),
            ("Top rated", "movie/top_rated"),
            ("Now playing", "movie/now_playing"),
            ("Coming soon", "movie/upcoming")
        ]

        var result: [HomeRow] = []
        for (title, endpoint) in endpoints {
            let list = try await fetchList(endpoint: endpoint)
            if !list.results.isEmpty {
                result.append(HomeRow(id: title, title: title, movies: list.results))
            }
        }

        if result.isEmpty { throw URLError(.resourceUnavailable) }
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

        let decoder = JSONDecoder(); decoder.keyDecodingStrategy = .convertFromSnakeCase
        return try decoder.decode(Movie.self, from: data)
    }

    func fetchTVShows() async throws -> [TVShow] {
        let url = baseURL
            .appendingPathComponent("tv")
            .appendingPathComponent("popular")
            .appendingQueryItems([
                URLQueryItem(name: "language", value: "en-US")
            ])

        let request = URLRequest(url: url)
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse,
              (200..<300).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }

        let decoder = JSONDecoder(); decoder.keyDecodingStrategy = .convertFromSnakeCase
        return try decoder.decode(TVShowListResponse.self, from: data).results
    }

    func fetchGenres() async throws -> [Genre] {
        let url = baseURL
            .appendingPathComponent("genre")
            .appendingPathComponent("movie")
            .appendingPathComponent("list")
            .appendingQueryItems([
                URLQueryItem(name: "language", value: "en-US")
            ])

        let request = URLRequest(url: url)
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse,
              (200..<300).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }

        let decoder = JSONDecoder(); decoder.keyDecodingStrategy = .convertFromSnakeCase
        return try decoder.decode(GenreListResponse.self, from: data).genres
    }

    func searchMovies(query: String) async throws -> [Movie] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }

        let url = baseURL
            .appendingPathComponent("search")
            .appendingPathComponent("movie")
            .appendingQueryItems([
                URLQueryItem(name: "query", value: trimmed),
                URLQueryItem(name: "language", value: "en-US")
            ])

        let request = URLRequest(url: url)
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse,
              (200..<300).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }

        let decoder = JSONDecoder(); decoder.keyDecodingStrategy = .convertFromSnakeCase
        return try decoder.decode(MovieListResponse.self, from: data).results
    }

    func resolvePublicStreamURL(title: String, year: String? = nil) async throws -> URL {
        let host = ProcessInfo.processInfo.environment["STREAM_API_BASE"] ?? "https://athletic-consideration-production-c842.up.railway.app"
        let sanitized = host.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard let base = URL(string: sanitized) else { throw URLError(.badURL) }

        let endpoint = base
            .appendingPathComponent("api")
            .appendingPathComponent("v1")
            .appendingPathComponent("public")
            .appendingPathComponent("stream")

        var components = URLComponents(url: endpoint, resolvingAgainstBase: false)!
        var queryItems = [URLQueryItem(name: "title", value: title)]
        if let year, !year.isEmpty { queryItems.append(URLQueryItem(name: "year", value: year)) }
        components.queryItems = queryItems

        let request = URLRequest(url: components.url!)
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse,
              (200..<300).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }

        let decoder = JSONDecoder(); decoder.keyDecodingStrategy = .convertFromSnakeCase
        let stream = try decoder.decode(StreamResponse.self, from: data)
        guard let resolved = stream.bestURL else { throw URLError(.resourceUnavailable) }
        return resolved
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

        let decoder = JSONDecoder(); decoder.keyDecodingStrategy = .convertFromSnakeCase
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
