#!/usr/bin/env python3
"""Optimize app TFLite models with pruning + PTQ and benchmark the result."""

from __future__ import annotations

import argparse
import json
import math
import multiprocessing as mp
import os
import shutil
import sys
import time
from contextlib import contextmanager
from dataclasses import dataclass
from pathlib import Path
from types import SimpleNamespace
from typing import Any

import numpy as np
import psutil


PROJECT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE_ROOT = Path("/home/choi/mca/project")
DEFAULT_OUTPUT_ROOT = PROJECT_ROOT / "artifacts" / "model_optimization"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, default=DEFAULT_SOURCE_ROOT)
    parser.add_argument("--output-root", type=Path, default=DEFAULT_OUTPUT_ROOT)
    parser.add_argument("--copy-to-assets", action="store_true")
    parser.add_argument("--skip-train", action="store_true", help="Skip pruning fine-tuning and only run export/eval.")
    parser.add_argument("--train-subset", type=int, default=None, help="Optional cap on train samples for debugging.")
    parser.add_argument("--test-subset", type=int, default=None, help="Optional cap on test samples for debugging.")
    parser.add_argument(
        "--models",
        nargs="*",
        default=None,
        help="Optional list of model keys to optimize (for example: voice_only_veracity face_emotion).",
    )
    parser.add_argument("--disable-pruning", action="store_true", help="Skip pruning and export a quantized-only model.")
    parser.add_argument("--target-sparsity-override", type=float, default=None)
    parser.add_argument("--finetune-epochs-override", type=int, default=None)
    parser.add_argument("--learning-rate-override", type=float, default=None)
    parser.add_argument("--quantization-mode", choices=("int8", "int8_sparse", "float16_sparse"), default="int8_sparse")
    parser.add_argument("--optimized-name-suffix", default=None, help="Suffix appended before the .tflite extension.")
    return parser.parse_args()


def import_training_modules(source_root: Path):
    sys.path.insert(0, str(source_root))

    import keras
    import tensorflow as tf
    import train_model_1
    import train_model_2
    from models import data_2_tf, model_2_tf

    return keras, tf, train_model_1, train_model_2, data_2_tf, model_2_tf


@contextmanager
def pushd(path: Path):
    previous = Path.cwd()
    os.chdir(path)
    try:
        yield
    finally:
        os.chdir(previous)


@dataclass
class ModelSpec:
    key: str
    artifact_name: str
    task: str
    modality: str
    checkpoint_path: Path
    baseline_tflite_path: Path
    optimized_tflite_name: str
    train_split: str
    test_split: str
    target_sparsity: float
    finetune_epochs: int
    finetune_batch_size: int
    learning_rate: float
    weight_decay: float
    is_face_model: bool = False


@dataclass
class DatasetBundle:
    train_inputs: dict[str, np.ndarray]
    train_labels: np.ndarray
    test_inputs: dict[str, np.ndarray]
    test_labels: np.ndarray
    class_names: list[str]
    train_dataset: Any
    test_dataset: Any
    representative_inputs: dict[str, np.ndarray]
    extra: dict[str, Any]


def apply_spec_overrides(spec: ModelSpec, args: argparse.Namespace) -> ModelSpec:
    optimized_name = spec.optimized_tflite_name
    if args.optimized_name_suffix:
        stem, suffix = optimized_name.rsplit(".", 1)
        optimized_name = f"{stem}_{args.optimized_name_suffix}.{suffix}"

    return ModelSpec(
        key=spec.key,
        artifact_name=spec.artifact_name,
        task=spec.task,
        modality=spec.modality,
        checkpoint_path=spec.checkpoint_path,
        baseline_tflite_path=spec.baseline_tflite_path,
        optimized_tflite_name=optimized_name,
        train_split=spec.train_split,
        test_split=spec.test_split,
        target_sparsity=args.target_sparsity_override if args.target_sparsity_override is not None else spec.target_sparsity,
        finetune_epochs=args.finetune_epochs_override if args.finetune_epochs_override is not None else spec.finetune_epochs,
        finetune_batch_size=spec.finetune_batch_size,
        learning_rate=args.learning_rate_override if args.learning_rate_override is not None else spec.learning_rate,
        weight_decay=spec.weight_decay,
        is_face_model=spec.is_face_model,
    )


