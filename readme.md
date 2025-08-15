### 🎯 What is it?

**Face-Point** is the **first open-source real-time face filter app** built using **MediaPipe FaceMesh** for high-performance, GPU-accelerated effects — and it’s blazing fast thanks to its **pure OpenGL** rendering pipeline.

> ⚡ No OpenCV. No fluff. Just raw OpenGL power.

---

### 🧵 Tech Stack Highlights

- 🧠 **MediaPipe FaceMesh** for facial landmark detection
- 🎮 **Pure OpenGL** for rendering — no OpenCV involved
- 📱 Optimized for Android with native performance
- 🧩 Modular design for custom filters and effects

---

### 📚 Credits & References

- 🎥 **Encoder Logic**: Based on [EncodeAndMuxTest.java](https://bigflake.com/mediacodec/EncodeAndMuxTest.java.txt) from Bigflake — a robust reference implementation for MediaCodec + MediaMuxer workflows
- 🖼️ **Offscreen Rendering**: Uses `EglSurfaceBase` and `EglCore` from Google’s [Grafika test app](https://github.com/google/grafika)
- 🧠 **Face Landmark Detection**: Utilizes Google’s [MediaPipe Face Landmarker](https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker/android) — official model and API used for high-accuracy facial tracking

---

### 🚫 Current Limitations

- 🎙️ **Audio recording is not supported yet**
  - Planned upgrade: **voice changer integration**
  - MediaCodec logic will be **migrated from Kotlin to C++** to eliminate processing overhead and boost performance

---

### 🔥 Why It Stands Out

- Built from scratch with precision and performance in mind
- Designed for real-time responsiveness on mobile GPUs
- Ideal for developers who want full control over rendering and effects

Want me to help you write a README badge section or a “Why OpenGL?” manifesto next? 😎

## 🎨 Filter Preview Grid

| Filter Name | Preview                        |
| ----------- | ------------------------------ |
| `1_default` | <img src="screenshots/1_default.png" style="width:200px;"/> |

| Filter Name     | Preview                            | Filter Name    | Preview                           |
| --------------- | ---------------------------------- | -------------- | --------------------------------- |
| `2_monkey`      | ![](./screenshots/2_monkey.png)      | `3_eye_censor` | ![](./screenshots/3_eye_censor.png) |
| `4_googly_eyes` | ![](./screenshots/4_googly_eyes.png) | `5_chad`       | ![](./screenshots/5_chad.png)       |
| `6_glsses`      | ![](./screenshots/6_glsses.png)      | `7_invert`     | ![](./screenshots/7_invert.png)     |
| `8_b&w`         | ![](./screenshots/8_b&w.png)         | `9_cine_still` | ![](./screenshots/9_cine_still.png) |
| `10_sunset`     | ![](./screenshots/10_sunset.png)     | `11_sunset_2`  | ![](./screenshots/11_sunset_2.png)  |
| `12_BW1`        | ![](./screenshots/12_BW1.png)        | Coming Soon... | ☁️☁️☁️☁️☁️☁️☁️                    |

# 🐞 Known Issues

## 🧨 Shader Crash: `sampler3D` on Mali GPUs

### 🔍 Issue Summary

A shader crash occurs when using the following uniform declaration on devices with **Mali GPUs** (commonly paired with **MediaTek CPUs**):
>uniform sampler3D lut;
