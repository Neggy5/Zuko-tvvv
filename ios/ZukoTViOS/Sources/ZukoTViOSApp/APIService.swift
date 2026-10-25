import Foundation

struct HomeRow: Identifiable, Codable {
    let id = UUID()
    let title: String
    let items: [Movie]

    enum CodingKeys: String, CodingKey {
        case title
        case items
    }
}

struct Movie: Identifiable, Codable {
    let id: Int
    let title: String
    let overview: String?
    let posterPath: String?
    let releaseDate: String?

    enum CodingKeys: String, CodingKey {
        case id
        case title
        case overview
        case posterPath = "poster_path"
        case releaseDate = "release_date"
    }

    var posterURL: URL? {
        guard let posterPath else { return nil }
        let base = "https://image.tmdb.org/t/p/w342"
        return URL(string: base + posterPath)
    }
}

struct TmdbListResponse: Codable {
    let results: [Movie]
}

struct TmdbHomeResponse: Codable {
    let rows: [HomeRow]
}

extension Movie: Hashable {}
extension HomeRow: Hashable {}
