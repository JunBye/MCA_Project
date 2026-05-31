# 유저 시나리오 & 안드로이드 앱 설계 문서

> 플랫폼: Android (Kotlin, Jetpack Compose)
> 모델(TFLite, on-device):
> - 시나리오 1 (Job Interview): **Face emotion(model_2) + Voice-only emotion + Voice-only veracity**
> - 시나리오 2 (Blind Date): **Voice+PPG emotion + Voice+PPG veracity** (model_1)
>
> _최종 업데이트: 2026-06-01 — TFLite 모델 실제 연동 + mel 전처리 학습 정합 + ML Kit 얼굴검출 반영_

---

## 1. 확정된 설계 결정 사항

| 항목 | 결정 |
| --- | --- |
| **시나리오 1 카메라 소스** | 후면 카메라로 상대 얼굴 촬영 → **ML Kit Face Detection으로 얼굴 박스 검출 → 96×96 크롭** (PPG 미사용) |
| **시나리오 1 모델** | 얼굴 감정(model_2) + 음성 감정/진위(voice-only). **PPG 제거됨** |
| **시나리오 2 입력** | **음성 + PPG 동시** (model_1 Voice+PPG, 세 입력 mel/ppg_features/ppg_signal 필수) |
| **앱 진입 방식** | 상황 선택 버튼 2개 (Blind Date / Job Interview) |
| **추론 구조** | 시작 → 측정 중(5초 슬라이딩 윈도우 / 2.5초 stride) → 결과 → 다시 시작/홈 |
| **mel 전처리** | 학습(`make_mel_spectrograms.py`)과 동일: n_fft=2048, hop=1024, hann, Slaney mel, `power_to_db(ref=max)`, per-window z-score. radix-2 FFT로 실시간화 |
| **얼굴 입력 스케일** | **0~255 float32** (모델 내부 Rescaling(1/255) 보유) |

---

## 2. 유저 시나리오

### 시나리오 1: Job Interview (대면 면접)

**상황**: 대면 1:1 면접 / 미팅 (면접관과 마주 앉은 상황)
**모델**: 얼굴 감정(model_2) + 음성 감정/진위(voice-only). **PPG 미사용**
**추론 방식**: 5초 슬라이딩 윈도우, 2.5초마다 추론
**카메라 입력**: 후면 카메라로 상대 얼굴 촬영 → **ML Kit Face Detection으로 얼굴 bounding box 검출 → 그 영역만 96×96 크롭**. 음성은 폰 마이크로 함께 캡처(mel)

**유저 플로우**:
1. 앱 실행 → 홈에서 "Job Interview" 선택
2. 카메라 + 마이크 권한 요청
3. "후면 카메라로 상대 얼굴이 보이도록 폰을 두세요" 안내
4. 시작 → 카메라 프리뷰(+얼굴 박스 오버레이) + 실시간 게이지 (2.5초마다 추론)
5. 종료 → 전체 타임라인 리포트(heatmap)

**출력**: 얼굴 감정 + 음성 감정(둘 다 표시) + 거짓 확률 + **Face–Voice mismatch**(두 감정 분포의 코사인 거리)

**얼굴 처리 (현재 구현)**:
- `InterviewCameraProcessor`가 ML Kit으로 가장 큰 얼굴을 검출(FAST 모드, landmark/classification off)
- 검출 박스에 18% 여백을 주고 96×96으로 크롭, **0~255 float32**로 모델 입력(모델 내부 Rescaling)
- 검출 박스를 정규화(0~1) 좌표로 UI에 전달 → 프리뷰 위 **사각형 오버레이**
- 얼굴 미검출 시 중앙 크롭 폴백 + tracking confidence 0 ("searching face…")
- 디버깅용 **전면/후면 전환 버튼** (Measuring 화면 우상단)
- ⚠️ TODO: 전면 카메라 미러링 시 박스 x좌표 보정 미적용 / 얼굴 모델 학습 시 정규화·RGB순서 최종 확인

---

### 시나리오 2: Blind Date (대면 단둘 대화)

