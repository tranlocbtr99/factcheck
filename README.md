# LTM FactCheck 3.0.1

Android app for evidence-backed fact checking, screen OCR, audio transcription and follow-up questions. Kotlin + Compose + Room. No keyword verdict database, seeded history, or fabricated fallback answers.

## Run

- JDK 21, Android SDK 36.1, build-tools 36.0.0.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
- Debug uses the standard local Android debug keystore; release requires `KEYSTORE_PATH`, `KEY_ALIAS`, `STORE_PASSWORD`, `KEY_PASSWORD`.
- `applicationId=vn.ltm.factcheck`, versionCode 30001. An update over 2.1.0 requires its original signing certificate. The previously published repository used a different application ID; this branch intentionally matches the existing APK.

## Connect a real AI service

Two supported modes, configured in **Cài đặt**:

1. **Personal Gemini key**: encrypted in Android Keystore, direct HTTPS calls to Google. A key saved in 2.1.0 is reused on the same device after a signed update. No personal API key is compiled into the APK. The model is configurable (default `gemini-3.8-flash`); availability and search grounding depend on the account/model. Health tests key/model access; only an actual check tests generation and search quota. 100 tasks/day local cap, not a provider billing cap.
2. **Private backend**: Cloudflare Worker in `backend/`. Configure an HTTPS URL and a device access token. Model key stays on the server. This is recommended for centrally managed use. The included token scheme is for a private installation, not public multi-user account management.

Without valid credentials the app shows configuration or service errors. It does not invent a verdict. Model output remains fallible; source existence and citation linkage do not prove that a source supports every inference. Open original sources before relying on a result.

## Features and boundaries

- Text entry, Android share text/image, local OCR with editable text, audio file transcription (8 MB maximum).
- Google Search grounding -> grounded source segments -> structured assessment -> validation. Original input is preserved. Confidence percentages are not displayed.
- Record-scoped follow-up chat, persisted history and preferences, source links and provider search attribution.
- Single screen-capture session, cancellation, rotation handling, explicit one-shot checks by default; continuous sampling is opt-in.
- Internal audio capture on Android 10+ where the source app permits it; explicit microphone alternative. 12-second bounded audio chunks. Capture pauses while a chunk is transcribed/checked, so live audio is sampled, not a complete continuous transcript. Use file import for complete short audio. Source applications can block capture; the app does not bypass protection.
- Screen and raw live audio are not written to disk by this app. Text and audio are sent to the configured provider as needed; provider retention terms still apply. History persists locally; Android backup/device transfer is disabled for app data.
- iOS-inspired Liquid Silver UI with light/dark mode, in-app backdrop blur on API 31+, highlighted glass navigation, reduced transparency/motion options. External overlays use a legible translucent panel; they do not blur another app's content.
- Legacy `reports.db` is imported read-only without deleting the original; Room v1 -> v2 migration adds provider attribution. Imported records are marked as old snapshots and can be checked again.

## Validation

`cd backend && npm ci && npm test && npx wrangler deploy --dry-run`

Android tests cover negation preservation, no verdict without a configured service, settings persistence, bounded WAV headers, history serialization and legacy import. Backend tests cover authentication, quota, grounded source linkage, unsupported verdict downgrade, provider failures and the two-pass request contract with a mocked provider. Mocked tests are not live accuracy validation.

See [deployment](docs/DEPLOYMENT.md) and [design](docs/DESIGN.md). No external AI service is activated by building the APK.

## Continue development

Read [STATUS.md](STATUS.md) and [TASKS.md](TASKS.md) first. Progress-only requests do not build the APK or rerun all tests. Implement one related batch, run checks for the changed behavior, then update the two files with the source commit and evidence. Documentation-only pushes skip the build workflow; newer runs on the same branch cancel older runs.
