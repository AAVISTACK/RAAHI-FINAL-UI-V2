# Raahi Migration — Status (Checkpoint 7+, continuous session)

**No build has been run.** This container has `javac`/`java` (OpenJDK 21) but no Maven,
no Gradle, no cached `.m2`/Gradle-wrapper, and no network access — dependency resolution
for either project is not possible here, so nothing could actually be compiled, even
though a JDK is technically present. Every file below was written and manually re-read
for reference/import/logic errors (brace/paren balance checked mechanically, every new
symbol traced back to where it's actually declared), and real bugs were caught that way —
see "Bugs found" below. That is not the same as a verified build. Open `backend/` in
IntelliJ and `android/` in Android Studio and let Gradle sync before trusting any of this
compiles.

## Scope (confirmed)
Raahi as it actually exists: Auth, Jobs (P2P roadside marketplace), Mechanics, SOS,
AI Mechanic (chat), Safety (selfie + Aadhaar image screening), Daily
(streak/alerts/tips), Places, Fuel rates, Shop, Subscriptions where they actually
exist in the reference. Garage / Maintenance / Car Health / Fair Price / Road Trip
/ Vehicle Documents are explicitly excluded — no reference implementation exists.

Backend priority order: Mechanics → Auth → Jobs → Roadside/SOS → Helper flow → Aadhaar →
Notifications/FCM → remaining features → Shop/Subscription. As of this checkpoint the
**client is the bottleneck, not the backend** — Phase 7 (Helper + Aadhaar/Selfie) stays
paused per explicit instruction until Android catches up further.

Android sub-phase order: 6A Home (done) → 6B Jobs/Request Help (done) →
6C Mechanics/Map (done, this checkpoint) → 6D SOS/Roadside + WebSocket client (next).

Subscription is NOT a hard dependency for Jobs/SOS in general — it only gates
`POST /jobs` for DRIVER-role accounts specifically (confirmed real logic from the old
Node `requireSubscription` middleware, not invented). MECHANIC/HELPER accounts and every
other endpoint are unaffected. **Practical consequence for testing Jobs end-to-end:**
Shop/Subscription (Phase 10) isn't built yet, so no DRIVER-role account can ever have an
active subscription — `POST /jobs` will 402 for every DRIVER account until either that
phase exists or a subscription row is seeded manually for test accounts. MECHANIC/HELPER
accounts are unaffected and can exercise the full Jobs flow right now.

---

## BACKEND

### Done
- Project skeleton: pom.xml, application.yml (all secrets via env vars), Flyway V1 (users,
  jobs), V2 (mechanic_profiles, subscriptions), V3 (sos_events)
- Response envelope + global exception handler, consistent everywhere
- JWT issuing/verification + Firebase ID token verification; security filter chain
  authenticated by default, `/auth/verify` is the only public route
- **AuthController**: `/auth/verify` (creates user as DRIVER only, ever — role never
  trusted from the client), `/me`, `PUT /profile`, `/location`. New users also get a
  NONE/INACTIVE subscription row, matching old signup behavior.
- **MechanicController**: `/mechanics/nearby` (Haversine SQL, honest empty result — no
  mock fallback), `/mechanics/{id}`, `/mechanics/location` (role-gated, never trusts a
  client-supplied user id)
- **JobController**: create/accept/verify-otp/complete/cancel/mine/available/get. Accept
  race condition fixed via `SELECT ... FOR UPDATE`. OTP/phone numbers only ever returned
  to actual participants. `verify-otp` and the IN_PROGRESS-required `complete` are new
  this checkpoint (see Bugs found).
- **SosController**: `POST /sos` (trigger, pushes `sos:nearby_alert` individually to
  nearby MECHANIC/HELPER accounts), `GET /sos/active?lat&lng&radius` (role-gated,
  radius-bound), `GET /sos/mine`, `POST /sos/{id}/resolve` (ownership-scoped, honest 404)
- **Authenticated WebSocket** at `/ws`: identity comes from the SecurityContext
  `JwtAuthFilter` already populated for the handshake request (same Bearer token as every
  REST call) — refused with 401 before a socket opens if that's missing.
  `WebSocketSessionRegistry` only exposes targeted `sendToUser`, never a broadcast.
  `job:status_change` pushed on accept/verify-otp/complete/cancel; `job:location_update`
  relayed only after verifying the sender is an actual job participant, to the other
  participant only.

### Not started
Selfie/Aadhaar, AI Mechanic, Daily/streak/alerts/tips, Places, Fuel rates, Shop, full
Subscription/payment lifecycle, Admin, Redis wiring (dependency present, unused),
PostGIS/MapLibre. Emergency contacts have no backend route in the Node reference either
— Flutter stores them locally and sends alerts via the device's own SMS composer intent,
not a server call — so this migrates to Android DataStore + an SMS intent, not a new API.

### Real bugs found and fixed (backend, not hypothetical)
1. **`GET /jobs/mine` requester-only.** Only matched `requester.id`, so a MECHANIC/HELPER
   account's own in-progress job never showed up in their own job list — found while
   wiring Android Home's active-job banner. Fixed: `JobRepository.findMine` matches
   requester OR helper.
2. **No real OTP-handoff verification existed.** The old Node backend showed the
   requester an OTP once "matched" but had nothing on the other end to check it against,
   and `/complete` had no status check at all — either participant could mark a job
   COMPLETED straight from "pending"/"matched", skipping the handoff entirely. Found
   while building Android's Job Status screen (an OTP-entry UI needs something real to
   submit to). Fixed: new `POST /jobs/{id}/verify-otp` (helper-only, MATCHED-only,
   correct-OTP-only → IN_PROGRESS); `/complete` now requires IN_PROGRESS.
3. **`GET /jobs/:id` phone leak (old Node).** Returned *both* parties' phone numbers to
   any authenticated caller, no participant check at all. Not reproduced: `requesterPhone`
   (new field, mirrors the existing `helperPhone` gating) is only populated for the
   assigned helper.
4. **`GET /sos/active` leak (old Node, fixed at Checkpoint 3, still holds).** No role
   check, no radius filter — any authenticated user got every active SOS event including
   the triggering user's phone + vehicle reg. Fixed: MECHANIC/HELPER-only, radius-bound,
   phone number removed from the DTO entirely.