**상황**: 블라인드 데이트, 연인 간 유도심문, "솔직히 말해봐" 추궁
**모델**: Voice + PPG (model_1, mel + ppg_features(16) + ppg_signal(256))
**추론 방식**: 5초 슬라이딩 윈도우, 2.5초마다 추론 (학습 윈도우와 동일)
**입력**:
- **음성**: 폰 마이크 → 최근 5초 → mel(128×96)
- **PPG**: 상대 손가락을 후면 카메라+플래시에 올림 → red 채널 신호 → ppg_signal/features + BPM

**유저 플로우**:
1. 앱 실행 → 홈에서 "Blind Date" 선택
2. 마이크 + 카메라 권한 요청
3. "상대에게 손가락을 카메라에 대달라고 요청하세요" 안내
4. PPG 잠금 확인 (BPM 안정화 대기)
5. 본격 측정 시작 → 2.5초마다 결과 카드 누적
6. 종료 → 결과 리스트 + heatmap 리포트

**BPM 산출 (현재 구현, `PPGbetterWithVoice` 방식 이식)**:
- EMA 평활 → 3-point local-max peak(최소 600ms 간격) → 최근 10초 interval **median** → `60000/median`
- 실제 ms timestamp 기반이라 프레임레이트 가정 불필요(안정적)

> ⚠️ **변경 이력**: baseline calibration 화면은 실제 동작이 없어 **제거됨**(Setup→PpgLock→Measuring).
> 발화 단위(VAD) 추론도 검토했으나, 학습이 5초 윈도우/2.5초 stride이므로 **5초 슬라이딩 윈도우로 롤백**.

---

## 3. 화면 구조 (Jetpack Compose + Navigation)

> **단일 Activity(`MainActivity`, `@AndroidEntryPoint`) + Compose `NavHost`** 구조.
> 각 화면은 `@Composable` 함수이고, 화면 상태는 `@HiltViewModel` ViewModel이 `StateFlow`로 보유한다.
> Interview / Blind Date 의 다단계 화면은 **nested navigation graph**로 묶어 같은 ViewModel을 공유한다.
> ML 추론은 **TFLite 모델 5종 실제 연동 완료**. 마이크 mel은 학습과 동일한 전처리(2048/1024/dB/Slaney)로 생성하며, 모델 미로드 시에만 안내 배너를 표시한다.

### 전체 네비게이션 다이어그램

```
Splash  (모델 로드 placeholder)
   ↓
Home  (시나리오 선택: 2개 카드 + 지난 기록)
   ├─▶ interview_graph (nested, InterviewViewModel 공유)
   │      Setup → Measuring → Processing ─┐
   │                                       │
   ├─▶ blinddate_graph (nested, BlindDateViewModel 공유)
   │      Setup → PpgLock → Measuring       ├─▶ Result/{sessionId} → (다시 시작 / 홈)
   │             → Processing ──────────────┘
   │
   └─▶ History → Result/{sessionId}
```

라우트 정의: `ui/navigation/Routes.kt`, 그래프 구성: `ui/navigation/AppNavHost.kt`

---

### 화면 1: `SplashScreen`

**역할**: 진입 화면, 모델 로드 대기 후 Home으로.

- 앱 타이틀 + ProgressBar ("모델 로딩 중...")
- `LaunchedEffect`로 ~1.5초 후 Home 이동 (`popUpTo(SPLASH){inclusive}`)
- TODO(model): `ModelManager.loadModels()` 비동기 로드 대기로 교체

---

### 화면 2: `HomeScreen`

**역할**: 시나리오 선택 메인.

- 타이틀 + 설명
- 카드 2개:
  - **"Job Interview"** 💼 — "대면 면접 실시간 분석 (음성+얼굴+PPG)"
  - **"Blind Date"** 🍷 — "대면 대화 발화별 분석 (음성+PPG)"
- 하단 TextButton — "지난 기록 보기"
- 콜백: `onInterview` → interview_graph, `onBlindDate` → blinddate_graph, `onHistory` → History

---

### 화면 그룹 3: Interview (시나리오 1) — `interview_graph`

융합 모델 기반 실시간 분석. 3개 화면이 `InterviewViewModel`(`@HiltViewModel`)을 공유.

#### 3-1 `InterviewSetupScreen`
- CAMERA / RECORD_AUDIO 권한 안내, "후면 카메라로 상대 얼굴이 보이도록" 안내
- "시작" → Measuring
- TODO(permission): 실제 권한 요청 후 버튼 활성화

