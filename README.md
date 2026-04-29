# DocScan - Android Document Scanner

An open-source Android document scanning app with automatic document detection, perspective correction, OCR text recognition, and PDF export.

## Features

- **Camera Scanning** — CameraX preview and capture with flash and multiple focus modes
- **A4 Guide Frame** — Portrait A4 ratio guide frame covering 90% of preview area for precise document alignment
- **Auto Document Detection** — Three-tier strategy (GrabCut → Otsu threshold → Brightness fallback) for white/light document detection
- **Perspective Correction** — Four-corner perspective transform to correct tilted documents
- **Manual Cropping** — Draggable four corners with semi-transparent mask and grid guides
- **Image Enhancement** — Grayscale, contrast enhancement (denoise + sharpen + CLAHE), black & white binarization
- **OCR Text Recognition** — Google ML Kit offline recognition supporting Chinese/English/Japanese/Korean
- **Text Overlay Preview** — Overlay recognized text blocks on the image
- **Multi-page Scanning** — Continuous scanning of multiple document pages
- **PDF Export** — A4-size PDF with page numbers, metadata, and searchable OCR text layer
- **PDF Page Ordering** — Drag-and-drop sorting, move up/down/top/bottom, delete pages
- **Image Saving** — Save scanned results as high-quality JPEG to gallery
- **Multi-select Merge** — Select multiple scanned files on home page and merge into PDF
- **Settings** — PDF/image save directory, OCR language, PDF quality configuration
- **Multi-language** — Chinese, English, Spanish; switchable in settings, supports system default
- **About Page** — Author info, donation addresses (XMR/USDT TRC20/USDT ERC20) with QR codes
- **Fully Offline** — All processing done locally, no network required

## Tech Stack

| Component | Technology | Description |
|-----------|-----------|-------------|
| Camera | CameraX | Preview, capture, FIT_CENTER mode for precise coordinate mapping |
| Document Detection | OpenCV 4.9.0 | GrabCut segmentation, Otsu threshold, Canny edge detection, contour extraction |
| White Page Detection | OpenCV + Brightness Analysis | Three-tier: GrabCut → Otsu → Brightness fallback |
| Perspective Correction | OpenCV | 4-point perspective transform with self-intersection detection |
| Image Enhancement | OpenCV + Android Canvas | Denoise + sharpen + CLAHE histogram equalization, adaptive binarization |
| OCR | Google ML Kit | Offline text recognition, Chinese/English/Japanese/Korean |
| PDF Generation | PDFBox-Android | A4-size PDF, page numbers, metadata, OCR text layer |
| UI Framework | Material Design | Material 3 components |
| Internationalization | Android Locale | LocaleHelper dynamic switching, Chinese/English/Spanish |
| Language | Kotlin | 100% Kotlin, coroutines for async processing |

## Project Structure

```
app/src/main/java/com/docscan/
├── DocScanApp.kt               # Application class, OpenCV initialization
├── scanner/                    # Core scanning engine
│   ├── DocumentDetector.kt     # General document edge detection (Canny+contours)
│   ├── WhitePageDetector.kt    # White page detection (GrabCut/Otsu/brightness fallback)
│   ├── PerspectiveCorrection.kt # 4-point perspective correction
│   └── ImageEnhancer.kt        # Image enhancement filters
├── ocr/                        # OCR module
│   └── OcrEngine.kt            # ML Kit text recognition engine
├── export/                     # Export module
│   ├── PdfExporter.kt          # PDF export (PDFBox)
│   └── FileHelper.kt           # File storage utilities
├── model/                      # Data models
│   ├── ScanPage.kt             # Scan page data (Parcelable)
│   └── ScanDocument.kt         # Document data
├── viewmodel/                  # ViewModel
│   └── ScanViewModel.kt        # Scan state management
├── util/                       # Utilities
│   ├── BitmapHelper.kt         # Bitmap decode/rotate/recycle
│   ├── AppSettings.kt          # SharedPreferences settings management
│   └── LocaleHelper.kt         # Multi-language switching utility
└── ui/                         # UI layer
    ├── MainActivity.kt         # Home (browse/multi-select/merge PDF)
    ├── ScanActivity.kt         # Scan (camera + A4 guide frame cropping)
    ├── ResultActivity.kt       # Results (preview/filter/OCR/crop/PDF export)
    ├── CropActivity.kt         # Standalone crop page
    ├── PdfOrderActivity.kt     # PDF page ordering (drag-and-drop)
    ├── AboutActivity.kt        # About (author/donation info)
    ├── SettingsActivity.kt     # Settings
    ├── DocumentOverlayView.kt  # A4 guide frame overlay (portrait A4 ratio)
    ├── CornerSelectionView.kt  # Four-corner selection view (drag+mask+grid)
    ├── CropImageView.kt        # Crop interaction view
    ├── TextOverlayView.kt      # OCR text block overlay
    └── TextRegionView.kt       # Text region selection view
```

