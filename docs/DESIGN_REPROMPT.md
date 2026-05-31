# Claude Design 역프롬프트 — 현재 앱 ↔ 원본 디자인 차이

> 작성: 2026-06-01
> 비교 대상: claude design 번들(`screens-a.jsx`, `screens-b.jsx`, `ui.jsx`) vs 현재 Android 앱
> 용도: 아래 항목들을 claude.ai/design 에 프롬프트로 넣어 **갱신된 디자인**을 받기 위함.
> 배경: 친구와 git 작업하며 디자인과 다르게 급조 추가/제거한 부분 + PPG 제거 방침 반영이 필요.

---

## 0. 큰 방침 (먼저 디자인 어시스턴트에게 알릴 것)

> "이 앱은 **PPG(심박/맥박) 기능을 전면 제거**하는 방향으로 바뀌었습니다.
> - **Job Interview**: 이제 Voice + Face 두 가지만 사용합니다. rPPG(심박) 표시를 모두 빼주세요.
> - **Blind Date**: PPG도 빼고 **Voice만** 사용하는 방향으로 검토 중입니다(아래 별도 항목 참고).
> 기존 디자인 전반에 'PPG / heart-rate / BPM / rPPG / 손가락 맥박' 표현이 박혀 있는데, 이걸 제거하거나 대체한 새 버전이 필요합니다."

---

## 1. Splash 화면

**현재 원본 디자인 문구**: `ON-DEVICE · VOICE · FACE · PPG`

**역프롬프트**:
> "Splash 화면 부제에서 `PPG`를 빼고 `ON-DEVICE · VOICE · FACE` 로 바꿔주세요.
> 가운데 로고가 HeartBeat(심박 파형) 아이콘인데, PPG를 안 쓰니 심박을 연상시키지 않는 아이콘(scan / waveform / layers 등)으로 대체한 버전도 함께 보여주세요."

---

## 2. Home — Scenario 카드

**원본**: Job Interview 메타 칩 = `VOICE · FACE · PPG(heart)`, Blind Date = `VOICE · PPG(heart)`

**역프롬프트**:
> "Home의 ScenarioCard 메타 칩을 다음으로 바꿔주세요.
> - Job Interview: `VOICE · FACE` (PPG/heart 칩 제거)
> - Blind Date: `VOICE` 만 (PPG/heart 칩 제거)
> subtitle 문구에서도 맥박/PPG 뉘앙스를 빼주세요."

---

## 3. Interview Setup

**원본 intro**: "We'll read voice, facial expression **and heart-rate (PPG)** continuously while you talk."

**역프롬프트**:
> "Interview Setup의 intro에서 heart-rate(PPG) 부분을 빼고
> 'We'll read voice and facial expression continuously while you talk.' 로 바꿔주세요.
> 권한 항목(Permissions)은 Camera(rear) + Microphone 2개만 유지하면 됩니다."

---

## 4. Interview Measuring (가장 큰 변경)

**원본 GaugesVariant 구성**:
1. 카메라 프리뷰 (face landmark)
2. Deception probability 게이지 (big)
3. **Surface emotion** 행 (얼굴 1개) — "dominant facial read"
4. **Heart rate · via facial rPPG** MetricRow + HeartBeat ← **PPG라 제거 대상**
5. Face–Voice mismatch 게이지

**현재 앱이 급조/변경한 것**:
- Heart rate 행을 이미 제거함
- "Surface emotion"을 **Face emotion + Voice emotion 2줄**로 확장함 (원본엔 voice emotion 행이 없음)

**역프롬프트**:
> "Interview Measuring(Gauges 변형)을 다음 구성으로 다시 디자인해주세요.
> 1. 카메라 프리뷰 (얼굴 트래킹 오버레이)
> 2. Deception probability 게이지 (big, alert 톤)
> 3. **감정 비교 카드** — 같은 카드 안에 2줄:
>    - `Face emotion` (아이콘 face, 'from camera') + 감정 라벨 + NN% conf
>    - `Voice emotion` (아이콘 mic, 'from microphone') + 감정 라벨 + NN% conf
>    이 두 줄이 바로 아래 mismatch 게이지의 근거가 되도록 시각적으로 묶어주세요.
> 4. **Face–Voice mismatch** 게이지 (warn/dim 톤) — 위 두 감정 행 바로 아래
> **Heart rate / rPPG / BPM 행은 완전히 제거**해주세요.
> dial / waveform 변형도 같은 원칙(PPG 제거, voice emotion 추가)으로 맞춰주세요."

---

## 5. Blind Date 전체 (PPG 제거 방향 — 확정 시)

