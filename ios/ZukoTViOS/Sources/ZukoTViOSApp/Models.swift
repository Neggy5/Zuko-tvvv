import SwiftUI

@MainActor
final class HomeViewModel: ObservableObject {
    @Published var rows: [HomeRow] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let api = APIService()

    func loadHome() async {
        isLoading = true
        errorMessage = nil

        do {
            let result = try await api.fetchHomeRows()
            rows = result
        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }
}

struct ContentView: View {
    @ObservedObject var viewModel: HomeViewModel

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoading {
                    ProgressView("Loading ZUKO TV...")
                } else if let error = viewModel.errorMessage {
                    VStack(spacing: 16) {
                        Text("Unable to load content")
                            .font(.headline)
                        Text(error)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                        Button("Retry") {
                            Task { await viewModel.loadHome() }
                        }
                    }
                    .padding()
                } else {
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 20) {
                            ForEach(viewModel.rows) { row in
                                VStack(alignment: .leading, spacing: 12) {
                                    Text(row.title)
                                        .font(.headline)
                                        .padding(.horizontal)

                                    ScrollView(.horizontal, showsIndicators: false) {
                                        HStack(spacing: 12) {
                                            ForEach(row.items) { movie in
                                                VStack(alignment: .leading, spacing: 8) {
                                                    AsyncImage(url: movie.posterURL) { image in
                                                        image
                                                            .resizable()
                                                            .aspectRatio(contentMode: .fill)
                                                    } placeholder: {
                                                        Color.gray.opacity(0.3)
                                                    }
                                                    .frame(width: 120, height: 180)
                                                    .clipShape(RoundedRectangle(cornerRadius: 12))

                                                    Text(movie.title)
                                                        .font(.caption)
                                                        .lineLimit(2)
                                                        .frame(width: 120, alignment: .leading)
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
                        Task { await viewModel.loadHome() }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                }
            }
        }
        .task {
            await viewModel.loadHome()
        }
    }
}
