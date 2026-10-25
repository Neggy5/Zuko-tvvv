import Foundation

struct Genre: Decodable, Identifiable {
    let id: Int
    let name: String
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
        return URL(string: "https://image.tmdb.org/t/p/w342" + path)
    }

    var backdropURL: URL? {
        guard let path = backdropPath ?? posterPath, !path.isEmpty else { return nil }
        return URL(string: "https://image.tmdb.org/t/p/w1280" + path)
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
