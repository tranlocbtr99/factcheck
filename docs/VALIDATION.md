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