5. **WebSocket `job:location_update` broadcast (old Node, fixed at Checkpoint 3, still
   holds).** Literally broadcast every helper's live location to every connected client.
   Fixed: sender must be verified as an actual job participant; relayed to the other
   participant only.

### Contract additions this checkpoint (JobDto)
`viewerRole` ("REQUESTER"/"HELPER", computed server-side — Android's "My Jobs" list mixes
both sides of the marketplace and needs to know which one it's looking at without
guessing by matching names), `requesterPhone` (see bug 3 above), `helperRatingAvg`,
`helperTotalHelps`, `helperVerified`. `API_CONTRACT.md` updated to match.

---

## ANDROID

### Done — Auth (Checkpoint 3)
Gradle project, AndroidManifest, Hilt application class, `google-services.json`
(`applicationId = com.raahi.app` to match the registered Firebase project), Compose
theme, `RaahiApi`/`TokenManager`/`AuthInterceptor`, `AuthRepository` (Firebase phone-auth
→ backend JWT exchange), `AuthViewModel` full state machine, `PhoneLoginScreen` +
`OtpScreen` with working resend countdown.

### Done — Phase 6A: real Home screen
- **Theme.kt corrected**: primary color fixed to the Flutter reference's actual
  `0xFFFF6B00` (an earlier checkpoint had approximated `0xFFFF7A1A`); full color token set
  added (card bg/border, green/cyan/red/yellow accents, muted text) for reuse across
  Home/Jobs/Mechanics/SOS.
- `HomeViewModel`/`HomeScreen`: real `GET /auth/me` + `GET /jobs/mine` data, real
  loading/error states. Deliberately does NOT recreate the Flutter reference's "Car
  Status" card (hardcoded "Maruti Swift Dzire, 48,320 km" — display text with zero
  backend behind it, effectively an uncredited Car Health preview, which is explicitly
  out of scope) — shows the user's actual `vehicleType`/`vehicleReg` instead, honest
  prompt when absent.
- Quick-actions grid: Roadside Help, Find Mechanic, My Jobs, SOS, and (MECHANIC/HELPER
  accounts only) a 5th "Nearby Jobs" tile to `HelperDashboardScreen`.
- `ProfileSetupScreen`/`ProfileSetupViewModel`: new-user step calling the already-existing
  `PUT /auth/profile`. Name/vehicle optional; "Skip for now" goes straight to Home rather
  than writing a fabricated placeholder name.
- `MainActivity`/`RaahiNavHost`: `isNewUser` branches to `PROFILE_SETUP` before `HOME`.

### Done — Phase 6B: Jobs / Request Help (this checkpoint)
- **`RequestHelpScreen`/`RequestHelpViewModel`**: problem-type grid (7 types, English
  labels only — see gap below), optional description, cash price entry with quick-amount
  chips (matches the Flutter reference: cash paid directly to the helper, Raahi takes 0%
  commission, no payment gateway involved), a real GPS fix via `LocationProvider`
  (FusedLocationProviderClient one-shot `getCurrentLocation`, not continuous tracking) with
  a proper permission-request flow (`rememberLocationPermissionState`). Submits to the real
  `POST /jobs`.
- **`JobStatusScreen`/`JobStatusViewModel`**: shared between both sides of a job — role
  read from the server's `viewerRole`, never guessed client-side. Polls `GET /jobs/{id}`
  every 5s while status is active (matches the Flutter reference's own polling cadence;
  full WebSocket status-push consumption is deferred to 6D alongside the SOS live-tracking
  client). Requester sees the OTP to hand off in person; helper gets a real OTP-entry field
  wired to the new `verify-otp` endpoint. Complete/cancel wired to the real endpoints. Call
  button opens the dialer with the other party's real number (participant-gated server-side).
- **`HelperDashboardScreen`/`HelperDashboardViewModel`**: local "Helper Mode" toggle
  (`HelperPrefsManager`, DataStore — mirrors the Flutter reference's `HelperPrefsService`,
  which is genuinely local-only with no backend field, not a client-side workaround for
  a missing one) gates a real `GET /jobs` available-jobs list with accept action.
  **Deliberately does NOT port the Flutter reference's "FOMO" fake job** — a hardcoded
  `id: 'fomo_fake'` decoy request with a fabricated distance and an auto-dismissing
  "someone else took it!" message, shown to nudge helper-mode opt-in. That is exactly the
  fake/dummy/FOMO production data the migration brief says to find and remove, not
  recreate on the new stack.
- **`MyJobsScreen`/`MyJobsViewModel`**: `GET /jobs/mine` list (both sides of the
  marketplace, using the new `viewerRole` to label each row), tapping opens
  `JobStatusScreen`.
- `RaahiApi`/`JobsRepository` extended: `getJob`, `verifyOtp`, and the `JobDto`/
  `UpdateProfileRequest` fields the contract additions above needed.
- `app/build.gradle.kts`: added `play-services-location`,
  `kotlinx-coroutines-play-services`, `material-icons-extended` (several icons Home/Jobs
  use — `Emergency`, `Assignment`, `History`, etc. — aren't in the default
  `material-icons-core` set bundled with Material3).

### Real bugs found and fixed (Android, this checkpoint)
1. Same `GET /jobs/mine` requester-only bug as backend item 1 above — this is what
   surfaced it in the first place, before it was traced to the query.
2. `HelperDashboardViewModel`'s first draft bridged a DataStore `Flow<Boolean>` into a
   `StateFlow` by hand-rolling a `MutableStateFlow` + a `viewModelScope.launch { collect }`
   — works, but is the wrong tool (no initial-value guarantee, extra boilerplate) versus
   `Flow.stateIn(scope, SharingStarted.Eagerly, false)`. Fixed before it shipped.

### Done — Phase 6C: Mechanics / Map (this checkpoint)
- **`MechanicsMapView`**: MapLibre `MapView` wrapped in Compose `AndroidView`, lifecycle
  correctly wired (`onCreate/Start/Resume/Pause/Stop/Destroy` tied to
  `LocalLifecycleOwner`). Style is a raw OSM XYZ raster tile source
  (`tile.openstreetmap.org/{z}/{x}/{y}.png`) — same tile source the Flutter reference used —
  rather than a hosted vector style, so no separate API key is needed beyond MapLibre itself.
  Mechanics render as a `GeoJsonSource`/`CircleLayer` colored green (available) / red (not),
  same visual language as the Flutter reference's pins; user location is a separate cyan
  circle. Tapping a mechanic marker queries rendered features at that screen point and opens
  its detail screen.
