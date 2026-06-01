# Model Optimization Report

- Generated at: `2026-06-01 19:00:05`
- Method: one-shot global magnitude pruning with masked fine-tuning, then post-training int8 quantization with float32 I/O and TensorFlow Lite experimental sparsity.
- Peak memory metric: host-side peak RSS delta while allocating the interpreter and running 20 repeated inferences in a fresh subprocess.

## Summary

| Model | Size Before (MB) | Size After (MB) | Size Delta | Peak RSS Before (MB) | Peak RSS After (MB) | Train Acc Before | Train Acc After | Test Acc Before | Test Acc After |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| model_1_voice_only_emotion_sensitive | 1.940 | 1.205 | 37.90% | 24.609 | 13.508 | 0.1317 | 0.1272 | 0.1473 | 0.1240 |

## Detailed Notes

- Emotion models also report top-2 accuracy in the JSON result file.
- Sparse export uses float32 inputs/outputs for Android compatibility, so the app input code does not need quant/dequant branches.
- Because the original checkpoints are stored in Keras 3 format, pruning was applied with a custom masked fine-tuning pass instead of `tfmot.prune_low_magnitude` directly on the loaded model objects.

## Artifact Files

- `model_1_voice_only_emotion_sensitive` -> `/mnt/c/Users/SAMSUNG/AndroidStudioProjects/MCA_Project/artifacts/model_optimization/20260601_190002/optimized_tflite/model_1_voice_only_emotion_sensitive_int8_sparse.tflite`
