<p align="center"><img src="icons/icon-192.png" width="128" alt="Annales icon"></p>

# Annales

**Your days, sealed as stamps.**

Annales is a stamp journal. Each day you cut up to three stamps from your photos, say how the day felt, and seal it. Sealing presses a real-looking postmark on every stamp, in your post office's own style, and puts the stamps into your phone's Photos. No account, no cloud, no server.

> **Pre-release (0.2.0).** Today, the Journal, Collections and Profile are in. Insights and backups come in later builds.

---

☕️ You can support me and Buy me a coffee ☕️

[https://buycoffee.to/szebastian](https://buycoffee.to/szebastian)

---

## What it does

- **Up to three stamps a day.** Pick a photo, move and pinch it under the 4:5 frame, and it comes back with a perforated edge cut from the photo itself.
- **How did it feel?** Drag along the horizon, from Very Unpleasant in violet to Very Pleasant in blue. The mood dot sits in the stamp's corner.
- **Seal the day.** Each stamp gets a Poczta Polska datownik (the office over the top, the postcode underneath, the date bridge and the hour) hanging off its right edge, and wavy cancel lines over the top-left. Every hit is pressed a little differently: angle, pressure and patchy ink.
- **Inks.** Official black, the way the post office does it, or Post red and Carmine as your own touch.
- **Saved to Photos.** Sealed stamps go into an **Annales** album, perforation and postmark included (Android 10 and later).
- **The Journal.** A calendar of your stamps. Open any past day to add stamps or seal it.
- **Collections.** A **trip** (dates and a place: those days are stamped by that city's post office), an **event** (a wedding, a launch), an **anniversary** (one date, every year) or a hand-picked **theme**, like Coffee.
- **Turn it over.** Open a stamp full screen and tap it to see the back: the date, the mood, when it was sealed, and a title and note you can write. Swipe through a collection, file a stamp into a theme, or make it the cover.
- **Two perforations.** Classic round holes, or Scallop's big soft bites.
- **Evening reminder.** A soft nudge at 21:30 if today is still unsealed.

## Getting started

1. Open **[Releases](../../releases)** on your phone and download **annales-debug.apk** from **Latest debug build**.
1. Open the file. If Android asks, allow your browser to **install unknown apps**.
1. Open Annales and allow **notifications** for the evening reminder.
1. In **Profile**, check your **post office** and **postcode** (Gliwice 1, 44-100 to start).

Updating: install the new APK over the old one. Your journal stays.

## Privacy

Everything stays on your phone: the journal, the cropped photos and the stamps. Annales has no internet permission at all. The only thing that leaves the app is what you seal, into your own Photos.

---

_Annales is Latin for yearly records: the Roman chronicles, kept year by year. It's a sibling to Consensus Weather, Hora, Caelum, Kalendae, Augur and Fidelis._

<details>
<summary>For developers</summary>

A native Android app in Kotlin and Jetpack Compose (Material 3, coroutines), with no other libraries. GitHub Actions builds a debug APK on every push to `main` and publishes it to the **latest-debug** pre-release.

| Where | What it is |
| --- | --- |
| `data/` | `JournalStore` (journal.json, photos and stamps in app storage), `CollectionStore` (collections.json) and `Places`, `Settings`, `Moods`, `Ink` |
| `render/StampRenderer.kt` | Perforation, the datownik, the wavy lines and the ink wear |
| `render/Photos.kt` | Decoding picked photos, saving sealed stamps to Photos › Annales |
| `reminder/` | The 21:30 reminder and putting it back after a restart |
| `ui/` | Today (`DayPage`), Journal, Collections and their form, the crop screen, the viewer, Profile and the Kraft theme |
| `tools/icon.js`, `tools/icon.html`, `tools/icon.sh` | Draw the icon and render every PNG in `icons/` and the launcher layers |
| `tools/install.sh` | Waits for the build of your last push and installs it on the plugged-in phone |
| `.github/workflows/android.yml` | Builds the APK and publishes it to the latest-debug release |

**Signing.** `annales-debug.keystore` is the family's debug key, so every build installs over the last one.

**Build and install:** push to `main`, then run `tools/install.sh`. **Icon:** edit `tools/icon.js`, then run `tools/icon.sh`.

</details>
