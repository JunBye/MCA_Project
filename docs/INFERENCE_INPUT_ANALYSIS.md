# 추론 Input 분석 — 학습 전처리 vs 앱 전처리

> 작성: 2026-06-01
> 목적: 모델이 정확히 판단하려면 앱의 추론 입력이 **학습 시점 전처리와 동일**해야 한다.
> 현재 fake로만 쏠리거나 부정확한 결과의 상당 부분은 **전처리 불일치**에서 온다.

참조 코드:
- 학습 mel 생성: `~/Coding/26_1_MCA/ppg_data/make_mel_spectrograms.py`
- 학습 윈도우 빌드: `~/Coding/26_1_MCA/model_1_no_raw_2026-05-30/data/model_1/build_windows.py`
- 학습 스크립트: `~/Coding/26_1_MCA/model_1_no_raw_2026-05-30/train_model_1.py`
- 실험 정리: `model_1_no_raw_2026-05-30/docs/EXPERIMENTS.md`
- 앱 mel: `app/.../audio/AudioFeatureExtractor.kt`
- 앱 추론: `app/.../ml/ModelManager.kt`

---

## 1. 모델이 실제로 기대하는 입력 (학습 파이프라인 역추적)

### 1-1. 오디오 → mel (학습)
`make_mel_spectrograms.py` 의 `librosa.feature.melspectrogram` + `power_to_db`:

| 파라미터 | 학습 값 | 비고 |
| --- | --- | --- |
| `sr` | **원본 유지** (`--sr` 기본 None) | voice.wav 원본 SR. 보통 44.1kHz/48kHz |
| `n_fft` | **2048** | |
| `hop_length` | **1024** | |
| `n_mels` | **128** | |
| `fmax` | **8000** | |
| log 변환 | `librosa.power_to_db(mel, ref=np.max)` | **dB 스케일, 범위 [-80, 0]** |

→ 결과 CSV `mel_spectrogram.csv`: shape `(128, T)`, 값 범위 `-80.0 ~ 0.0`.
   (실측: MU3D `(128,826)`, MCA `(128,208)`)

### 1-2. mel → 학습 윈도우 (build_windows.py)
1. 클립 전체 mel(128×T)을 로드
2. `duration_sec` 기준으로 5초(또는 10초) 구간을 시간축에서 슬라이스
3. `resize_time_axis(...)` → 시간축을 **96 프레임**으로 `np.interp` 보간
4. `normalize(...)` → **per-window z-score** `(x - mean) / std` (std<1e-6면 1.0)

→ 최종 모델 입력: `(128, 96, 1)`, **mean≈0, std≈1** (실측 확인: min -1.87, max 3.53, mean 0.0, std 1.0)

### 1-3. 라벨 / 출력 매핑 (반드시 고정)
- **emotion (8-class)**: `["angry","contempt","disgust","fear","happy","neutral","sad","surprise"]` (인덱스 0~7)
- **veracity (2-class)**: `["true","fake"]` → **인덱스 0 = true, 1 = fake**
- 출력은 학습 시 이미 `softmax` 적용된 레이어 (`Dense(activation="softmax")`)

> ⚠️ 앱이 `fakeProbability = veracityScores[1]` 로 쓰는 것은 매핑상 **맞다** (1=fake).
> 단 출력에 softmax가 이미 들어있으므로, 앱에서 한 번 더 softmax를 적용하면 분포가 평탄해진다. (아래 3-3)

---

## 2. 앱이 현재 만드는 입력 (AudioFeatureExtractor.kt)

| 단계 | 앱 현재 값 | 학습 값 | 일치 |
| --- | --- | --- | --- |
| 캡처 SR | **16000 강제** (리샘플) | 원본 유지(보통 44.1k) | ⚠️ |
| 윈도우 길이 | 5초 (`WINDOW_SAMPLES = 16000*5`) | 5초/10초 | △ run에 따라 |
| `n_fft` (FFT_SIZE) | **400** | **2048** | ❌ |
| hop / 프레임 생성 | 96 프레임을 **윈도우 전체에 균등 분포**시켜 STFT (hop 개념 없음) | `hop_length=1024` 고정 stride | ❌ |
| `n_mels` (MEL_BINS) | 128 | 128 | ✅ |
| mel filterbank fmax | sr/2 = 8000 (16k 기준) | 8000 | ✅ (16k일 때) |
| log 변환 | `ln(max(energy, 1e-6))` **자연로그** | `power_to_db(ref=np.max)` **dB(=10·log10), 클립 max 기준** | ❌ |
| 정규화 | per-window z-score + `clip(-4,4)` | per-window z-score (clip 없음) | ✅ 거의 동일 |
| 최종 shape | `(128, 96, 1)` (flatten 12288) | `(128, 96, 1)` | ✅ |

### 핵심 불일치 (영향 큰 순)
1. **log 스케일이 다름**: 학습은 dB(`10*log10`, ref=max → 범위 [-80,0]), 앱은 자연로그(`ln`).
   z-score를 양쪽 다 걸어서 스케일 차이는 어느정도 흡수되지만, **ref=np.max(클립 최댓값 기준)** 이라는 점과 dB 압축 곡선이 달라 **분포 모양 자체가 다르다.** 모델이 본 적 없는 입력 분포가 됨.
2. **n_fft 400 vs 2048**: 주파수 해상도가 5배 차이. 같은 mel bin이라도 **각 bin에 들어가는 에너지 분포가 완전히 다름.** 저주파 해상도/번짐이 달라짐.
3. **hop/프레임 생성 방식 차이**: 학습은 hop_length=1024 고정 stride STFT 후 시간축을 96으로 보간. 앱은 윈도우 전체에 96 프레임을 균등 배치해 각 위치에서 400-pt FFT. → **시간축 표현이 다름.**
4. **SR 16k 강제 리샘플**: 원본이 44.1k면 8k 위 정보가 사라진 채 필터뱅크가 0~8000을 채움. 학습은 원본 SR에서 fmax=8000. (둘 다 fmax 8000이라 영향은 위 1·2보다 작지만, 리샘플 아티팩트는 있음)

