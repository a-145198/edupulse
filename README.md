# ⚡ EduPulse: Doubt-to-Diagram
> **100% Offline, On-Device AI Homework & Physics Problem Solver for Android**  
> *Built for the iQOO / Android On-Device Hackathon 2026*

---

## 🌟 Overview
**EduPulse** brings an elite STEM tutor straight to students' pockets—completely disconnected from the internet. By combining **local GPU LLM inference (Google LiteRT-LM & Gemma 2B)**, **on-device OCR (PaddleOCR v5 via ONNX Runtime)**, **interactive vector physics simulation (Jetpack Compose Canvas)**, and **native regional languages (Telugu, Hindi, English)**, EduPulse solves the educational digital divide with zero cloud latency, zero subscription costs, and 100% air-gapped student privacy.

> 📖 **Full Technical Master Documentation:** For the exhaustive 12-section technical blueprint covering the full 8-layer architecture, mathematical derivations, character confusion matrices, silent OCR corruption solutions, and complete benchmarks, see [**`COMPLETE_PROJECT_DOCUMENTATION.md`**](COMPLETE_PROJECT_DOCUMENTATION.md).

---

## 🚀 Key Features

### 1. 📐 Doubt-to-Diagram (Dynamic Physics Canvas)
- Automatically extracts kinematic parameters ($m, u, v, a, F, s, t$) from the AI's step-by-step mathematical reasoning.
- Renders hardware-accelerated 60fps vector diagrams:
  - **Motion Track View:** Visualizes initial velocity ($\longrightarrow$), opposing braking force ($\longleftarrow$), stopping distance ($\longleftrightarrow s$), and ghost rest marker.
  - **Free-Body Forces View:** 4-way balanced equilibrium showing Normal, Gravity, Motion, and Retarding vectors.
  - **Animated Simulation:** Tapping `▶ Simulate Motion` smoothly animates the mass block decelerating across the track to rest.

### 2. 🧪 Interactive "What-If?" Simulation Sandbox
- Tapping `[🧪 What-If? 🧪]` opens an interactive speed experiment slider ($2.0\text{ m/s}$ to $30.0\text{ m/s}$).
- Real-time recalculation of stopping distance ($s = \frac{u^2}{2a}$) and time ($t = \frac{u}{a}$).
- Dynamically scales vector arrow lengths on the Canvas and updates parameter chips live.
- Allows students to test physical principles (e.g. *doubling speed quadruples braking distance*) visually before solving homework.

### 3. 📊 On-Device Silicon Telemetry HUD
- A clickable `[ 📊 Silicon Stats ]` chip in the app header opens a live telemetry dashboard.
- Displays detected hardware (`Google Pixel 6a - Google Tensor G1 / Mali-G78 GPU`), LiteRT-LM OpenCL GPU backend, ~22–28 tokens/sec throughput, ONNX OCR latency (~180ms), JVM heap allocation, and **0.00 KB network traffic proof**.

### 4. 🇮🇳 Multilingual Regional Language Support
- Native script generation and reasoning for **English**, **తెలుగు (Telugu)**, and **हिन्दी (Hindi)**.
- High-adherence native script system prompts prevent language bleed-through.

### 5. 🔊 Offline Spoken Explanations (TTS)
- One-tap `🔊 Listen` audio narration powered by Android's native offline `TextToSpeech` engine.
- Filters markdown formatting to deliver fluid, natural audio in regional accents.

### 6. 📸 CameraX & Gallery OCR Pipeline
- Custom CameraX viewfinder guide box ensures notebook problems are framed properly.
- Android Photo Picker allows selecting images from gallery with zero legacy storage permissions.
- Dual-phase OCR correction: rule-based regex repairs + Gemma self-reconstruction.

---

## 🛠️ Technology Stack & Engineering Rationale

| Component | Technology | Why We Chose It |
|---|---|---|
| **LLM Inference** | **Google LiteRT-LM (OpenCL GPU)** | Native C++ runtime executing Gemma 2B INT4 weights directly on mobile GPU shader cores (~25 tok/s). Uses OS `mmap` zero-copy loading to prevent Java heap exhaustion. |
| **Vision / OCR** | **PaddleOCR v5 (ONNX Runtime Mobile)** | State-of-the-art DBNet text detection + CRNN text recognition. Outperforms Tesseract on handwritten classroom notes with ~180ms inference. |
| **User Interface** | **Jetpack Compose + Material 3** | Modern declarative UI, edge-to-edge system bars, dynamic color theming, and reactive state management. |
| **Graphics** | **Compose Vector Canvas** | Native GPU vector rendering avoiding the overhead, latency, and memory bloat of WebViews or image generation models. |
| **Audio** | **Android Native TTS** | Built-in, zero-cloud text-to-speech with full regional language support. |
| **Security** | **Semgrep Static Analysis** | Verified with 0 security or data leakage vulnerabilities. |

---

## 📱 Hardware & Performance Verification
Tested and verified on physical hardware: **Google Pixel 6a (Google Tensor G1)**.

- **LLM Speed:** ~22 – 28 tokens / second
- **OCR Latency:** ~180 ms full image parse
- **Java Heap Footprint:** ~7 MB active / 256 MB max (1.4 GB model weights mapped in native C++ memory)
- **Network I/O:** **0.00 KB** (100% functional in Airplane Mode)

---

## 🏗️ Project Architecture
```
app/src/main/java/com/edupulse/app/
├── EduPulseApp.kt              # App initializations
├── MainActivity.kt             # Edge-to-edge entrypoint
├── diagram/
│   ├── PhysicsDiagram.kt       # Kinematics/Forces domain models & regex parser
│   └── DiagramRenderer.kt      # Vector Canvas, animations, What-If slider
├── llm/
│   └── GemmaEngine.kt          # LiteRT-LM OpenCL GPU engine wrapper
├── ocr/
│   ├── OcrCorrection.kt        # Regex dictionary & character repairs
│   └── paddle/                 # ONNX PaddleOCR Det + Rec pipeline
├── tts/
│   └── TtsManager.kt           # Offline Android TTS engine
└── ui/
    ├── HomeScreen.kt           # Camera, Chat, Silicon Telemetry HUD
    └── HomeViewModel.kt        # StateFlow business logic coordinator
```

---

## 🧪 Testing & Validation
- **OCR Unit Tests:** `uv run python ocr/test_ocr.py` (100% passing)
- **Security Audit:** `semgrep scan --config=p/security-audit` (0 findings)
- **Compilation:** `./gradlew assembleDebug`