class FixedPruningMaskCallback:
    """Keep pruned weights at zero during fine-tuning."""

    def __init__(self, masked_weights: list[tuple[Any, np.ndarray]]):
        self.masked_weights = masked_weights
        self.model = None
        self.params = {}

    def set_model(self, model) -> None:
        self.model = model

    def set_params(self, params) -> None:
        self.params = params

    def _apply_masks(self) -> None:
        for weight, mask in self.masked_weights:
            weight.assign(weight * mask)

    def on_train_batch_end(self, batch, logs=None) -> None:
        self._apply_masks()

    def on_train_begin(self, logs=None) -> None:
        self._apply_masks()

    def on_train_batch_begin(self, batch, logs=None) -> None:
        return None

    def on_epoch_begin(self, epoch, logs=None) -> None:
        return None

    def on_epoch_end(self, epoch, logs=None) -> None:
        self._apply_masks()

    def on_train_end(self, logs=None) -> None:
        self._apply_masks()


def model_specs(source_root: Path) -> list[ModelSpec]:
    assets = PROJECT_ROOT / "app" / "src" / "main" / "assets"
    return [
        ModelSpec(
            key="voice_ppg_emotion",
            artifact_name="model_1_emotion",
            task="emotion",
            modality="voice_ppg",
            checkpoint_path=source_root / "outputs" / "voice_ppg_emotion" / "best_model.keras",
            baseline_tflite_path=assets / "model_1_emotion_float16.tflite",
            optimized_tflite_name="model_1_emotion_int8_sparse.tflite",
            train_split="train",
            test_split="test",
            target_sparsity=0.40,
            finetune_epochs=3,
            finetune_batch_size=64,
            learning_rate=1e-4,
            weight_decay=1e-5,
        ),
        ModelSpec(
            key="voice_ppg_veracity",
            artifact_name="model_1_veracity",
            task="veracity",
            modality="voice_ppg",
            checkpoint_path=source_root / "outputs" / "voice_ppg_veracity" / "best_model.keras",
            baseline_tflite_path=assets / "model_1_veracity_float16.tflite",
            optimized_tflite_name="model_1_veracity_int8_sparse.tflite",
            train_split="train",
            test_split="test",
            target_sparsity=0.25,
            finetune_epochs=3,
            finetune_batch_size=128,
            learning_rate=1e-4,
            weight_decay=1e-5,
        ),
        ModelSpec(
            key="voice_only_emotion",
            artifact_name="model_1_voice_only_emotion",
            task="emotion",
            modality="voice_only",
            checkpoint_path=source_root / "outputs" / "voice_only_emotion" / "best_model.keras",
            baseline_tflite_path=assets / "model_1_voice_only_emotion_float16.tflite",
            optimized_tflite_name="model_1_voice_only_emotion_int8_sparse.tflite",
            train_split="train",
            test_split="test",
            target_sparsity=0.35,
            finetune_epochs=3,
            finetune_batch_size=64,
            learning_rate=1e-4,
            weight_decay=1e-5,
        ),
        ModelSpec(
            key="voice_only_veracity",
            artifact_name="model_1_voice_only_veracity",
            task="veracity",
            modality="voice_only",
            checkpoint_path=source_root / "outputs" / "voice_only_veracity" / "best_model.keras",
            baseline_tflite_path=assets / "model_1_voice_only_veracity_float16.tflite",
            optimized_tflite_name="model_1_voice_only_veracity_int8_sparse.tflite",
            train_split="train",
            test_split="test",
            target_sparsity=0.25,
            finetune_epochs=3,
            finetune_batch_size=128,
            learning_rate=1e-4,
            weight_decay=1e-5,
        ),
        ModelSpec(
            key="face_emotion",
            artifact_name="model_2_face_emotion",
            task="emotion",
            modality="face",
            checkpoint_path=source_root / "outputs" / "face_model" / "best_model.keras",
            baseline_tflite_path=assets / "model_2_face_emotion_float16.tflite",
            optimized_tflite_name="model_2_face_emotion_int8_sparse.tflite",
            train_split="train",
            test_split="test",
            target_sparsity=0.35,
            finetune_epochs=3,
            finetune_batch_size=32,
            learning_rate=5e-5,
            weight_decay=1e-5,
            is_face_model=True,
        ),
    ]


def maybe_subset(inputs: dict[str, np.ndarray], labels: np.ndarray, limit: int | None) -> tuple[dict[str, np.ndarray], np.ndarray]:
    if limit is None or len(labels) <= limit:
        return inputs, labels
    return {key: value[:limit] for key, value in inputs.items()}, labels[:limit]