**원본 흐름**: Setup → **PPG Lock(손가락 맥박 잠금)** → Calibration → Measuring(CURRENT BPM 카드 + PPG locked 칩)

**역프롬프트 (PPG 제거 시)**:
> "Blind Date에서 PPG(손가락 맥박)를 제거하는 버전을 디자인해주세요.
> - **PPG Lock 화면 제거**: 손가락을 카메라에 대는 단계가 사라집니다. Setup → (선택)Calibration → Measuring 로 단축.
> - Setup 권한: Camera(+flash) 제거, **Microphone만**.
> - Measuring 화면 상단의 'CURRENT BPM' 카드와 'PPG locked' 칩 제거. 대신 '듣는 중(LISTENING)' 상태와 발화 카운트를 강조.
> - 결과 카드(Answer #N)의 bpmDelta(심박 변화) 표시 제거, 대신 voice 기반 지표(tremor/감정/fake 확률)만 표시.
> Calibration(baseline 질문 3개)은 음성 baseline 용도로 유지할지 같이 제안해주세요."

> ※ Calibration 유지/제거는 미정 — 디자인 어시스턴트에게 둘 다 옵션으로 받기.

---

## 6. Result — Emotion Timeline Heatmap

**원본**: 7행(감정) × N열(세그먼트) 컬러 그리드. 색은 **2색** (Genuine teal-green → Deceptive red), 범례 = `Genuine` / `Deceptive`.

**현재 앱 급조 버전(카톡 스샷)**: 라벨만 있고 셀이 안 그려짐 + 범례가 `Low / Medium / High` 3색.

**현재 코드 상태**: 방금 셀 렌더링 버그 수정 + 범례를 원본 2색(Genuine/Deceptive)으로 맞춤. **이 화면은 디자인 재요청 불필요** (원본대로 복구됨).

**다만 디자인 어시스턴트에게 확인할 점**:
> "Result heatmap의 감정 행 순서가 원본은 `Neutral, Happy, Sad, Angry, Surprise, Fear, Disgust` 7개인데,
> 실제 모델은 8개(`Angry, Contempt, Disgust, Fear, Happy, Neutral, Sad, Surprise`)입니다.
> Contempt를 포함한 **8행 버전** heatmap을 디자인해주세요. 행 순서/라벨을 모델과 일치시켜주세요."

---

## 7. History 화면

**원본 카드 부제**: `{date} · {segments} segments`, 우측 `AVG DECEPTION`
**현재 앱**: `{segments} segments · {dominantEmotion}` (날짜 없음)

**역프롬프트**:
> "History 카드 부제를 `{날짜·시간} · {N} segments` 형태로 바꿔주세요(원본대로). dominant emotion은 우측 보조 영역으로 옮기거나 생략. 날짜 포맷 예: 'May 30, 2026 · 14:32'."

---

## 8. 모델 라벨 정합성 (전 화면 공통)

> "앱 전반에서 감정 라벨은 모델 출력과 일치해야 합니다. 8클래스:
> `Angry, Contempt, Disgust, Fear, Happy, Neutral, Sad, Surprise`.
> 원본 디자인의 SURFACE(`Interest/Composed/Tension/Doubt/Enthusiasm` 등 임의 라벨)는 데모용이므로, 실제 8개 감정 라벨로 교체한 버전이 필요합니다."

---

## 부록 — 비교 요약표

| 화면 | 원본 디자인 | 현재 앱 | 조치 |
| --- | --- | --- | --- |
| Splash | VOICE·FACE·PPG | VOICE·FACE·PPG | PPG 제거 (재요청) |
| Home Interview meta | VOICE·FACE·PPG | VOICE·FACE | 앱이 이미 맞음, 디자인도 PPG 제거 |
| Home Date meta | VOICE·PPG | VOICE·PPG(heart) | VOICE만 (재요청) |
| Interview Setup intro | +heart-rate(PPG) | (확인) | PPG 문구 제거 (재요청) |
| Interview Measuring | Surface emotion 1 + HR(rPPG) | Face+Voice emotion 2, HR 제거 | 디자인 재요청 (voice emotion 추가, HR 제거) |
| Blind Date | PPG Lock + BPM 카드 | 동일(원본) | PPG 제거 버전 재요청 |
| Result heatmap | 7행 2색 Genuine/Deceptive | 셀 안뜸 + 3색 → **복구함** | 8행(Contempt 포함) 재요청 |
| History 부제 | date · segments | segments · emotion | date 포함으로 재요청 |
| 감정 라벨 | 데모 임의 라벨 | 8클래스 | 8클래스로 통일 재요청 |
