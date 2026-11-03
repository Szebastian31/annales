# How the sibling apps share a day with Annales

Annales asks each sibling for a short summary of one day. Nothing leaves the phone: it's one app asking another, on the same phone.

**The address.** Each app adds a read-only content provider at `<package>.annales` and answers
`content://<package>.annales/day/YYYY-MM-DD` with two columns, `key` and `value`, one row per fact. No rows means nothing that day.

**Who may ask.** Only Annales: the provider checks that the caller's package is `com.annales.journal`, and returns nothing otherwise.

| App | Package | Keys |
| --- | --- | --- |
| Fidelis | `com.fidelis.player` | `music.track`, `music.artist` (the track heard most that day, counted once it plays half way or 4 min), `music.minutes` (whole minutes actually playing) |
| Kalendae | `com.kalendae.money` | `money.spent` (logged spends that day), `money.safe` (safe to spend per day, as Kalendae showed it that day), formatted like `84 zł`; `money.spentN`, `money.safeN`, the same as plain numbers in your display currency |
| Augur | `com.augur.stars` | `augur.tone` (the day's tone, e.g. `Reach out`), `augur.agree` (how many of the four day systems voted for it, e.g. `3 of 4`), `augur.number` (the lucky number), `augur.colour` and `augur.colourHex` (the lucky colour's name and hex; several hexes may be comma-separated) |

Weather and the moon aren't asked for: Annales works out the weather itself the way Consensus Weather does (the same thirteen
Open-Meteo models, averaged, the condition most models vote for), and calculates the moon like Caelum.

**When Annales asks.** Each time you open a day that isn't sealed yet. Sealing a day freezes what it had.

**Web-based siblings** (Kalendae, Augur) keep their data in the WebView's storage, which the provider can't read directly. Their
page writes the day's summary to the native shell through a small JavaScript bridge (`AnnalesShare.put(date, json)`) whenever it
saves, and the provider serves the last 120 days from there.

**Where each app keeps it.** Fidelis keeps its own small listening log (separate from the music library and from
Last.fm, so it works without an account), Kalendae's page shares the last seven days each time you leave it, and
Augur shares today's tone whenever it works out the day. Each keeps 120 days, in its own private storage.

**Older days.** Kalendae only knows safe-to-spend on the day itself, and Augur only shares a day you opened it on,
so a gap there means the app wasn't opened that day, not that something broke.