def load_model1_bundle(
    spec: ModelSpec,
    source_root: Path,
    tf,
    train_model_1,
    train_subset: int | None,
    test_subset: int | None,
) -> DatasetBundle:
    manifest_root = source_root / "data" / "model_1" / "windows" / "manifests"
    with pushd(source_root):
        raw_train = train_model_1.load_window_dataset(manifest_root / f"{spec.train_split}.csv")
        raw_test = train_model_1.load_window_dataset(manifest_root / f"{spec.test_split}.csv")
    train_data = train_model_1.filter_task_dataset(raw_train, spec.task, spec.modality)
    test_data = train_model_1.filter_task_dataset(raw_test, spec.task, spec.modality)

    if spec.modality == "voice_ppg":
        train_model_1.standardize_ppg_features(train_data, test_data)

    class_names = list(train_model_1.TASKS[spec.task]["class_names"])
    train_inputs = train_model_1.inputs_for_prediction(train_data, spec.modality)
    test_inputs = train_model_1.inputs_for_prediction(test_data, spec.modality)
    train_labels = np.asarray(train_data["label"], dtype=np.int64)
    test_labels = np.asarray(test_data["label"], dtype=np.int64)

    train_inputs, train_labels = maybe_subset(train_inputs, train_labels, train_subset)
    test_inputs, test_labels = maybe_subset(test_inputs, test_labels, test_subset)

    train_dataset = tf.data.Dataset.from_tensor_slices((train_inputs, train_labels)).batch(spec.finetune_batch_size).prefetch(tf.data.AUTOTUNE)
    test_dataset = tf.data.Dataset.from_tensor_slices((test_inputs, test_labels)).batch(spec.finetune_batch_size).prefetch(tf.data.AUTOTUNE)

    representative_count = min(128, len(train_labels))
    representative_inputs = {key: value[:representative_count] for key, value in train_inputs.items()}

    return DatasetBundle(
        train_inputs=train_inputs,
        train_labels=train_labels,
        test_inputs=test_inputs,
        test_labels=test_labels,
        class_names=class_names,
        train_dataset=train_dataset,
        test_dataset=test_dataset,
        representative_inputs=representative_inputs,
        extra={
            "ppg_feature_dim": int(train_inputs.get("ppg_features", np.zeros((1, 16))).shape[-1]),
            "ppg_signal_shape": tuple(train_inputs.get("ppg_signal", np.zeros((1, 256, 1))).shape[1:]),
            "mel_shape": tuple(train_inputs["mel"].shape[1:]),
        },
    )


def load_face_bundle(
    spec: ModelSpec,
    source_root: Path,
    tf,
    data_2_tf,
    train_subset: int | None,
    test_subset: int | None,
) -> DatasetBundle:
    data_root = source_root / "data" / "model_2" / "face_emotion_yolo"
    train_samples = data_2_tf.collect_samples(data_root / spec.train_split)
    test_samples = data_2_tf.collect_samples(data_root / spec.test_split)
    if train_subset is not None:
        train_samples = train_samples[:train_subset]
    if test_subset is not None:
        test_samples = test_samples[:test_subset]

    def load_images(samples: list[tuple[str, int]]) -> tuple[np.ndarray, np.ndarray]:
        images = []
        labels = []
        for image_path, label in samples:
            image_bytes = tf.io.read_file(image_path)
            image = tf.image.decode_image(image_bytes, channels=3, expand_animations=False)
            image = tf.image.resize(image, [96, 96], antialias=True)
            images.append(tf.cast(tf.round(image), tf.uint8).numpy())
            labels.append(label)
        return np.stack(images).astype(np.uint8), np.asarray(labels, dtype=np.int64)

    train_images, train_labels = load_images(train_samples)
    test_images, test_labels = load_images(test_samples)
    train_inputs = {"image": train_images}
    test_inputs = {"image": test_images}
    train_dataset = (
        tf.data.Dataset.from_tensor_slices((train_images, train_labels))
        .batch(spec.finetune_batch_size)
        .map(lambda images, labels: (tf.cast(images, tf.float32), labels), num_parallel_calls=tf.data.AUTOTUNE)
        .prefetch(1)
    )
    test_dataset = (
        tf.data.Dataset.from_tensor_slices((test_images, test_labels))
        .batch(spec.finetune_batch_size)
        .map(lambda images, labels: (tf.cast(images, tf.float32), labels), num_parallel_calls=tf.data.AUTOTUNE)
        .prefetch(1)
    )
    representative_inputs = {"image": train_images[: min(128, len(train_labels))]}

    return DatasetBundle(
        train_inputs=train_inputs,
        train_labels=train_labels,
        test_inputs=test_inputs,
        test_labels=test_labels,
        class_names=list(data_2_tf.CLASS_NAMES),
        train_dataset=train_dataset,
        test_dataset=test_dataset,
        representative_inputs=representative_inputs,
        extra={"input_shape": tuple(train_images.shape[1:])},
    )


