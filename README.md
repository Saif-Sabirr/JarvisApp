# Jarvis — Offline Voice Assistant (Android)

A floating voice-command assistant: hold the orb, speak a command, and it calls,
texts, opens apps, or answers you out loud — all fully offline except web search.

I built the full source in this environment, but **I can't compile the APK myself
here** — there's no Android SDK or internet access in this sandbox. Below are two
ways to get a real, installable APK on your phone. Pick whichever is easier for you.

---

## Option A — No PC needed (GitHub Actions builds it for you)

1. Create a free account at https://github.com if you don't have one.
2. Create a new **empty** repository (any name, e.g. `jarvis-app`).
3. Upload every file in this project folder to that repository (GitHub's web
   uploader works fine — drag the whole folder in, or use `git push` if you're
   comfortable with git). Keep the folder structure exactly as-is.
4. Go to the **Actions** tab of your repo. A workflow called "Build Jarvis APK"
   will run automatically (takes ~3-5 minutes).
5. When it finishes, click into the run → under **Artifacts**, download
   `jarvis-debug-apk` (a zip containing `app-debug.apk`).
6. On your phone: unzip if needed, then open `app-debug.apk` from your Downloads
   or Files app and tap **Install**. Android will ask you to allow "install
   unknown apps" for that app (Chrome/Files) the first time — allow it, then
   install.

This produces a debug-signed APK, which is completely fine for installing on
your own phone.

## Option B — Android Studio (more control, lets you use "Run" over USB)

1. Install **Android Studio** (free, from developer.android.com/studio) on a
   Windows/Mac/Linux computer.
2. Open Android Studio → "Open" → select this `JarvisApp` folder.
3. Let it sync Gradle (needs internet the first time, to download the Android
   SDK components and libraries — this is separate from the sandbox I built
   this in, so it will work normally on your computer).
4. On your phone: Settings → About phone → tap "Build number" 7 times to
   unlock Developer Options → enable **USB debugging**.
5. Plug your phone into the computer, select it in Android Studio's device
   dropdown, and click **Run ▶**. It installs and launches directly.

---

## Setting it up on your phone after installing

1. Open the **Jarvis** app.
2. Tap **1. Grant mic / call / text / contacts / camera** → allow everything.
3. Tap **2. Allow floating overlay** → this opens a system settings page —
   toggle "Allow display over other apps" for Jarvis, then go back.
4. Tap **3. Start Jarvis**. A small glowing orb appears floating on your
   screen — you can now switch to any other app.
5. Tap the orb to expand the side panel, hold the mic button, and speak.
   Drag the collapsed orb anywhere on screen; tap ✕ to collapse the panel
   again.

## What works offline vs. what doesn't

- **Fully offline**: calling a contact, texting a contact, opening apps,
  flashlight, volume, time, date, battery level, jokes.
- **Needs internet**: nothing critical — "search for X" is the only command
  that would need it, and it's not wired to actually fetch results in this
  build (it's flagged in the code as a place to add that later if you want).
- Voice recognition itself uses Android's built-in `SpeechRecognizer` with
  `EXTRA_PREFER_OFFLINE`. Whether it's *truly* offline depends on your device:
  most modern Android phones with Google's app can do on-device recognition
  once you've downloaded the offline language pack (Google app → Settings →
  Voice → Offline speech recognition → download your language). Without that
  pack, the OS may fall back to an online recognizer.

## Camera scanning ("scan this" / "what is this")

Say **"scan this"**, **"what is this"**, or **"identify this"** and Jarvis opens
a full-screen camera view with an Iron-Man-style HUD:

- **Barcode / label detection** — fully offline, runs on-device via ML Kit.
  If it finds a barcode, cyan targeting brackets snap around it.
- **Product lookup** — only this step needs the internet, since no phone can
  store a database of every product ever made. It calls a free public
  barcode-lookup API and speaks the result ("This is X by Y"). If you're
  offline, it tells you so instead of guessing.
- **Fingertip trail** — hold your hand up and a glowing line traces your
  index fingertip as you move it, fully offline (Google's on-device hand
  tracking model, no camera data ever leaves the phone for this part).

One manual step is required for the fingertip trail specifically: I couldn't
download the tracking model file in the sandbox I built this in (no internet
access there). See `app/src/main/assets/README_ASSETS.txt` — it's one file,
one link, drop it in a folder. Barcode/product scanning works immediately
without it; only the finger-trail effect needs it.

## Talking to Jarvis, and switching language

Jarvis understands **English or Urdu** and can be set to always **reply**
in whichever one you pick — independent of which one it's currently
listening for.

- Tap the **EN / UR chip** at the top of the floating panel to switch.
  This changes both what Jarvis listens for and what it replies in.
- Common commands (call, text, open app, flashlight, volume, time, date,
  joke, scan) are recognized in both languages — e.g. "call mom" or
  "مما کو کال کرو" both work.
- Urdu text-to-speech depends on your phone having the Urdu voice pack
  installed. If it isn't, Jarvis tells you once and falls back to English
  speech until you install it (Settings → General → Languages → Text-to-
  speech output → install the Urdu voice).
- The Urdu phrase matching is keyword-based, not a full grammar parser —
  it covers the common ways people phrase these commands, but you may find
  gaps. `CommandProcessor.kt` is where to add more phrasings.

## Always-on listening — no button needed

Once you tap "3. Start Jarvis" and the floating icon appears, **it's already
listening** — just say a command like "call mom" and it acts, no need to
tap or hold anything first. Under the hood it's a continuous loop: listen →
act → listen again.

Worth knowing, in plain terms: this means the microphone is active
essentially the whole time Jarvis is running in the background, which is
a real privacy and battery trade-off compared to push-to-talk. Two controls
help with that:
- **⏸ PAUSE LISTENING** in the panel stops the mic instantly; tap again to
  resume.
- Closing the app / stopping the "Jarvis is listening" notification (swipe
  it away, or tap it → App info → Force stop) shuts the whole service down.

Nothing recognized offline ever leaves your phone. Only the two features
that explicitly need the internet (product lookup, and installing an Urdu
voice pack if you don't have one) talk to anything external.

## The floating icon

It's now an animated "arc reactor" style HUD icon (`ArcReactorView.kt`) —
a slowly rotating outer ring of tick marks, a faster counter-rotating inner
ring, and a pulsing glowing core, drawn live on a Canvas rather than a
static image. Color shifts by state: cyan while idle, amber while actively
listening, green while Jarvis is speaking.

## Real-world things worth knowing

- Google Play restricts the `CALL_PHONE` / `SEND_SMS` permissions for apps
  submitted to the Play Store unless the app is your default calling/SMS app.
  Since you're installing this directly (sideloading), that restriction
  doesn't apply to you — it'll work as built.
- Some phone brands (Xiaomi, Oppo, Samsung, etc.) aggressively kill
  background apps to save battery. If the floating orb disappears after a
  while, go to Settings → Battery → find Jarvis → disable battery
  optimization / allow "autostart" for it.
- Flashlight control can behave slightly differently across camera hardware;
  the code picks the first camera that reports a flash unit.