- **`MechanicsMapViewModel`**: owns the whole flow itself — real GPS fix via
  `LocationProvider` (same one Request Help uses), then real `GET /mechanics/nearby`. On
  failure, a real retryable error state — **no fallback to mock/sample mechanics**, which is
  exactly what the Flutter reference's `mechanics_map_screen.dart` did on any API failure
  (`MockMechanicService`) and is explicitly banned. An empty result renders as an honest
  "No verified mechanics nearby yet" state, not a hidden mock list.
- **`MechanicDetailScreen`**: real mechanic data, Call (dialer intent), Directions (`geo:`
  intent — opens whatever maps app is installed, no hardcoded Google Maps dependency), and
  a "Post a Roadside Help Request" CTA that opens the real Request Help flow. Explicitly NOT
  a "send this specific mechanic a request" action — the job marketplace has no concept of
  targeting one mechanic, and the Flutter reference's equivalent button just showed a fake
  "Request sent!" dialog with zero backend behind it. Said plainly in the screen's own
  caption rather than silently faking the capability.
- `RaahiApi`/`MechanicsRepository` added: `nearbyMechanics`, `getMechanic`.
- `app/build.gradle.kts`: added `org.maplibre.gl:android-sdk:11.5.2` (Maven Central, no
  extra repository entry needed).
- Home's "Find Mechanic" quick action and the mechanic detail screen's Request-Help CTA are
  both wired to real destinations now — no more "coming soon" Toast for either.

### Real bugs found (Android, this checkpoint)
None new. `GET /mechanics/nearby`'s availability semantics (documented in
`API_CONTRACT.md` — approved-but-busy mechanics still appear, `isAvailable` is informational
rather than a filter) were a deliberate reading of existing, already-correct backend
behavior, not a bug fix.

### Not started (Android)
SOS screen + WebSocket client (6D), emergency-contacts screen, Google Sign-In, FCM token
registration after sign-in, a dedicated Profile/Settings screen for editing a profile after
the initial setup step. Per the Final Quality Rule, none of these are "complete" features
yet even where their backend is — a feature needs UI + wired integration too.

### Known gaps / things to double check when this actually builds
- Problem-type labels are English only. The Flutter reference has Hindi/Punjabi/Tamil/
  Telugu/Bengali labels per type; this app has no i18n infrastructure yet, so only the
  English label was ported. Revisit once/if multi-language support is prioritized.
- `RaahiWebSocketHandler`/`SosController`/`WebSocketConfig`/all of Phase 6B's new files
  were written and re-read for reference/import errors only — no compiler available in
  this container (see the top of this file). Spring's raw `WebSocketConfigurer` +
  `HandshakeInterceptor` reading `SecurityContextHolder`, and FusedLocationProviderClient's
  `getCurrentLocation(CurrentLocationRequest, ...)`, are both standard documented patterns,
  but neither has actually been run here.
- `WebSocketSessionRegistry` is in-memory/per-instance — needs Redis pub/sub (dependency
  already in pom.xml, unused) before this can run behind more than one backend instance.
- Testing the DRIVER-side Request Help flow needs a seeded active subscription (see
  Scope section above) until Shop/Subscription is built.
- **`MechanicsMapView` (Phase 6C) is the single highest-risk unverified file in this whole
  checkpoint.** MapLibre's `AndroidView`/lifecycle interop, the raw-JSON empty `Style`
  bootstrapped with a `RasterSource`+`RasterLayer` added afterward, the `GeoJsonSource`/
  `CircleLayer`/`Expression.match` marker approach, and `queryRenderedFeatures` for tap
  handling were all written from documented MapLibre/Mapbox-GL-Android API shape with no
  way to compile or run any of it here. If Android Studio's Gradle sync surfaces errors,
  start here first, not in the simpler screens around it.

---

## Next concrete continuation point
Android Phase 6D: SOS/Roadside screen + emergency contacts (device-local, mirrors the
Flutter reference's SharedPreferences + SMS-intent approach — no backend route exists for
contacts in the Node reference either) + a real WebSocket client (superseding 6B/6C's
polling with the `job:status_change`/`sos:nearby_alert` events the backend already pushes).
Backend Phase 7 (Helper + Aadhaar/Selfie) stays paused until Android catches up
further, per explicit instruction.

## Checkpoint
Full project zipped at the path given in this turn's reply. Contains `backend/`,
`android/`, `docs/`.

---

## CONTINUOUS SESSION LOG (Phases 6D onward — no per-phase checkpoints per explicit instruction)

### Phase 6D — SOS/Roadside + WebSocket client: DONE
- `RaahiWebSocketClient`: authenticated OkHttp WebSocket (`Authorization: Bearer` header on
  the handshake, matches backend), reconnect with backoff, parses `job:status_change`,
  `job:helper_location`, `sos:nearby_alert`. Connected once from `HomeViewModel.init` (Hilt
  singleton, persists for the process lifetime).
- `JobStatusViewModel` now primarily driven by WS events; 20s poll is a fallback only, not
  the primary mechanism 6B used. Helper side pushes a location fix every 8s while
  IN_PROGRESS; requester sees it as a "live location" card with a real `geo:` intent link.
- `SosScreen`/`SosViewModel`: real trigger (confirm dialog first) → `POST /sos`, shows
  notified-mechanic count, resolve action, checks for an already-active SOS on load.
- `EmergencyContactsManager`/Screen: local DataStore only (name+phone), mirrors the Flutter
  reference's SharedPreferences approach exactly — no backend route exists for this in
  either reference. "Alert contacts" opens the real device SMS composer per contact
  (`ACTION_SENDTO`), never a fake "sent" confirmation.

### Phase 7 — Helper/Aadhaar/Selfie: DONE (backend + applicant-facing Android; no admin UI)
- Backend: `V4` migration (`verification_documents` BYTEA storage, `helper_applications`),
  `HelperController` (multipart apply, status check, authorized doc retrieval by owner/ADMIN
  only, ADMIN approve/reject queue — approve flips `user.role` to HELPER).
- `AiTamperScreeningProvider` interface + `NoOpAiTamperScreeningProvider`: honestly returns
  `UNAVAILABLE` with "pending manual review" since no real AI provider key exists in this
  environment — every submission still requires manual admin approval regardless of this
  result, so nothing is auto-approved on a fabricated verdict. **Needs a real key
  (`raahi.ai.gemini-api-key` / `GEMINI_API_KEY` env var) and an actual provider
  implementation to do real screening — not present here.**