def load_dataset_bundle(
    spec: ModelSpec,
    source_root: Path,
    tf,
    train_model_1,
    data_2_tf,
    train_subset: int | None,
    test_subset: int | None,
) -> DatasetBundle:
    if spec.is_face_model:
        return load_face_bundle(spec, source_root, tf, data_2_tf, train_subset, test_subset)
    return load_model1_bundle(spec, source_root, tf, train_model_1, train_subset, test_subset)


def load_standalone_model(keras, checkpoint_path: Path):
    return keras.models.load_model(checkpoint_path, compile=False)


def keras_inputs_from_bundle(spec: ModelSpec, inputs: dict[str, np.ndarray]) -> dict[str, np.ndarray] | np.ndarray:
    prepared = {}
    for key, value in inputs.items():
        prepared[key] = value.astype(np.float32, copy=False) if key == "image" else value
    if spec.is_face_model:
        return prepared["image"]
    return prepared


def build_representative_dataset(tf, model, representative_inputs: dict[str, np.ndarray]):
    input_names = [tensor.name.split(":")[0] for tensor in model.inputs]
    multi_input = len(input_names) > 1

    def generator():
        count = len(next(iter(representative_inputs.values())))
        for index in range(count):
            sample_list = []
            sample_dict = {}
            for name in input_names:
                key = "image" if "image" in name else "mel" if "mel" in name else "ppg_features" if "ppg_features" in name else "ppg_signal"
                tensor = tf.convert_to_tensor(representative_inputs[key][index:index + 1], dtype=tf.float32)
                sample_list.append(tensor)
                sample_dict[name] = tensor
            yield sample_dict if multi_input else sample_list

    return generator


def collect_prunable_weights(model) -> list[Any]:
    prunable = []
    for weight in model.trainable_weights:
        weight_name = weight.name.lower()
        if any(token in weight_name for token in ("kernel", "depthwise_kernel", "pointwise_kernel")):
            prunable.append(weight)
    return prunable


def build_masks(prunable_weights: list[Any], target_sparsity: float) -> list[np.ndarray]:
    flat_weights = np.concatenate([np.abs(weight.numpy()).reshape(-1) for weight in prunable_weights])
    threshold = np.quantile(flat_weights, target_sparsity)
    masks = []
    for weight in prunable_weights:
        mask = (np.abs(weight.numpy()) > threshold).astype(np.float32)
        if mask.sum() == 0:
            mask = np.ones_like(mask, dtype=np.float32)
        masks.append(mask)
    return masks


def actual_sparsity(masked_weights: list[tuple[Any, np.ndarray]]) -> float:
    total = 0
    zeros = 0
    for _, mask in masked_weights:
        total += mask.size
        zeros += int(mask.size - mask.sum())
    return float(zeros / total) if total else 0.0


def compile_model_for_finetune(spec: ModelSpec, model, bundle: DatasetBundle, keras, tf, train_model_1, model_2_tf) -> None:
    if spec.is_face_model:
        model.compile(
            optimizer=keras.optimizers.AdamW(learning_rate=spec.learning_rate, weight_decay=spec.weight_decay),
            loss=model_2_tf.SparseCategoricalCrossentropyWithSmoothing(
                num_classes=len(bundle.class_names),
                label_smoothing=0.05,
            ),
            metrics=model_2_tf.build_accuracy_metrics("top1"),
        )
        return

    class_weights = train_model_1.compute_class_weights(bundle.train_labels, len(bundle.class_names))
    metrics = [
        tf.keras.metrics.SparseCategoricalAccuracy(name="accuracy"),
        train_model_1.SparseMacroAccuracy(num_classes=len(bundle.class_names)),
    ]
    if spec.task == "emotion":
        metrics.append(tf.keras.metrics.SparseTopKCategoricalAccuracy(k=2, name="top2_accuracy"))
    model.compile(
        optimizer=keras.optimizers.AdamW(learning_rate=spec.learning_rate, weight_decay=spec.weight_decay),
        loss=train_model_1.WeightedSparseCategoricalCrossentropy(class_weights.astype(float).tolist()),
        metrics=metrics,
    )


