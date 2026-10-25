import Foundation
import SwiftUI

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
        guard let posterPath else { return nil }
        return URL(string: "https://image.tmdb.org/t/p/w342" + posterPath)
    }

    var backdropURL: URL? {
        guard let backdropPath ?? posterPath else { return nil }
        return URL(string: "https://image.tmdb.org/t/p/w1280" + effectiveBackdrop)
    }

    private var effectiveBackdrop: String {
        if let backdropPath, !backdropPath.isEmpty { return backdropPath }
        return posterPath ?? ""
    }
}

struct MovieListResponse: Decodable {
    let results: [Movie]
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

extension Movie {
    static let placeholder = Movie(
        id: 0,
        title: "Loading",
        overview: nil,
        posterPath: nil,
        backdropPath: nil,
        releaseDate: nil,
        voteAverage: nil,
        runtime: nil,
        genres: nil,
        credits: nil,
        similar: nil,
        imdbId: nil
    )
}
