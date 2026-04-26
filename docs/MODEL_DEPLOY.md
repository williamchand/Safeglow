# Gemma 4 E2B Model Deployment Procedure

The Gemma 4 E2B `.litertlm` model file is ~2.58 GB and is intentionally NOT bundled in the APK
(would exceed Play Store APK size limits and violate the "model in filesDir" architectural rule).

## One-time setup before any inference test

1. Download the model file from HuggingFace (Wi-Fi recommended):
   `https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm`
   File: `gemma-4-E2B-it.litertlm` (2.58 GB)

2. Connect a physical Android device with USB debugging enabled.
   (Emulator is NOT supported — LiteRT GPU delegate validation requires physical hardware.)

3. Install the APK first (so the app's data directory exists):
   `./gradlew installDebug`

4. Push the model into the app's external files directory:
   ```
   adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.safeglow.edge/files/gemma-4-E2B-it.litertlm
   ```

5. Verify the push:
   ```
   adb shell ls -lh /data/data/com.safeglow.edge/files/
   ```
   (or, if SE-Linux blocks direct read:)
   ```
   adb shell run-as com.safeglow.edge ls -lh files/
   ```

6. The Kotlin code resolves the path as:
   `context.filesDir.resolve("gemma-4-E2B-it.litertlm").absolutePath`

## Hackathon demo notes

- Push the model the night before — over a 2.5 GB file, USB 2.0 takes ~3–5 minutes.
- If the device is wiped or the app is uninstalled, the model file is deleted and must be re-pushed.