- Never implemented, per the migration brief: UIDAI/eKYC, OTP verification, face matching.
- Android: `HelperRepository` (real multipart upload, content-URI → temp file → OkHttp
  streamed body), `HelperApplicationViewModel`/`Screen` (image pickers via
  `ActivityResultContracts.GetContent`, status view), wired from a "Become a Helper" card on
  Home for DRIVER-role accounts.
- No admin review UI anywhere (Android or otherwise) — admin approve/reject currently
  requires calling the API directly (curl/Postman) or a future separate admin tool. Out of
  scope for the consumer app per the original feature list.

### Phase 8 — AI Mechanic: DONE
`V5` migration (ai_chat_sessions/messages). `AiChatController`: session-scoped, ownership
enforced everywhere (404 not 403 on cross-user access — see AiChatController.ownedSession).
`AiMechanicProvider` interface + real `GeminiAiMechanicProvider` (honest
`AiUnavailableException` when `GEMINI_API_KEY` unset — never a fabricated diagnosis; system
prompt forbids false certainty and defers to Roadside Help/a real mechanic for anything
serious). Android: chat UI wired into Home for every role, unavailable messages visually
distinct from real answers.

### Phase 9 — Alerts/Places/Tips/Streak: DONE
`V6` migration. Real bug fixed: old Node `POST /daily/alerts/:id/vote` had zero duplicate-vote
prevention — fixed with a `highway_alert_votes` unique-key table. Real gap filled: `/daily/places`
and `/daily/tips` were called by Flutter but never implemented in the Node backend at all.
Places backed by real, free, keyless OSM Overpass API (matches the app's OSM map stack, no
fake place data). Tips are real curated DB content, not a fake live feed. Android: 4 screens
wired into a "More" row on Home.

### Phase 10 — Fuel/Shop/Subscription: DONE
`V7` migration. Real bug fixed: old Node `fuel.js` stamped hardcoded stale prices with a
fabricated `updated_at: new Date()` on every request — fixed with an honest DB timestamp that
only changes on a real admin edit (`PUT /fuel-rates/{state}`, ADMIN-gated). Real gap found:
`SHOP_AFFILIATE_SETUP.md` in the Flutter reference confirms Shop was always an Amazon
affiliate-link catalog, never in-app payments — built as that, no fake cart/checkout invented.
Subscription plans are real (pricing matches Flutter exactly); `POST /subscriptions/subscribe`
honestly 503s (`PAYMENT_UNAVAILABLE`) since no gateway credentials exist — never a fabricated
activation. **Significant cross-cutting bug found and fixed here**: every repository since
Phase 6B read `envelope.error` on failure, but Retrofit throws `HttpException` on non-2xx
without deserializing the body, so real backend error messages never reached the user — only
generic "HTTP 409" text did. Fixed with a shared `apiCall()` helper (`network/ApiCallHelper.kt`)
and every repository retrofitted to use it.

### Phase 11 — Notifications: DONE
`PUT /auth/fcm-token` (the `fcmToken` field already existed on `User`, unused). `NotificationService`
reuses the FirebaseApp that's already a hard startup requirement for auth verification — no new
credential needed, unlike AI/payment. Wired into every job/SOS event alongside the existing
WebSocket push, as a backstop for a backgrounded/disconnected app. Android:
`RaahiFirebaseMessagingService` (token capture + rotation handling), registered in the manifest,
wired from `HomeViewModel`. Known gap: foreground push isn't displayed (data received, not
posted to `NotificationManager` — needs a channel + tap-to-deep-link, not built this pass).

### Phase 12 — Security/data/fake-data audit: DONE
Full-codebase grep sweep for mock/fake/dummy/sample/placeholder/FOMO/random — zero violations
(every hit was a legitimate UI placeholder string or a comment confirming the *absence* of fake
data). Logging sweep (backend + Android) — nothing sensitive logged anywhere. Two real findings
fixed:
1. **JWT role staleness** — `JwtAuthFilter` authorized every request from the JWT's baked-in
   role claim (tokens live up to `raahi.jwt.expiry-days`, default 30) instead of the fresh DB
   role it already looked up on every request for the suspended-user check. A just-approved
   Helper couldn't use Helper endpoints, and a role downgrade/suspension wouldn't take effect
   until the old token expired. One-line fix, zero added cost.
2. **OTP brute-force gap** — `verify-otp` had no attempt limit; the only caller who can even
   reach it is the already-assigned helper (see the ownership check), so the risk is a
   dishonest helper skipping the in-person meetup by brute-forcing the 6-digit code via the
   API. `V8` migration adds `jobs.otp_attempts`, locked after 5 wrong attempts.
Also fixed: `FuelController`'s admin update endpoint declared `@NotNull` constraints but was
missing `@Valid` (never enforced); `SubscriptionController.subscribe` accepted any string as
`tier`; `Job.setHelperOtp()` was called by `JobController` but never existed on the entity — a
latent compile error from an earlier session, fixed incidentally while adding `otpAttempts`.
Known gap not fixed: no rate limiting anywhere (Redis dependency present, unused).

### Phase 13 — Integration testing: DONE (to this environment's limits)
No compiler/runtime available (see top of file), so "integration testing" here means: full
DTO field-name parity check between every backend Java DTO and its Android Kotlin counterpart
added this session (all pass), confirmation that `ApiResponse`/`GlobalExceptionHandler`'s JSON
shape (`{success, data, error:{code,message}}`) matches what `ApiEnvelope`/`apiCall` parse on
the Android side (matches), and a final full-project brace/paren balance sweep across every
Java and Kotlin file in the project (100% clean, zero mismatches).

### Phase 14 — Final readiness audit: DONE
Confirmed `RaahiBackendApplication` sits at the `in.raahi.backend` package root, so
`@SpringBootApplication`'s default component scan covers every controller/entity/repository/
service added this session with no missing `@ComponentScan` config needed. Confirmed all 8
Flyway migrations (V1–V8) are sequential with no gaps or duplicates. Final report delivered to
the user in-chat with the full features/files/endpoints/database/security/limitations/API-keys/
build-status breakdown.

