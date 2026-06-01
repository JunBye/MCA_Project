# Model Optimization

Updated: `2026-06-01`

This app's TensorFlow Lite assets were benchmarked with the same workflow for every model:

1. Load the original Keras checkpoint used to export the existing mobile model.
2. Apply one-shot global magnitude pruning.
3. Fine-tune with fixed pruning masks so zeroed weights stay zero.
4. Export a TensorFlow Lite model with post-training int8 quantization and TFLite experimental sparsity.
5. Measure:
   - model size before and after
   - host-side peak RSS delta while allocating the interpreter and running repeated inferences
   - train-set accuracy
   - test-set accuracy

Deployment criterion:

- Promotion decisions were made from `test-set accuracy`, not train accuracy.
- Train accuracy is included as a supporting overfit/regression signal.
- Concretely, an optimized model was promoted only when its absolute `test accuracy` drop stayed within about `0.03` and the app contract stayed unchanged.

Notes:

- Inputs and outputs stay `float32`, so the Android app does not need extra quantize/dequantize branches.
- Peak memory here is a host benchmark, not an on-device Android profiler number. It is still useful for relative comparison.
- Emotion models also report top-2 accuracy because the UI surfaces top-2 labels.
- Sensitive voice-only emotion used the baseline float16 export from `/home/choi/mca/project/outputs_sensitive/mobile_tflite/` because that model was not yet packaged in `app/src/main/assets`.

## Results

| Model | Deployed? | Size Before (MB) | Size After (MB) | Size Reduction | Peak RSS Before (MB) | Peak RSS After (MB) | Train Acc Before | Train Acc After | Test Acc Before | Test Acc After |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `model_1_emotion` | No | 2.372 | 1.313 | 44.66% | 16.879 | 11.398 | 0.3371 | 0.1563 | 0.3333 | 0.1395 |
| `model_1_veracity` | Yes | 2.371 | 1.458 | 38.50% | 17.109 | 11.250 | 0.5596 | 0.5367 | 0.4865 | 0.4685 |
| `model_1_voice_only_emotion_sensitive` | Yes | 1.940 | 1.205 | 37.90% | 24.609 | 13.508 | 0.1317 | 0.1272 | 0.1473 | 0.1240 |
| `model_1_voice_only_veracity` | Yes | 1.846 | 1.095 | 40.68% | 15.625 | 9.758 | 0.6862 | 0.7160 | 0.4955 | 0.4955 |
| `model_2_face_emotion` | No | 1.458 | 1.316 | 9.70% | 9.473 | 8.242 | 0.7755 | 0.4535 | 0.6784 | 0.4022 |

## Emotion Top-2

| Model | Train Top-2 Before | Train Top-2 After | Test Top-2 Before | Test Top-2 After |
| --- | ---: | ---: | ---: | ---: |
| `model_1_emotion` | 0.5871 | 0.2946 | 0.5736 | 0.2558 |
| `model_1_voice_only_emotion_sensitive` | 0.2600 | 0.2489 | 0.2636 | 0.2481 |
| `model_2_face_emotion` | 0.9269 | 0.6849 | 0.8610 | 0.6352 |

## Deployment Decision

Promoted into the app:

- `model_1_veracity_int8_sparse.tflite`
- `model_1_voice_only_emotion_sensitive_int8_sparse.tflite`
- `model_1_voice_only_veracity_int8_sparse.tflite`

Retained the original float16 baseline in the app:

- `model_1_emotion_float16.tflite`
- `model_2_face_emotion_float16.tflite`

Why:

- `test accuracy` was the main gate for deployment.
- `model_1_veracity` kept a similar accuracy profile while cutting both size and peak memory.
- `model_1_voice_only_emotion_sensitive` lost some accuracy, but the drop stayed modest relative to the large size and memory savings.
- `model_1_voice_only_veracity` kept test accuracy flat and improved train accuracy while shrinking substantially.
- `model_1_emotion` and `model_2_face_emotion` both regressed too sharply to safely promote.

## Accuracy Basis

The numbers below are the ones used for the deployment call:

