"""
Convert the trained Keras model (.h5) to TensorFlow Lite (.tflite)
for deployment on Android devices.
"""

import tensorflow as tf
import os

H5_MODEL_PATH = "spam_model.h5"
TFLITE_OUTPUT_PATH = "spam_model.tflite"

# ──────────────────────────────────────────────
# 1. Load the trained Keras model
# ──────────────────────────────────────────────
if not os.path.exists(H5_MODEL_PATH):
    print(f"❌ Model file '{H5_MODEL_PATH}' not found!")
    print("   Run train.py first to generate the model.")
    exit(1)

print(f"Loading model from '{H5_MODEL_PATH}'...")
model = tf.keras.models.load_model(H5_MODEL_PATH)

# ──────────────────────────────────────────────
# 2. Convert to TensorFlow Lite
# ──────────────────────────────────────────────
print("Converting to TFLite format...")
converter = tf.lite.TFLiteConverter.from_keras_model(model)

# Optional: enable optimizations for smaller model size
# converter.optimizations = [tf.lite.Optimize.DEFAULT]

tflite_model = converter.convert()

# ──────────────────────────────────────────────
# 3. Save the .tflite file
# ──────────────────────────────────────────────
with open(TFLITE_OUTPUT_PATH, "wb") as f:
    f.write(tflite_model)

file_size_mb = os.path.getsize(TFLITE_OUTPUT_PATH) / (1024 * 1024)
print(f"\n✅ TFLite model created: '{TFLITE_OUTPUT_PATH}'")
print(f"   File size: {file_size_mb:.2f} MB")
print("\n📱 Copy this file into your Android app's assets folder.")
