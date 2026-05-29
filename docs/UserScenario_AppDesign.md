# 유저 시나리오 & 안드로이드 앱 설계 문서

> 플랫폼: Android (Kotlin, Android Studio)
> 모델: Voice+PPG (시나리오 2), Voice+Face+PPG 융합 (시나리오 1)

---

## 1. 확정된 설계 결정 사항

| 항목 | 결정 |
| --- | --- |
| **시나리오 1 카메라 소스** | **상대 화면 캡처** (MediaProjection API로 영상통화 화면 캡처) |
| **시나리오 2 입력** | **음성 + PPG 동시** (Voice+PPG 모델은 두 입력 모두 필수) |
| **앱 진입 방식** | **상황 선택 버튼 2개** (Blind Date / Job Interview) |
| **추론 구조** | 시작 → 측정 중 → 결과 → 다시 시작/홈 (다중 화면) |

---

## 2. 유저 시나리오

### 시나리오 1: Job Interview (영상 면접)

**상황**: 화상 면접, 원격 1:1 미팅
**모델**: 융합 (Voice + Face + PPG)
**추론 방식**: 슬라이딩 윈도우 실시간 (5초 단위)
**카메라 입력**: MediaProjection API로 영상통화 앱 화면 캡처 → 상대(면접관) 얼굴에서 face landmark + PPG 동시 추출

**유저 플로우**:
1. 앱 실행 → 홈에서 "Job Interview" 선택
2. 화면 캡처 권한 요청 → 마이크 권한 요청
3. "면접 앱을 켜고 시작 버튼을 누르세요" 안내
4. 시작 → 백그라운드에서 화면+음성 캡처, 오버레이 위젯으로 실시간 게이지 표시
5. 종료 → 전체 타임라인 리포트

**왜 화면 캡처인가**:
- 영상통화 시 상대를 직접 찍을 수 없음
- MediaProjection으로 화상통화 앱(Zoom, Google Meet 등)의 화면을 캡처해 상대 얼굴 추출
- 같은 화면에서 상대의 음성도 함께 캡처 가능

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

## 3. 액티비티/프래그먼트 구조

### 전체 네비게이션 다이어그램

```
┌────────────────┐
│ SplashActivity │  (로고 + 권한 사전 체크)
└───────┬────────┘
        ↓
┌────────────────┐
│  HomeActivity  │  (시나리오 선택: 2개 버튼)
└───┬────────┬───┘
    │        │
    ↓        ↓
┌─────────────┐      ┌──────────────┐
│ Interview   │      │  BlindDate   │
│ Activity    │      │  Activity    │
│ (Fragment   │      │  (Fragment   │
│  Container) │      │   Container) │
└─────┬───────┘      └──────┬───────┘
      │                     │
      └──────────┬──────────┘
                 ↓
        ┌─────────────────┐
        │ ResultActivity  │  (공통 결과 화면)
        └────────┬────────┘
                 ↓
        ┌─────────────────┐
        │  HistoryActivity│  (과거 세션 목록, 선택)
        └─────────────────┘
```

---

### Activity 1: `SplashActivity`

**역할**: 앱 진입점, 로고 표시, 모델 로드 (TFLite 인터프리터 초기화)

**레이아웃**: `activity_splash.xml`
- 앱 로고 ImageView
- ProgressBar ("모델 로딩 중...")

**주요 동작**:
```kotlin
- onCreate: TFLite 모델 비동기 로드 (Voice+PPG, Fusion 두 모델)
- 로드 완료 시 → HomeActivity로 이동, finish()
```

**소요 시간**: 1.5~2초

---

### Activity 2: `HomeActivity`

**역할**: 시나리오 선택 메인 화면

**레이아웃**: `activity_home.xml`
- 상단: 앱 타이틀, 설명 텍스트
- 중앙: 큰 버튼 2개 (CardView)
  - **"Job Interview"** — 아이콘: 💼, 설명: "영상 면접 실시간 분석"
  - **"Blind Date"** — 아이콘: 🍷, 설명: "대면 대화 발화별 분석"
- 하단: 작은 버튼 — "지난 기록 보기" (HistoryActivity로)

**주요 동작**:
```kotlin
- Interview 버튼 클릭 → InterviewActivity 시작
- BlindDate 버튼 클릭 → BlindDateActivity 시작
- History 버튼 클릭 → HistoryActivity 시작
```

---

### Activity 3: `InterviewActivity` (시나리오 1)

**역할**: 융합 모델 기반 실시간 분석. 내부에서 Fragment로 단계 전환.

**구조**: 단일 Activity + FragmentContainerView로 4단계 전환

#### Fragment 3-1: `InterviewSetupFragment`
**화면**: 권한 요청 + 안내
- "화면 캡처 권한이 필요합니다" → MediaProjection 권한 요청
- "마이크 권한이 필요합니다" → RECORD_AUDIO 권한 요청
- 모든 권한 획득 시 → "시작" 버튼 활성화
- "시작" 클릭 → `InterviewMeasuringFragment`로 전환 + ForegroundService 시작

