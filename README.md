# 🧹 CleanLens AI — Smart Spam Image Cleaner

> **A mini Google Photos cleaner** — Detects spam images, duplicates, and suggests cleanup using CNN + OCR + Smart Scoring, all running on-device with TensorFlow Lite.

[![Android](https://img.shields.io/badge/Platform-Android-green?logo=android)](https://developer.android.com)
[![TensorFlow Lite](https://img.shields.io/badge/ML-TensorFlow%20Lite-orange?logo=tensorflow)](https://www.tensorflow.org/lite)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple?logo=kotlin)](https://kotlinlang.org)
[![Python](https://img.shields.io/badge/Training-Python-blue?logo=python)](https://python.org)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

---

## 🎯 What It Does

CleanLens AI scans your phone's image folders and intelligently identifies:

- 🚫 **Spam Images** — "Good Morning" forwards, festival wishes, chain messages
- 📋 **Duplicate Images** — Exact copies + visually similar images (cropped, compressed)
- 📝 **Text-Heavy Spam** — Uses OCR to read text inside images and detect spam keywords

Then suggests cleanup with a **one-tap "Move to Trash"** that safely sends images to your phone's Recently Deleted folder.

---

## 🏗️ Architecture

```
📱 User selects folder to scan
         ↓
┌─────────────────────────────────────────────┐
│  📂 Gallery Scanner (MediaStore API)        │
│  Query all images from selected folder      │
└─────────────────┬───────────────────────────┘
                  ↓
┌─────────────────────────────────────────────┐
│  🧠 CNN Spam Classifier (TFLite)            │
│  MobileNet transfer learning → spam/normal  │
│  Input: 224×224 RGB → Output: probability   │
└─────────────────┬───────────────────────────┘
                  ↓
┌─────────────────────────────────────────────┐
│  📝 OCR Text Detection (ML Kit)            │
│  Extract text → check 50+ spam keywords    │
│  "good morning", "jai shree ram", etc.      │
└─────────────────┬───────────────────────────┘
                  ↓
┌─────────────────────────────────────────────┐
│  📋 Duplicate Detection                     │
│  MD5 Hash (exact) + pHash (near-duplicates) │
└─────────────────┬───────────────────────────┘
                  ↓
┌─────────────────────────────────────────────┐
│  📊 Smart Spam Scoring                      │
│  CNN > 0.7 (+0.5) + OCR keyword (+0.3)      │
│  + Duplicate (+0.3) + Old image (+0.2)      │
│  Total > 0.5 → Suggest Delete               │
└─────────────────┬───────────────────────────┘
                  ↓
┌─────────────────────────────────────────────┐
│  🗑️ Suggestion UI                           │
│  Grid view → Select All → Move to Trash     │
│  (Goes to Gallery's Recently Deleted)       │
└─────────────────────────────────────────────┘
```

---

## 🛠️ Tech Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **ML Model** | MobileNet + TensorFlow | Transfer learning for spam classification |
| **Mobile ML** | TensorFlow Lite | On-device inference (no internet needed) |
| **OCR** | Google ML Kit | Extract text from images for keyword detection |
| **Hashing** | MD5 + Perceptual Hash | Exact + near-duplicate detection |
| **Android** | Kotlin + MVVM | Production-grade architecture |
| **Async** | Kotlin Coroutines | Background scanning without UI freezing |
| **UI** | Material Design 3 | Modern, dark-themed interface |
| **Image Loading** | Coil | Efficient thumbnail loading in RecyclerView |

---

## 📂 Project Structure

```
CleanLens-AI/
│
├── 🐍 Python (ML Training)
│   ├── train.py              # Two-phase training: frozen → fine-tuned
│   ├── predict.py            # Single image prediction script
│   ├── convert.py            # Convert .h5 → .tflite
│   ├── requirements.txt      # Python dependencies
│   └── dataset/              # Training images
│       ├── spam/             # Spam images (good morning, forwards)
│       └── normal/           # Normal photos (camera, personal)
│
├── 📱 Android App
│   └── android/
│       ├── app/src/main/
│       │   ├── assets/
│       │   │   └── spam_model.tflite          # Converted model
│       │   ├── java/com/cleanlens/ai/
│       │   │   ├── MainActivity.kt            # Home screen + single image classify
│       │   │   ├── ScanActivity.kt            # Full gallery scan results
│       │   │   ├── SpamClassifier.kt          # TFLite model inference
│       │   │   ├── GalleryScanner.kt          # MediaStore image queries
│       │   │   ├── OcrAnalyzer.kt             # ML Kit OCR + spam keywords
│       │   │   ├── DuplicateDetector.kt       # MD5 + perceptual hashing
│       │   │   ├── ScanViewModel.kt           # MVVM ViewModel (Coroutines)
│       │   │   ├── ScanResultAdapter.kt       # RecyclerView adapter
│       │   │   └── ScannedImage.kt            # Data model + scoring
│       │   └── res/
│       │       ├── layout/                    # XML layouts
│       │       ├── values/                    # Colors, strings, themes
│       │       └── drawable/                  # Gradients, badges, icons
│       └── build.gradle                       # Dependencies
│
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites

- Python 3.8+ with TensorFlow
- Android Studio (Hedgehog or later)
- Android SDK 34
- Java 17+

### 1. Train the Model (Python)

```bash
# Create virtual environment
python -m venv spam_env
spam_env\Scripts\activate      # Windows
source spam_env/bin/activate   # macOS/Linux

# Install dependencies
pip install -r requirements.txt

# Add images to dataset/spam/ and dataset/normal/
# Then train:
python train.py

# Convert to mobile format:
python convert.py
```

**Training Output:**
- `spam_model.h5` — Full Keras model
- `spam_model.tflite` — Mobile-optimized model
- `evaluation_results/` — Confusion matrix, ROC curve, classification report

### 2. Run the Android App

```bash
# Open the android/ folder in Android Studio
# Sync Gradle → Connect device/emulator → Run ▶
```

Or build from command line:
```bash
cd android
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug    # Install on connected device
```

---

## 📊 ML Model Details

### Architecture
```
MobileNet (ImageNet pretrained)
    ↓ GlobalAveragePooling2D
    ↓ Dense(256, ReLU) + BatchNorm + Dropout(0.4)
    ↓ Dense(128, ReLU) + BatchNorm + Dropout(0.3)
    ↓ Dense(2, Softmax) → [Normal, Spam]
```

### Training Strategy
| Phase | Epochs | Learning Rate | What's Training |
|-------|--------|--------------|----------------|
| **Phase 1** | 10 | 1e-3 | Classification head only (MobileNet frozen) |
| **Phase 2** | 15 | 1e-5 | Last 20 MobileNet layers + head (fine-tuning) |

### Callbacks
- **Early Stopping** — Stops if val_loss doesn't improve for 5 epochs
- **ReduceLROnPlateau** — Halves LR if val_loss stalls for 3 epochs
- **ModelCheckpoint** — Saves best model by val_accuracy

### Evaluation Metrics
- Confusion Matrix
- Precision, Recall, F1-Score (per class)
- ROC Curve with AUC Score

---

## 📊 Smart Spam Scoring

Each image gets a combined score from multiple signals:

| Signal | Condition | Score |
|--------|-----------|:-----:|
| 🧠 CNN Model | Spam probability > 0.7 | **+0.5** |
| 📝 OCR Keywords | Spam text detected | **+0.3** |
| 📋 Duplicate | Image has copies | **+0.3** |
| 📅 Old Image | Added > 6 months ago | **+0.2** |

**Total > 0.5 → Flagged as Spam**

### OCR Spam Keywords (50+)
Covers English greetings, Hindi phrases, festival wishes, forwarding cues, and motivational spam:

```
"good morning", "happy sunday", "jai shree ram", "happy diwali",
"forward this", "share with", "believe in yourself", "suprabhat", ...
```

---

## 🔍 Duplicate Detection

### Method 1: MD5 Hash (Exact Duplicates)
Generates MD5 hash of raw image bytes. Identical hash = identical image.

### Method 2: Perceptual Hash (Near-Duplicates)
1. Resize image to 8×8 grayscale
2. Compute average pixel brightness
3. Generate 64-bit hash (pixel > avg = 1, else 0)
4. Compare hashes via Hamming distance
5. Distance < 10 = visually similar

**Catches:** Cropped images, re-compressed JPEGs, brightness adjustments, screenshots of same content.

---

## 📱 App Features

| Feature | Description |
|---------|------------|
| 📷 **Single Image Check** | Pick from gallery or take a photo → instant spam/normal result |
| 🔍 **Full Folder Scan** | Select any folder → batch scan all images |
| 📂 **Folder Picker** | Choose which folder to scan (WhatsApp, Camera, Downloads, etc.) |
| 📊 **Results Dashboard** | Shows spam count, duplicate count, storage saveable |
| ☑️ **Batch Selection** | Select All / individual checkboxes for cleanup |
| 🗑️ **Safe Delete** | Moves to Gallery's "Recently Deleted" (recoverable for 30 days) |
| 🏷️ **Smart Badges** | Color-coded spam score + OCR/duplicate indicators per image |

---

## 🧪 Testing

### Single Image Prediction (Python)
```bash
python predict.py path/to/image.jpg
```

### On Android
1. Connect phone via USB (enable USB Debugging)
2. Run app from Android Studio
3. Grant storage permission
4. Tap "Scan Full Gallery" → pick a folder
5. Review results → select spam → "Move to Trash"

---

## 📋 Android Permissions

| Permission | Why It's Needed |
|------------|----------------|
| `READ_MEDIA_IMAGES` | Access device images for scanning (Android 13+) |
| `READ_EXTERNAL_STORAGE` | Access images on older Android versions |
| `WRITE_EXTERNAL_STORAGE` | Delete images on Android 10 and below |
| `CAMERA` | Take photos for single-image classification |

---

## 🗺️ Roadmap

- [x] Train CNN spam classifier (MobileNet)
- [x] Convert to TFLite for mobile
- [x] Android app with single image classification
- [x] Full gallery folder scanning
- [x] OCR text detection + spam keywords
- [x] Duplicate detection (MD5 + pHash)
- [x] Smart spam scoring system
- [x] Move to Trash (native Android)
- [ ] Face detection (protect family photos)
- [x] Batch scan multiple folders
- [ ] Scan history / statistics dashboard
- [ ] Model comparison (MobileNet vs EfficientNet)

---

## 🤝 Contributing

1. Fork the repo
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License.

---

## 👨‍💻 Author

**Prem Brnwal**
- GitHub: [@prembrnwal](https://github.com/prembrnwal)

---

<p align="center">
  Built with ❤️ using TensorFlow, Kotlin, and a lot of forwarded Good Morning images 🌅
</p>
