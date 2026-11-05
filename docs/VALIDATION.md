# Validation — 3.0.1

- Android release build succeeded: `:app:assembleRelease`.
- Android unit/Robolectric/Compose checks: 14 tests passed, 0 failures. Covers original input negation, missing connection, history round trip, read-only import from the 2.x database, saved monitoring settings, session context reset, WAV headers and navigation/disabled-state behavior in light/dark themes.
- Android Lint: passed, 0 errors, 41 warnings (including resource/icon cleanup, deprecated APIs, convenience API suggestions and custom overlay accessibility).
- Worker: 16 Node tests passed, including a mocked two-pass provider exchange, linked source validation, unsupported verdict downgrade, authentication, global daily budget and upstream outage handling.
- Wrangler deployment bundle dry-run succeeded. No backend was deployed and no provider secrets were provisioned.
- APK package `vn.ltm.factcheck`, versionName `3.0.1`, versionCode `30001`, minSdk 24, targetSdk 36.
- Release signing certificate SHA-256: `603bef85b172df9d535a0ef70fe31f1e65356082fc971cbf2ee13caa689a495a`, identical to the supplied 2.1.0 APK. Keystore and passwords are excluded from source control.

Not verified: an actual paid/live provider request, factual accuracy on a labeled corpus, installation over 2.1.0 on a physical device, iQOO capture/permissions/background behavior, and internal audio capture from individual third-party apps. Emulator boot/visual capture was unavailable in this environment. Compose tests check displayed controls and navigation; they are not screenshot comparison tests.

Install as an update without uninstalling the old app. A compatible key saved by 2.1.0 is read on the same device. Otherwise configure a personal Gemini API key in Settings or deploy the private Worker. Test one manual factual claim and inspect its linked sources before enabling continuous capture.

## 3.0.1 evidence

- Recovered implementation commit: 2004b41; merge: 44c96e6; validation fixes: c814856.
- Reran `:app:testDebugUnitTest :app:lintDebug :app:assembleRelease`: BUILD SUCCESSFUL, 14 Android tests, no failures/errors; Lint 0 errors/41 warnings.
- Reran `node --test backend/test/worker.test.js`: 16 pass. `npm ci` and `wrangler deploy --dry-run`: successful.
- Added tests for thought filtering, strict verdict/explanation types, strict source IDs, invalid source URLs, no second call without sources, malformed upstream JSON, long chat context and invalid requests consuming no quota.
- APK SHA-256: b5e9f59d07e2ad7e7fde3585ec14b5bb5b4e604c7757c18065c1e4c5d9e86264. apksigner verification succeeded, with certificate identical to supplied 2.1.0. aapt confirmed vn.ltm.factcheck / 3.0.1 / 30001 / minSdk 24 / targetSdk 36.
- No attached adb device. Live model requests, physical upgrade, image-based visual review and third-party app capture remain unverified. The Worker has not been deployed.

## 3.1.0 — simplified continuous screen flow

Source commits: b2ae32b (continuous OCR + single Gemini request), f10a0fb (cached screen result), e2df70f (duplicate OCR presentation state). Version 3.1.0/30100, package vn.ltm.factcheck.

- Android testDebugUnitTest: 21 tests, 0 failures/errors. Added coverage for short lines, numbers, negation, interface noise, continuous defaults, single-response parsing, missing/invalid evidence and cached result counters. Prior history/settings/Compose tests remain included.
- Android lintDebug: 0 errors, 44 warnings. Signed assembleRelease succeeded; a final assembleRelease also rebuilds the presentation-state change from e2df70f.
- Backend: 16 tests pass, including exactly one provider request with Search + responseJsonSchema and rejecting model-invented URLs. Wrangler deploy --dry-run passed.
- Request contract follows https://ai.google.dev/gemini-api/docs/generate-content/structured-output (Gemini models supporting built-in tools with structured output). This documents the API contract; no live provider call was made.
- The screen service samples OCR independently while awaiting Gemini, serializes network verification and uses a conflated queue. Pending-screen changes, pause/resume, screen-sharing permissions, visual layout and battery behavior still need a physical-device test. Unit tests do not prove end-to-end continuous capture.
- 2.1.0 upgrade on a physical device, live Gemini accuracy, source relevance in real results, and audio capture remain unverified. The backend has not been deployed; private-backend installations must deploy the new Worker to get the one-request contract.

- APK LTMFactCheck-3.1.0.apk SHA-256: ed459c57dca4bca0510eb7730f02aac6a1a9d7374b8530465ed858ea88e4d703. Signature verified, certificate SHA-256 603bef85b172df9d535a0ef70fe31f1e65356082fc971cbf2ee13caa689a495a, identical to 2.1.0. Final assembleRelease: BUILD SUCCESSFUL.

## 3.1.1 — quota handling and excluded Fact Check Tools

- Local source commit c43bd18. Android testDebugUnitTest: 28 tests, 0 failures/errors. Backend: 20 tests pass; Wrangler dry-run passed.
- Tests cover daily/zero/unknown/rate-limit 429, RetryInfo rounding, Retry-After, persistent model/access failures, bounded exponential backoff, safe error forwarding and refusal to accept Fact Check Tools as evidence.
- User screenshot confirms screen OCR output and the 3.1.0 Google-429 message, but cannot identify the exact quota type because the old client discarded the error details. No live account quota has been inspected or changed.
- UI source filtering preserves original source indices in legacy records; original databases are not deleted or rewritten.
- Physical-device pause/resume and live quota diagnostics remain unverified until the new build is installed. No provider credentials are in source or APK.

- Final testDebugUnitTest/lintDebug/assembleRelease: BUILD SUCCESSFUL. Lint: 0 errors, 46 warnings. APK vn.ltm.factcheck 3.1.1/30101; SHA-256 54b99f3dae79ddaccefe11cb93d5bbd5d1c450893a3a4673d39030595a10cc32; apksigner passed, certificate matches 2.1.0 (603bef85b172df9d535a0ef70fe31f1e65356082fc971cbf2ee13caa689a495a).

## 3.2.0 — model-only binary judgement (pending validation)
User now requests DUNG/SAI with explanation and no Search/sources. Requests have no tools; new results carry assessmentMode=model_knowledge and a fixed external-verification disclaimer. Existing grounded tests cover only historical parsers, not the new evaluation path.

New tests cover no-tools request/schema, source-free binary results, stripping generated evidence/links, invalid output and an old-backend mode mismatch. Backend and UI tests are adjusted for the new contract.

Local execution environment is unavailable. No local tests, APK signing or live provider checks have run for 3.2.0. Follow CI for the new commit. Previous 28/20 passes and APK 3.1.1 do not validate these changes. Release signing remains blocked until the original environment/key is available.
