# Validation record

Date: 2026-09-13. Device: OnePlus CPH2747, Android 16 / OxygenOS, owner user 0, wireless ADB.

## Build and installation

- Final build: core-build-08.log, Gradle 9.6.1, JDK 21 runtime/JDK 17 compiler, SDK 37, NDK 28.2.13676358.
- Six PlusKeyGesturesTest tests passed, no failures/errors/skips. Covers quick taps, hold/release, duplicates, cancellation, timeout, stale and delayed logs.
- APK: artifacts/index-plus-key/Index-Plus-Key-1.12.0.2.apk.
- SHA256: BF901BF3B302663D780BB2011D7183224685FF2EA39805319C87A1585C764A9A.
- Installed package coredevices.coreapp.pluskey, version 1.12.0.2, alongside stock. Custom-app data survived upgrades.
- Granted microphone, notification and READ_LOGS permissions for user 0.
- Verified Enable is disabled until local model preparation succeeds in the final build.

## End-to-end device verification

- Owner physically held/released the Plus Key for multiple recordings.
- Captured PCM16 mono at 16 kHz; submitted through Core's LocalAudioRecording queue with the long gesture.
- Android audio diagnostics show capture stopping on release. Final-build log at 21:23:02 shows capture stopped, then exactly one queue submission for that file.
- On-device Parakeet transcribed recordings. The earlier oranges test transcribed correctly.
- Fixed Windows packaging: APK contains the real 13,732,623-byte model ZIP with 254 entries instead of a 46-byte symlink target. Initial fallback notes were not counted as agent verification.
- Device log confirmed Cactus model initialization. Conversation records then showed language_model_used=cactus-needle-pebble-ft, an assistant builtin_note.create_note call, a successful tool response with is_forced_tool=0, and a feed item referencing that call.
- Final-build recordings 9-11 completed; English notes were created successfully. Recording 11 matched its transcription in the created note.
- Custom app's Index feed visibly displayed new items. No ring or PC connection was needed.

## Limits of validation

- Spanish reminder requests were transcribed but the upstream agent incorrectly interpreted them as shopping-list items. English note creation worked.
- Physical quick-tap, mid-capture Disable and locked-screen recording tests were not completed. Gesture logic is unit tested; no device verification is claimed for these cases.
- Only this OnePlus/OxygenOS log format was tested. The listener infers transitions from vendor logs and cannot suppress the key's assigned system action.
- No Google sign-in, watch pairing, long-running battery behavior, reboot persistence or iOS build validation was performed. Reopen setup and prepare/enable after process termination.
- Local inference uses the phone; Core's existing synchronization may still upload recordings/items. This is not an offline-only or no-upload build.

## Custom endpoints update (2026-09-13)

Index settings and the Plus Key setup screen now include Custom AI endpoints. LLM and speech each have independent enable, base URL, model, and API key fields. Requested defaults are qwen/qwen3.8-27b and whisper-large-v3-turbo at https://api.groq.com/openai/v1. The supplied key was entered only into the device configuration and live test requests; it is not in source or the APK.

The latest APK SHA256 is EE213278F6A21BD49A9025CA55C36DFEC4C60C20DD906DCDB231188BC2740F03 (core-endpoints-final.log). Eleven host tests passed. Two device instrumentation tests verified the custom recording route, encrypted settings persistence, and authentication errors against a loopback fixture. Database recording 12 has the expected custom speech/LLM labels, a non-forced note tool result, and a created Index item.

Live Groq model discovery accepted both exact requested model IDs. The phone's LLM and speech Test buttons both succeeded against Groq. Both routes remain enabled after APK update/process restart. A physical-key live-Groq recording check is pending.

Live validation completed: the final APK SHA256 is D885B34CA56415C69063537F7055B51A798F54EEF61C296CA1A152D74163A671 (core-endpoints-live-build.log). The two fixture device tests passed and the opt-in live Groq recording test passed after correcting a truncated ADB test-audio transfer. Recording 17 transcribed the generated spoken note through whisper-large-v3-turbo, ran qwen/qwen3.8-27b, executed a non-forced create-note tool and saved the resulting item. Actual user recordings also completed using the custom models. No additional physical test is required for endpoint validation.

## Inline Index settings controls (2026-09-13)

- Agent Model and Speech Engine offer Custom endpoint with editable base URL, model, masked API key and Save custom endpoint. Each route saves independently and choosing a standard mode preserves its custom credentials.
- OnePlus Plus Key controls now appear directly in Index settings: Prepare Index, Enable key, Disable key, live status and permission help.
- Latest APK built and installed successfully on OnePlus CPH2747. Five endpoint and six gesture host tests passed. Selector persistence instrumentation previously passed (1 test), including independent toggles and credential preservation.
- Device UI verified the LLM inline fields and saved confirmation. Prepare, Enable and Disable were exercised directly in Index settings; the listener was re-enabled and showed Ready — hold the Plus Key. Speech uses the same editor component; its inline form was not independently visually verified in this final pass.
- Requested model IDs and saved encrypted credentials retained. No additional live provider test was needed for this UI change. Patch reverse-apply and whitespace checks passed; patch contains no real API key or private Firebase configuration.
- Latest APK SHA256: 0671FA7E211BA0BE64BD9095ADF24BEC95BE3105A07B4366D9FE31BB95345774

## Oneplus integration section (2026-09-13)