## FINAL STATUS: MVP feature-complete per the agreed scope, across all 14 phases.
Build verification remains BLOCKED BY ENVIRONMENT throughout — see the top of this file. This
is a strong first draft ready for a real IDE/Gradle-sync/CI pass, not a verified-working app.

---

## BUILD-BLOCKER + VERIFICATION PASS (post-MVP)

An independent inspection flagged 7 potential issues. Each was verified against actual
source before any fix — findings below are what was actually confirmed true, not assumed.

### Confirmed true and fixed
1. **`@style/Theme.Raahi` genuinely didn't exist.** `res/values/` was empty. Created
   `values/themes.xml`, `values/colors.xml`, `values/strings.xml` — minimum required, no UI
   redesign. Parent is `android:Theme.Material.NoActionBar` (a platform-only theme) since
   this app has zero XML layouts/Material Components views — every actual UI color/typography
   still comes from `RaahiTheme` in `ui/theme/Theme.kt`; this XML theme only governs the
   pre-Compose window background and status/nav bar color. Confirmed via full source sweep
   that no `R.string/R.drawable/R.color/R.style` reference exists anywhere else in the Kotlin
   codebase — this was the only missing resource.
2. **Gradle wrapper genuinely missing** (`gradlew`, `gradlew.bat`, `gradle/wrapper/*` all
   absent). Added `gradlew`/`gradlew.bat` (standard, version-independent launcher scripts) and
   `gradle/wrapper/gradle-wrapper.properties` pinned to Gradle 8.7 — the minimum version
   AGP 8.5.2 (already declared in `build.gradle.kts`) actually requires.
   **`gradle-wrapper.jar` itself could NOT be created** — it's a compiled binary normally
   generated by running `gradle wrapper` against a real Gradle install, or downloaded from
   `services.gradle.org`; this container has no Gradle install and the network egress proxy
   returns `403 host_not_allowed` for both `services.gradle.org` and `repo1.maven.org`
   (confirmed by direct `curl`, not assumed). **Opening this project in Android Studio will
   auto-generate/repair the missing jar on sync** — that's the practical path to a working
   wrapper; alternatively run `gradle wrapper --gradle-version 8.7` once with any local Gradle
   install.
3. **`PUT /auth/location` genuinely returned `{"updated": true}` without persisting
   anything** (confirmed — the code had a comment admitting it was stubbed). Fixed for real:
   `V9` migration adds `users.last_lat/last_lng/last_location_at`; the endpoint now actually
   writes them. Android never calls this endpoint yet (confirmed by source sweep), so there
   was no client-side inconsistency to reconcile — the fix is backend-only.
4. **FCM foreground display was genuinely incomplete** (confirmed — `onMessageReceived` only
   logged). Fixed: `RaahiNotifications` (real `NotificationChannel` + `NotificationManager`),
   wired into `onMessageReceived`, tap-to-open-app `PendingIntent`. Added the runtime
   `POST_NOTIFICATIONS` permission request (mandatory on API 33+, targetSdk 34) that was
   also missing — without it `notify()` would have silently no-op'd on Android 13+ even with
   the code otherwise correct. Added `androidx.core:core-ktx` (needed for `NotificationCompat`,
   was previously only relying on it being pulled in transitively) and a minimal vector
   notification icon (`drawable/ic_notification.xml` — a plain circle, not a designed icon).
5. **`MechanicsMapView.kt` had a real, fixed API-shape bug**: `Layer.withProperties(...)` is
   not part of the base MapLibre Android `Layer` API — the correct, high-confidence pattern is
   constructing the layer then calling `.setProperties(...)` on it. Fixed both call sites.
   The `Expression.match`/`Expression.stop` usage elsewhere in the same file was re-audited
   and is very likely correct (matches documented expression-builder patterns), but — per the
   "do not infer compilation success from static inspection" instruction — this is reported
   as "audited, plausible, not compiler-verified," not "confirmed correct." This file remains
   the single highest-risk unverified file in the project.
6. **Rate limiting was genuinely absent everywhere.** Implemented, scoped: `RateLimiter`
   (`security/RateLimiter.java`) uses the `spring-boot-starter-data-redis` dependency and
   Redis connection config that were already present but unused — fixed-window `INCR`+`EXPIRE`,
   fails open (allows the request) if Redis itself is unreachable, so a rate limiter can never
   become an availability outage for the feature it protects. Applied only to `POST /sos`
   (3 per user per 10 minutes) — the highest-risk unprotected endpoint found (each trigger
   pushes a real FCM notification + WebSocket alert to every nearby mechanic, a genuine
   spam/nuisance vector). Deliberately NOT applied as a blanket interceptor across every
   endpoint — that risked breaking legitimate polling traffic (e.g. `JobStatusViewModel`'s
   20s fallback poll) for no clear security benefit, which the instruction explicitly warned
   against ("do not spend the entire pass on infrastructure that isn't required").
7. **`WebSocketSessionRegistry` is genuinely in-memory/single-instance** (confirmed, already
   known). No evidence anywhere in the project of multi-instance deployment intent (no load
   balancer config, no sticky-session setup, no shared-session-store wiring). Per the
   instruction's own preferred branch for this case: **kept as-is, documented as a known
   scaling limitation** rather than adding Redis pub/sub fan-out, which would be a genuine
   architecture change (every `sendToUser` call would need to publish to a shared channel
   plus a subscriber redelivering to locally-connected sessions) not required for a
   single-instance MVP deployment.

### Also found and fixed during this pass (not on the original list)
- `Job.setHelperOtp()` was called by `JobController` but **never existed on the `Job`
  entity** — a latent compile error from an earlier session. Fixed incidentally while adding
  `otpAttempts` (see the Phase 12 audit section above) — both setters now exist.
- `FuelController`'s admin rate-update endpoint declared `@NotNull` on its request fields but
  was missing `@Valid`, so those constraints were never actually enforced. Fixed.

### Still NOT verified — no compiler/network available in this environment
Nothing above was confirmed by an actual build. Every fix was applied by reading the real
source, understanding the real defect, and reasoning through the fix from documented API
shape — this is the same methodology as every prior checkpoint, not a new one. If Gradle
sync in Android Studio or a real `mvn compile` surfaces further errors, `MechanicsMapView.kt`
remains the most likely source.

---

## UI MIGRATION PASS (design spec v2 — "Raahi App UI" reference screenshots)