def prune_and_finetune(
    spec: ModelSpec,
    model,
    bundle: DatasetBundle,
    keras,
    tf,
    train_model_1,
    model_2_tf,
    args: argparse.Namespace,
    skip_train: bool,
):
    compile_model_for_finetune(spec, model, bundle, keras, tf, train_model_1, model_2_tf)
    if args.disable_pruning:
        if not skip_train and spec.finetune_epochs > 0:
            model.fit(
                bundle.train_dataset,
                epochs=spec.finetune_epochs,
                verbose=2,
            )
        return {
            "target_sparsity": 0.0,
            "actual_sparsity": 0.0,
            "prunable_tensors": 0,
        }
    prunable_weights = collect_prunable_weights(model)
    masks = build_masks(prunable_weights, spec.target_sparsity)
    masked_weights = list(zip(prunable_weights, masks))
    callback = FixedPruningMaskCallback(masked_weights)
    callback._apply_masks()

    if not skip_train:
        model.fit(
            bundle.train_dataset,
            epochs=spec.finetune_epochs,
            verbose=2,
            callbacks=[callback],
        )
    callback._apply_masks()
    return {
        "target_sparsity": spec.target_sparsity,
        "actual_sparsity": actual_sparsity(masked_weights),
        "prunable_tensors": len(masked_weights),
    }


def export_quantized_float_io(tf, model, representative_dataset_fn, output_path: Path, quantization_mode: str) -> None:
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    if quantization_mode == "int8_sparse":
        converter.optimizations = [tf.lite.Optimize.DEFAULT, tf.lite.Optimize.EXPERIMENTAL_SPARSITY]
        converter.representative_dataset = representative_dataset_fn()
        converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    elif quantization_mode == "int8":
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.representative_dataset = representative_dataset_fn()
        converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    else:
        converter.optimizations = [tf.lite.Optimize.DEFAULT, tf.lite.Optimize.EXPERIMENTAL_SPARSITY]
        converter.target_spec.supported_types = [tf.float16]
    tflite_model = converter.convert()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(tflite_model)


def tensor_key_from_name(name: str) -> str:
    lowered = name.lower()
    if "image" in lowered:
        return "image"
    if "ppg_features" in lowered:
        return "ppg_features"
    if "ppg_signal" in lowered:
        return "ppg_signal"
    return "mel"


def create_tflite_interpreter(tf, model_path: Path):
    return tf.lite.Interpreter(
        model_path=str(model_path),
        num_threads=1,
        experimental_op_resolver_type=tf.lite.experimental.OpResolverType.BUILTIN_WITHOUT_DEFAULT_DELEGATES,
    )


def run_tflite_predictions(tf, model_path: Path, inputs: dict[str, np.ndarray], batch_size: int = 32) -> np.ndarray:
    interpreter = create_tflite_interpreter(tf, model_path)
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    sample_count = len(next(iter(inputs.values())))
    outputs: list[np.ndarray] = []

    for start in range(0, sample_count, batch_size):
        stop = min(start + batch_size, sample_count)
        current_batch = stop - start
        for detail in input_details:
            shape = detail["shape"].copy()
            shape[0] = current_batch
            interpreter.resize_tensor_input(detail["index"], shape, strict=False)
        interpreter.allocate_tensors()

        for detail in input_details:
            key = tensor_key_from_name(detail["name"])
            batch = inputs[key][start:stop]
            interpreter.set_tensor(detail["index"], batch.astype(detail["dtype"], copy=False))
        interpreter.invoke()
        outputs.append(interpreter.get_tensor(output_details[0]["index"]))

    return np.concatenate(outputs, axis=0)


def run_keras_predictions(model, spec: ModelSpec, inputs: dict[str, np.ndarray], batch_size: int = 32) -> np.ndarray:
    predictions = model.predict(keras_inputs_from_bundle(spec, inputs), batch_size=batch_size, verbose=0)
    return np.asarray(predictions)


def evaluate_predictions(probabilities: np.ndarray, labels: np.ndarray, class_names: list[str]) -> dict[str, Any]:
    predicted = probabilities.argmax(axis=1)
    accuracy = float((predicted == labels).mean()) if len(labels) else 0.0
    result = {"accuracy": accuracy}
    if len(class_names) == 8:
        top2 = np.argsort(probabilities, axis=1)[:, -2:]
        top2_accuracy = float(np.any(top2 == labels[:, None], axis=1).mean()) if len(labels) else 0.0
        result["top2_accuracy"] = top2_accuracy
    return result


def bytes_to_mb(value: int) -> float:
    return round(value / (1024 * 1024), 3)