#### Fragment 3-2: `InterviewMeasuringFragment`
**화면**: 측정 중 상태 (앱은 최소화 가능, 오버레이로 게이지 표시)
- 큰 텍스트: "측정 중..."
- 실시간 게이지:
  - 표면 감정 (현재 detect된 emotion + 확률)
  - 거짓 확률 (0~100% 게이지)
  - BPM (PPG에서)
  - Face-Voice 불일치 점수
- 하단: "종료" 버튼 (큰 빨간 버튼)
- 백그라운드 시: 시스템 오버레이 위젯으로 동일 정보 미니뷰 (SYSTEM_ALERT_WINDOW 권한 필요)

**내부 동작**:
- `MediaProjectionService` (ForegroundService): 화면 프레임 수집
- `AudioCaptureService`: 마이크/시스템 오디오 수집
- 5초 슬라이딩 윈도우로 융합 모델 추론
- LiveData/StateFlow로 UI에 결과 push

#### Fragment 3-3: `InterviewProcessingFragment`
**화면**: 종료 직후 결과 집계 중
- ProgressBar + "결과 분석 중..."
- 세션 전체 녹화 데이터를 기반으로 최종 리포트 생성
- 완료 시 → `ResultActivity`로 전환 (Intent에 sessionId 전달)

---

### Activity 4: `BlindDateActivity` (시나리오 2)

**역할**: Voice+PPG 모델 기반 발화 단위 분석

**구조**: 단일 Activity + Fragment 5단계

#### Fragment 4-1: `BlindDateSetupFragment`
**화면**: 권한 요청
- 마이크 권한 + 카메라 권한 요청
- 권한 획득 시 → `BlindDatePpgLockFragment`로 전환

#### Fragment 4-2: `BlindDatePpgLockFragment`
**화면**: PPG 잠금 (손가락 인식 대기)
- 상단 카메라 프리뷰 (작은 원형)
- "상대방에게 카메라에 손가락을 대달라고 요청하세요"
- 플래시 자동 ON
- PPG 신호 안정화 감지 (RGB 분산 임계치 + 주기성 검출)
- 잠금 성공 시 → 큰 체크마크 + "✓ PPG 잠금 완료, BPM: 72"
- "다음" 버튼 활성화 → `BlindDateCalibrationFragment`

#### Fragment 4-3: `BlindDateCalibrationFragment`
**화면**: Baseline 캘리브레이션
- "평소 심박수/목소리를 측정합니다"
- 가벼운 질문 3개 자동 출력 (TTS or 텍스트):
  - "오늘 점심 뭐 드셨어요?"
  - "최근 본 영화 있어요?"
  - "주말에 뭐 하셨어요?"
- 각 질문에 답변 시 음성 + PPG 캡처
- baseline (평균 BPM, 음성 특징 평균) 저장
- 완료 → `BlindDateMeasuringFragment`

#### Fragment 4-4: `BlindDateMeasuringFragment`
**화면**: 본 측정
- 상단: 현재 BPM (실시간), 손가락 PPG 상태 (잠금 유지 확인)
- 중앙: 발화 카드 리스트 (RecyclerView, 새 발화가 위로 추가)
  - 각 카드: "답변 #N — 진짜 89% / 가짜 11% / BPM +5 / 음성 떨림 낮음"
- 하단: "종료" 버튼

**내부 동작**:
- VAD (Voice Activity Detection)로 발화 시작/끝 감지
- 발화 종료 시점에 해당 구간의 음성+PPG를 Voice+PPG 모델에 입력
- 결과를 카드로 추가
- PPG 신호 끊김 시 (손가락 뗌) 경고 표시

#### Fragment 4-5: `BlindDateProcessingFragment`
**화면**: 종료 후 집계
- ProgressBar + "결과 분석 중..."
- 완료 시 → `ResultActivity`로 전환

---

### Activity 5: `ResultActivity` (공통)

**역할**: 두 시나리오의 최종 결과 표시

**레이아웃**: `activity_result.xml`
- 상단: 세션 요약 (모드, 시간, 총 발화 수)
- 중앙: 타임라인 히트맵
  - X축: 시간
  - Y축: 감정 (7개)
  - 색상: 거짓 확률 (빨강이 진할수록 가짜)
- 발화별 상세 리스트 (RecyclerView):
  - 시나리오 1: 5초 윈도우별 결과
  - 시나리오 2: 발화 단위 결과
  - 각 항목 클릭 → 음성 재생 + 상세 정보
- 하단 버튼 2개:
  - "다시 시작" → 이전 Activity (Interview or BlindDate) 재실행
  - "홈으로" → HomeActivity (백스택 클리어)

**내부 동작**:
- Intent로 받은 sessionId로 로컬 DB(Room)에서 세션 데이터 로드
- MPAndroidChart 등으로 히트맵/그래프 렌더링

---

### Activity 6: `HistoryActivity`

**역할**: 과거 세션 목록

**레이아웃**: `activity_history.xml`
- RecyclerView: 세션 카드 리스트
  - 카드: 날짜, 모드 아이콘, 평균 거짓 확률, 발화 수
