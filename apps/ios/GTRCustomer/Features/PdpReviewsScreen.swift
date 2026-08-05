import PhotosUI
import SwiftUI

/// PDP-scoped reviews — list approved + submit with Bridge-First photo attach.
/// Reviews live on the product page, not the Account hub.
struct PdpReviewsScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    let oem: String
    let stockItemId: UUID?
    var onBack: (() -> Void)? = nil

    @State private var approved: [ProductReview] = []
    @State private var stats: ProductReviewStats?
    @State private var rating = 5
    @State private var bodyText = ""
    @State private var status: String?
    @State private var busy = false
    @State private var lastSubmittedId: UUID?
    @State private var photoItem: PhotosPickerItem?
    @State private var cameraBridge = UIKitReviewCameraBridge()

    var body: some View {
        ShopDefaultScreen(
            title: "Reviews",
            subtitle: oem,
            onBack: onBack,
            scrollable: false
        ) {
            List {
                Section {
                    Text("Customer reviews for this part — submit from here, not Account.")
                        .font(GTRType.body(.caption))
                        .foregroundStyle(GTRColors.silverDim)
                    HStack {
                        ShopRatingRow(
                            ratingLabel: stats.map {
                                String(format: "%.1f · %d", $0.avgRating, $0.reviewCount)
                            } ?? "No reviews yet"
                        )
                        Spacer()
                        Button("Refresh") { Task { await loadPdp() } }
                            .disabled(busy)
                    }
                }

                Section("Approved reviews") {
                    if approved.isEmpty && !busy {
                        Text("No approved reviews yet — be the first after purchase.")
                            .font(GTRType.body(.subheadline))
                            .foregroundStyle(GTRColors.silverDim)
                    }
                    ForEach(approved) { review in
                        reviewRow(review)
                    }
                }

                Section("Write a review") {
                    Stepper("Rating: \(rating)", value: $rating, in: 1 ... 5)
                    TextField("Comment (optional)", text: $bodyText, axis: .vertical)
                        .lineLimit(3 ... 6)
                    Button("Submit review") { Task { await submit() } }
                        .disabled(busy || oem.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }

                if let lastSubmittedId {
                    Section("Attach photo") {
                        Button {
                            Task { await captureViaBridge() }
                        } label: {
                            Label("Take photo (camera bridge)", systemImage: "camera")
                        }
                        .disabled(busy)
                        PhotosPicker(selection: $photoItem, matching: .images, photoLibrary: .shared()) {
                            Label("Choose from Photos", systemImage: "photo.on.rectangle")
                        }
                        .disabled(busy)
                        Text("Bridge-First camera via bridges/ios/ReviewCamera — not WebView.")
                            .font(GTRType.label(.caption2))
                            .foregroundStyle(GTRColors.silverDim)
                        Text(lastSubmittedId.uuidString)
                            .font(GTRType.label(.caption))
                            .monospaced()
                    }
                }

                if let status {
                    Section {
                        Text(status)
                            .font(GTRType.body(.footnote))
                            .foregroundStyle(GTRColors.silverDim)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(GTRColors.chalk)
            .navigationBarTitleDisplayMode(.inline)
            .task { await loadPdp() }
            .onChange(of: photoItem) { _, newItem in
                guard let newItem else { return }
                Task { await uploadPickedPhoto(newItem) }
            }
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

    private func loadPdp() async {
        busy = true
        defer { busy = false }
        let needle = oem.trimmingCharacters(in: .whitespacesAndNewlines)
        do {
            stats = try await session.api.getProductReviewStats(stockItemId: stockItemId, oem: needle)
            approved = try await session.api.listApprovedReviews(oem: needle)
            status = nil
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
                stockItemId: stockItemId,
                oem: needle
            )
            lastSubmittedId = id
            bodyText = ""
            await loadPdp()
            status = "Submitted via submit_customer_product_review"
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

    private func captureViaBridge() async {
        guard let reviewId = lastSubmittedId else { return }
        busy = true
        defer { busy = false }
        do {
            let shot = try await cameraBridge.capturePhoto()
            let url = URL(fileURLWithPath: shot.localPath)
            let data = try Data(contentsOf: url)
            _ = try await session.api.uploadReviewPhoto(
                reviewId: reviewId,
                photo: ReviewPhotoUpload(data: data, fileExtension: "jpg", contentType: shot.mimeType),
                sortOrder: 0
            )
            status = "Camera bridge → review-photos"
        } catch {
            status = error.localizedDescription
        }
    }
}

#Preview {
    NavigationStack {
        PdpReviewsScreen(oem: "15208-65F0C", stockItemId: nil)
    }
    .environmentObject(StorefrontSession(api: FakeStorefrontApi()))
}