#### 3-2 `InterviewMeasuringScreen`
- 상단: **CameraX 실시간 프리뷰 + ML Kit 얼굴 박스 오버레이**, "face detected / searching face…" 상태, 전면/후면 전환 버튼
- 실시간 게이지: Deception probability(big) / **Face emotion + Voice emotion 2줄** / Face–Voice mismatch
- 하단: "종료"(빨강) → Processing
- `viewModel.onCameraFrame`이 ML Kit 비동기 검출(`manageImageClose=false`) → faceImage/faceBox 갱신
- 2.5초마다 `inferInterview(mel, faceImage)` → model_2(얼굴) + voice-only emotion/veracity

#### 3-3 `InterviewProcessingScreen`
- ProgressBar + "결과 분석 중..."
- `viewModel.stopMeasuring { sessionId -> ... }` → 세션 저장 후 `Result/{sessionId}`로 (`popUpTo(interview_graph){inclusive}`)

---

### 화면 그룹 4: Blind Date (시나리오 2) — `blinddate_graph`

Voice+PPG 모델 기반 분석. **4개 화면**(calibration 제거)이 `BlindDateViewModel` 공유.

#### 4-1 `BlindDateSetupScreen`
- RECORD_AUDIO / CAMERA(후면+플래시) 권한 요청(실제 런타임 권한) → "다음"

#### 4-2 `BlindDatePpgLockScreen`
- "상대에게 손가락을 카메라에 대달라" 안내 + CameraX 프리뷰(torch ON)
- `FingerPpgProcessor`가 red 채널 분석 → 잠금 판정 + BPM 표시 → "다음"(Measuring)
- 자동 잠금 실패 시 "Lock anyway" 수동 fallback

#### 4-3 `BlindDateMeasuringScreen`
- 상단: 현재 BPM(median 기반) + CameraX 프리뷰(손가락)
- 결과 카드 리스트: "Answer #N — fake/genuine N% / 감정 top2"
- 2.5초마다 `inferVoicePpg(mel, ppg_features, ppg_signal)` → 카드 추가, 하단 "종료" → Processing

#### 4-4 `BlindDateProcessingScreen`
- ProgressBar → `stopMeasuring` → `Result/{sessionId}`

---

### 화면 5: `ResultScreen` (공통)

**역할**: 두 시나리오의 최종 결과 표시. `ResultViewModel`이 sessionId로 세션 로드.

- 세션 요약: 모드 pill + 세그먼트 수, **Avg deception score**(그라데이션 카드) + Fake segments N/M
- **Dominant emotion**(큰 라벨 + "in N / M segments") + **Emotion spread**(상위 3개 막대그래프)
- **Emotion Timeline Heatmap**: 행=8감정, 열=세그먼트(슬라이딩 윈도우). 셀 색 = 그 윈도우에서 감정 우세 순위(1등 진한 teal / 2등 중간 / 나머지 회색). **가로 스크롤**(셀 폭 고정) + 최신 자동스크롤
- 세그먼트별 상세 리스트
- 하단 버튼: "다시 시작" / "홈으로"

---

### 화면 6: `HistoryScreen`

**역할**: 과거 세션 목록. `HistoryViewModel`이 `SessionRepository.observeSessions()` 구독.

- 세션 카드 리스트(`LazyColumn`): 모드 · 세그먼트 수 · 평균 거짓 확률
- 카드 클릭 → `Result/{sessionId}`
- 빈 상태: "아직 기록이 없습니다"

---

## 4. 데이터 모델

> **현재 구현**: `domain/model/Models.kt`의 순수 Kotlin data class(`Session`, `SegmentResult`, `InferenceOutput`, `Emotion`, `Mode`) + `InMemorySessionRepository`.
> 아래 Room `@Entity` 정의는 **영속화 도입 시 목표 스키마**다 (⬜ TODO). 도입 시 `di`에 `DatabaseModule(@Provides AppDatabase/DAO)`을 추가하고 Repository 구현을 교체한다.

### (목표) Entity: `Session`
```kotlin
@Entity
data class Session(
    @PrimaryKey val id: String,           // UUID
    val mode: String,                      // "INTERVIEW" or "BLIND_DATE"
    val startTime: Long,
    val endTime: Long,
    val avgFakeProbability: Float,
    val dominantEmotion: String
)
```

