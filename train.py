"""
Spam Image Detection - Training Script (v2.0)
Uses MobileNet (pretrained on ImageNet) for transfer learning.
Classifies images as 'spam' (e.g. Good Morning forwards) or 'normal' (personal photos).

Features:
  - Two-phase training: frozen base → fine-tuned last 20 layers
  - Early stopping + learning rate scheduling
  - Full evaluation: confusion matrix, F1, precision, recall, ROC curve
  - Saves best model checkpoint automatically
"""

import tensorflow as tf
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from tensorflow.keras.applications import MobileNet
from tensorflow.keras import layers, models
from tensorflow.keras.callbacks import EarlyStopping, ReduceLROnPlateau, ModelCheckpoint
import matplotlib.pyplot as plt
import numpy as np
import os

# Suppress TF info logs
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'

# ──────────────────────────────────────────────
# 1. Configuration
# ──────────────────────────────────────────────
IMG_SIZE = 224          # MobileNet expects 224x224
BATCH_SIZE = 32
PHASE1_EPOCHS = 10      # Frozen base training
PHASE2_EPOCHS = 15      # Fine-tuning last layers
FINE_TUNE_LAYERS = 20   # Number of MobileNet layers to unfreeze
DATASET_DIR = "dataset"
MODEL_SAVE_PATH = "spam_model.h5"
RESULTS_DIR = "evaluation_results"

# Create results directory
os.makedirs(RESULTS_DIR, exist_ok=True)

# ──────────────────────────────────────────────
# 2. Image Preprocessing + Data Augmentation
#    - rescale pixel values to [0, 1]
#    - 80/20 train/validation split
#    - augmentation to reduce overfitting
# ──────────────────────────────────────────────
train_datagen = ImageDataGenerator(
    rescale=1.0 / 255,
    validation_split=0.2,
    rotation_range=25,
    zoom_range=0.2,
    horizontal_flip=True,
    vertical_flip=False,
    width_shift_range=0.15,
    height_shift_range=0.15,
    shear_range=0.15,
    brightness_range=[0.7, 1.3],
    fill_mode='nearest',
)

# Validation data should NOT be augmented (only rescale)
val_datagen = ImageDataGenerator(
    rescale=1.0 / 255,
    validation_split=0.2,
)

print("=" * 60)
print("🧹 CleanLens AI — Spam Image Classifier Training")
print("=" * 60)

print("\nLoading training data...")
train_data = train_datagen.flow_from_directory(
    DATASET_DIR,
    target_size=(IMG_SIZE, IMG_SIZE),
    batch_size=BATCH_SIZE,
    class_mode="categorical",
    subset="training",
    shuffle=True,
    seed=42,
)

print("Loading validation data...")
val_data = val_datagen.flow_from_directory(
    DATASET_DIR,
    target_size=(IMG_SIZE, IMG_SIZE),
    batch_size=BATCH_SIZE,
    class_mode="categorical",
    subset="validation",
    shuffle=False,
    seed=42,
)

# Print detected classes
print(f"\n📂 Classes detected: {train_data.class_indices}")
print(f"   Training samples:   {train_data.samples}")
print(f"   Validation samples: {val_data.samples}")

CLASS_NAMES = list(train_data.class_indices.keys())
print(f"   Class names: {CLASS_NAMES}\n")

# ──────────────────────────────────────────────
# 3. Load Pretrained MobileNet (without top layer)
#    Freeze ALL base layers for Phase 1.
# ──────────────────────────────────────────────
print("Loading pretrained MobileNet...")
base_model = MobileNet(
    weights="imagenet",
    include_top=False,
    input_shape=(IMG_SIZE, IMG_SIZE, 3),
)
base_model.trainable = False

# ──────────────────────────────────────────────
# 4. Build Classification Head
#    GlobalAveragePooling2D → Dense 256 → Dropout → Dense 128 → Dropout → Softmax
# ──────────────────────────────────────────────
model = models.Sequential([
    base_model,
    layers.GlobalAveragePooling2D(),
    layers.Dense(256, activation="relu"),
    layers.BatchNormalization(),
    layers.Dropout(0.4),
    layers.Dense(128, activation="relu"),
    layers.BatchNormalization(),
    layers.Dropout(0.3),
    layers.Dense(2, activation="softmax"),   # 2 classes: normal, spam
])

