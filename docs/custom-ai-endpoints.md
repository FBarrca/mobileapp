# Custom AI endpoints (Android)

## LLM transcription cleanup

Open Index settings > Oneplus integration > Transcription cleanup. Enable Clean up with LLM, enter intended spellings under Custom words for cleanup, and Save cleanup settings. Cleanup defaults off. It uses the configured LLM URL, model and key independently of the agent route switch. Saving either endpoint preserves cleanup settings.

For custom speech recordings, the sequence is speech recognition, a separate text-only LLM editing request, then the Index agent. The editing request has no tools and asks the model to preserve meaning, language, numbers, dates and negations while fixing likely recognition errors using custom words. The cleaned text becomes the saved transcript and agent input; there is no separate raw-transcript history. Cleanup adds one LLM request and latency. Failed, empty or truncated cleanup results stop processing with an error; disable cleanup or correct the endpoint and retry. Typed chat and standard speech engines are unaffected.

The former Whisper dictionary field and multipart spelling prompt have been removed. Older saved vocabulary is ignored without resetting endpoint credentials. Custom words now belong to the separate LLM cleanup settings, stored with the encrypted endpoint configuration.

In Index settings, open Agent Model or Speech Engine and choose Custom endpoint. Edit Endpoint base URL, Model, and the masked API key directly in the picker, then tap Save custom endpoint. Each service is saved independently. Selecting a standard engine disables only that custom route and preserves its fields. The separate Custom AI endpoints screen also provides connection tests and a copy-key shortcut.

The LLM and speech services each have an independent enable switch, base URL, model and optional bearer API key. The fields default to https://api.groq.com/openai/v1, qwen/qwen3.8-27b (LLM), and whisper-large-v3-turbo (speech). Keys are blank in a new installation. Both switches default off so merely opening settings does not send data.

Enter your key, use Copy LLM API key to speech if appropriate, enable the desired services and save. Test LLM sends a short prompt; Test speech sends half a second of silent WAV audio. These tests use the entered fields, including unsaved edits. They make real requests and may consume provider quota. Re-run Prepare Index before enabling the Plus Key after restarting the app. When both custom services are enabled, preparation does not load or download official models.

The base URL includes the provider's API prefix, not the full operation path. Requests append /chat/completions and /audio/transcriptions. Speech uploads PCM16 mono WAV in multipart form data, with model and response_format=json. Language is sent when explicitly selected. The LLM uses non-streaming chat completions and function tools; tool responses feed back into up to three tool rounds. Your LLM must support OpenAI-compatible function calling.

Enabled custom services take precedence for new Index recording operations. Custom LLM also handles standard Index chat and MCP sandbox chat. Dedicated official Search mode is unchanged. Disabled custom services use existing Index settings. The app does not silently fall back to an official service when a selected custom endpoint fails. Existing backup and sync settings are unchanged.

Endpoint credentials are AES-GCM encrypted with an Android Keystore key in noBackupFilesDir. They are not included in APKs, source, backups, or diagnostics. Redirects are not followed, preventing bearer credentials from being forwarded to a different endpoint. HTTPS is recommended; HTTP is supported for trusted local development servers and sends credentials/audio in cleartext. Provider error responses are not logged; the UI reports actionable HTTP status errors.

## Validation

- Android debug build succeeds; five endpoint unit tests and six Plus Key gesture tests pass.
- Device instrumentation uses tools/custom-endpoints-fixture.py through adb reverse tcp:8766 tcp:8766. It verifies encrypted settings persistence, real multipart WAV transport, model IDs, recording queue routing, a non-forced Index note tool result, and safe authentication errors.
- On 2026-09-13, Groq's authenticated model list included both requested defaults, and live LLM and speech connection tests passed from OnePlus CPH2747.
- Test credentials and local fixture configuration are restored afterward. No real API key is committed.

Fixture: run python tools/custom-endpoints-fixture.py, forward the port using adb reverse, then run the CustomEndpointsDeviceTest class in the Android test APK. The fixture only listens on 127.0.0.1 and accepts a dummy fixture-key bearer token.

API references: https://console.groq.com/docs/speech-to-text and https://console.groq.com/docs/tool-use/overview.

The live configured-recording instrumentation test also passed against Groq with a generated spoken note. It verifies both custom model labels and a non-forced Index note tool result. Run it only when intentional: it uses the saved endpoints and may consume provider quota. Set the instrumentation argument liveEndpoints=true and provide PCM16 mono 16kHz speech as cache/endpoint-live-test.pcm in the app sandbox. Use adb push followed by run-as cp and verify the byte count; piping raw PCM through an adb shell can truncate binary data. Without the argument, this test is skipped.

Index settings also contains Prepare Index and Enable key / Disable key controls under OnePlus Plus Key, with live listener status. Prepare after a process restart, then enable while the app is visible. Microphone consent is requested when needed; Permission setup and help explains the one-time log permission.

Open **Index settings > Oneplus integration** for the grouped Plus Key controls, expandable LLM and speech endpoint editors (URL, model and masked API key), independent custom-route switches, and endpoint connection-test settings.