- 카드 클릭 → `ResultActivity` (해당 sessionId 전달)
- 빈 상태: "아직 기록이 없습니다"

---

## 4. 데이터 모델 (Room DB)

### Entity: `Session`
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

## 5. 핵심 서비스/매니저 클래스

### `ModelManager` (Singleton)
- TFLite 인터프리터 보유 (Voice+PPG 모델, Fusion 모델)
- `inferVoicePpg(audioFeatures, ppgFeatures): InferenceOutput`
- `inferFusion(audioFeatures, faceFeatures, ppgFeatures): InferenceOutput`

### `AudioFeatureExtractor`
- 원본 PCM → MFCC, Pitch, Energy, Jitter, Shimmer 추출
- TarsosDSP 또는 자체 구현

### `PpgExtractor`
- Camera2 API + 후면 플래시
- 프레임별 ROI 평균 RGB → 시간축 신호 → bandpass filter → BPM, HRV

### `VadProcessor`
- WebRTC VAD 또는 silero-vad TFLite
- 발화 시작/종료 콜백

### `MediaProjectionService` (ForegroundService, 시나리오 1 전용)
- 화면 캡처 → 프레임 추출 → MediaPipe Face Mesh → 랜드마크
- 시스템 오디오 캡처 (Android 10+)

### `FaceMeshExtractor`
- MediaPipe Tasks Android (Face Landmarker)
- 캡처된 프레임 → 468개 3D 랜드마크 → 1404차원 벡터

### `OverlayWindowManager` (시나리오 1 전용)
- SYSTEM_ALERT_WINDOW로 백그라운드 위젯 표시
- 다른 앱 위에 작은 게이지 UI 띄움

---

## 6. 권한 요구사항

| 권한 | 시나리오 1 | 시나리오 2 | 시점 |
| --- | --- | --- | --- |
| `RECORD_AUDIO` | ✓ | ✓ | Setup 단계 |
| `CAMERA` | — | ✓ (후면+플래시) | Setup 단계 |
| `FOREGROUND_SERVICE` | ✓ | — | 매니페스트 |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | ✓ | — | 매니페스트 (Android 14+) |
| `SYSTEM_ALERT_WINDOW` | ✓ | — | Setup 단계 (선택) |
| `POST_NOTIFICATIONS` | ✓ | — | Setup 단계 (Android 13+) |
| MediaProjection 동의 | ✓ | — | 측정 시작 직전 (Intent) |

---

## 7. 화면 전환 요약표

| Activity | Fragment/State | 다음 화면 트리거 |
| --- | --- | --- |
| Splash | (단일 화면) | 모델 로드 완료 → Home |
| Home | (단일 화면) | 버튼 클릭 → Interview / BlindDate / History |
| Interview | Setup | 권한 완료 + 시작 클릭 → Measuring |
| Interview | Measuring | 종료 클릭 → Processing |
| Interview | Processing | 집계 완료 → ResultActivity |
| BlindDate | Setup | 권한 완료 → PpgLock |
| BlindDate | PpgLock | PPG 안정 + 다음 클릭 → Calibration |
| BlindDate | Calibration | 3개 질문 완료 → Measuring |
| BlindDate | Measuring | 종료 클릭 → Processing |
| BlindDate | Processing | 집계 완료 → ResultActivity |
| Result | (단일 화면) | "다시 시작" → 이전 Activity / "홈으로" → Home |
| History | (단일 화면) | 카드 클릭 → Result |

---

## 8. 패키지 구조 제안

```
com.example.emotiondetector/
├── ui/
│   ├── splash/SplashActivity.kt
│   ├── home/HomeActivity.kt
│   ├── interview/
│   │   ├── InterviewActivity.kt
│   │   ├── InterviewSetupFragment.kt
│   │   ├── InterviewMeasuringFragment.kt
│   │   └── InterviewProcessingFragment.kt
│   ├── blinddate/
│   │   ├── BlindDateActivity.kt
│   │   ├── BlindDateSetupFragment.kt
│   │   ├── BlindDatePpgLockFragment.kt
│   │   ├── BlindDateCalibrationFragment.kt
│   │   ├── BlindDateMeasuringFragment.kt
│   │   └── BlindDateProcessingFragment.kt
│   ├── result/ResultActivity.kt
│   └── history/HistoryActivity.kt
├── viewmodel/
│   ├── InterviewViewModel.kt
│   ├── BlindDateViewModel.kt
│   └── ResultViewModel.kt
├── ml/
│   ├── ModelManager.kt
│   ├── AudioFeatureExtractor.kt
│   ├── FaceMeshExtractor.kt
│   ├── PpgExtractor.kt
│   └── VadProcessor.kt
├── service/
│   ├── MediaProjectionService.kt
│   └── OverlayWindowManager.kt
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt
│   │   ├── SessionDao.kt
│   │   └── InferenceResultDao.kt
│   ├── entity/
│   │   ├── Session.kt
│   │   └── InferenceResult.kt
│   └── repository/SessionRepository.kt
└── util/
    ├── PermissionHelper.kt
    └── Constants.kt
```
