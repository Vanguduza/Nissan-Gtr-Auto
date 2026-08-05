# ReviewCamera (iOS)

Bridge-First still capture for **customer product review** photos.

- Contract shape mirrors `bridges/contracts/pod.ts` `PodCameraBridge` (local path only).
- Implementation: `UIImagePickerController` + `AVFoundation` permission — **no** WebView / HTML5 camera.
- Consumed by `apps/ios/GTRCustomer` Reviews screen.

```swift
let bridge = UIKitReviewCameraBridge()
let shot = try await bridge.capturePhoto()
// upload shot.localPath via StorefrontApi.uploadReviewPhoto
```