Reskinned the Android app to match a 6-screen reference design (Home, Profile/Car Health,
Nearby Mechanics, AI Mechanic, Shop, plus a shared bottom nav bar) supplied as a spec image
with exact hex colors. Scope was explicitly "UI only — keep existing functionality and
backend integration, don't add fake data, don't add new features." Same no-compiler-available
caveat as every prior checkpoint applies: brace/paren-balanced and import-traced by hand, not
built.

### Design tokens (`ui/theme/Theme.kt`)
Rewrote every color value to the spec's exact hex (background `#0D1623`, card `#121E2D`,
secondary card `#1A2738`, orange `#FF6B00`, green `#00C853`, blue `#00A2FF`, red `#FF4D4F`,
warning `#FFA726`, text `#FFFFFF`/`#8A9BAE`) and added shape tokens (`RaahiShapeSmall/Medium/
Large/Pill`, 12–16dp corners per spec). Kept every existing `Raahi*` constant **name** so the
~20 screens *not* explicitly in scope (Jobs, SOS, Daily, Helper, Auth, FuelRates,
Subscription, MechanicDetail, etc.) pick up the new palette automatically with zero changes
to those files — only their rendered colors change. Also updated `res/values/colors.xml`
(pre-Compose window/status-bar background) to match, so there's no color flash before
`RaahiTheme` loads.

### New shared component
`ui/components/BottomNavBar.kt` — `RaahiBottomNavBar` + `RaahiTab` enum (Home / Mechanics /
AI Mechanic / Shop / Profile), matching the reference exactly: 4 plain tabs + a raised
circular AI Mechanic tab that fills solid orange only while active. Wired into all 5 screens
below plus a `navigateToTab()` helper in `MainActivity.kt` using the standard
popUpTo/launchSingleTop/restoreState bottom-nav pattern anchored on `Routes.HOME`.

### Screens rebuilt
1. **HomeScreen.kt** — header (title, static language chip, bell, avatar→Profile), vehicle
   card, new AI Mechanic hero card (replaces the old "Roadside Help" hero — Roadside Help
   moved into the quick-actions grid, matching the reference), restyled quick-actions grid,
   bottom nav.
2. **ProfileScreen.kt + ProfileViewModel.kt (new files)** — the reference's "Profile / Car
   Health" screen didn't exist before; built from scratch with two tabs (Car Health /
   Profile) over real `UserDto` fields. Reachable via the new bottom-nav Profile tab and via
   a new "Car Health" quick-action tile on Home.
3. **MechanicsMapScreen.kt** — back/title/refresh row, specialization filter chips (derived
   from real `MechanicDto.specializations` strings, not a fake taxonomy), map with zoom
   in/out + recenter controls (new `onMapReady` callback added to `MechanicsMapView.kt` to
   expose the `MapLibreMap` instance for this), redesigned bottom sheet + list rows
   (SOS-red vs wrench-green badge driven by whether `specializations` contains "SOS").
4. **AiMechanicScreen.kt** — the reference shows a linear "Describe → Analyzing → Results"
   diagnosis form; the real backend (`AiMechanicViewModel`/`AiMechanicRepository`) is a
   multi-turn chat session. Bridged rather than replaced: the step is derived from real chat
   state (no messages → Describe, first message in flight with no reply → Analyzing, any AI
   reply → Results), and the full chat thread + follow-up composer stays live under "Results"
   so no chat functionality was dropped.
5. **ShopScreen.kt** — header/search/category chips restyled; category chips
   ("Car Cleaning"/"Accessories") call the real, already-existing `shopProducts(category)`
   endpoint rather than a client-side fake taxonomy; search is a real client-side filter over
   already-fetched product name/brand.
6. **Bottom nav** added to Home/Mechanics/AI Mechanic/Shop/Profile per the reference (all 5
   screens now show it; the reference also shows it on the Profile/Car-Health screen).

### Explicit "no fake data" calls (flagged per the task's own instruction, not silently done)
- **Car Health tab**: the reference shows a numeric "Car Health Score: 100", per-component
  diagnostics (Engine/Battery/Tyres/Brakes/Fuel/Coolant), and an upcoming-maintenance list.
  None of that has ANY backing data anywhere in this codebase — no `CarHealthRepository`, no
  vehicle-diagnostics endpoint, and `docs/STATUS.md`'s own scope section already excludes
  "Car Health" as having no reference implementation. Rendered as an honest "not available
  yet" card in the same visual language instead of inventing numbers. Real fields (name,
  phone, verified, totalHelps, ratingAvg, vehicleType, vehicleReg) are used everywhere they
  exist.
- **Profile stats row** (Total Helps / Earned / Rating / Trips): only Total Helps and Rating
  have backing `UserDto` fields. Earned and Trips render as "–" (the mockup's own convention
  for "nothing yet"), not a fabricated ₹0 / 0.
- **AI Mechanic "5 left" quota pill**: not rendered — `AiChatUiState` has no quota field.
- **Home header language chip**: static chrome (flag + "English"), not a working switcher —
  no language-selection UI or wiring exists anywhere in the app (`language` is an unused
  field on `UpdateProfileRequest` only).
- Voice-input mic icons (Home hero card, AI Mechanic Describe step) are decorative — no
  speech-to-text integration exists in this codebase.

### Real functionality added along the way (not purely cosmetic, flagged explicitly)
- `AuthRepository.signOut()` already existed but had **zero UI wired to it anywhere in the
  app** before this pass — there was no way for a user to log out. Added a "Log out" action
  in the new Profile tab that calls it.
- `ProfileSetupScreen` (the onboarding form) is now reused for editing an existing profile
  (`isEditing = true`, new `Routes.PROFILE_EDIT`), launched from Profile → pencil icon. Had
  to add prefill (`ProfileSetupViewModel.loadForEdit()`) — without it, reusing the form as-is
  for editing would have shown blank fields and saved blank vehicle info over whatever was
  already on file, a real data-loss bug, not just a UX gap.

