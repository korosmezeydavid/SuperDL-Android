#!/usr/bin/env python3
"""Train and export the offline HUF banknote TFLite classifier."""

from __future__ import annotations

import argparse
import random
from pathlib import Path

import numpy as np
import tensorflow as tf

INPUT_SIZE = 224
NUM_CLASSES = 7
LABELS = [
    "none",
    "huf_500",
    "huf_1000",
    "huf_2000",
    "huf_5000",
    "huf_10000",
    "huf_20000",
]

DENOMINATION_COLORS = {
    1: (210, 145, 55),
    2: (55, 95, 175),
    3: (120, 70, 45),
    4: (175, 185, 55),
    5: (155, 55, 95),
    6: (55, 125, 115),
}


def make_none_sample(rng: np.random.Generator) -> np.ndarray:
    """Üres járat, asztal, textil, homály – ne legyen hamis bankjegy."""
    kind = int(rng.integers(0, 5))
    if kind == 0:
        base = rng.integers(18, 55, size=3).astype(np.float32)
        image = rng.normal(loc=base, scale=6.0, size=(INPUT_SIZE, INPUT_SIZE, 3))
    elif kind == 1:
        base = rng.integers(170, 235, size=3).astype(np.float32)
        image = rng.normal(loc=base, scale=8.0, size=(INPUT_SIZE, INPUT_SIZE, 3))
    elif kind == 2:
        wood = np.array([125, 88, 52], dtype=np.float32)
        image = rng.normal(loc=wood, scale=14.0, size=(INPUT_SIZE, INPUT_SIZE, 3))
    elif kind == 3:
        fabric = rng.integers(35, 110, size=3).astype(np.float32)
        image = rng.normal(loc=fabric, scale=18.0, size=(INPUT_SIZE, INPUT_SIZE, 3))
    else:
        base = rng.integers(0, 256, size=3).astype(np.float32)
        image = rng.normal(loc=base, scale=42.0, size=(INPUT_SIZE, INPUT_SIZE, 3))
        image = np.clip(image, 0, 255)
        return image.astype(np.float32)

    noise = rng.uniform(-6, 6, size=(INPUT_SIZE, INPUT_SIZE, 3))
    image = np.clip(image + noise, 0, 255)
    return image.astype(np.float32)


def make_banknote_sample(class_id: int, rng: np.random.Generator) -> np.ndarray:
    color = np.array(DENOMINATION_COLORS[class_id], dtype=np.float32)
    image = rng.normal(loc=color, scale=16.0, size=(INPUT_SIZE, INPUT_SIZE, 3))
    margin_x = int(rng.integers(22, 48))
    margin_y = int(rng.integers(28, 58))
    image[:margin_y, :] *= rng.uniform(0.35, 0.62)
    image[-margin_y:, :] *= rng.uniform(0.35, 0.62)
    image[:, :margin_x] *= rng.uniform(0.35, 0.62)
    image[:, -margin_x:] *= rng.uniform(0.35, 0.62)
    gradient = np.linspace(0.82, 1.18, INPUT_SIZE, dtype=np.float32)[:, None, None]
    image *= gradient
    strip_x = int(rng.integers(70, 150))
    image[:, strip_x:strip_x + 8, :] *= rng.uniform(1.05, 1.25)
    noise = rng.uniform(-14, 14, size=(INPUT_SIZE, INPUT_SIZE, 3))
    image = np.clip(image + noise, 0, 255)
    if rng.random() < 0.25:
        image = tf.image.adjust_brightness(tf.convert_to_tensor(image), float(rng.uniform(-0.12, 0.12))).numpy()
    return image.astype(np.float32)


def make_sample(class_id: int, rng: np.random.Generator) -> np.ndarray:
    if class_id == 0:
        return make_none_sample(rng)
    return make_banknote_sample(class_id, rng)


def build_dataset(samples_per_class: int, seed: int) -> tuple[np.ndarray, np.ndarray]:
    rng = np.random.default_rng(seed)
    images: list[np.ndarray] = []
    labels: list[int] = []
    none_samples = int(samples_per_class * 1.6)
    for class_id in range(NUM_CLASSES):
        count = none_samples if class_id == 0 else samples_per_class
        for _ in range(count):
            images.append(make_sample(class_id, rng))
            labels.append(class_id)
    x = np.stack(images).astype(np.float32)
    y = tf.keras.utils.to_categorical(labels, NUM_CLASSES).astype(np.float32)
    perm = rng.permutation(len(labels))
    return x[perm], y[perm]


def build_model() -> tf.keras.Model:
    base = tf.keras.applications.MobileNetV3Small(
        input_shape=(INPUT_SIZE, INPUT_SIZE, 3),
        include_top=False,
        weights="imagenet",
        pooling="avg",
    )
    base.trainable = True
    inputs = tf.keras.Input(shape=(INPUT_SIZE, INPUT_SIZE, 3), name="image")
    x = base(inputs)
    x = tf.keras.layers.Dropout(0.20)(x)
    outputs = tf.keras.layers.Dense(NUM_CLASSES, activation="softmax", name="denomination")(x)
    model = tf.keras.Model(inputs=inputs, outputs=outputs, name="huf_banknote_classifier")
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=2e-4),
        loss="categorical_crossentropy",
        metrics=["accuracy"],
    )
    return model


def export_tflite(model: tf.keras.Model, output_path: Path) -> None:
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]
    tflite_model = converter.convert()
    output_path.write_bytes(tflite_model)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets",
    )
    parser.add_argument("--epochs", type=int, default=10)
    parser.add_argument("--samples-per-class", type=int, default=320)
    args = parser.parse_args()

    args.output_dir.mkdir(parents=True, exist_ok=True)

    x_train, y_train = build_dataset(args.samples_per_class, seed=42)
    x_val, y_val = build_dataset(max(args.samples_per_class // 3, 60), seed=99)

    model = build_model()
    model.fit(
        x_train,
        y_train,
        validation_data=(x_val, y_val),
        epochs=args.epochs,
        batch_size=32,
        verbose=1,
    )

    model_path = args.output_dir / "huf_banknote_classifier.tflite"
    labels_path = args.output_dir / "huf_banknote_labels.txt"
    export_tflite(model, model_path)
    labels_path.write_text("\n".join(LABELS) + "\n", encoding="utf-8")

    print(f"Wrote {model_path} ({model_path.stat().st_size} bytes)")
    print(f"Wrote {labels_path}")


if __name__ == "__main__":
    main()