# Rebuild log

Target: one phone, iQOO Neo 6, Android 14 (Funtouch OS / OriginOS). No other devices matter.

IDs: `A` architecture, `B` bug from the audit of 2026-10-22, `U` UI. Status: `[ ]` open, `[~]` in progress, `[x]` done, `[-]` dropped.
Bugs are fixed inside the phase that rewrites their code, not patched in the old code first.

## Open questions

- What did "the repo code for 3P" mean?

## Decisions

- 2026-10-22 LLM: any OpenAI-compatible API (OpenAI, Groq for free models). Base URL, model and key set in the app.
- 2026-10-22 Blocks: "all the basic and cool ones". Catalog in `core/.../catalog/Blocks.kt`: 10 triggers, 6 conditions, 21 actions.
- 2026-10-22 Order change: the new data layer, handlers and runner are built next to the old code so the build stays green. U1 (design) moves ahead of the switch-over, so the old screens are replaced once instead of adapted and then replaced.

## Phase 0. Build that works

- [x] A1 Regenerate the Gradle wrapper (jar is missing). Fixes B4. Gradle 9.8.2. Root cause: `*.jar` in `.gitignore` came after the wrapper exception and re-ignored it; exception moved below.
- [x] A2 AGP 9.4.1 (built-in Kotlin), Kotlin 2.4.21, KSP 2.3.12 instead of kapt, `gradle/libs.versions.toml`. compileSdk 37 (current AndroidX needs it), targetSdk 36, minSdk 34 (the phone is Android 14, so no older-version code paths are needed). Unused WorkManager and Room removed from `runtime`. `:server` taken out of the build; the folder is deleted in phase 2 (B9).
- [x] A3 Android SDK in `~/Android/Sdk` (platform 37.0, build-tools 37.0.0), Temurin JDK 21 in `~/.jdks`, set in `~/.gradle/gradle.properties`. `local.properties` points at the SDK. `./gradlew assembleDebug :runtime:testDebugUnitTest` passes.
- [ ] A4 Debug build installs on the phone over adb. Waiting for the phone to be connected with USB debugging.

## Phase 1. Core: flow model, validator, engine

- [x] A5 New pure-Kotlin `core` module: flow model, validator, engine. Testable without a phone.
- [x] A6 Flow model: one trigger per flow, ordered steps, if/else branches, typed parameters, step outputs usable later (`{{location}}`).
- [x] B1 A failed or skipped condition stops the branch. No downstream side effects.
- [x] B5 One list of block types shared by the prompt, the validator and the parser. Unknown types rejected with a clear message.
- [x] B11 Parameters validated before a flow is saved (times, numbers, phone numbers, URLs). A bad step fails alone, never crashes the app.
- [x] B17 Tests: false condition runs nothing downstream, branches, validator rejects bad graphs. 20 tests in `core/src/test`.
- [x] A16 Codex review of `core/` (gpt-6.1-sol). 12 findings, all fixed with tests: "every Monday" became daily; unknown params now errors (a misspelled filter used to widen a trigger); filled-in `{{values}}` re-checked at run time; integer overflow; inverted battery range; bare flow replies; variable-only URLs; branch scope rule in the prompt; `ask_ai` marked sensitive; camera/background-location limits stated. 30 tests.

## Phase 2. LLM on the phone, delete the server

- [x] A7 `ai/OpenAiChatModel.kt`: chat completions with JSON mode against any OpenAI-compatible base URL; `/models` list for the picker; readable errors for 401/404/429, cut-off and refusal.
- [x] A8 `ai/AiSettings.kt`: key encrypted with an Android Keystore AES-GCM key. Presets Groq, OpenAI, Custom. Setup and Settings test the key with `/models` before saving and list the key's models when the name is wrong.
- [x] A9 `FlowAgent` sends the current draft and the chat history; follow-ups edit the draft.
- [x] B3 No server address to configure.
- [x] B9 `server/` deleted.
- [x] B18 `agent-client/`, `runtime/`, `data/`, old screens, view models, services and helpers deleted. security-crypto and BuildConfig server URLs removed.

## Phase 3. Runner and triggers

- [x] A10 One flow runner (`run/FlowRunner.kt`): WorkManager job per run, one run per flow at a time (extra triggers ignored), cancellable, every step saved, never retried (no double SMS), stale runs marked interrupted on start.
- [x] B2 Time trigger: `setAlarmClock` (Doze-exempt, most reliable on Vivo), re-registered on boot, update, time and time-zone change. Logic tested in `TriggerRulesTest`.
- [x] B2 Location trigger via geofences on saved places. Also SMS, notification, wrong-unlock, charger, battery-low, headphones and Wi-Fi triggers (`trigger/`).
- [x] B13 Handlers report the real outcome: HTTP status, SMS delivery result, Wi-Fi "needs you" instead of fake success.
- [x] B14 Camera access serialized; cancellation propagates; failed captures cleaned up.
- [x] B20 Pedometer and camera waits shortened and bounded.
- [x] B11 Real battery level, time and day instead of hardcoded 80% and "weekday". Overnight time windows work.
- [~] A11 Settings has "No battery restrictions" and an Autostart shortcut (iQOO security app, falls back to app info). Check the shortcut on the phone.

## Phase 4. Permissions