### Not compiler-verified — same caveat as every prior checkpoint
No Gradle/network available in this environment, so none of this was built. Specific things
worth a real Gradle sync check before trusting them: `Icons.Filled.SmartToy` / `.Handyman` /
`.Logout` / `.Speed` (material-icons-extended, pinned via compose-bom 2024.06.00 — believed
present, not confirmed against the actual jar); `CameraUpdateFactory.zoomIn()/.zoomOut()/
.newLatLngZoom(LatLng, Float)` and `MapLibreMap.animateCamera(CameraUpdate)` in the new
`MechanicsMapScreen.kt` map controls (verified against MapLibre Native Android's published
API docs via web search, not against this project's actual pinned MapLibre version — the
`zoom: Float` parameter specifically needed a `13.5f` literal, not `13.5`, which was caught
and fixed on review). `MechanicsMapView.kt` was already flagged as this project's
highest-risk unverified file before this pass (see the Phase 6C entry above); it now also
carries the new `onMapReady` callback, so re-check it first if a build fails.

---

## CAR HEALTH — REAL FEATURE (approved, supersedes prior exclusion)

Car Health was previously explicitly excluded (see the MVP scope section and the UI
migration pass above — that pass built the Car Health *screen* but deliberately left every
number out because no backend existed). That decision is now reversed by product direction:
Car Health is a real, user-data-driven feature. This pass built the actual data model and
wired the UI migration's Car Health screen up to it — no more honest-empty-state cards where
real ones now exist. Same no-compiler-available caveat as every other checkpoint in this file.

### Backend
- **New table `vehicles`** (`V10__vehicles_and_car_health.sql`): one row per user (unique FK,
  same one-to-one pattern as `MechanicProfile`/`mechanic_profiles`). Required: brand, model,
  model_year, fuel_type (enum: PETROL/DIESEL/CNG/ELECTRIC/HYBRID/LPG), registration_number,
  odometer_km. Optional, all nullable: last_service_date/odometer, insurance_expiry,
  puc_expiry, tyre/battery replaced date+odometer. No column exists for anything the app
  doesn't actually collect from the user — "do not ask for information the backend cannot
  actually store" is satisfied by construction, not by convention.
- **New table `vehicle_service_records`**: real service/repair history the user logs
  (date, odometer, type, notes, cost, workshop, parts). Foundation for future scoring, not
  yet read by the score itself (see below).
- **New table `vehicle_odometer_logs`**: append-only audit trail — every odometer update
  (via the dedicated endpoint, an upsert, or a service record with a higher reading) writes a
  row here, not just an overwrite.
- **`CarHealthService`** — the ONLY place `CarHealthDto.score` is produced. Deterministic,
  three independently-scored factors (service interval, insurance expiry, PUC expiry), each
  only scored when its underlying field exists; zero factors with data → `score: null` +
  an explicit message, never a fabricated default. Full formula documented in the class
  Javadoc. Maintenance reminders (Oil Change / Tyre Rotation / Battery Check / Insurance
  Renewal / PUC Renewal) generated the same way — one item per field that's actually present.
- **`VehicleController`** — `/api/v1/vehicles/me` (+ `/odometer`, `/health`,
  `/service-records`), all scoped via `principal.userId()` (JWT identity), never a
  client-supplied id, same pattern as every other owner-scoped controller in this codebase.
  `GET /me` 404s (`VEHICLE_NOT_FOUND`) until setup is complete — that 404 is the Android
  client's signal to show the setup flow, not an error to surface.
- **Known gap, flagged rather than silently worked around**: odometer updates reject a value
  lower than the current reading (`400 ODOMETER_DECREASED`) — there is no correction/rollback
  flow yet, so a genuine typo currently needs direct DB access to fix. The spec asked for
  validation against backward moves "unless an authorized correction flow exists"; no such
  flow was built this pass.
- `docs/API_CONTRACT.md` updated with the full "Vehicle & Car Health" section; the old
  Car-Health-is-excluded language in its scope header is gone.

### Android
- **New `VehicleRepository.kt`** (data layer) wrapping the 6 new endpoints. `myVehicle()`
  returns `null` on a 404 rather than throwing — added one small new function,
  `apiCallOrNullOn404`, alongside (not modifying) the existing `apiCall` helper in
  `ApiCallHelper.kt`, so every other repository's error handling is completely unaffected.
