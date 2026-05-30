익# 유저 시나리오 & 안드로이드 앱 설계 문서

> 플랫폼: Android (Kotlin, Android Studio)
> 모델: Voice+PPG (시나리오 2), Voice+Face+PPG 융합 (시나리오 1)

---

## 1. 확정된 설계 결정 사항

| 항목 | 결정 |
| --- | --- |
| **시나리오 1 카메라 소스** | **후면 카메라 직접 촬영** (상대 얼굴을 후면 카메라로 찍어 face landmark + PPG 동시 추출) |
| **시나리오 2 입력** | **음성 + PPG 동시** (Voice+PPG 모델은 두 입력 모두 필수) |
| **앱 진입 방식** | **상황 선택 버튼 2개** (Blind Date / Job Interview) |
| **추론 구조** | 시작 → 측정 중 → 결과 → 다시 시작/홈 (다중 화면) |

---

## 2. 유저 시나리오

### 시나리오 1: Job Interview (대면 면접)

**상황**: 대면 1:1 면접 / 미팅 (면접관과 마주 앉은 상황)
**모델**: 융합 (Voice + Face + PPG)
**추론 방식**: 슬라이딩 윈도우 실시간 (5초 단위)
**카메라 입력**: 후면 카메라로 상대(면접관) 얼굴을 직접 촬영 → 프레임에서 face landmark + PPG(뺨 RGB) 동시 추출, 음성은 폰 마이크로 함께 캡처

**유저 플로우**:
1. 앱 실행 → 홈에서 "Job Interview" 선택
2. 카메라 권한 요청 → 마이크 권한 요청
3. "후면 카메라로 상대 얼굴이 보이도록 폰을 두세요" 안내
4. 시작 → 카메라 프리뷰 + 실시간 게이지 표시 (5초 윈도우마다 추론)
5. 종료 → 전체 타임라인 리포트

**왜 후면 카메라 직접 촬영인가**:
- 대면 상황에서 상대 얼굴을 후면 카메라로 직접 찍는 것이 가장 단순하고 화질이 좋음
- 같은 프레임에서 face landmark와 PPG(뺨 RGB 분산)를 동시에 추출
- 음성은 폰 마이크로 함께 확보 → 융합 모델의 세 입력(Voice/Face/PPG)을 한 번에 충족

---

### 시나리오 2: Blind Date (대면 단둘 대화)

**상황**: 블라인드 데이트, 연인 간 유도심문, "솔직히 말해봐" 추궁
**모델**: Voice + PPG
**추론 방식**: 발화 단위 (VAD 기반 자동 분절)
**입력**:
- **음성**: 폰 마이크로 상대 음성 녹음
- **PPG**: 상대 손가락을 후면 카메라+플래시에 올림 → RGB 변화로 PPG 추출

**유저 플로우**:
1. 앱 실행 → 홈에서 "Blind Date" 선택
2. 마이크 + 카메라 권한 요청
3. "상대에게 손가락을 카메라에 대달라고 요청하세요" 안내
4. PPG 잠금 확인 (BPM 안정화 대기) + 평상시 baseline 캘리브레이션 (편한 질문 3개)
5. 본격 측정 시작 → 발화 단위로 결과 카드 누적
6. 종료 → 질문-답변별 결과 리스트 리포트

**왜 손가락 PPG인가**:
- 대면 상황에서 카메라로 상대 얼굴을 정면에서 계속 찍는 건 부자연스러움
- 손가락 PPG는 "재미있는 인터랙션 요소" (게임처럼 손가락을 대라고 요청)
- Voice+PPG 모델의 두 입력을 자연스럽게 확보

---

## 3. 화면 구조 (Jetpack Compose + Navigation)

> **단일 Activity(`MainActivity`, `@AndroidEntryPoint`) + Compose `NavHost`** 구조.
> 각 화면은 `@Composable` 함수이고, 화면 상태는 `@HiltViewModel` ViewModel이 `StateFlow`로 보유한다.
> Interview / Blind Date 의 다단계 화면은 **nested navigation graph**로 묶어 같은 ViewModel을 공유한다.
> ML 추론은 모델(.tflite) 연동 전까지 stub이며, 추론 시도 시 `ModelManager`가 `ModelNotReadyException("Not ready!")`을 던져 UI에 "Not ready!" 배너로 표시된다.

### 전체 네비게이션 다이어그램