## Build & Run

### Requirements

- JDK 17+
- Android SDK (compileSdk 34)
- Android Studio (recommended) or Gradle 8.5+

### Build Steps

1. Clone the project
2. Configure SDK path in `local.properties`:
   ```properties
   sdk.dir=/path/to/android-sdk
   ```
3. Build Debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
4. Build Release APK:
   ```bash
   ./gradlew assembleRelease
   ```

### Output

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`

## Usage

### Basic Scanning

1. Open app → Tap the scan button (bottom right)
2. Aim at document → Place document within the A4 guide frame
3. Tap capture button to take photo
4. System auto-crops and corrects based on guide frame area
5. View scan result → Add more pages if needed
6. Export as PDF or save images to gallery

### Manual Cropping

1. Switch to "Crop" mode in results page
2. Auto-detect document edges or manually drag four corners
3. Confirm crop → Perspective correction output

### Multi-page PDF Merge

1. Tap "Select" on home page to enter multi-select mode
2. Select pages to merge
3. Tap "Merge PDF" → Go to ordering page
4. Drag to adjust page order
5. Confirm and export PDF

### OCR Text Recognition

1. Switch to "Text" mode in results page to view recognized text
2. Switch to "Overlay" mode to see text block positions
3. Copy recognized text to clipboard

### Language Switching

1. Tap "Language" in Settings
2. Select System Default / 中文 / English / Español
3. Language will change after restarting the app

## Core Algorithms

### Multi-language Internationalization

```
res/
├── values/strings.xml          # Default resources (Chinese)
├── values-zh/strings.xml       # Chinese
├── values-en/strings.xml       # English
└── values-es/strings.xml       # Spanish
```

Switching flow:
1. User selects language in Settings → Saved to SharedPreferences
2. `DocScanApp.attachBaseContext()` calls `LocaleHelper.onAttach()` to apply language
3. `LocaleHelper.applyLanguage()` sets Locale and creates new ConfigurationContext
4. Prompt to restart app after switching

### Crop Coordinate Mapping

```
A4 guide frame corners (view coordinates)
    → Calculate actual preview area in view based on preview aspect ratio
    → Subtract preview area offset, divide by preview area size → Normalized coordinates (0~1)
    → Map to captured image coordinates based on preview/capture aspect ratio difference
    → PerspectiveCorrection.correct() → Perspective-corrected crop
```

Key points:
- Preview resolution width/height swapped based on sensor rotation (90°/270°) for correct portrait aspect ratio
- PreviewView uses FIT_CENTER mode, full preview content displayed without cropping
- Guide frame drawn based on actual preview area, precisely aligned with preview content

### White Page Detection (Three-tier Strategy)

```
1. GrabCut segmentation → Foreground mask → Morphological processing → Contour finding → Quad approximation
2. Otsu threshold segmentation → Binary mask → Morphological processing → Contour finding → Quad approximation
3. Brightness analysis fallback → Otsu threshold → Morphological cleanup → Connected components → Boundary extraction → Quad approximation
```

### Perspective Correction

```
4 corner points input → Validation (4 points/range/no self-intersection)
    → Calculate output width/height (max of top/bottom and left/right edges)
    → getPerspectiveTransform() → warpPerspective()
    → Corrected Bitmap
```

## Author

**lorime**

Email: lorime@126.com

## Donate

If this project helps you, consider supporting its development!

> XMR: 4DSQMNzzq46N1z2pZWAVdeA6JvUL9TCB2bnBiA3ZzoqEdYJnMydt5akCa3vtmapeDsbVKGPFdNkzzqTcJS8M8oyK7WGj5qMvNZRw61w6wMF
>
> USDT (TRC20): TG6DCBoQszDxc64owRZKkSHqZfcAQrqR8uM
>
> USDT (ERC20): 0x4323d39BA9b6Bd0570920e63a8D3a192b4459330

QR codes available in the `erweima/` directory.

## License

MIT License