- `model_1_emotion`: test `0.3333 -> 0.1395`, train `0.3371 -> 0.1563`
- `model_1_veracity`: test `0.4865 -> 0.4685`, train `0.5596 -> 0.5367`
- `model_1_voice_only_emotion_sensitive`: test `0.1473 -> 0.1240`, train `0.1317 -> 0.1272`
- `model_1_voice_only_veracity`: test `0.4955 -> 0.4955`, train `0.6862 -> 0.7160`
- `model_2_face_emotion`: test `0.6784 -> 0.4022`, train `0.7755 -> 0.4535`

## Packaged Asset Delta

Reference baseline set:

- `model_1_emotion_float16.tflite`
- `model_1_veracity_float16.tflite`
- `model_1_voice_only_emotion_sensitive_float16.tflite`
- `model_1_voice_only_veracity_float16.tflite`
- `model_2_face_emotion_float16.tflite`

Currently deployed packaged set:

- `model_1_emotion_float16.tflite`
- `model_1_veracity_int8_sparse.tflite`
- `model_1_voice_only_emotion_sensitive_int8_sparse.tflite`
- `model_1_voice_only_veracity_int8_sparse.tflite`
- `model_2_face_emotion_float16.tflite`

Total packaged model size moved from `9.987 MB` to `7.588 MB`, which is a `24.02%` reduction without promoting the two high-regression models.

## Reproduction

Optimization and benchmarking were run with:

```bash
conda run -n base python tools/optimize_app_models.py --models voice_ppg_emotion
conda run -n base python tools/optimize_app_models.py --models voice_ppg_veracity
conda run -n base python tools/optimize_app_models.py --models voice_only_emotion_sensitive
conda run -n base python tools/optimize_app_models.py --models voice_only_veracity
conda run -n base python tools/optimize_app_models.py --models face_emotion
```

Per-run benchmark JSON and the non-promoted optimized binaries are stored under `artifacts/model_optimization/`.

## Follow-up Retries On Held Models

I ran one extra round on the two rejected models without touching the currently deployed app assets.

### `model_1_emotion`

`int8` quantization only:

- Artifact: `artifacts/model_optimization/20260601_215615/optimized_tflite/model_1_emotion_int8_sparse_qonly_rerun.tflite`
- Size: `2.372 MB -> 1.256 MB`
- Test accuracy: `0.3333 -> 0.1395`
- Train accuracy: `0.3371 -> 0.1618`

Conservative prune + `float16 sparse`:

- Artifact: `artifacts/model_optimization/20260601_215753/optimized_tflite/model_1_emotion_int8_sparse_alt_sparse15_f16.tflite`
- Settings: sparsity `0.15`, epochs `6`, learning rate `5e-5`
- Size: `2.372 MB -> 2.374 MB`
- Test accuracy: `0.3333 -> 0.1395`
- Train accuracy: `0.3371 -> 0.1563`

Conclusion:

- `int8` is where most of the size win comes from, but it destroys accuracy for this model.
- backing off to `float16 sparse` preserves the file format but gives effectively no size win and still does not recover accuracy.

### `model_2_face_emotion`

`int8` quantization only:

- Artifact: `artifacts/model_optimization/20260601_215358/optimized_tflite/model_2_face_emotion_int8_sparse_qonly.tflite`
- Size: `1.458 MB -> 0.958 MB`
- Test accuracy: `0.6784 -> 0.3822`
- Train accuracy: `0.7755 -> 0.3888`

Very weak prune + `float16 sparse`:

- Artifact: `artifacts/model_optimization/20260601_221054/optimized_tflite/model_2_face_emotion_int8_sparse_alt_sparse02_f16.tflite`
- Settings: sparsity `0.02`, epochs `2`, learning rate `1e-5`
- Size: `1.458 MB -> 1.458 MB`
- Test accuracy: `0.6784 -> 0.4334`
- Train accuracy: `0.7755 -> 0.4632`

Conclusion:

- just like the multimodal emotion model, `int8` gives the size win but costs too much accuracy.
- `float16 sparse` keeps accuracy a bit higher than `int8`, but the model does not become meaningfully smaller.
- for now the original `model_2_face_emotion_float16.tflite` remains the right deployment choice.
