# Model Optimization Report

- Generated at: `2026-06-01 21:54:04`
- Method: one-shot global magnitude pruning with masked fine-tuning, then post-training int8 quantization with float32 I/O and TensorFlow Lite experimental sparsity.
- Peak memory metric: host-side peak RSS delta while allocating the interpreter and running 20 repeated inferences in a fresh subprocess.

## Summary

| Model | Size Before (MB) | Size After (MB) | Size Delta | Peak RSS Before (MB) | Peak RSS After (MB) | Train Acc Before | Train Acc After | Test Acc Before | Test Acc After |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| model_2_face_emotion | 1.458 | 0.958 | 34.30% | 9.477 | 6.102 | 0.7755 | 0.3888 | 0.6784 | 0.3822 |

## Detailed Notes

- Emotion models also report top-2 accuracy in the JSON result file.
- Sparse export uses float32 inputs/outputs for Android compatibility, so the app input code does not need quant/dequant branches.
- Because the original checkpoints are stored in Keras 3 format, pruning was applied with a custom masked fine-tuning pass instead of `tfmot.prune_low_magnitude` directly on the loaded model objects.

## Artifact Files

- `model_2_face_emotion` -> `/mnt/c/Users/SAMSUNG/AndroidStudioProjects/MCA_Project/artifacts/model_optimization/20260601_215358/optimized_tflite/model_2_face_emotion_int8_sparse_qonly.tflite`
