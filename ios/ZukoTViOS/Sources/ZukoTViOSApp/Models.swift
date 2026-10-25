import Foundation

struct Genre: Decodable, Identifiable {
    let id: Int
    let name: String
}

struct GenreListResponse: Decodable {
    let genres: [Genre]
}

struct CastMember: Decodable {
    let name: String?
    let character: String?
}

struct Credits: Decodable {
    let cast: [CastMember]?
}

struct MovieListResponse: Decodable {
    let results: [Movie]
}

struct TVShowListResponse: Decodable {
    let results: [TVShow]
}

struct Movie: Decodable, Identifiable {
    let id: Int
    let title: String
    let overview: String?
    let posterPath: String?
    let backdropPath: String?
    let releaseDate: String?
    let voteAverage: Double?
    let runtime: Int?
    let genres: [Genre]?
    let credits: Credits?
    let similar: MovieListResponse?
    let imdbId: String?

    var posterURL: URL? {
        guard let path = posterPath, !path.isEmpty else { return nil }
        return URL(string: "https://image.tmdb.org/t/p/w342\(path)")
    }

    var backdropURL: URL? {
        let path = backdropPath ?? posterPath
        guard let path, !path.isEmpty else { return nil }
        return URL(string: "https://image.tmdb.org/t/p/w1280\(path)")
    }

    var releaseYear: String? {
        guard let releaseDate, releaseDate.count >= 4 else { return nil }
        return String(releaseDate.prefix(4))
    }

    var runtimeLabel: String? {
        guard let runtime, runtime > 0 else { return nil }
        let hours = runtime / 60
        let minutes = runtime % 60
        return hours > 0 ? "\(hours)h \(minutes)m" : "\(minutes)m"
    }

    var ratingString: String? {
        guard let voteAverage else { return nil }
        return String(format: "%.1f", voteAverage)
    }

    var castNames: [String] {
        (credits?.cast ?? []).compactMap { $0.name }.prefix(5).map { $0 }
    }
}

struct TVShow: Decodable, Identifiable {
    let id: Int
    let name: String
    let overview: String?
    let posterPath: String?
    let backdropPath: String?
    let firstAirDate: String?
    let voteAverage: Double?

    var posterURL: URL? {
        guard let path = posterPath, !path.isEmpty else { return nil }
        return URL(string: "https://image.tmdb.org/t/p/w342\(path)")
    }

    var backdropURL: URL? {
        let path = backdropPath ?? posterPath
        guard let path, !path.isEmpty else { return nil }
        return URL(string: "https://image.tmdb.org/t/p/w1280\(path)")
    }

    var releaseYear: String? {
        guard let firstAirDate, firstAirDate.count >= 4 else { return nil }
        return String(firstAirDate.prefix(4))
    }

    var ratingString: String? {
        guard let voteAverage else { return nil }
        return String(format: "%.1f", voteAverage)
    }
}

struct HomeRow: Identifiable, Decodable {
    let id: String
    let title: String
    let movies: [Movie]

    enum CodingKeys: String, CodingKey {
        case title
        case movies
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        title = try container.decode(String.self, forKey: .title)
        movies = try container.decode([Movie].self, forKey: .movies)
        id = title
    }
}

struct StreamResponse: Decodable {
    let stream: String?
    let m3u8: String?
    let url: String?

    var bestURL: URL? {
        let options = [stream, m3u8, url].compactMap { value in
            value?.trimmingCharacters(in: .whitespacesAndNewlines)
        }.filter { !$0.isEmpty }
        guard let first = options.first else { return nil }
        return URL(string: first)
    }
}

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
final class SearchStore: ObservableObject {
    @Published var results: [Movie] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    private let api = APIService()

    func search(_ text: String) async {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        isLoading = true
        errorMessage = nil
        do {
            results = try await api.searchMovies(query: trimmed)
        } catch {
            errorMessage = error.localizedDescription
            results = []
        }
        isLoading = false
    }
}

@MainActor
final class TvShowsStore: ObservableObject {
    @Published var shows: [TVShow] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    private let api = APIService()

    func load() async {
        isLoading = true
        errorMessage = nil
        do {
            shows = try await api.fetchTVShows()
        } catch {
            errorMessage = error.localizedDescription
            shows = []
        }
        isLoading = false
    }
}

@MainActor
final class GenresStore: ObservableObject {
    @Published var genres: [Genre] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    private let api = APIService()

    func load() async {
        isLoading = true
        errorMessage = nil
        do {
            genres = try await api.fetchGenres()
        } catch {
            errorMessage = error.localizedDescription
            genres = []
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
    private let api = APIService()

    init(movie: Movie) {
        self.movie = movie
        self.player = AVPlayer()
    }

    func loadStream(from movie: Movie? = nil) async {
        isLoading = true
        errorMessage = nil
        do {
            let title = movie?.title ?? self.movie.title
            let year = movie?.releaseYear ?? self.movie.releaseYear
            let url = try await api.resolvePublicStreamURL(title: title, year: year)
            let item = AVPlayerItem(url: url)
            player.replaceCurrentItem(with: item)
            player.play()
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
}