Replaced the standalone top-level controls with an Oneplus integration button. Its scrollable sheet groups the key controls, expandable LLM and speech editors, custom-route switches, and connection-test settings. Built and installed on the OnePlus; screenshot verified the open section, both endpoint buttons, Disable key, and Ready status. The focused host test build passed. APK SHA256: 9586AD4227204C125BC8DB2E8284A7277E823C771BE9B9D49FBC5BC604777534. Exported patch reverse check and whitespace check passed.

## Speech custom dictionary (2026-09-13)

Added Custom dictionary to the shared speech editor and native endpoint form. New optional vocabulary data migrates old settings without resetting keys. Normalized non-empty words are sent as the multipart prompt field; clearing the dictionary omits it. API reference: https://console.groq.com/docs/speech-to-text.

Build and install succeeded. Seven endpoint and six gesture host tests passed, including legacy settings decoding, vocabulary serialization, blank prompt omission and separator normalization. Device recording pipeline instrumentation passed against the loopback fixture, which required the expected prompt and WAV multipart fields. The test restored the previous encrypted endpoint settings. The fixture was stopped and the ADB reverse removed. The Custom dictionary label was verified in the Oneplus integration speech editor, and the key was re-enabled. No live provider request was needed for validation.

APK SHA256: 0836772C143AC168A50184C0D8FD88E4BE0858C3747CAE4447B9A898330F4E79. Exported patch reverse-apply and whitespace checks passed.

## LLM transcription cleanup replaces Whisper dictionary (2026-09-13)

Removed the speech dictionary UI, model field, and multipart prompt. Legacy dictionary data is ignored while preserving endpoint credentials. Added opt-in Transcription cleanup under Oneplus integration, with an enable switch and custom words saved independently. Custom speech now optionally performs a separate text-only LLM request before emitting the transcript to Index. It uses the configured LLM endpoint/model/key; no tools are sent. Invalid, empty or truncated responses stop processing. The edited text is stored and passed to the agent.

Android builds and install passed. Eight endpoint and six gesture host tests passed. Device fixture pipeline test passed and checked raw speech output -> cleanup request with custom words -> corrected transcript in agent messages. A live configured LLM cleanup request passed, retaining the test's name and date reference. Existing endpoint configuration restored after fixture testing. Test server stopped and ADB reverse removed. Transcription cleanup entry verified on device; final enable/save interaction was not completed because the device view changed during navigation. Cleanup remains opt-in.

APK SHA256: AE3B3836A6CC57AB1E8EC6F23789557DA226B21A4A1B4D4EA005B1C37C79789D. Updated source patch reverse check and whitespace check passed.

## Remove temporary UI (2026-09-13)

Removed IndexPlusKeyActivity and CustomEndpointsActivity, their manifest registrations, extra launcher entry, duplicate endpoint link, and standalone permission-help link. Native Oneplus integration keeps key controls, endpoint editors and LLM cleanup. Missing log permission is reported inline. The key notification now targets MainActivity's Index deep link.

Build and 14 focused host tests passed. Updated APK installed successfully; Android launcher query reports only MainActivity for the custom package. Source reference search found no remaining references to the removed activities or endpoint entry component. Saved configuration was not reset. Full patch reverse-apply and whitespace checks passed.

APK SHA256: 46B241A4172C2EBF958A61352D844A135D8621287F00E78F24C33EF4B3E9309D.

## Skip device and account onboarding (2026-09-13)

Added Skip setup - use Index on this phone on every onboarding stage. It enables Index, marks initial onboarding complete, and persists a preference to start on the Index tab without a ring. Native feature permissions remain requested when needed. Build and 14 focused tests passed; APK installed through ADB. Device opened Index and a force-stop/normal relaunch returned directly to Index feed. User confirmed the skip works. Patch checks passed.

APK SHA256: 237D70E0D437FD9A3843B2D5DA73B6F5AA50CD8164C6248389063593D3F34465.

## Native wireless key setup (2026-09-13)

Ported DeskLink's guided wireless-debugging wizard, local ADB discovery, temporary setup service and notification code entry into Oneplus integration > Key setup. The grant command targets the running app package and Android user. Android settings remain user-controlled. Pairing code is not persisted; setup identity is private/no-backup; connections are loopback-only and bounded by timeout. Service is non-exported. Added required dependencies and verbatim license notices.

Build and 24 host tests passed: 14 existing endpoint/gesture tests plus 10 setup input/address/tunnel tests, including current-package command validation, shell syntax rejection, stale/foreign discovery filtering and stalled-connection timeout. APK installed through ADB. Device UI verified Key setup and the existing-grant Ready screen. Fresh wireless pairing was not repeated because this installation already has READ_LOGS. No existing permission or provider credential was reset. Full patch reverse check passed; source whitespace check passed excluding verbatim upstream license whitespace.

APK SHA256: E6F57FF1BE7909817D8AC215B7259F558DCABB99544902E2413DA028887C7D4E.

## Standalone Pebble fork (2026-09-13)

All Index source commits and this documentation now live in https://github.com/FBarrca/mobileapp on default branch codex/index-plus-key, with coredevices/mobileapp retained as upstream. Local checkout: C:/Users/Fran/GitHub/pebble-mobileapp. Index integration files were removed from DeskLink's current tree without altering its PC-audio work. The build helper now runs from this repository and accepts explicit SDK/JDK paths.

Built successfully from the standalone checkout and passed all 24 focused tests. APK: artifacts/index-plus-key/Index-Plus-Key.apk, SHA256 2994FD3C70C03650153E1D0DA5EF3EC944C0059D4ECF22E9500E78A4B57AFF7B. SDKs and JDKs remain installed in the existing shared tools location. No API key or private Firebase initialization config was pushed. The prior APK and leftover local build caches were preserved under ignored artifacts.