model.summary()

# ──────────────────────────────────────────────
# 5. Callbacks
# ──────────────────────────────────────────────
early_stop = EarlyStopping(
    monitor='val_loss',
    patience=5,
    restore_best_weights=True,
    verbose=1,
)

reduce_lr = ReduceLROnPlateau(
    monitor='val_loss',
    factor=0.5,
    patience=3,
    min_lr=1e-7,
    verbose=1,
)

checkpoint = ModelCheckpoint(
    MODEL_SAVE_PATH,
    monitor='val_accuracy',
    save_best_only=True,
    verbose=1,
)

callbacks = [early_stop, reduce_lr, checkpoint]

# ──────────────────────────────────────────────
# 6. PHASE 1 — Train classification head (base frozen)
# ──────────────────────────────────────────────
print("\n" + "=" * 60)
print("🚀 PHASE 1: Training classification head (base frozen)")
print("=" * 60 + "\n")

model.compile(
    optimizer=tf.keras.optimizers.Adam(learning_rate=1e-3),
    loss="categorical_crossentropy",
    metrics=["accuracy"],
)

history_phase1 = model.fit(
    train_data,
    validation_data=val_data,
    epochs=PHASE1_EPOCHS,
    callbacks=callbacks,
)

# ──────────────────────────────────────────────
# 7. PHASE 2 — Fine-tune last N layers of MobileNet
# ──────────────────────────────────────────────
print("\n" + "=" * 60)
print(f"🔧 PHASE 2: Fine-tuning last {FINE_TUNE_LAYERS} MobileNet layers")
print("=" * 60 + "\n")

# Unfreeze the last N layers
base_model.trainable = True
for layer in base_model.layers[:-FINE_TUNE_LAYERS]:
    layer.trainable = False

trainable_count = sum(1 for layer in base_model.layers if layer.trainable)
print(f"   Trainable MobileNet layers: {trainable_count}/{len(base_model.layers)}")

# Re-compile with lower learning rate (important!)
model.compile(
    optimizer=tf.keras.optimizers.Adam(learning_rate=1e-5),
    loss="categorical_crossentropy",
    metrics=["accuracy"],
)

history_phase2 = model.fit(
    train_data,
    validation_data=val_data,
    epochs=PHASE2_EPOCHS,
    callbacks=callbacks,
)

# ──────────────────────────────────────────────
# 8. Save Final Model
# ──────────────────────────────────────────────
model.save(MODEL_SAVE_PATH)
print(f"\n✅ Final model saved to '{MODEL_SAVE_PATH}'")

# ──────────────────────────────────────────────
# 9. EVALUATION — Confusion Matrix, F1, Precision, Recall, ROC
# ──────────────────────────────────────────────
print("\n" + "=" * 60)
print("📊 EVALUATION — Generating metrics & plots...")
print("=" * 60 + "\n")

from sklearn.metrics import (
    classification_report,
    confusion_matrix,
    roc_curve,
    auc,
)

# Get predictions on validation set
val_data.reset()
y_pred_probs = model.predict(val_data, verbose=1)
y_pred = np.argmax(y_pred_probs, axis=1)
y_true = val_data.classes

# ── 9a. Classification Report ──
report = classification_report(y_true, y_pred, target_names=CLASS_NAMES, digits=4)
print("\n📋 Classification Report:")
print(report)

# Save report to file
with open(os.path.join(RESULTS_DIR, "classification_report.txt"), "w") as f:
    f.write("CleanLens AI — Classification Report\n")
    f.write("=" * 50 + "\n\n")
    f.write(report)
print(f"   Saved to '{RESULTS_DIR}/classification_report.txt'")

# ── 9b. Confusion Matrix ──
cm = confusion_matrix(y_true, y_pred)
fig, ax = plt.subplots(figsize=(8, 6))
im = ax.imshow(cm, interpolation='nearest', cmap='Blues')
ax.figure.colorbar(im, ax=ax)
ax.set(
    xticks=np.arange(cm.shape[1]),
    yticks=np.arange(cm.shape[0]),
    xticklabels=CLASS_NAMES,
    yticklabels=CLASS_NAMES,
    title='Confusion Matrix',
    ylabel='Actual Label',
    xlabel='Predicted Label',
)
# Annotate cells with counts
thresh = cm.max() / 2.0
for i in range(cm.shape[0]):
    for j in range(cm.shape[1]):
        ax.text(j, i, format(cm[i, j], 'd'),
                ha="center", va="center",
                color="white" if cm[i, j] > thresh else "black",
                fontsize=20, fontweight='bold')
