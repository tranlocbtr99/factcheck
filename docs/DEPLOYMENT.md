# Private backend deployment

```sh
cd backend
npm ci
npx wrangler login
npx wrangler secret put GEMINI_API_KEY
npx wrangler secret put ACCESS_TOKEN
npm run deploy
```

Use an account-owned Gemini API key with access to the configured model and Google Search grounding. Use a long random ACCESS_TOKEN and enter it only in the app's connection settings. Do not commit `.dev.vars` or tokens. Set `GEMINI_MODEL` and `DAILY_LIMIT` in wrangler.jsonc. The Durable Object binding enforces a global daily reservation count before provider requests. Failed requests consume a reservation; one verify task makes one model request with Google Search and structured output, and grounding can incur additional cost. This is not a monetary cap; configure provider budget controls too.

The Worker returns explicit HTTP errors for missing configuration, invalid tokens, quota exhaustion and upstream failures. `/health` is public and only reports configuration readiness. Authenticated `/connection` checks server configuration and access token without making a paid model request. `/verify`, `/chat` and `/transcribe` reserve quota. Bodies are bounded before parsing. Request bodies and tokens are not logged by application code.

The deployment uses synchronous requests. Cancellation in Android cancels the HTTP call; a request already accepted by the provider may continue and incur cost. For a public product, replace the private bearer token with per-user authentication, per-user budgets, rotation/revocation, observability without content logging, and an async queue before opening registration.

After deployment: test with the actual provider, open each linked source, confirm search suggestions display, and test error/quota handling. Do not label the build operational until these pass with actual credentials.

## Release signing

Keep the existing certificate for `vn.ltm.factcheck`. The signing backup is intentionally excluded from this repository. Verify a new release certificate against 2.1.0 using `apksigner verify --print-certs`, then test `adb install -r` over the old APK with existing history. Never uninstall the old app as an upgrade step.
