# Index Plus Key for Android

The custom app records the phone microphone while the OnePlus Plus Key is held and submits it to Index on release. It installs as coredevices.coreapp.pluskey alongside the stock app.

## Use

On first launch, choose **Skip setup - use Index on this phone** to bypass device selection and sign-in. This enables Index and remembers it as the starting tab. Permissions are requested when the corresponding feature needs them.

1. Open the main app, then Index settings > Oneplus integration.
2. Configure the LLM and speech endpoints, model names and API keys in their native editors. Save each route. Defaults are Groq at https://api.groq.com/openai/v1 with qwen/qwen3.8-27b and whisper-large-v3-turbo.
3. Optionally enable Transcription cleanup and save custom words. This adds a separate LLM editing request before Index processes the transcript.
4. Tap Prepare Index, then Enable key while the screen is visible. Microphone consent is requested when needed. Alternatively, a computer can grant key log access with: adb shell pm grant --user 0 coredevices.coreapp.pluskey android.permission.READ_LOGS.
5. Hold for at least 300 ms, speak, then release. Maximum recording is two minutes. Disable key cancels unfinished capture. Re-enable after Android stops the app.

Disable DeskLink PC microphone mode first. The phone's system key assignment still runs; choose an assignment that does not use the microphone. The notification opens the main Index app. Temporary setup/test screens and their extra launcher entry have been removed.

## Build

This repository is the standalone fork at https://github.com/FBarrca/mobileapp, branch codex/index-plus-key. The full implementation and commit history are here; no DeskLink checkout or patch application is needed.

On Windows, run scripts/Build-IndexPlusKey.ps1 with -AndroidSdk, -Java21 and -Java17 pointing to your installed toolchains (or set ANDROID_HOME, JAVA_HOME and JAVA17_HOME). Provide local androidApp/src/google-services.json initialization configuration. The script builds and runs the 24 focused host tests, then writes artifacts/index-plus-key/Index-Plus-Key.apk. A reachable version tag is required; this branch retains 1.12.0.2. Build outputs, private initialization config and credentials are not committed.

See validation.md for the historical device checks, research.md for the original integration research, and ../custom-ai-endpoints.md for current endpoint behavior. Paths in historical validation entries refer to the development workspace before this fork was separated.

## Wireless key permission setup

Open Index settings > Oneplus integration > Key setup. The guided flow matches DeskLink: enable Developer options and Wireless debugging on Wi-Fi, start pairing, open Pair device with pairing code, and enter the six digits through the Index setup notification while the Android dialog stays open. Return to Key setup and grant the permission. Then use Prepare Index and Enable key in Oneplus integration. Android settings and pairing prompts still require your interaction; no computer is needed on Android 11 or later.

Setup requests notification permission (and local-network permission on Android 17+) when needed. If OxygenOS blocks the grant, follow the System optimization guidance in the wizard. Restore that setting and turn off Wireless debugging after setup. Setup pairs only with this phone and executes only the READ_LOGS grant for the running app package and Android user. Pairing credentials stay in private no-backup storage; codes are not persisted. Connections close after each action or timeout. Dependency license notices are packaged under assets/licenses/plus-key-setup.