- [x] B6 Notification permission asked on the review screen when a flow uses `notify`; turned-off notifications open the app's notification settings.
- [x] B8 `run/FlowControl.kt`: one enable/turn off/delete path for the Home switch, review screen and delete. A flow is never enabled with a permission missing; the switch opens the review screen instead.
- [x] A12 Home re-checks permissions on resume. An enabled flow with a revoked permission moves to "Needs you" with "Blocked: needs X".

## Phase 5. Data

- [x] B10 Room schema export and real migrations; remove destructive migration.
- [x] B12 Room is the only source of the flow list. Drafts survive restart (chat keeps its draft id in saved state). Delete from the flow screen.
- [x] B19 Backup excludes the API key prefs, trigger prefs and private photos. Cleartext kept for LAN webhooks; validator warns on `http://`.
- [x] A13 Chat history persisted per draft.

## Next: switch-over

- [x] A17 Manifest: new receivers, services, permissions; old ones removed. `USE_EXACT_ALARM` (sideloaded, granted at install).
- [x] A18 New screens on `AutomateApp`. Old modules deleted. Build has two modules: `:app`, `:core`.

## Phase 6. UI rebuild

- [x] U1 Design direction. `docs/design/mockup.html` (v2, after GPT critique). Built in phase 6.
  - Light first (owner's choice), true-black dark theme for AMOLED, System/Light/Dark setting.
  - Brand Electric Blue `#1337EC`. Category colours: amber When, violet Only if, teal Then.
  - Bricolage Grotesque for flow titles written as sentences, Instrument Sans for UI.
  - Signature: the wire. Short wire per Home row, full wire in drafts, live wire during runs.
  - GPT critique applied: failures first on Home, readable status text, darker small text for contrast, 48 dp targets, draft diff with Undo, warnings on the affected step, review screen before turning on, run screen led by the current step, five step states, first-launch key setup.
- [x] U2 Navigation Compose with typed routes (`ui/App.kt`).
- [x] U3 Edge-to-edge, insets, keyboard padding, System/Light/Dark setting. Brand colours instead of dynamic colour (design decision in U1).
- [x] U4 Home: Needs you / On / Off / Drafts, mini wire per row, real last run and next run, empty state with examples.
- [ ] U5 "Edit steps" in the draft card (mockup), edit a step's parameters by hand (tap a step on the wire). For now edits go through chat. The old canvas and its no-op buttons are deleted (B15).
- [x] U6 Chat: draft card with the wire, changed steps marked, Undo, validator warnings on the step, errors shown inline. Examples on Home fill the box.
- [x] U7 Flow screen: live run card, wire with step states (done, passed, not met, failed, running, waiting, skipped), history, Run now, Test run, Stop.
- [x] U8 Review screen: wire, everything the flow sends in full, each permission with Allow, Test run, Turn on only when all granted. Test runs replace SMS and web requests with "would send" (`AutomateApp.testEngine`).
- [~] U9 Haptics on buttons and switches, pulse on the running step, screen transitions. Check feel on the phone.

## Phase 7. On-device testing (iQOO Neo 6)

- [ ] B7 Photo on failed unlock: test with the screen locked. Keep, rework or drop based on the result.
- [ ] A14 Test: run while locked, after reboot, after denying a permission, with no network, with duplicate trigger events.

## Phase 8. Extras

- [ ] U10 Quick Settings tile to run a flow.
- [ ] U11 Home-screen widget.
- [ ] A15 More blocks, only the ones actually used.

## Done

- 2026-10-22 Switch-over and phase 6: new UI (Setup, Home, Chat, Review, Flow, Settings), FlowControl, test runs, old modules deleted.
  - Checked on an Android 15 emulator sized like the Neo 6 (1080x2400, 440 dpi) against a fake OpenAI-compatible server: setup, chat, edit with Undo, review, turn on, live run, test run with a missing permission, dark theme.
  - Crash found on the emulator only: Android's ICU regex rejects the bare `}}` in `Templates.kt` that the JVM tests accept. Every chat reply crashed the app. Fixed.
  - Codex review of the UI (gpt-6.1-sol), fixed: editing an enabled flow sends it back to draft so the change is reviewed before it runs; test runs also fake `ask_ai`; fine and coarse location asked together; permanent denials open Settings; flow on/off and trigger sync cannot be cut off halfway; failed branch conditions skip both branches; Home reads the latest run per flow; camera warning attached to the photo step; setup form survives rotation. Not fixed: Undo is lost if Android kills the app (the edit stays saved).
  - GPT critique of the screenshots applied: struck-through old values, 48 dp targets, shorter status text, dashed waiting steps, "so far" on the run timer. Manual flows get a Run button on Home instead of a switch.
  - `app/src/test/.../RunStatusesTest.kt`: 3 tests for the run-state mapping.

- 2026-10-22 Phase 0: A1, A2, A3. Build works on branch `rebuild`.
- 2026-10-22 Phases 2, 3, 5 (code side): AI client, key store, new Room store, Android handlers for every block, runner, all triggers. Not wired to UI or manifest yet.
- 2026-10-22 Phase 1: A5, A6, B1, B5, B11 (validation side), B17. `core` module with model, catalog, validator, engine, agent; 20 tests pass.
