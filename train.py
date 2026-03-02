"""
Spam Image Detection - Training Script
Uses MobileNet (pretrained on ImageNet) for transfer learning.
Classifies images as 'spam' (e.g. Good Morning forwards) or 'normal' (personal photos).
"""

import tensorflow as tf
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from tensorflow.keras.applications import MobileNet
from tensorflow.keras import layers, models
import matplotlib.pyplot as plt
import os

# ──────────────────────────────────────────────
# 1. Configuration
# ──────────────────────────────────────────────
IMG_SIZE = 224          # MobileNet expects 224x224
BATCH_SIZE = 32
EPOCHS = 5
DATASET_DIR = "dataset"
MODEL_SAVE_PATH = "spam_model.h5"

# ──────────────────────────────────────────────
# 2. Image Preprocessing + Data Augmentation
#    - rescale pixel values to [0, 1]
#    - 80/20 train/validation split
#    - augmentation to reduce overfitting
# ──────────────────────────────────────────────
train_datagen = ImageDataGenerator(
    rescale=1.0 / 255,
    validation_split=0.2,
    rotation_range=20,
    zoom_range=0.2,
    horizontal_flip=True,
    width_shift_range=0.1,
    height_shift_range=0.1,
    shear_range=0.1,
    brightness_range=[0.8, 1.2],
)

print("Loading training data...")
train_data = train_datagen.flow_from_directory(
    DATASET_DIR,
    target_size=(IMG_SIZE, IMG_SIZE),
    batch_size=BATCH_SIZE,
    class_mode="categorical",
    subset="training",
)

print("Loading validation data...")
val_data = train_datagen.flow_from_directory(
    DATASET_DIR,
    target_size=(IMG_SIZE, IMG_SIZE),
    batch_size=BATCH_SIZE,
    class_mode="categorical",
    subset="validation",
)

# Print detected classes
print(f"\nClasses detected: {train_data.class_indices}")
print(f"Training samples: {train_data.samples}")
print(f"Validation samples: {val_data.samples}\n")

# ──────────────────────────────────────────────
# 3. Load Pretrained MobileNet (without top layer)
#    Freeze all base layers so we only train
#    the new classification head.
# ──────────────────────────────────────────────
base_model = MobileNet(
    weights="imagenet",
    include_top=False,
    input_shape=(IMG_SIZE, IMG_SIZE, 3),
)
base_model.trainable = False

# ──────────────────────────────────────────────
# 4. Build Classification Head
#    GlobalAveragePooling2D → Dense 128 → Dropout → Softmax (2 classes)
# ──────────────────────────────────────────────
model = models.Sequential([
    base_model,
    layers.GlobalAveragePooling2D(),
    layers.Dense(128, activation="relu"),
    layers.Dropout(0.5),
    layers.Dense(2, activation="softmax"),   # 2 classes: normal, spam
])

model.summary()

# ──────────────────────────────────────────────
# 5. Compile
# ──────────────────────────────────────────────
model.compile(
    optimizer="adam",
    loss="categorical_crossentropy",
    metrics=["accuracy"],
)

# ──────────────────────────────────────────────
# 6. Train
# ──────────────────────────────────────────────
print("\n🚀 Starting training...\n")
history = model.fit(
    train_data,
    validation_data=val_data,
    epochs=EPOCHS,
)

# ──────────────────────────────────────────────
# 7. Save Model
# ──────────────────────────────────────────────
model.save(MODEL_SAVE_PATH)
print(f"\n✅ Model saved to '{MODEL_SAVE_PATH}'")

# ──────────────────────────────────────────────
# 8. Plot Training History
# ──────────────────────────────────────────────
fig, axes = plt.subplots(1, 2, figsize=(12, 4))

# Accuracy
axes[0].plot(history.history["accuracy"], label="Train Accuracy")
axes[0].plot(history.history["val_accuracy"], label="Val Accuracy")
axes[0].set_title("Accuracy")
axes[0].set_xlabel("Epoch")
axes[0].set_ylabel("Accuracy")
axes[0].legend()

# Loss
axes[1].plot(history.history["loss"], label="Train Loss")
axes[1].plot(history.history["val_loss"], label="Val Loss")
axes[1].set_title("Loss")
axes[1].set_xlabel("Epoch")
axes[1].set_ylabel("Loss")
axes[1].legend()

plt.tight_layout()
plt.savefig("training_history.png", dpi=150)
print("📊 Training plot saved to 'training_history.png'")
plt.show()
