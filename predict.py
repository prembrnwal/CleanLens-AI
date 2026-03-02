"""
Spam Image Detection - Single Image Prediction Script
Loads the trained model and classifies a given image as 'spam' or 'normal'.

Usage:
    python predict.py <image_path>
    python predict.py dataset/spam/example.jpg
"""

import sys
import os
import numpy as np
import tensorflow as tf
from tensorflow.keras.preprocessing import image

# ──────────────────────────────────────────────
# Configuration
# ──────────────────────────────────────────────
IMG_SIZE = 224
MODEL_PATH = "spam_model.h5"
CLASS_NAMES = ["normal", "spam"]  # alphabetical order (same as flow_from_directory)


def load_model():
    """Load the trained Keras model."""
    if not os.path.exists(MODEL_PATH):
        print(f"❌ Model file '{MODEL_PATH}' not found!")
        print("   Run train.py first to generate the model.")
        sys.exit(1)

    print(f"Loading model from '{MODEL_PATH}'...")
    model = tf.keras.models.load_model(MODEL_PATH)
    return model


def predict_image(model, img_path):
    """
    Predict whether a single image is spam or normal.

    Args:
        model: Loaded Keras model
        img_path: Path to the image file

    Returns:
        dict with 'class', 'confidence', and 'probabilities'
    """
    if not os.path.exists(img_path):
        print(f"❌ Image not found: '{img_path}'")
        sys.exit(1)

    # Load and preprocess the image
    img = image.load_img(img_path, target_size=(IMG_SIZE, IMG_SIZE))
    img_array = image.img_to_array(img)
    img_array = np.expand_dims(img_array, axis=0)  # Add batch dimension
    img_array = img_array / 255.0  # Rescale to [0, 1]

    # Predict
    predictions = model.predict(img_array, verbose=0)
    predicted_index = np.argmax(predictions[0])
    confidence = predictions[0][predicted_index] * 100

    return {
        "class": CLASS_NAMES[predicted_index],
        "confidence": confidence,
        "probabilities": {
            CLASS_NAMES[i]: f"{predictions[0][i] * 100:.2f}%"
            for i in range(len(CLASS_NAMES))
        },
    }


def predict_folder(model, folder_path):
    """Predict all images in a folder."""
    supported_ext = (".jpg", ".jpeg", ".png", ".bmp", ".webp")
    images = [
        f for f in os.listdir(folder_path)
        if f.lower().endswith(supported_ext)
    ]

    if not images:
        print(f"❌ No images found in '{folder_path}'")
        return

    print(f"\n📂 Predicting {len(images)} images from '{folder_path}'...\n")
    print(f"{'Image':<45} {'Prediction':<10} {'Confidence':<12}")
    print("─" * 67)

    spam_count = 0
    normal_count = 0

    for img_name in sorted(images):
        img_path = os.path.join(folder_path, img_name)
        result = predict_image(model, img_path)

        emoji = "🚫" if result["class"] == "spam" else "✅"
        print(f"{img_name:<45} {emoji} {result['class']:<8} {result['confidence']:.1f}%")

        if result["class"] == "spam":
            spam_count += 1
        else:
            normal_count += 1

    print("─" * 67)
    print(f"\n📊 Summary: {spam_count} spam, {normal_count} normal (out of {len(images)} images)")


def main():
    if len(sys.argv) < 2:
        print("Spam Image Detector - Prediction Tool")
        print("=" * 40)
        print("\nUsage:")
        print("  python predict.py <image_path>       → Predict a single image")
        print("  python predict.py <folder_path>      → Predict all images in a folder")
        print("\nExamples:")
        print("  python predict.py photo.jpg")
        print("  python predict.py dataset/spam")
        print("  python predict.py dataset/normal")
        sys.exit(0)

    target = sys.argv[1]
    model = load_model()

    if os.path.isdir(target):
        # Predict all images in a folder
        predict_folder(model, target)
    else:
        # Predict a single image
        result = predict_image(model, target)

        print("\n" + "=" * 40)
        print("🔍 PREDICTION RESULT")
        print("=" * 40)
        print(f"📄 Image:      {os.path.basename(target)}")

        emoji = "🚫 SPAM" if result["class"] == "spam" else "✅ NORMAL"
        print(f"🏷️  Class:      {emoji}")
        print(f"📊 Confidence: {result['confidence']:.2f}%")
        print(f"\n   Probabilities:")
        for cls, prob in result["probabilities"].items():
            print(f"     • {cls}: {prob}")
        print("=" * 40)


if __name__ == "__main__":
    main()
