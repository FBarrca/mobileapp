# Custom AI endpoints (Android)

First launch offers Skip setup - use Index on this phone on every onboarding stage. It completes initial onboarding without a device or account, enables Index and selects Index as the starting tab on later launches.

Open Index settings > Oneplus integration. Configure LLM endpoint and Configure speech endpoint provide the route switches, base URL, model and masked API key fields. Save each endpoint independently. The Agent Model and Speech Engine custom pickers use the same editors. Defaults are https://api.groq.com/openai/v1, qwen/qwen3.8-27b for LLM and whisper-large-v3-turbo for speech. Existing saved model choices and credentials are preserved.

## OnePlus key

Use Prepare Index, then Enable key while the app is visible. Preparation validates custom endpoints and loads local models only for services without a custom endpoint. Disable key cancels unfinished capture. The key notification opens the main Index app. After a process restart, repeat preparation and enablement. Microphone consent is requested when needed. As an alternative to Key setup, a computer can grant log permission with: adb shell pm grant --user 0 coredevices.coreapp.pluskey android.permission.READ_LOGS.

The temporary standalone setup activity, extra launcher entry, duplicate endpoint activity and connection-test buttons have been removed. All user configuration lives in native Index settings.

## LLM transcription cleanup

Under Oneplus integration > Transcription cleanup, enable Clean up with LLM, enter intended spellings in Custom words for cleanup, and Save cleanup settings. Cleanup defaults off and uses the configured LLM URL, model and key independently of the agent route switch.

Custom speech recordings run speech recognition, a separate text-only LLM editing request, then the Index agent. Cleanup has no tools and asks the model to preserve meaning, language, numbers, dates and negations. The edited transcript is saved and passed to the agent; there is no separate raw-transcript history. Cleanup adds one request and latency. Invalid, empty or truncated results stop processing; disable cleanup or fix the endpoint and retry. Typed chat and standard speech engines are unaffected.

The former Whisper spelling-hint dictionary was removed; older vocabulary fields are ignored without resetting credentials.

## Transport and storage

Requests append /chat/completions and /audio/transcriptions to the base URL. Speech uploads PCM16 mono WAV with model and response_format=json, plus language when selected. The agent uses OpenAI-compatible function calling with up to three tool rounds. Dedicated official Search and existing backup/sync behavior are unchanged. Custom endpoint failures do not silently fall back to an official service.

Credentials use AES-GCM with Android Keystore in noBackupFilesDir. They are excluded from APKs, source and backups. Redirects are disabled and provider error bodies are not logged. Use HTTPS; HTTP is available for trusted local development.

## Validation

Eight endpoint and six gesture host tests cover configuration migration, parsing and gestures. CustomEndpointsDeviceTest tests the full recording pipeline against tools/custom-endpoints-fixture.py via adb reverse tcp:8766 tcp:8766. The fixture checks speech upload, the separate cleanup request, and corrected text reaching the agent before a note tool call. Settings are restored afterward. Live configured cleanup and recording tests are opt-in using liveEndpoints=true; they incur provider requests. The live recording test additionally requires PCM16 mono 16kHz cache/endpoint-live-test.pcm.

## Wireless key permission setup

Open Index settings > Oneplus integration > Key setup. The guided flow matches DeskLink: enable Developer options and Wireless debugging on Wi-Fi, start pairing, open Pair device with pairing code, and enter the six digits through the Index setup notification while the Android dialog stays open. Return to Key setup and grant the permission. Then use Prepare Index and Enable key in Oneplus integration. Android settings and pairing prompts still require your interaction; no computer is needed on Android 11 or later.

Setup requests notification permission (and local-network permission on Android 17+) when needed. If OxygenOS blocks the grant, follow the System optimization guidance in the wizard. Restore that setting and turn off Wireless debugging after setup. Setup pairs only with this phone and executes only the READ_LOGS grant for the running app package and Android user. Pairing credentials stay in private no-backup storage; codes are not persisted. Connections close after each action or timeout. Dependency license notices are packaged under assets/licenses/plus-key-setup.