def measure_peak_memory_tflite_worker(model_path: str, sample_inputs: dict[str, np.ndarray], queue) -> None:
    import tensorflow as tf

    process = psutil.Process(os.getpid())

    def rss_mb() -> float:
        return process.memory_info().rss / (1024 * 1024)

    baseline = rss_mb()
    interpreter = tf.lite.Interpreter(
        model_path=model_path,
        num_threads=1,
        experimental_op_resolver_type=tf.lite.experimental.OpResolverType.BUILTIN_WITHOUT_DEFAULT_DELEGATES,
    )
    input_details = interpreter.get_input_details()
    peak = rss_mb()
    for detail in input_details:
        shape = detail["shape"].copy()
        shape[0] = 1
        interpreter.resize_tensor_input(detail["index"], shape, strict=False)
    interpreter.allocate_tensors()
    peak = max(peak, rss_mb())
    for _ in range(20):
        for detail in input_details:
            key = tensor_key_from_name(detail["name"])
            interpreter.set_tensor(detail["index"], sample_inputs[key].astype(detail["dtype"], copy=False))
        interpreter.invoke()
        peak = max(peak, rss_mb())
    queue.put(
        {
            "baseline_rss_mb": round(baseline, 3),
            "peak_rss_mb": round(peak, 3),
            "peak_delta_mb": round(peak - baseline, 3),
        }
    )


def measure_peak_memory_tflite(model_path: Path, sample_inputs: dict[str, np.ndarray]) -> dict[str, float]:
    ctx = mp.get_context("spawn")
    queue = ctx.Queue()
    process = ctx.Process(target=measure_peak_memory_tflite_worker, args=(str(model_path), sample_inputs, queue))
    process.start()
    process.join()
    if process.exitcode != 0:
        raise RuntimeError(f"Memory benchmark failed for {model_path}")
    return queue.get()


def measure_peak_memory_keras_worker(source_root: str, checkpoint_path: str, is_face_model: bool, sample_inputs: dict[str, np.ndarray], queue) -> None:
    sys.path.insert(0, source_root)
    import keras

    process = psutil.Process(os.getpid())

    def rss_mb() -> float:
        return process.memory_info().rss / (1024 * 1024)

    baseline = rss_mb()
    model = keras.models.load_model(checkpoint_path, compile=False)
    peak = max(baseline, rss_mb())
    inputs = {"image": sample_inputs["image"].astype(np.float32, copy=False)} if is_face_model else sample_inputs
    keras_inputs = inputs["image"] if is_face_model else inputs
    for _ in range(20):
        _ = model.predict(keras_inputs, batch_size=1, verbose=0)
        peak = max(peak, rss_mb())
    queue.put(
        {
            "baseline_rss_mb": round(baseline, 3),
            "peak_rss_mb": round(peak, 3),
            "peak_delta_mb": round(peak - baseline, 3),
        }
    )


def measure_peak_memory_keras(source_root: Path, checkpoint_path: Path, is_face_model: bool, sample_inputs: dict[str, np.ndarray]) -> dict[str, float]:
    ctx = mp.get_context("spawn")
    queue = ctx.Queue()
    process = ctx.Process(
        target=measure_peak_memory_keras_worker,
        args=(str(source_root), str(checkpoint_path), is_face_model, sample_inputs, queue),
    )
    process.start()
    process.join()
    if process.exitcode != 0:
        raise RuntimeError(f"Keras memory benchmark failed for {checkpoint_path}")
    return queue.get()


def measure_latency_tflite(tf, model_path: Path, sample_inputs: dict[str, np.ndarray], runs: int = 60, warmup: int = 10) -> dict[str, float]:
    interpreter = create_tflite_interpreter(tf, model_path)
    input_details = interpreter.get_input_details()
    for detail in input_details:
        shape = detail["shape"].copy()
        shape[0] = 1
        interpreter.resize_tensor_input(detail["index"], shape, strict=False)
    interpreter.allocate_tensors()
    total_runs = runs + warmup
    latencies_ms: list[float] = []
    for index in range(total_runs):
        for detail in input_details:
            key = tensor_key_from_name(detail["name"])
            interpreter.set_tensor(detail["index"], sample_inputs[key].astype(detail["dtype"], copy=False))
        start = time.perf_counter()
        interpreter.invoke()
        elapsed_ms = (time.perf_counter() - start) * 1000.0
        if index >= warmup:
            latencies_ms.append(elapsed_ms)
    return {
        "mean_ms": round(float(np.mean(latencies_ms)), 3),
        "p95_ms": round(float(np.percentile(latencies_ms, 95)), 3),
        "runs": runs,
    }


def measure_latency_keras(model, spec: ModelSpec, sample_inputs: dict[str, np.ndarray], runs: int = 60, warmup: int = 10) -> dict[str, float]:
    keras_inputs = keras_inputs_from_bundle(spec, sample_inputs)
    total_runs = runs + warmup
    latencies_ms: list[float] = []
    for index in range(total_runs):
        start = time.perf_counter()
        _ = model.predict(keras_inputs, batch_size=1, verbose=0)
        elapsed_ms = (time.perf_counter() - start) * 1000.0
        if index >= warmup:
            latencies_ms.append(elapsed_ms)
    return {
        "mean_ms": round(float(np.mean(latencies_ms)), 3),
        "p95_ms": round(float(np.percentile(latencies_ms, 95)), 3),
        "runs": runs,
    }