```
Splash  (모델 로드 placeholder)
   ↓
Home  (시나리오 선택: 2개 카드 + 지난 기록)
   ├─▶ interview_graph (nested, InterviewViewModel 공유)
   │      Setup → Measuring → Processing ─┐
   │                                       │
   ├─▶ blinddate_graph (nested, BlindDateViewModel 공유)
   │      Setup → PpgLock → Calibration    ├─▶ Result/{sessionId} → (다시 시작 / 홈)
   │             → Measuring → Processing ─┘
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
- 상단: 카메라 프리뷰 placeholder (`[ 카메라 프리뷰 ]` Surface)
- 실시간 게이지: 거짓 확률 / 표면 감정 / BPM / 얼굴-음성 불일치
- 모델 미연동 시 "Not ready!" 배너 노출
- 하단: "종료"(빨강) → Processing
- `LaunchedEffect`에서 `viewModel.startMeasuring()` 호출
- TODO: CameraX 프리뷰+FaceMesh, 마이크, 5초 윈도우 `inferFusion`

#### 3-3 `InterviewProcessingScreen`
- ProgressBar + "결과 분석 중..."
- `viewModel.stopMeasuring { sessionId -> ... }` → 세션 저장 후 `Result/{sessionId}`로 (`popUpTo(interview_graph){inclusive}`)

---

### 화면 그룹 4: Blind Date (시나리오 2) — `blinddate_graph`

Voice+PPG 모델 기반 발화 단위 분석. 5개 화면이 `BlindDateViewModel` 공유.

#### 4-1 `BlindDateSetupScreen`
- RECORD_AUDIO / CAMERA(후면+플래시) 권한 안내 → "다음"

#### 4-2 `BlindDatePpgLockScreen`
- "상대에게 손가락을 카메라에 대달라" 안내
- `viewModel.lockPpg()` (현재 즉시 성공 stub) → "✓ PPG 잠금 완료, BPM: 72" → "다음"
- TODO(ppg): Camera2 + 플래시, RGB 신호 안정화 감지

#### 4-3 `BlindDateCalibrationScreen`
- baseline 질문 3개(`CALIBRATION_QUESTIONS`) 순차 표시
- 각 질문 후 `nextCalibrationQuestion()`, 마지막엔 "측정 시작" → Measuring
- TODO(calibration): 답변 시 음성+PPG baseline 수집

#### 4-4 `BlindDateMeasuringScreen`
- 상단: 현재 BPM
- 발화 카드 리스트(`LazyColumn`): "답변 #N — 진짜 89% / BPM ..."
- "Not ready!" 배너, 하단 "종료" → Processing
- TODO(vad): VAD 발화 분절 → 발화마다 `inferVoicePpg` → 카드 추가

#### 4-5 `BlindDateProcessingScreen`
- ProgressBar → `stopMeasuring` → `Result/{sessionId}`

---

### 화면 5: `ResultScreen` (공통)

**역할**: 두 시나리오의 최종 결과 표시. `ResultViewModel`이 sessionId로 세션 로드.

- 세션 요약 (모드 / 세그먼트 수 / 평균 거짓 확률)
- 세그먼트별 상세 리스트(`LazyColumn`) — 모델 미연동 시 "기록된 세그먼트가 없습니다"
- 하단 버튼: "다시 시작"(이전 흐름으로 popBackStack) / "홈으로"
- TODO(chart): 타임라인 히트맵(시간×감정×거짓확률) 렌더링

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

### `ModelManager` (`@Singleton`, `@Inject constructor`)
- TFLite 인터프리터 보유 예정 (Voice+PPG 모델, Fusion 모델)
- `inferVoicePpg(audioFeatures, ppgFeatures): InferenceOutput`
- `inferFusion(audioFeatures, faceFeatures, ppgFeatures): InferenceOutput`
- **현재 stub**: `isReady = false`, 추론 메서드는 `ModelNotReadyException("Not ready!")`을 던짐. `.tflite` 연동 시 `loadModels()`/추론 구현을 채운다.

### `AudioFeatureExtractor`
- 원본 PCM → MFCC, Pitch, Energy, Jitter, Shimmer 추출
- TarsosDSP 또는 자체 구현

### `PpgExtractor` (rPPG)
- **rPPG 방식** — 별도 센서 없이 카메라 프레임의 ROI 색 변화로 심박을 추출 (참고: `PPGbetterwithvoice` 프로젝트)
- 입력: `ImageReader`(YUV_420_888) 프레임의 ROI 픽셀 평균값 → 시간축 1D 신호
  - 시나리오 1: 얼굴 프레임의 **뺨 ROI** 평균 (플래시 불필요)
  - 시나리오 2: 후면 **플래시 ON**, **손가락 ROI** 평균
- 신호 처리: EMA 평활 → bandpass filter → 피크 검출(또는 FFT) → BPM, HRV
- 참고 구현의 핵심 흐름: 프레임 평균 → EMA → 3-포인트 로컬 피크 → 피크 간격 중앙값 → BPM(45~180 클램프)

### `VadProcessor`
- WebRTC VAD 또는 silero-vad TFLite
- 발화 시작/종료 콜백

### `CameraFrameAnalyzer` (시나리오 1 전용)
- CameraX ImageAnalysis로 후면 카메라 프레임 수집
- 프레임 → MediaPipe Face Mesh(랜드마크) + 뺨 ROI(PPG) 동시 처리

### `FaceMeshExtractor`
- MediaPipe Tasks Android (Face Landmarker)
- 캡처된 프레임 → 468개 3D 랜드마크 → 1404차원 벡터

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
| BlindDatePpgLockScreen | `blinddate/ppglock` | PPG 잠금 + 다음 → Calibration |
| BlindDateCalibrationScreen | `blinddate/calibration` | 3개 질문 완료 → Measuring |
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