plt.tight_layout()
plt.savefig(os.path.join(RESULTS_DIR, "confusion_matrix.png"), dpi=150)
print(f"   Saved confusion matrix to '{RESULTS_DIR}/confusion_matrix.png'")

# ── 9c. ROC Curve ──
fpr, tpr, _ = roc_curve(y_true, y_pred_probs[:, 1])
roc_auc = auc(fpr, tpr)

fig, ax = plt.subplots(figsize=(8, 6))
ax.plot(fpr, tpr, color='#6C63FF', lw=2, label=f'ROC curve (AUC = {roc_auc:.4f})')
ax.plot([0, 1], [0, 1], color='gray', lw=1, linestyle='--', label='Random')
ax.set_xlim([0.0, 1.0])
ax.set_ylim([0.0, 1.05])
ax.set_xlabel('False Positive Rate', fontsize=12)
ax.set_ylabel('True Positive Rate', fontsize=12)
ax.set_title('ROC Curve — Spam Detection', fontsize=14)
ax.legend(loc="lower right", fontsize=11)
ax.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig(os.path.join(RESULTS_DIR, "roc_curve.png"), dpi=150)
print(f"   Saved ROC curve to '{RESULTS_DIR}/roc_curve.png'")
print(f"   AUC Score: {roc_auc:.4f}")

# ── 9d. Training History Plot (combined both phases) ──
# Merge histories from Phase 1 and Phase 2
acc = history_phase1.history["accuracy"] + history_phase2.history["accuracy"]
val_acc = history_phase1.history["val_accuracy"] + history_phase2.history["val_accuracy"]
loss = history_phase1.history["loss"] + history_phase2.history["loss"]
val_loss = history_phase1.history["val_loss"] + history_phase2.history["val_loss"]
total_epochs = len(acc)
phase1_end = len(history_phase1.history["accuracy"])

fig, axes = plt.subplots(1, 2, figsize=(14, 5))

# Accuracy
axes[0].plot(acc, label="Train Accuracy", color='#6C63FF')
axes[0].plot(val_acc, label="Val Accuracy", color='#FF4D6A')
axes[0].axvline(x=phase1_end - 0.5, color='gray', linestyle='--', alpha=0.7, label='Fine-tune start')
axes[0].set_title("Accuracy (Phase 1 + Phase 2)", fontsize=13)
axes[0].set_xlabel("Epoch")
axes[0].set_ylabel("Accuracy")
axes[0].legend()
axes[0].grid(True, alpha=0.3)

# Loss
axes[1].plot(loss, label="Train Loss", color='#6C63FF')
axes[1].plot(val_loss, label="Val Loss", color='#FF4D6A')
axes[1].axvline(x=phase1_end - 0.5, color='gray', linestyle='--', alpha=0.7, label='Fine-tune start')
axes[1].set_title("Loss (Phase 1 + Phase 2)", fontsize=13)
axes[1].set_xlabel("Epoch")
axes[1].set_ylabel("Loss")
axes[1].legend()
axes[1].grid(True, alpha=0.3)

plt.tight_layout()
plt.savefig(os.path.join(RESULTS_DIR, "training_history.png"), dpi=150)
print(f"   Saved training history to '{RESULTS_DIR}/training_history.png'")

# ──────────────────────────────────────────────
# 10. Final Summary
# ──────────────────────────────────────────────
print("\n" + "=" * 60)
print("🎉 TRAINING COMPLETE — Summary")
print("=" * 60)
print(f"   Model:           MobileNet + Custom Head")
print(f"   Phase 1 epochs:  {phase1_end} (frozen base)")
print(f"   Phase 2 epochs:  {total_epochs - phase1_end} (fine-tuned last {FINE_TUNE_LAYERS} layers)")
print(f"   Final Val Acc:   {val_acc[-1]:.4f}")
print(f"   AUC Score:       {roc_auc:.4f}")
print(f"   Model saved:     {MODEL_SAVE_PATH}")
print(f"   Results saved:   {RESULTS_DIR}/")
print("=" * 60)