def optimize_one_model(
    spec: ModelSpec,
    args: argparse.Namespace,
    source_root: Path,
    output_root: Path,
    keras,
    tf,
    train_model_1,
    data_2_tf,
    model_2_tf,
) -> dict[str, Any]:
    spec = apply_spec_overrides(spec, args)
    print(f"\n=== Optimizing {spec.key} ===")
    bundle = load_dataset_bundle(
        spec,
        source_root,
        tf,
        train_model_1,
        data_2_tf,
        args.train_subset,
        args.test_subset,
    )
    baseline_model = load_standalone_model(keras, spec.checkpoint_path)
    baseline_train = evaluate_predictions(
        run_keras_predictions(baseline_model, spec, bundle.train_inputs, batch_size=spec.finetune_batch_size),
        bundle.train_labels,
        bundle.class_names,
    )
    baseline_test = evaluate_predictions(
        run_keras_predictions(baseline_model, spec, bundle.test_inputs, batch_size=spec.finetune_batch_size),
        bundle.test_labels,
        bundle.class_names,
    )

    model = load_standalone_model(keras, spec.checkpoint_path)
    pruning_summary = prune_and_finetune(
        spec,
        model,
        bundle,
        keras,
        tf,
        train_model_1,
        model_2_tf,
        args=args,
        skip_train=args.skip_train,
    )
    optimized_path = output_root / "optimized_tflite" / spec.optimized_tflite_name
    representative_dataset_fn = lambda: build_representative_dataset(tf, model, bundle.representative_inputs)
    export_quantized_float_io(tf, model, representative_dataset_fn, optimized_path, args.quantization_mode)

    optimized_train = evaluate_predictions(
        run_tflite_predictions(tf, optimized_path, bundle.train_inputs, batch_size=spec.finetune_batch_size),
        bundle.train_labels,
        bundle.class_names,
    )
    optimized_test = evaluate_predictions(
        run_tflite_predictions(tf, optimized_path, bundle.test_inputs, batch_size=spec.finetune_batch_size),
        bundle.test_labels,
        bundle.class_names,
    )

    sample_inputs = {key: value[:1] for key, value in bundle.representative_inputs.items()}
    baseline_memory = measure_peak_memory_keras(source_root, spec.checkpoint_path, spec.is_face_model, sample_inputs)
    optimized_memory = measure_peak_memory_tflite(optimized_path, sample_inputs)
    baseline_latency = measure_latency_keras(baseline_model, spec, sample_inputs)
    optimized_latency = measure_latency_tflite(tf, optimized_path, sample_inputs)

    result = {
        "artifact_name": spec.artifact_name,
        "optimized_tflite_path": str(optimized_path),
        "baseline_checkpoint_path": str(spec.checkpoint_path),
        "baseline_float16_tflite_path": str(spec.baseline_tflite_path),
        "checkpoint_path": str(spec.checkpoint_path),
        "class_names": bundle.class_names,
        "pruning": pruning_summary,
        "experiment": {
            "disable_pruning": args.disable_pruning,
            "quantization_mode": args.quantization_mode,
            "finetune_epochs": spec.finetune_epochs,
            "learning_rate": spec.learning_rate,
        },
        "sizes": {
            "baseline_bytes": spec.checkpoint_path.stat().st_size,
            "baseline_mb": bytes_to_mb(spec.checkpoint_path.stat().st_size),
            "optimized_bytes": optimized_path.stat().st_size,
            "optimized_mb": bytes_to_mb(optimized_path.stat().st_size),
            "reduction_percent": round(
                (1.0 - (optimized_path.stat().st_size / spec.checkpoint_path.stat().st_size)) * 100.0,
                2,
            ),
        },
        "memory": {
            "baseline": baseline_memory,
            "optimized": optimized_memory,
        },
        "latency": {
            "baseline": baseline_latency,
            "optimized": optimized_latency,
        },
        "accuracy": {
            "train": {
                "baseline": baseline_train,
                "optimized": optimized_train,
            },
            "test": {
                "baseline": baseline_test,
                "optimized": optimized_test,
            },
        },
    }
    return result


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def render_markdown(results: dict[str, Any]) -> str:
    lines = [
        "# Model Optimization Report",
        "",
        f"- Generated at: `{results['generated_at']}`",
        "- Baseline for comparison: original pre-TFLite `.keras` checkpoint, not the float16 mobile export.",
        "- Method: one-shot global magnitude pruning with masked fine-tuning, then post-training quantization and sparse TFLite export when requested.",
        "- Peak memory metric: host-side peak RSS delta while loading the model and running repeated batch-1 inferences in a fresh subprocess.",
        "- Latency metric: batch-1 host-side mean / p95 inference time with warmup removed.",
        "",
        "## Summary",
        "",
        "| Model | Size Before (MB) | Size After (MB) | Size Delta | Peak RSS Before (MB) | Peak RSS After (MB) | Latency Before (ms) | Latency After (ms) | Train Acc Before | Train Acc After | Test Acc Before | Test Acc After |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for item in results["models"]:
        lines.append(
            "| {name} | {b_mb:.3f} | {a_mb:.3f} | {delta:.2f}% | {mem_b:.3f} | {mem_a:.3f} | {lat_b:.3f} | {lat_a:.3f} | {train_b:.4f} | {train_a:.4f} | {test_b:.4f} | {test_a:.4f} |".format(
                name=item["artifact_name"],
                b_mb=item["sizes"]["baseline_mb"],
                a_mb=item["sizes"]["optimized_mb"],
                delta=item["sizes"]["reduction_percent"],
                mem_b=item["memory"]["baseline"]["peak_delta_mb"],
                mem_a=item["memory"]["optimized"]["peak_delta_mb"],
                lat_b=item["latency"]["baseline"]["mean_ms"],
                lat_a=item["latency"]["optimized"]["mean_ms"],
                train_b=item["accuracy"]["train"]["baseline"]["accuracy"],
                train_a=item["accuracy"]["train"]["optimized"]["accuracy"],
                test_b=item["accuracy"]["test"]["baseline"]["accuracy"],
                test_a=item["accuracy"]["test"]["optimized"]["accuracy"],
            )
        )
    lines += [
        "",
        "## Detailed Notes",
        "",
        "- Emotion models also report top-2 accuracy in the JSON result file.",
        "- Sparse export uses float32 inputs/outputs for Android compatibility, so the app input code does not need quant/dequant branches.",
        "- Keras baseline metrics use the original saved checkpoint directly, so they reflect pre-TFLite behavior.",
        "- TFLite latency/memory benchmarking disables default delegates and uses `num_threads=1` to stay closer to the current Android app configuration.",
        "- Because the original checkpoints are stored in Keras 3 format, pruning was applied with a custom masked fine-tuning pass instead of `tfmot.prune_low_magnitude` directly on the loaded model objects.",
        "",
        "## Artifact Files",
        "",
    ]
    for item in results["models"]:
        lines.append(f"- `{item['artifact_name']}` -> `{item['optimized_tflite_path']}`")
    return "\n".join(lines) + "\n"


def main() -> None:
    args = parse_args()
    source_root = args.source_root.resolve()
    output_root = args.output_root.resolve() / time.strftime("%Y%m%d_%H%M%S")
    output_root.mkdir(parents=True, exist_ok=True)

    keras, tf, train_model_1, train_model_2, data_2_tf, model_2_tf = import_training_modules(source_root)
    specs = model_specs(source_root)
    if args.models:
        selected = set(args.models)
        specs = [spec for spec in specs if spec.key in selected]
        if not specs:
            raise ValueError(f"No matching model keys for --models={args.models!r}")
    results = {
        "generated_at": time.strftime("%Y-%m-%d %H:%M:%S"),
        "source_root": str(source_root),
        "project_root": str(PROJECT_ROOT),
        "models": [],
    }

    for spec in specs:
        result = optimize_one_model(
            spec,
            args,
            source_root,
            output_root,
            keras,
            tf,
            train_model_1,
            data_2_tf,
            model_2_tf,
        )
        results["models"].append(result)
        keras.backend.clear_session()

    write_json(output_root / "benchmark_results.json", results)
    (output_root / "MODEL_OPTIMIZATION_REPORT.md").write_text(render_markdown(results), encoding="utf-8")

    if args.copy_to_assets:
        assets_dir = PROJECT_ROOT / "app" / "src" / "main" / "assets"
        assets_dir.mkdir(parents=True, exist_ok=True)
        for item in results["models"]:
            source = Path(item["optimized_tflite_path"])
            destination = assets_dir / source.name
            shutil.copy2(source, destination)
            print(f"Copied {source.name} -> {destination}")

    print(f"\nWrote optimization artifacts to {output_root}")


if __name__ == "__main__":
    main()
