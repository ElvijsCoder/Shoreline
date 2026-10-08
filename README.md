<div align="center">

```
 ·  ·  ·  ·  ·  ·  ●  ·  ·
 ·  ·  ·  ·  ·  ·  ●  ●  ·
 ·  ·  ·  ·  ·  ●  ●  ·  ·
 ·  ·  ·  ●  ●  ·  ·  ·  ·
 ·  ●  ●  ·  ·  ·  ·  ·  ·
 ·  ●  ·  ·  ·  ·  ·  ·  ·
 ·  ·  ●  ·  ·  ·  ·  ·  ·
 ·  ·  ·  ●  ·  ·  ·  ·  ·
 ·  ·  ·  ·  ●  ·  ·  ·  ·
```

# S H O R E L I N E

**A calm way to drink enough water. The tide rises as you drink.**

Designed by Elvijs S. · built with Claude

</div>

---

## ▶ What is this

Shoreline is a water tracker for Android, made for a Nothing Phone (3a). Your daily goal is worked out from your body, your day and the weather, and a shoreline moves across the screen as you get closer to it. Log a drink with one tap in the app, on the home screen widget, in Quick Settings, or straight from a reminder.

Everything stays on your phone. No account, no ads.

---

## ◉ What's in it

### The goal
- **Science-backed:** Based on the Yamada et al. (Science, 2022) water-turnover equation: weight, age, activity, temperature, humidity and altitude. It tells you in plain words how the number was worked out.
- **Adjusts itself:** A quick morning check (urine colour) nudges the goal up or down after a few days.
- **Pregnancy and nursing:** EFSA's extra water (about +300 ml pregnant, +700 ml nursing).
- **Fluid limit:** If a doctor has told you to limit fluids, set it and Shoreline never asks for more.

### Logging
- **Your cups:** Three quick buttons with your own sizes (your mug, your bottle), plus Custom.
- **Your drinks:** Coffee, tea and friends built in, plus your own with their caffeine.
- **Bottle mode:** Set your bottle size, then log ¼, ½, ¾ or any amount with a slider.
- **Undo:** Tap to undo, hold to clear the day, tap again to restore.
- **Forgot one?** Add it to any day, at the right time, from History.

### Caffeine
- **Inner ring:** Shows today's caffeine against your limit (EFSA), turning red past it.
- **Wears off:** Bright dots are caffeine still active in your body; they fade over the day (half-life about 5 hours).
- **Tips:** For big single doses, and how much will still be active at bedtime.
- **Cutoff reminder:** Only on days you've actually had caffeine.

### Reminders
- **Only when useful:** While you're awake and behind pace. They back off when you're ahead and pause for two hours after more than a litre in an hour.
- **Log from the notification:** +your cup, **Custom** (type an amount right in the notification), or **Snooze 30 min**.
- **Night owls welcome:** Bedtimes after midnight work.
- **Weekly weight check:** A Monday nudge to keep the goal accurate.

### Home screen
- **Widgets:** Small (2×2: tap the ring to add a cup) and wide (4×2: the shoreline itself, moving across as you drink, with two cups). Both have **UNDO** and fill up when you tap.
- **Quick Settings tile:** Adds your middle cup and shows progress.
- **Themed icon:** Follows Nothing's monochrome icons.

### History
- **Streak:** Days in a row at your goal, plus your best.
- **Charts:** Week and month dot charts and a month calendar.
- **Day view:** Tap any day to see its drinks, remove one, or add one you forgot.

### Feel
- **The goal wave:** Reaching your goal sends a wave across the screen, leaving foam behind, with a soft wash sound.
- **Tilt:** The water sloshes towards the low side when you tilt the phone.
- **Rolling counter:** The percentage and "ml left" roll digit by digit, like a mechanical counter.

### The rest
- **Sunday recap:** Your week in one notification: goal days, streak and daily average.
- **Weather:** Fetched from Open-Meteo with a rough (~1 km) location, a few times a day, only if you turn it on.
- **Backup:** Export and import everything as one file.
- **Reduce motion:** Respects Android's "Remove animations."

---

## ⌕ Where to find things

| Looking for… | It's here |
|---|---|
| Log a drink | The cup buttons, the widget, the Quick Settings tile, or a reminder |
| Your cup sizes and own drinks | **Settings → Cups & drinks** |
| Weight, activity, fluid limit | **Settings → You** |
| Wake and sleep time, reminders | **Settings → Your day** |
| Fix yesterday | **History** → tap the day |
| How the goal is worked out | **Settings → About your goal** |
| Backup | **Settings → Backup** |

---

## ⬇ Getting it on your phone

1. **Push** to `main`. GitHub Actions runs the tests and builds the APK.
2. **Download:** Open **Releases** on your phone and grab the newest `Shoreline-N.apk`.
3. **Install:** Every build is signed with the same key (`keys/shoreline-debug.keystore`), so new versions install over the old one and keep your data.

### Coming from the first version?
The first version was signed with a different key every build, so Android needs one last uninstall. To keep your history:

1. In the **old** app: **Goal settings → Export backup**, and save the file.
2. Uninstall the old app, then install the new one.
3. Skip through the welcome screens, then **Settings → Backup → Import backup** and pick that file.

Your settings, today's drinks and all your daily history come across.

---

## ⚙ Under the hood

- **Screens:** Plain HTML, CSS and JavaScript modules in `www/`, run by Capacitor.
- **Native:** Java in `native/java/` owns the data and everything that runs without the app open: widgets, tile, reminders, notification buttons.
- **One source of truth:** Every drink, from anywhere, is written to the same timestamped log in `Store.java`, so the app, widgets and tile always agree, and drinks land on the day they were drunk.
- **Tests:** The goal, caffeine and waking-day math lives in `www/js/goal.js` and is tested on every build (`npm test`).

```
www/js/
├─ app.js        home screen, logging, start-up
├─ goal.js       the math (tested)
├─ store.js      talks to the native store; converts old backups
├─ state.js      shared state and saving
├─ ring.js       the dot ring        shore.js   the shoreline
├─ history.js    streaks, charts, day view
├─ settings.js   settings            fields.js  setting definitions
├─ welcome.js    first run           weather.js Open-Meteo
└─ tips.js       the line under the ring
native/java/
├─ Store.java          the drink log, history, profile
├─ ShorePlugin.java    bridge to the web app
├─ Scheduler.java      reminders, midnight refresh, nudges
├─ ActionReceiver.java every button and alarm
├─ Notify.java         notifications
└─ WidgetBase / SmallWidget / WideWidget / ShorelineTile
```

---

## ✎ Science notes

- **Not medical advice.** If you have a heart or kidney condition, follow your doctor.
- **The HDI term:** The Yamada equation also has a human development index (HDI) term that isn't included yet, until its coding is checked against the paper. If it applies, goals in high-HDI countries may run slightly low.

---

## ✦ Credits

- **Design & direction:** Elvijs S.
- **Built with:** Claude.
- **Fonts:** Doto and Space Grotesk, under the SIL Open Font License (`OFL-fonts.txt`).
- **Weather:** [Open-Meteo](https://open-meteo.com).