---

## 3. 진단: 왜 fake/부정확이 나오는가

### 3-1. 전처리 불일치 (앱 측 — 고칠 수 있음)
위 2번. 모델이 학습 때 본 입력 분포와 앱이 주는 분포가 달라 **OOD(분포 밖) 입력** → 예측이 한쪽으로 쏠리거나 무의미해짐. **앱에서 mel 생성을 학습과 동일하게 맞추면 개선 가능.**

### 3-2. 모델 자체 한계 (학습 측 — EXPERIMENTS.md 결론)
`EXPERIMENTS.md` 가 이미 규명:
- **veracity(거짓말)는 데이터 부족으로 test 0.49~0.58, 사실상 무작위~약간.** 화자/클립 다양성 부족이 본질적 한계.
- 권장 베스트는 `model_1_mca_10s_s25_strong` (veracity true 0.54 / fake 0.56로 **유일하게 균형**).
- → **veracity는 원래 잘 안 맞는 게 정상.** 앱 전처리를 고쳐도 거짓말 정확도가 극적으로 오르진 않는다. emotion(0.34)이 그나마 신호 있음.

### 3-3. 이중 softmax 의심 (앱 측 — 확인 필요)
- 학습 모델 출력층이 이미 `softmax`. 앱 `ModelManager`는 출력에 `softmax()`를 **한 번 더** 적용 (`runVoicePpgVeracity` 등).
- softmax(softmax(x))는 분포를 **더 평탄하게** 만든다 → confidence가 뭉개지고 0.5 근처로 쏠릴 수 있음.
- **확인 필요**: TFLite 변환된 모델의 마지막 op가 softmax인지. 맞다면 앱의 두 번째 softmax 제거.

---

## 4. 권장 조치 (우선순위)

### A. 앱 mel 전처리를 학습과 일치시키기 (가장 효과 큼)
`AudioFeatureExtractor.kt` 를 다음으로 수정:
1. `FFT_SIZE` 400 → **2048**, hop **1024** 고정 stride 방식으로 변경 (프레임 균등분포 폐기)
2. log 변환을 `ln` → **dB 스케일** `10*log10(power)` 후 **ref=프레임 전체 max**, floor `-80` (= `max - 80` 클램프)
3. SR 처리: 가능하면 원본 SR 유지하거나, 최소한 학습이 16k가 아니었음을 인지하고 fmax=8000 유지
4. 시간축: hop=1024로 STFT한 뒤 96 프레임으로 보간(`np.interp` 동등 로직) — build_windows와 동일하게

> 목표: 앱 mel의 최종 분포가 학습 npz처럼 **per-window z-score (mean0/std1)** 이고, 그 직전 dB 분포가 [-80,0]이 되도록.

### B. 이중 softmax 확인 및 제거 (저비용)
- `saved_model_cli` 또는 netron으로 tflite 출력 op 확인.
- 이미 softmax면 `ModelManager`의 `softmax(output[0])` → 그대로 반환으로 변경.

### C. 기대치 조정 (문서/PPT)
- veracity는 데이터 한계로 보조 지표. emotion 위주로 데모/설명.
- 앱 결과를 클립(세션) 단위로 **집계(평균/투표)** 하면 작은 노이즈가 줄어 안정적 (EXPERIMENTS.md 다음 후보와 동일).

### D. PPG (별개 트랙)
- 현재 탑재 voice+PPG 모델(`model_1_emotion/veracity`)은 PPG 입력 3개 중 ppgFeatures/ppgSignal 필요.
- EXPERIMENTS 결론: **PPG는 veracity에 무관(제거해도 동일).** → Blind Date도 voice-only 모델로 가는 게 타당.
- Job Interview는 이미 voice-only + face라 PPG 없음. (구버전 PPG rate 표시 제거됨)

---

## 5. 입력이 추론에 들어가는 런타임 흐름 (참고)

### Blind Date (Voice + PPG)
```
마이크 → RealtimeAudioEngine → 최근 5초 → buildModelMel() → mel[128×96]
후면카메라(손가락) → FingerPpgProcessor.analyze() → ppgSignal[256], ppgFeatures[16], bpm
  ↓ (둘 다 매 프레임 latest* 갱신)
2.5초마다 measuringJob:
  inferVoicePpg(VoicePpgInput(mel, ppgFeatures, ppgSignal, bpmHint))
    → model_1_emotion(softmax) → emotion top2
    → model_1_veracity(softmax) → fakeProbability = scores[1]
```

### Job Interview (Voice-only + Face)
```
마이크 → mel[128×96]
후면카메라(얼굴) → InterviewCameraProcessor → faceImage[96×96×3] (중앙 72% 크롭, 얼굴검출 없음)
  ↓
2.5초마다 measuringJob:
  inferInterview(InterviewInput(mel, faceImage))
    → model_2_face_emotion → 얼굴 감정
    → model_1_voice_only_emotion → 음성 감정
    → model_1_voice_only_veracity → fakeProbability
    → faceVoiceDiscordance = cosine(얼굴감정, 음성감정)
```

> 주의: 문서상 Blind Date는 "발화 단위(VAD)"지만, 실제 코드는 **2.5초 고정 주기 + 롤링 5초 윈도우**(`latestSnapshot`)를 쓴다. `pollCompletedUtterance`(VAD)는 구현돼 있으나 ViewModel이 사용하지 않음. PPT/문서 수정 시 반영 필요.