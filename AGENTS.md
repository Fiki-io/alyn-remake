# AGENTS.md — Developer & AI Context Guide for Alyn SA-MP Mobile

This document serves as the primary technical guide and context reference for AI coding assistants (such as Antigravity, Claude, ChatGPT, Cursor, Copilot) and human developers working on this codebase.

---

## 1. Project Overview & Tech Stack

- **Project Name**: Alyn SA-MP Mobile (v17.x Snapshot, March 2025)
- **Description**: An Android multiplayer client (SA-MP 0.3.7 / 0.3.7-R4) and launcher mod for **Grand Theft Auto: San Andreas (Android v2.10)**.
- **Language Stack**:
  - **Launcher & UI**: Java 17, Android SDK 35, Jetpack Architecture Components, OkHttp, Glide.
  - **Client Core & Mod Engine**: Modern C++ (C++20), CMake 3.22.1+, Android NDK `25.1.8937393`.
- **Target Architectures (ABIs)**: `armeabi-v7a` (32-bit) and `arm64-v8a` (64-bit).
- **Core Mechanism**: Dynamic ELF symbol resolution and memory hooking into `libGTASA.so` at runtime.

---

## 2. Key Directories & File Map

```
alyn_samp_v17_1/
├── app/
│   ├── src/main/
│   │   ├── java/
│   │   │   ├── ro/alynsampmobile/launcher/  # Launcher logic (MainActivity, Splash, Settings, Updater)
│   │   │   ├── ro/alynsampmobile/game/      # Game Activity (SAMP.java) & UI overlay widgets
│   │   │   ├── com/rockstargames/gtasa/     # Base GTASA Android wrapper class
│   │   │   ├── com/nvidia/devtech/          # NvEventQueueActivity & Native lifecycle handlers
│   │   │   └── com/wardrumstudios/utils/    # Audio, gamepad, and utility interfaces
│   │   ├── cpp/
│   │   │   ├── Alyn_SAMPMOBILE/             # Main Native SA-MP Client
│   │   │   │   ├── Client.cpp / .h          # Native entry point, lifecycle, game loop polling
│   │   │   │   ├── Game/                    # Game entity wrappers (PlayerPed, Vehicle, Object, Camera)
│   │   │   │   │   ├── Hooks.cpp            # Function hooking into libGTASA.so
│   │   │   │   │   └── Patches.cpp          # Bytecode/memory patches for game logic & limits
│   │   │   │   ├── Net/                     # SA-MP networking layer (RakNet, RPCs, Sync Pools)
│   │   │   │   ├── Voice/                   # Spatial 3D Voice chat (Opus codec + BASS audio)
│   │   │   │   ├── UI/                      # Dear ImGui rendering & overlay widgets
│   │   │   │   ├── Java/                    # JNI bridge between C++ and Android UI
│   │   │   │   ├── Memory/                  # SymUtils & ELF symbol hooking engine (Gloss)
│   │   │   │   └── Deps/                    # Bundled 3rd-party libs (RakNet, ImGui, spdlog, RapidJSON, etc.)
│   │   │   └── SocialClub/                  # Rockstar SocialClub bypass / stub library
│   │   └── jniLibs/                         # Prebuilt libGTASA.so, libOpenAL64.so, libbass.so
│   └── build.gradle                         # App module build configuration
├── ARCHITECTURE.md                          # Detailed architectural design & flow diagrams
└── README.md                                # Official release notes from author
```

---

## 3. Important Rules & Gotchas for AI / Developers

### ⚠️ A. GTA SA Version Compatibility
The client hooks **exclusively** into **GTA: San Andreas Android v2.10** (`libGTASA.so`). Offset addresses, symbol names, and struct layouts defined in `app/src/main/cpp/Alyn_SAMPMOBILE/Game/` and `GameSA/` match only this exact game build.

### ⚠️ B. JNI & Interfacing Layers
- **Launcher $\rightarrow$ Game Handshake**: `MainActivity` starts `SAMP` Activity with Intent extra `extra_check = "alynsampmobile1337"`.
- **Java $\leftrightarrow$ C++ Bridge**:
  - `SAMP.initializeSAMP()` calls native `Java_ro_alynsampmobile_game_SAMP_initializeSAMP()` in [`Java.cpp`](file:///root/Downloads/project/alyn_samp_v17_1/app/src/main/cpp/Alyn_SAMPMOBILE/Java/Java.cpp).
  - C++ invokes Android UI widgets (dialogs, chat, keyboard, wanted level) via cached `JNIEnv` method IDs in [`Java.cpp`](file:///root/Downloads/project/alyn_samp_v17_1/app/src/main/cpp/Alyn_SAMPMOBILE/Java/Java.cpp).

### ⚠️ C. Security & Obfuscation
- **Paranoid String Encryption**: Java classes use `@Obfuscate` (com.joom.paranoid).
- **Signature Verification**: [`SignatureChecker.java`](file:///root/Downloads/project/alyn_samp_v17_1/app/src/main/java/ro/alynsampmobile/launcher/utils/SignatureChecker.java) and native function `nativeCheckSignature` check SHA-256 apk signature. During custom development, ensure signatures match or bypass this check.

### ⚠️ D. Missing Release Secrets (Expected)
The public release snapshot intentionally omits:
1. `key.jks` — Create your own debug/release keystore in `app/build.gradle`.
2. `google-services.json` — Add your own Firebase config or remove `com.google.gms.google-services` and `firebase.crashlytics` plugins from `app/build.gradle`.
3. `YOUR_APPLOVIN_SDK_KEY` & `YOUR_AD_UNIT_ID` — Placeholders in `AndroidManifest.xml` and `MainActivity.java`.

---

## 4. Build Commands & Environment

- **JDK Version**: Java 17
- **Android Gradle Plugin / Gradle**: AGP 8.x + Gradle 8.x
- **Build APK Command**:
  ```bash
  ./gradlew assembleDebug
  # or for release:
  ./gradlew assembleRelease
  ```
- **Output Binary**: `app/build/outputs/apk/{variant}/alyn_sampmobile.apk`

---

## 5. Coding Conventions
- **C++**: C++20 standard, prefer smart pointers and RAII, use `spdlog` for native logging (`spdlog::info(...)`).
- **Memory Hooking**: Always use `g_saSym->GetSymbol<T>()` or `GlossHook()` / `Memory::writeMemory()` when patching/hooking `libGTASA.so`.
- **UI Modifications**: In-game overlays can be rendered either via Android native views ([`ro.alynsampmobile.game.ui`](file:///root/Downloads/project/alyn_samp_v17_1/app/src/main/java/ro/alynsampmobile/game/ui)) or through Dear ImGui ([`app/src/main/cpp/Alyn_SAMPMOBILE/UI`](file:///root/Downloads/project/alyn_samp_v17_1/app/src/main/cpp/Alyn_SAMPMOBILE/UI)).
