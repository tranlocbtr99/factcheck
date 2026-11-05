# LTM FactCheck 3.1.1

Android app for evidence-backed fact checking, screen OCR, audio transcription and follow-up questions. Kotlin + Compose + Room. No keyword verdict database, seeded history, or fabricated fallback answers.

## Run

- JDK 21, Android SDK 36.1, build-tools 36.0.0.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
- Debug uses the standard local Android debug keystore; release requires `KEYSTORE_PATH`, `KEY_ALIAS`, `STORE_PASSWORD`, `KEY_PASSWORD`.
- `applicationId=vn.ltm.factcheck`, versionCode 30101. An update over 2.1.0 requires its original signing certificate. The previously published repository used a different application ID; this branch intentionally matches the existing APK.

## Connect a real AI service

Two supported modes, configured in **Cài đặt**:

1. **Personal Gemini key**: encrypted in Android Keystore, direct HTTPS calls to Google. A key saved in 2.1.0 is reused on the same device after a signed update. No personal API key is compiled into the APK. The model is configurable (default `gemini-3.8-flash`); availability and search grounding depend on the account/model. Health tests key/model access; only an actual check tests generation and search quota. 100 tasks/day local cap, not a provider billing cap.
2. **Private backend**: Cloudflare Worker in `backend/`. Configure an HTTPS URL and a device access token. Model key stays on the server. This is recommended for centrally managed use. The included token scheme is for a private installation, not public multi-user account management.

Without valid credentials the app shows configuration or service errors. It does not invent a verdict. Model output remains fallible; source existence and citation linkage do not prove that a source supports every inference. Open original sources before relying on a result.

## Features and boundaries

- Text entry, Android share text/image, local OCR with editable text, audio file transcription (8 MB maximum).
- One Gemini request with Google Search grounding and a structured verdict -> citation metadata validation. Original input is preserved. Confidence percentages are not displayed.
- Record-scoped follow-up chat, persisted history and preferences, source links and provider search attribution.
- Single screen-capture session, cancellation, rotation handling, continuous OCR every 3 seconds by default after starting a session; only changed text is sent. OCR continues while Gemini works; a conflated queue keeps only the newest waiting screen, with one screen verification in flight.
- Internal audio capture on Android 10+ where the source app permits it; explicit microphone alternative. 12-second bounded audio chunks. Capture pauses while a chunk is transcribed/checked, so live audio is sampled, not a complete continuous transcript. Use file import for complete short audio. Source applications can block capture; the app does not bypass protection.
- Screen and raw live audio are not written to disk by this app. Text and audio are sent to the configured provider as needed; provider retention terms still apply. History persists locally; Android backup/device transfer is disabled for app data.
- iOS-inspired Liquid Silver UI with light/dark mode, in-app backdrop blur on API 31+, highlighted glass navigation, reduced transparency/motion options. External overlays use a legible translucent panel; they do not blur another app's content.
- Legacy `reports.db` is imported read-only without deleting the original; Room v1 -> v2 migration adds provider attribution. Imported records are marked as old snapshots and can be checked again.

## Validation

`cd backend && npm ci && npm test && npx wrangler deploy --dry-run`

Android tests cover negation preservation, no verdict without a configured service, settings persistence, bounded WAV headers, history serialization and legacy import. Backend tests cover authentication, quota, grounded source linkage, unsupported verdict downgrade, provider failures and the single-call request contract with a mocked provider. Mocked tests are not live accuracy validation.

See [deployment](docs/DEPLOYMENT.md) and [design](docs/DESIGN.md). No external AI service is activated by building the APK.

## Continue development

Read [STATUS.md](STATUS.md) and [TASKS.md](TASKS.md) first. Progress-only requests do not build the APK or rerun all tests. Implement one related batch, run checks for the changed behavior, then update the two files with the source commit and evidence. Documentation-only pushes skip the build workflow; newer runs on the same branch cancel older runs.

## Continuous screen flow (3.1)

Start a screen session and grant Android screen-sharing permission. The start action enables continuous mode. Text is extracted locally (standalone interface labels are removed, facts/negations/numbers are retained) and sent to Gemini with Google Search enabled. One response supplies DUNG/SAI/CHUA_DU_BANG_CHUNG, explanation and citation metadata. Only HTTPS sources actually linked by the provider become source buttons; URLs invented in generated JSON are ignored. Open the source to assess the evidence.

The floating result displays both true and false results, a short explanation and up to two directly openable source links. OCR samples the newest screen independently of network latency; no request is made for unchanged text. At most one screen request is in flight and one newest screen waits. Earlier completed screens can be retained in history but do not overwrite a newer screen's visible result. Pause cancels screen verification; a failed request has a 30-second retry cooldown. Continuous sampling does not capture every transient frame or read video without subtitles. Audio capture/transcription remains a separate optional feature.

The combined Search + structured-output request requires a compatible Gemini model (default gemini-3.8-flash). This follows https://ai.google.dev/gemini-api/docs/generate-content/structured-output . Live account/model availability still requires a real check with credentials. One API request can involve multiple provider searches and is not a fixed-cost operation.

## Quota diagnostics (3.1.1)

Google API 429 can describe rate limits or quota exhaustion; the old generic message did not distinguish these. The app now reads structured QuotaFailure/RetryInfo and numeric Retry-After. Per-day/zero/unknown quotas and persistent access/model/request failures pause automatic sending. Known rate limits respect the provider delay; other transient failures back off (30, 60, 120, 240, 300 seconds). Explicit Resume permits another attempt. This cannot grant account quota. Open Settings → Xem hạn mức Gemini trong AI Studio, check the selected model/project, and retry after resolving the reported limit. Raw provider messages are not exposed.

The user does not want Google Fact Check Tools/Explorer. It is not a verification service or fallback used by this app. Its URLs are excluded from evidence and legacy detail links. Gemini directly evaluates extracted screen text with Search for original sources and returns its answer in the app. Search attribution supplied by the provider remains attribution, not a requirement to perform manual verification.