### Entity: `InferenceResult`
```kotlin
@Entity
data class InferenceResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,                 // FK to Session
    val timestamp: Long,
    val segmentIndex: Int,                 // 윈도우 또는 발화 인덱스
    val emotion: String,
    val emotionConfidence: Float,
    val fakeProbability: Float,
    val bpm: Float?,
    val faceVoiceDiscordance: Float?,      // 시나리오 1만
    val audioFilePath: String?             // 재생용 음성 파일 경로
)
```

---

## 5. 핵심 서비스/매니저 클래스 (+ Hilt DI)

> 모든 매니저/Extractor는 `@Inject constructor`로 생성자 주입한다.
> ViewModel(`@HiltViewModel`)이 이들을 주입받고, Activity(`@AndroidEntryPoint`)·Composable(`hiltViewModel()`)을 통해 연결된다.
> 인터페이스(예: `SessionRepository`)는 `di/AppModule`의 `@Binds`로 구현체와 묶는다.

### `ModelManager` (`@Singleton`, `@Inject constructor`) — **구현 완료**
- TFLite 인터프리터 5종 보유 (CPU, XNNPACK off — 호환성 우선):
  - `model_1_emotion` / `model_1_veracity` (Voice+PPG, 입력: mel/ppg_features/ppg_signal)
  - `model_1_voice_only_emotion` / `_veracity` (mel만)
  - `model_2_face_emotion` (96×96×3, 0~255)
- `inferVoicePpg(VoicePpgInput)` — 시나리오 2
- `inferInterview(InterviewInput)` — 시나리오 1 (얼굴+음성 emotion 둘 다, Face–Voice mismatch)
- 출력 `InferenceOutput`: emotion/topEmotions/**emotionDistribution(8클래스 전체)**/fakeProbability/voiceEmotion/faceVoiceDiscordance
- veracity 매핑: index 0=true, 1=fake → `fakeProbability = scores[1]`

### `AudioFeatureExtractor` — **구현 완료 (학습 정합)**
- 마이크 16k → 최근 5초 → **librosa 동등 log-mel(128×96)**: n_fft=2048, hop=1024, hann(center=reflect), Slaney mel(area-norm), `power_to_db(ref=max)`, 시간축 96 보간, per-window z-score
- **radix-2 FFT**로 실시간화(naive DFT는 오디오 스레드 블로킹 → 윈도우 안 뜸). numpy와 1e-13 일치 검증

### `FingerPpgProcessor` (시나리오 2, rPPG) — **구현 완료**
- 후면 플래시 ON, 손가락 ROI red 채널 → ppg_signal(256)/ppg_features(16)
- BPM: EMA → 3-point peak(≥600ms) → 최근 10초 interval median → `60000/median` (`PPGbetterWithVoice` 이식)

### `InterviewCameraProcessor` (시나리오 1) — **구현 완료**
- **ML Kit Face Detection**(FAST) → 가장 큰 얼굴 박스(+18% 여백) → 96×96 크롭, **0~255 float32**
- 박스를 정규화 좌표로 UI에 전달(오버레이). 회전(rotationDegrees) 보정 포함. 비동기(`analyze(image, onReading)`)

### (미사용/대체됨)
- ~~VadProcessor~~ — 5초 윈도우 채택으로 미사용 (`RealtimeAudioEngine`에 `pollCompletedUtterance` 구현은 존재)
- ~~MediaPipe FaceMesh~~ — ML Kit Face Detection으로 대체 (랜드마크 불필요, 박스만 사용)

---

## 6. 권한 요구사항

| 권한 | 시나리오 1 | 시나리오 2 | 시점 |
| --- | --- | --- | --- |
| `RECORD_AUDIO` | ✓ | ✓ | Setup 단계 |
| `CAMERA` | ✓ (후면) | ✓ (후면+플래시) | Setup 단계 |

---

## 7. 화면 전환 요약표 (Compose route)

