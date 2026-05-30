# Figma Make 프롬프트

> 사용법: 아래 **Master Prompt**를 Figma Make에 먼저 붙여넣어 전체 앱을 생성하고,
> 화면별로 더 다듬고 싶으면 **Per-Screen Prompts**에서 해당 화면 프롬프트를 이어서 넣으세요.
> 영어 프롬프트가 결과가 더 안정적이라 영어로 작성했습니다.

---

## 1. Master Prompt (전체 앱 한 번에 생성)

```
Design a mobile app UI for Android called "Emotion Detector" — an on-device app that
analyzes whether a person's displayed emotion is genuine or fake, using voice, face,
and PPG (heart rate) signals in real time.

Tone & style:
- Modern, clean, trustworthy. Slightly clinical/scientific but friendly.
- Dark-friendly. Use a calm primary color (deep indigo/teal) with a clear "alert red"
  reserved only for the fake/deception gauge and the Stop button.
- Material 3 components, rounded corners (16dp cards), generous spacing, large readable
  numbers for live metrics (BPM, percentages).
- Mobile portrait, single column, one screen per route.

Build these 10 screens, connected as a flow:

1. Splash — app title "Emotion Detector", a subtle pulse/heartbeat logo, a small
   progress spinner, caption "Loading models...".

2. Home — title + subtitle "Choose a situation". Two big tappable cards stacked
   vertically: "💼 Job Interview — Real-time analysis (voice + face + PPG)" and
   "🍷 Blind Date — Per-utterance analysis (voice + PPG)". Below them a small text
   button "View past sessions".

3. Interview Setup — a permission/intro screen. Two checklist rows: "Camera (rear)"
   and "Microphone". Instruction line "Place the phone so the rear camera sees the
   other person's face." A primary "Start" button at the bottom.

4. Interview Measuring — top: a rear-camera preview area (4:3, dark placeholder with
   face-landmark overlay hint). Below: live gauges — a prominent "Deception probability"
   horizontal bar in red, then rows for "Surface emotion", "BPM" (big heart icon), and
   "Face–Voice mismatch". A subtle warning banner slot for "⚠️ Not ready!". A large red
   "Stop" button at the bottom.

5. Processing — centered spinner + "Analyzing results...". (shared style for both flows)

6. Blind Date Setup — permission intro: "Microphone", "Camera (rear + flash)".
   Primary "Next".

7. Blind Date PPG Lock — instruction "Ask the other person to place a finger on the
   camera." A circular camera preview with a glowing ring that turns green when locked.
   Status text "✓ PPG locked, BPM 72". "Next" button.

8. Blind Date Calibration — "Baseline measurement (1/3)". A large quoted question card
   e.g. "What did you have for lunch today?". Primary "Next question" / "Start" button.

9. Blind Date Measuring — top: "Current BPM" with heart icon. A scrolling list of
   utterance result cards added newest-on-top, each like "Answer #3 — Genuine 89% / BPM +5
   / low voice tremor", with a small genuine/fake colored chip. A "Stop" button.

10. Result — session summary header (mode, duration, segments). A timeline heatmap
    placeholder (x = time, y = 7 emotions, color = deception, red = fake). A list of
    per-segment cards. Two bottom buttons: "Restart" (outlined) and "Home" (filled).

Also include an empty-state History screen: a list of past session cards
(mode icon, date, avg deception %, segment count) with empty text "No sessions yet".

Keep components consistent across screens (same gauge component, same card style,
same button hierarchy). Provide reusable components for: LabeledGauge (label + % + bar),
MetricRow (icon + label + value), ResultCard, ScenarioCard.
```

---

## 2. Per-Screen Prompts (개별 화면 다듬기용)

### Home
```
Refine the Home screen. Two large scenario cards with emoji, bold title, and a one-line
description. Make the cards feel tappable (elevation, ripple). Add a small footer text
button "View past sessions". App title at top with a heartbeat accent line.
```

### Interview Measuring (가장 중요한 화면)
```
Refine the Interview Measuring screen. Layout top-to-bottom:
- Rear camera preview (4:3) with a face-mesh landmark overlay style.
- A prominent "Deception probability" gauge bar in red with a big % number.
- A "Surface emotion" row showing emoji + label + confidence.
- A "BPM" row with a heart icon and large number.
- A "Face–Voice mismatch" row.
- A dismissible warning banner reading "⚠️ Not ready!" (shown while the model is loading).
- A full-width red "Stop" button.
Make the live numbers large and glanceable, suitable for a stressful interview moment.
```

### Blind Date Measuring
```
Refine the Blind Date Measuring screen. Top shows "Current BPM" with a heart icon.
Below is a scrollable feed of utterance cards (newest on top). Each card: "Answer #N",
a genuine/fake percentage, a small colored chip (green = genuine, red = fake), and tiny
metrics (BPM delta, voice tremor). Bottom: full-width red "Stop" button.
```

### Result
```
Refine the Result screen. Header with session summary (mode, duration, segment count,
average deception %). A timeline heatmap: x-axis time, y-axis 7 emotions, cell color from
green (genuine) to red (fake). Below, a scrollable list of per-segment result cards.
Bottom row: outlined "Restart" + filled "Home".
```

---

## 3. 디자인 시스템 힌트 (원하면 같이 첨부)

```
Design tokens:
- Primary: deep indigo (#3F51B5) or teal (#00897B)
- Alert/Deception: red (#E53935) — used ONLY for fake probability and Stop button
- Genuine: green (#43A047)
- Surface: near-black (#121212) cards on dark, or white cards on light — support both
- Corner radius: 16dp cards, 12dp buttons
- Typography: large bold numerals for metrics, medium body for labels
- 7 emotions: happy, sad, angry, fear, disgust, surprise, neutral (use emoji + label)
```

---

## 4. 참고 — 우리 앱과 매핑

| Figma 화면 | 실제 Composable | route |
| --- | --- | --- |
| Splash | `SplashScreen` | `splash` |
| Home | `HomeScreen` | `home` |
| Interview Setup/Measuring/Processing | `InterviewScreens.kt` | `interview/*` |
| Blind Date 5 screens | `BlindDateScreens.kt` | `blinddate/*` |
| Result | `ResultScreen` | `result/{sessionId}` |
| History | `HistoryScreen` | `history` |

> Figma Make 결과는 디자인 참고용이고, 실제 적용은 생성된 레이아웃/색/컴포넌트를
> 위 Composable에 옮겨 넣는 방식으로 진행하면 됩니다 (상태/콜백은 이미 분리돼 있음).
