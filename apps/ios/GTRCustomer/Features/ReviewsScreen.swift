import PhotosUI
import SwiftUI

/// Product reviews — submit / list (own + approved by OEM) / stats / photo attach.
/// Photos via PhotosPicker (no iOS camera bridge yet — Bridge-First; not WebView camera).
struct ReviewsScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @State private var ownReviews: [ProductReview] = []
    @State private var approved: [ProductReview] = []
    @State private var stats: ProductReviewStats?
    @State private var oem = "15208-65F0C"
    @State private var rating = 5
    @State private var bodyText = ""
    @State private var status: String?
    @State private var busy = false
    @State private var lastSubmittedId: UUID?
    @State private var photoItem: PhotosPickerItem?

    var body: some View {
        List {
            Section("PDP stats (OEM)") {
                TextField("OEM part number", text: $oem)
                    .textInputAutocapitalization(.characters)
                    .font(.body.monospaced())
                Button("Load stats + approved") { Task { await loadPdp() } }
                    .disabled(busy || oem.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                if let stats {
                    LabeledContent("Avg rating", value: "\(stats.avgRating)")
                    LabeledContent("Approved count", value: "\(stats.reviewCount)")
                }
            }

            Section("Approved for OEM") {
                if approved.isEmpty {
                    Text("None loaded")
                        .foregroundStyle(.secondary)
                }
                ForEach(approved) { review in
                    reviewRow(review)
                }
            }

            Section("My reviews") {
                if ownReviews.isEmpty {
                    Text("No reviews yet")
                        .foregroundStyle(.secondary)
                }
                ForEach(ownReviews) { review in
                    reviewRow(review)
                }
            }

            Section("Submit review") {
                Stepper("Rating: \(rating)", value: $rating, in: 1 ... 5)
                TextField("Comment (optional)", text: $bodyText, axis: .vertical)
                    .lineLimit(3 ... 6)
                Button("Submit") { Task { await submit() } }
                    .disabled(busy || oem.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }

            if let lastSubmittedId {
                Section("Attach photo to last pending") {
                    Text(lastSubmittedId.uuidString)
                        .font(.caption.monospaced())
                    PhotosPicker(selection: $photoItem, matching: .images, photoLibrary: .shared()) {
                        Label("Choose from Photos", systemImage: "photo.on.rectangle")
                    }
                    .disabled(busy)
                    Text("Camera bridge not wired on iOS — Photos library only (Bridge-First).")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }

            if let status {
                Section {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("Reviews")
        .task { await refreshOwn() }
        .refreshable {
            await refreshOwn()
            await loadPdp()
        }
        .onChange(of: photoItem) { _, newItem in
            guard let newItem else { return }
            Task { await uploadPickedPhoto(newItem) }
        }
    }

    @ViewBuilder
    private func reviewRow(_ review: ProductReview) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(review.label).font(.headline)
                Spacer()
                Text(review.status.rawValue)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Text(String(repeating: "★", count: max(1, min(5, review.rating))))
                .font(.caption)
            if !review.body.isEmpty {
                Text(review.body)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }

    private func refreshOwn() async {
        busy = true
        defer { busy = false }
        do {
            ownReviews = try await session.api.listOwnReviews()
            status = nil
        } catch {
            status = error.localizedDescription
        }
    }

    private func loadPdp() async {
        busy = true
        defer { busy = false }
        let needle = oem.trimmingCharacters(in: .whitespacesAndNewlines)
        do {
            stats = try await session.api.getProductReviewStats(stockItemId: nil, oem: needle)
            approved = try await session.api.listApprovedReviews(oem: needle)
            status = "Loaded get_product_review_stats + approved list"
        } catch {
            status = error.localizedDescription
        }
    }

    private func submit() async {
        busy = true
        defer { busy = false }
        let needle = oem.trimmingCharacters(in: .whitespacesAndNewlines)
        do {
            let id = try await session.api.submitProductReview(
                rating: rating,
                body: bodyText,
                stockItemId: nil,
                oem: needle
            )
            lastSubmittedId = id
            bodyText = ""
            ownReviews = try await session.api.listOwnReviews()
            status = "Submitted via submit_customer_product_review → \(id.uuidString.prefix(8))…"
        } catch {
            status = error.localizedDescription
        }
    }

    private func uploadPickedPhoto(_ item: PhotosPickerItem) async {
        guard let reviewId = lastSubmittedId else { return }
        busy = true
        defer {
            busy = false
            photoItem = nil
        }
        do {
            guard let data = try await item.loadTransferable(type: Data.self), !data.isEmpty else {
                status = "Could not read photo data."
                return
            }
            _ = try await session.api.uploadReviewPhoto(
                reviewId: reviewId,
                photo: ReviewPhotoUpload(data: data, fileExtension: "jpg", contentType: "image/jpeg"),
                sortOrder: 0
            )
            status = "Uploaded to review-photos + add_customer_product_review_photo"
        } catch {
            status = error.localizedDescription
        }
    }
}

#Preview {
    NavigationStack {
        ReviewsScreen()
    }
    .environmentObject(StorefrontSession(api: FakeStorefrontApi()))
}
