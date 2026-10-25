# LPlay: CarLink for the Leapmotor C01 (2024-2026)

LPlay is a modified version of [CarLink](https://github.com/F-e-n-y-x/CarLink) by F-e-n-y-x (GNU AGPL-3.0), tuned for the
Leapmotor C01 head unit (Snapdragon SA8155, Android 12). It keeps CarLink's two engines and its screens: an **iPhone gets
CarPlay**, an **Android phone gets Android Auto**, by cable or wirelessly, in one app. The credit is in About, in `NOTICE` and in
every commit message; the unmodified CarLink release is the first commit of the `lplay` branch.

Nothing in this document has been run on a car yet. The parked-car test plan at the end is how to confirm it. Why the cluster
code looks the way it does is in the DiPlay LP notes (`FINDINGS.md` of the DiPlay Leapmotor fork) and in
[VENDOR_CHANGES.md](VENDOR_CHANGES.md) (every edit inside `vendor/`).

## What changed from CarLink 1.0.6

| Area | Change |
|---|---|
| App identity | package `uz.carhelper.lplay`, label "LPlay", version `0.1.0`. Installs next to CarLink and DiPlay: no shared data, and DiAuto's fixed `NAVIGATION_UPDATE` permission is removed so the two do not collide. **Run only one phone-projection app at a time.** |
| Credit | About: "Based on CarLink 1.0.6 by F-e-n-y-x", a Credits entry, a source QR code for CarLink, the licence text; `NOTICE`; the README; commit history. No start-up screen. |
| Updates | **off.** CarLink's updater only knows CarLink's own releases, which LPlay must not offer or install. The About card, the install permission and the "Report on GitHub" buttons (CarLink's issue tracker) are hidden. "Share log" stays. |
| Car profile | new profile **Leapmotor C01**, detected from the car's multimedia app (`com.leapmotor.multimedia`) or a fingerprint that names Leapmotor; Settings > Car > Car brand offers Leapmotor and Other. BYD is not detected and no BYD feature turns on. |
| Wheel and media keys | **all key handling removed on this profile.** LP Music owns the wheel, dock and voice buttons. Both engines keep a transport-only media session (play, pause, next, previous from a media controller) and ignore hardware media-button events; CarPlay passes keys in its window on unchanged (volume and back still work); Android Auto's vendor car-key receivers, which abort ordered key broadcasts, are never switched on; the learned-wheel-keys page is hidden. |
| Notification | CarPlay's only notification is the iPhone's now-playing track as a media notification (previous / play-pause / next / disconnect), which is also the foreground-service notification. Android Auto mirrors the phone's track into its media session and now-playing notification. |
| Cluster, CarPlay | the CarPlay map goes to the instrument cluster: display `local:131` (logical id 2), a transparent **720x720** window at the top right, no ADB, no input. The iPhone is asked for a stream of exactly that size. Settings > Car > Instrument cluster: on/off (off by default), the fade, a test card. |
| Cluster, Android Auto | the same window, fed by Android Auto's second video sink (type CLUSTER). Android Auto only offers fixed codec sizes, so the picture is a 1280x720 frame of which the window shows the middle 720 columns. Two ways to ask the phone: *square with side margins* (560 px of width margin) or *full picture, middle shown*. **Experimental: untested with a phone.** |
| Left-edge fade | the map's left edge fades into the car's own cluster picture (off / 10 / 20 / 27 / 35 %, default 20 %), done by taking alpha off the video itself (works on a TextureView only). Idea from the C11 fork by w012w012, new code. |
| Car buttons | Settings > Car > Car buttons: a **recorder** that writes down the wheel, voice and phone broadcasts and key events for 15 minutes, and **Learn** for the answer-call, hang-up and assistant buttons. A learned button can then answer or end the CarPlay or Android Auto call or start Siri / the assistant, but only after "Let the learned buttons act" is switched on; **it is off by default**, so a button can be learned and recorded without anything reaching the phone. Listening only: it never aborts a broadcast, so the car's own apps and LP Music see every button as before. The car's broadcasts carry no sender check Android lets an app verify, so another app on the head unit could send the same broadcast; that matters only while the switch is on and a call is active. |
| ADB, root | the head unit's adb and root are not used on this profile: no silent VPN pre-consent over adb, no car-hotspot import or write through adb or root, the root switch is hidden. The VPN and hotspot paths use Android's own dialogs and the typed SSID and password. |
| Tests | a Windows path-separator test is made portable; new tests cover the profile, the settings mapping, the cluster display rules, the fade, the AA geometry and square profile, and the car-button signatures. |

### Not in LPlay (yet)

- **Vehicle data** (battery, range, speed, outside temperature for the side panel): dropped. The cluster already shows them.
  To think about later: **tyre pressure** (the car's launcher widget shows it; an ordinary app cannot read it from the car),
  **average fuel** and **speed in km/h**. CarLink's engine reads BYD's APIs directly at seven places, so any of these needs a
  small hook there, and the `android.car` property ids were never confirmed on a C01.
- **A Leapmotor HUD** (your car has none) and the **Leapmotor call-popup hider**. The BYD one exists because the car's own
  Bluetooth phone app puts its call screen over CarPlay's; whether `com.leapmotor.phone` does the same is for test step 10.
- **The activation signal** some Leapmotor firmware may need before the cluster shows an app window (SOME/IP `gaode_pub` event
  1090 in other projects; they skip it when the car's own `com.leapmotor.autonavi` already owns it). Not implemented: LPlay's
  window opens on the cluster display over the car's own map (it takes the window back at most three times a minute if the map
  covers it). Needed only if the cluster stays blank while the car's navigation is idle; see test steps 3 and 4.
- Everything BYD-only is still in the code (CarLink's own BYD profile, HUD and cluster routes); LPlay just never turns it on.
  Removing it would make every CarLink update a hand merge.

## The CarPlay accessory identity

CarPlay needs an MFi accessory identity (`offline-mfi/identity.pk8` and `certificate.p7b`). It is **not part of the source** and
never committed: upstream says it was recovered from public Carlinkit C2Air firmware and that its suitability is unresolved. A
build without it compiles and runs, but cannot start CarPlay ("unable to load CarPlay authentication"). Android Auto needs no
such file. Supply your own: put the two files in a folder as `offline-mfi\identity.pk8` and `offline-mfi\certificate.p7b` and pass
that folder with `-AuthAssetsDir` (or set `DIPLAY_AUTH_ASSETS_DIR`). This repository and its patches never contain them, `.gitignore`
blocks `*.pk8`, and the build never prints them.

## Build

Needs JDK 25, the Android SDK (platforms 34, 36 and 37, build-tools 36, NDK 28.2 and 29, CMake 3.22.1) and your signing key.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-lplay.ps1 `
  -Keystore C:\keys\signing-key.jks -KeystoreInfoFile C:\keys\signing-key.txt `
  -AuthAssetsDir C:\keys\diplay-auth -RunTests
```

`-KeystoreInfoFile` holds `alias=` and `password=` lines. The values go to Gradle through environment variables of that one
process and are never printed. The script verifies the signature (certificate digests only) and prints the APK path and SHA-256.
On Windows it maps a spare drive letter to the checkout's parent folder when a path contains `\u` (for example `C:\Users\...`),
because the Android aidl tool writes its command line into a generated Java comment and `\u` there is an illegal escape;
`-NoSubst` turns that off. `-Arm64Only` leaves out the 32-bit libraries (about 2 MB smaller); use it once the head unit's
`getprop ro.product.cpu.abilist` starts with `arm64-v8a`. Do not run `scripts/release.sh --publish` or `scripts/fetch_icons.py`: they are CarLink's release tools.

## Patch series and updates

LPlay is a patch series on top of CarLink. The separate patcher (`LPlay-patcher`) clones `F-e-n-y-x/CarLink` at the pinned tag
(`UPSTREAM`), checks the commit, applies `patches\*.patch` in order with `git am --3way` on a branch `lplay` and, with `-Build`,
runs the build script above. `make-patches.ps1` regenerates the patches from the working branch. Nothing in the patcher pushes
or installs anything.

To take a new CarLink release: `apply.ps1 -Tag vX.Y.Z`. Edits inside `vendor/` are small and marked `// LPlay`, so conflicts stay
local; when `git am` stops, fix the files, `git am --continue`, then rebuild and run the tests. CarLink itself imports its
engines by hand from DiPlay and DiAuto, so their updates arrive with CarLink's.

## Settings you will notice (Settings > Car, Leapmotor profile)

- **Instrument cluster:** map on the cluster (off by default; applies from the next connection), fade of the left edge, Android
  Auto cluster picture mode, and a **test card** that puts a numbered grid on the cluster for 20 seconds.
- **Car buttons:** the recorder, "share the recording", and the three Learn rows.
- The learned-wheel-keys row, the root switch and the car-brand list of other makes are gone.

## What was checked without a car

On an Android 12 x86_64 emulator with a simulated second display of 1920x720 (`adb shell settings put global overlay_display_devices
1920x720/213`, which becomes display id 2 like the car's cluster), with the debug build and with the R8-shrunk release build:

- Setup, Home and Settings > Car for the Leapmotor profile: the brand note, the instrument-cluster group, the fade sheet, the Android
  Auto picture sheet, the car-button group; no steering-wheel row, no BYD wording on the normal path. No error or warning from the app
  in the log, 119 MB PSS in Settings, only the main process runs (the `:aa` process does not start), cold start about 2 s.
- The cluster test card lands on display 2 as a 720x720 square at the top right (its four corner colours and grid read correctly).
- `ClusterWindowInstrumentedTest` (`./gradlew :lplay-cluster:connectedDebugAndroidTest`, emulator only): the real route starts the real
  window on that display, the window confirms its display, hands over a Surface, shows frames drawn into it (the test plays the
  decoder), shows the 20 % fade on its left edge, and closes again. A screenshot of the display (`adb shell screencap -d 2`) shows
  the square, its place and the fade.
- Car buttons: the recorder writes the car's broadcasts, Learn stores the next one, and nothing acts until "Let the learned buttons act"
  is on; with it on, a learned press reaches the engine hook (no session on an emulator, so it reports "performed=false").
- 12 route tests and the receiver tests (Robolectric) in the normal build.

Not checked: the CarPlay and Android Auto decoders feeding the window, a phone of either kind, the car's own firmware (whether it shows
the window, covers it, or needs the activation signal), and anything that depends on the car's apps. The parked-car test plan below
covers those.

## Parked-car test plan

Rules for every step: car parked, nobody in the passenger seat, never in child mode, do not change the car's ADB, developer or
security settings. Take a screenshot before touching anything. Stop at the first step that does not behave.

1. **Install side by side.** Install the signed APK; the original DiPlay and CarLink, if present, stay. Open LPlay, finish the
   setup guide. Expected: Home, no update banner and no "Report" button anywhere; About names CarLink.
2. **Profile.** Settings > Car > Car brand: "Detected: Leapmotor C01". If it says generic, choose Leapmotor by hand and tell me what
   the car's packages are (`com.leapmotor.multimedia` should be installed).
3. **Test card.** Settings > Car > Instrument cluster > Cluster test card. Expected: a grid for 20 s in the top right of the
   instrument cluster, touching nothing else; the passenger screen shows nothing. Note which part of the square is visible.
   "No cluster display found" means the displays differ from `local:131` / id 2: open Diagnostics and send the display list.
4. **CarPlay on the cluster.** Turn the cluster map on, connect an iPhone. Expected: the map in the square, its left edge fading
   (try Off / 10 / 20 / 27 / 35 % in the sheet; it applies at once). Disconnect: the car's own cluster picture comes back, no
   frozen map or black square. If the cluster stays blank, the car may need an activation signal; report it, do not guess.
5. **Cover test.** Open the car's own navigation. Expected: LPlay brings its window back at most three times a minute and then
   leaves it alone.
6. **Notification and LP Music.** While CarPlay plays, the notification shows the track with previous / play-pause / next; LP Music
   shows the track and its controls work; the wheel play/pause and next/previous buttons act through LP Music. LP Music's
   "give the wheel to LPlay/DiPlay" switch is off. Repeat with Android Auto.
7. **Record the buttons.** Settings > Car > Car buttons > Record the car's buttons. Press, one at a time and writing it down:
   play/pause, next, previous, the voice (speak) button, the answer/hang-up button. Then Share the recording. If the voice or
   answer button leaves no line, run `adb shell dumpsys activity broadcasts` right after pressing it.
8. **Learn the buttons.** For Answer, Hang-up and Assistant: tap the row, press the car's button within 20 s; the row shows what it
   learned. Nothing is sent to the phone yet. Then switch on "Let the learned buttons act" and make a CarPlay call to the iPhone: the learned answer button should answer, the hang-up button end it, the assistant
   button start Siri. Note whether the car's own phone popup also appears over CarPlay, and whether LP Music still reacts to its
   own buttons. Repeat the three with an Android phone.
9. **Android Auto on the cluster** (experimental). Choose the picture mode "Square with side margins", connect an Android phone with
   a navigation app open. Expected: a map in the square. If the cluster stays empty, switch to "Full picture, middle shown" and
   reconnect; report which phone and which mode worked.
10. **Calls and sound.** A CarPlay call: popup and audio work. Note whether the car's Bluetooth phone also reacts.
11. **Wireless.** Wi-Fi Direct first, then the car's hotspot with the typed SSID and password. Expected: no "Allow debugging?" prompt
    on the car at any point.

## Open decisions

- The activation signal for the cluster (SOME/IP event 1090), only after test steps 3 and 4.
- Which of tyre pressure, average fuel and speed to bring back, and from where (above).
- Publishing: a fork of CarLink on your GitHub account with the patch series (never the identity files), and a release once the
  parked-car tests have been done. The AGPL entitles every recipient of a build to the complete source (CarLink at the pinned tag
  plus the patch series); nothing is pushed until you say so.
- The launcher icon is still CarLink's mark; fine for now.
- The BYD HUD icons (PolyForm Noncommercial, not covered by the AGPL) stay in the source tree but are **not packaged** in LPlay
  builds. To bring them back later, build with `-Plplay.bydIcons=true`; About then lists them again.
