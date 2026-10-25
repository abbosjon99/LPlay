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
| Car buttons | Settings > Car > Car buttons: a **recorder** that writes down the wheel, voice and phone broadcasts and key events for 15 minutes, and **Learn** for the answer-call, hang-up and assistant buttons. A learned button then answers or ends the CarPlay or Android Auto call or starts Siri / the assistant. Listening only: it never aborts a broadcast, so the car's own apps and LP Music see every button as before. |
| ADB, root | the head unit's adb and root are not used on this profile: no silent VPN pre-consent over adb, no car-hotspot import or write through adb or root, the root switch is hidden. The VPN and hotspot paths use Android's own dialogs and the typed SSID and password. |
| Tests | a Windows path-separator test is made portable; new tests cover the profile, the settings mapping, the cluster display rules, the fade, the AA geometry and square profile, and the car-button signatures. |

### Not in LPlay (yet)

- **Vehicle data** (battery, range, speed, tyre pressure, outside temperature for the side panel). The DiPlay LP fork read these
  from `android.car`; the property ids were never confirmed on a C01, and CarLink's engine reads BYD's APIs directly at seven
  places that would need a hook. Left out until a probe on the car shows what it answers.
- **A Leapmotor HUD** (your car has none) and the **Leapmotor call-popup hider** (needs a capture of how `com.leapmotor.phone`
  shows its popup).
- **The activation signal** some Leapmotor firmware may need before the cluster shows an app window (SOME/IP `gaode_pub` event
  1090 in other projects). Not implemented; decide after test steps 3 and 4.
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
`-NoSubst` turns that off. Do not run `scripts/release.sh --publish` or `scripts/fetch_icons.py`: they are CarLink's release tools.

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
   learned. Make a CarPlay call to the iPhone: the learned answer button should answer, the hang-up button end it, the assistant
   button start Siri. Note whether the car's own phone popup also appears over CarPlay, and whether LP Music still reacts to its
   own buttons. Repeat the three with an Android phone.
9. **Android Auto on the cluster** (experimental). Choose the picture mode "Square with side margins", connect an Android phone with
   a navigation app open. Expected: a map in the square. If the cluster stays empty, switch to "Full picture, middle shown" and
   reconnect; report which phone and which mode worked.
10. **Calls and sound.** A CarPlay call: popup and audio work. Note whether the car's Bluetooth phone also reacts.
11. **Wireless.** Wi-Fi Direct first, then the car's hotspot with the typed SSID and password. Expected: no "Allow debugging?" prompt
    on the car at any point.

## Open decisions

- Whether to supply the CarPlay accessory identity (above).
- The activation signal for the cluster (SOME/IP event 1090), only after test steps 3 and 4.
- Whether LPlay gets a mark of its own: the launcher icon is still CarLink's.
- Where LPlay's source lives: the AGPL entitles every recipient of a build to the complete source (CarLink at the pinned tag plus
  the patch series). Publishing the patcher, or the fork, is your decision; nothing is pushed.
- The BYD HUD icons in the CarLink tree are licensed PolyForm Noncommercial and are not covered by the AGPL; LPlay never shows them.
  Say if you want them deleted from the tree.