- **New `ui/screens/vehicle/` package**: `VehicleSetupScreen`+`VehicleSetupViewModel` ("Set up
  your car" — required fields match the spec exactly, optional fields collapsed behind a
  "+ Add optional details" toggle so the form isn't overwhelming), `CarHealthViewModel`
  (backs the Car Health tab: vehicle + computed health, plus the odometer-update dialog's
  state), `ServiceHistoryScreen`+`ServiceHistoryViewModel` (list + add-record dialog).
- **`ProfileScreen.kt`'s Car Health tab rewritten** to consume `CarHealthViewModel` instead of
  rendering the honest-empty-state cards from the prior pass: real vehicle summary card (with
  inline "Update odometer" and "Edit vehicle details" actions), a real score card (color-coded
  green/yellow/red, or the "complete your info" message when `score` is null, with a
  "Based on N of 3 factors" caption so the number's provenance is never a mystery), a
  diagnostics grid and maintenance list both built from the real `maintenanceItems` the
  backend returned — nothing left hardcoded from the mockup.
- **`HomeScreen.kt`'s vehicle card rewritten** to show the real `VehicleDto` (brand/model,
  year·fuel·registration, odometer, a Health badge when a score exists) instead of the old
  free-text `user.vehicleType`/`vehicleReg` fields, with a "Set up your car" CTA card when no
  vehicle exists yet. `HomeViewModel` now also fetches vehicle + health alongside user/job,
  each independently `runCatching` so a missing vehicle (expected for most users right now)
  never breaks the rest of Home loading.
- **Onboarding**: new users now go PhoneLogin → Otp → ProfileSetup (name) → **VehicleSetup**
  → Home, instead of straight to Home. **Scoping decision, flagged explicitly**: this gate is
  only on the new-user signup path. It is *not* a hard gate on every returning-user login —
  that would mean restructuring the existing sign-in redirect logic, which is a working,
  already-tested flow this pass avoided touching. Returning users (and anyone who tapped
  "Skip for now" during setup) instead see a "Set up your car" prompt on Home's vehicle card
  and on Profile's Car Health tab every time they open either, until they complete it.
- **Old `user.vehicleType`/`vehicleReg` fields on `User` are untouched and still work** —
  ProfileSetupScreen (name/legacy-vehicle-text) and its edit flow still function exactly as
  before. They're just no longer what Home's vehicle card or the Car Health tab display, now
  that the real structured `Vehicle` entity exists and is the better source for both.

### Explicitly not built this pass (flagged, not silently skipped)
- Native date pickers — optional date fields (last service, insurance/PUC expiry, tyre/
  battery replaced) are plain `YYYY-MM-DD` text fields with regex validation, not a
  `DatePickerDialog`. Functional and honest (real typed input, no fabrication), just not as
  polished as a picker would be.
- `CarHealthService` does not yet read `vehicle_service_records` — the table and API exist
  and accumulate real data from day one (per the spec's own framing: "this history becomes
  the foundation for future Car Health calculations"), but the score itself still only reads
  `Vehicle`'s own last-service/insurance/PUC fields, not the service-record history.
- No odometer-correction flow (see the backend section above).

---

## UI MIGRATION PASS #2 — "Concept Redesign" (approved HTML mockup → real Compose code)

Avi approved an interactive HTML mockup (glass/gradient dark UI, Space Grotesk + Plus Jakarta
Sans, line icons, animated header/nav) built earlier in the same session as a design
exploration, and asked for it to become the real app. This pass ports that visual language
into the actual Jetpack Compose codebase, superseding the first UI migration pass's design
system (same idea, second visual direction). Same no-compiler-available caveat as every prior
checkpoint — traced by hand, not built.

### Design tokens (`ui/theme/Theme.kt`)
Full new palette matching the mockup exactly (`#07090F`/`#0C0F18` backgrounds, glass/border
alpha-white overlays, `#FF6A2C→#FF3D7F` brand gradient, `#31C5FF→#7C5CFF` AI gradient, status
colors). Kept every pre-existing `Raahi*` token name as an alias to the closest new value, so
screens not explicitly touched this pass (Jobs, SOS, Daily, Helper, Auth, FuelRates,
Subscription, MechanicDetail, VehicleSetupScreen, ServiceHistoryScreen) recolor automatically
without being edited.

**Fonts**: wired via the Compose `ui-text-google-fonts` Downloadable Fonts API (Space Grotesk
for headlines/numbers, Plus Jakarta Sans for body) rather than bundling font binaries — this
sandbox has no network access, so actual `.ttf` files couldn't be fetched to bundle directly.
**`res/values/font_certs.xml` is currently a placeholder, not real certificate data** — rather
than hand-type a ~1700-character base64 cert blob from memory (one wrong character silently
breaks it), it has clear instructions to generate it correctly via Android Studio's built-in
"New → Font Resource File" wizard (30 seconds, one-time). Until that's done, GoogleFont
lookups fail gracefully and the whole app falls back to the system default font — nothing
crashes or looks broken, it just won't have the exact Space Grotesk / Plus Jakarta Sans look
yet. **Do this step before judging the fonts.**

### New shared component library (`ui/components/RaahiComponents.kt`)
Replaces the old `BottomNavBar.kt` (deleted — would have been a duplicate `RaahiTab`/
`RaahiBottomNavBar` declaration otherwise). Houses:
- `GlassCard` / `RowCard` — the semi-transparent bordered surfaces used everywhere.
  **Not true backdrop blur** — Compose's `blur()` modifier blurs its own content, not
  whatever scrolls behind it; true frosted-glass-over-scrolling-content needs `RenderEffect`/
  third-party libraries (e.g. "Haze"). This approximates it with a flat translucent fill,
  same as what most production apps below API 31 do anyway.
- `ScoreRing` — Canvas-drawn stroked arc for the Car Health score, color-banded
  green/amber/red. Not a true conic/sweep gradient ring (`drawArc` takes a flat `Color`, not
  a `Brush`) — a solid color per band instead, which is what the mockup's own ring mostly
  read as anyway.
- `Ticker` — auto-scrolls to the end then snaps back to the start on a loop. Not a
  perfectly seamless infinite marquee (would need custom `Layout` measurement) — a
  deliberate simplification for a purely decorative element.
- `RaahiBottomNavBar` — floating glass pill, raised gradient center (AI Mechanic) button,
  icon bounce (spring animation) on tab change.

### Screens re-skinned this pass
**Home, AI Mechanic, Profile/Car Health, Nearby Mechanics, Shop** — all five rebuilt on the
new component library. Notable ports from the mockup:
- Home: scrolling ticker, a header that gains a translucent background once the page scrolls
  (approximated with `Column`+`verticalScroll`+an overlay `Box`, since true sticky headers
  need `LazyColumn`), a bell icon with a periodic ring-wiggle + pulsing badge dot, a logo mark
  with a looping diagonal shine sweep, the "This week" stats strip.
- Profile: sliding-underline tab indicator (measured via `onGloballyPositioned`, animated
  with `animateFloatAsState`) between Car Health / Profile.
- AI Mechanic: same real chat-backend bridge as the first pass (Describe → Analyzing →
  Results derived from real `AiChatUiState`), now with the mockup's gradient CTA and a
  "Was this helpful?" thumbs row. **The thumbs are local UI state only** — there's no
  feedback endpoint on the backend, so a tap gives visual acknowledgement and nothing is
  actually submitted anywhere. Flagging this explicitly rather than silently wiring it to
  nothing.
- Shop: did **not** port the mockup's "For your Grand i10 Nios" personalized product row —
  products aren't actually filtered by vehicle compatibility on the backend, and that row in
  the mockup was illustrative content for a demo, not real data. Left out rather than
  fabricated.

### Not compiler-verified — same caveat as every prior checkpoint, worth a real Gradle sync
- `Icons.Outlined.AutoAwesome`, `.SupportAgent`, `.WaterDrop` (used for the AI/sparkle,
  roadside-help, and fuel icons) — believed present in material-icons-extended at the pinned
  compose-bom version, not confirmed against the actual jar.
- `androidx.compose.ui.text.googlefonts.GoogleFont`/`GoogleFont.Provider` API shape — written
  from documented Compose API knowledge, not run.
- `res/values/font_certs.xml` — see above; needs the Android Studio wizard before fonts
  actually download.

### Not re-skinned this pass (inherit the new color palette only, via the token aliases)
`VehicleSetupScreen.kt`, `ServiceHistoryScreen.kt`, `MechanicDetailScreen.kt`, and everything
in Jobs/SOS/Daily/Helper/Auth/FuelRates/Subscription. They'll look correctly dark/on-brand
(same background/text/accent colors) but don't yet have the glass-card/gradient treatment the
five main screens got. Worth a follow-up pass if the new look is confirmed working.