| 화면(Composable) | route | 다음 화면 트리거 |
| --- | --- | --- |
| SplashScreen | `splash` | ~1.5초 후 → Home |
| HomeScreen | `home` | 카드/버튼 → interview_graph / blinddate_graph / History |
| InterviewSetupScreen | `interview/setup` | 시작 → Measuring |
| InterviewMeasuringScreen | `interview/measuring` | 종료 → Processing |
| InterviewProcessingScreen | `interview/processing` | `stopMeasuring` 완료 → `result/{sessionId}` |
| BlindDateSetupScreen | `blinddate/setup` | 다음 → PpgLock |
| BlindDatePpgLockScreen | `blinddate/ppglock` | PPG 잠금 + 다음 → Measuring |
| BlindDateMeasuringScreen | `blinddate/measuring` | 종료 → Processing |
| BlindDateProcessingScreen | `blinddate/processing` | `stopMeasuring` 완료 → `result/{sessionId}` |
| ResultScreen | `result/{sessionId}` | "다시 시작" → 이전 흐름 / "홈으로" → Home |
| HistoryScreen | `history` | 카드 클릭 → `result/{sessionId}` |

---

## 8. 패키지 구조 (실제 구현, `com.example.mca_project`)

> ✅ = 현재 구현됨(골격/stub), ⬜ = 모델·센서 연동 시 추가 예정

```
com.example.mca_project/
├── EmotionDetectorApp.kt        ✅ @HiltAndroidApp
├── MainActivity.kt              ✅ @AndroidEntryPoint, setContent { AppNavHost() }
├── di/
│   └── AppModule.kt             ✅ @Binds SessionRepository  (⬜ Room DatabaseModule)
├── domain/model/
│   └── Models.kt                ✅ Mode, Emotion, InferenceOutput, Session, SegmentResult
├── ml/
│   ├── ModelManager.kt          ✅ stub (ModelNotReadyException)
│   ├── AudioFeatureExtractor.kt ⬜
│   ├── FaceMeshExtractor.kt     ⬜ (시나리오 1)
│   ├── CameraFrameAnalyzer.kt   ⬜ (시나리오 1, CameraX)
│   ├── PpgExtractor.kt          ⬜ (rPPG)
│   └── VadProcessor.kt          ⬜ (시나리오 2)
├── data/repository/
│   ├── SessionRepository.kt         ✅ interface
│   └── InMemorySessionRepository.kt ✅ stub  (⬜ Room 구현으로 교체)
└── ui/
    ├── navigation/
    │   ├── Routes.kt            ✅
    │   └── AppNavHost.kt        ✅ NavHost + nested graphs
    ├── components/
    │   └── CommonComponents.kt  ✅ LabeledGauge, InfoBanner
    ├── splash/SplashScreen.kt   ✅
    ├── home/HomeScreen.kt       ✅
    ├── interview/
    │   ├── InterviewViewModel.kt ✅ @HiltViewModel
    │   └── InterviewScreens.kt   ✅ Setup/Measuring/Processing
    ├── blinddate/
    │   ├── BlindDateViewModel.kt ✅ @HiltViewModel
    │   └── BlindDateScreens.kt   ✅ Setup/PpgLock/Calibration/Measuring/Processing
    ├── result/
    │   ├── ResultViewModel.kt    ✅ @HiltViewModel
    │   └── ResultScreen.kt       ✅
    └── history/
        ├── HistoryViewModel.kt   ✅ @HiltViewModel
        └── HistoryScreen.kt      ✅
```

---

## 9. 의존성 주입 (Hilt) 요약

| 어노테이션 | 적용 대상 | 비고 |
| --- | --- | --- |
| `@HiltAndroidApp` | `EmotionDetectorApp` | DI 그래프 진입점 |
| `@AndroidEntryPoint` | `MainActivity` | 하위 Composable의 `hiltViewModel()` 활성화 |
| `@HiltViewModel` + `@Inject` | 4개 ViewModel | ModelManager / SessionRepository 주입 |
| `@Inject constructor` | ModelManager, InMemorySessionRepository | 자동 생성 |
| `@Module @InstallIn(SingletonComponent) @Binds` | `AppModule` | SessionRepository → InMemory 구현 바인딩 |

**빌드 설정**: KSP로 Hilt 컴파일러 실행. AGP 9 빌트인 Kotlin 환경에서 `android.disallowKotlinSourceSets=false` 필요 (KSP 생성 소스 등록용).
