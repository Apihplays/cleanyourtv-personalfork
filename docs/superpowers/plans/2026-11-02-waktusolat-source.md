# Official JAKIM prayer times (waktusolat source) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "waktusolat" data source to `addons/prayer-times/` that serves JAKIM's official Kuala Terengganu (zone TRG01) times from `api.waktusolat.app`, keeping Aladhan as the untouched default.

**Architecture:** A pure-Java class `Waktusolat` owns the URL, month-table parsing (epoch seconds → HH:mm times and minutes-after-midnight), Hijri formatting, and month-file caching; `MainActivity` and `PrayerScheduler` branch on a new `source` config string: `aladhan` keeps today's paths bit-for-bit, `waktusolat` runs the cache-or-fetch flow. The UI, scheduler, alarms and launcher card are untouched.

**Tech stack:** Java 17, Android `org.json` (already used), JUnit 4 local tests, Gradle wrapper in the add-on.

**Spec:** `docs/superpowers/specs/2026-11-02-waktusolat-design.md`

## Global Constraints

- Plain Java only in the add-on; no new runtime dependency. Only test-scope deps allowed (`testImplementation`), matching the existing `addons/weather/app/build.gradle` pattern.
- Default `source` stays `aladhan`: the new behaviour is off until config turns it on, and anyone not touching `config.xml` sees today's behaviour unchanged.
- `minSdk` 26, `targetSdk`/`compileSdk` 34 unchanged; no AndroidManifest changes (the app already has `INTERNET`).
- JAKIM zone case-insensitive lowercase in config (`trg01`); the API URL is built lowercase.
- The status/status-line copy and the six-entry display order must not change: entries stay Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha; the waktusolat keys map positionally to `fajr, syuruk, dhuhr, asr, maghrib, isha`.
- Work in an isolated worktree (per superpowers:using-git-worktrees at execution time); commits per task, YAGNI/DRY/TDD.

## Review Focus

- **A day missing from the month table** (month rollover before JAKIM publishes, or February's short months on days 29–31): the app must show the offline/error status, never another day's times. → Task 1, test `missingDayThrows`.
- **UTC drift** (epochs read as anything but Malaysia local +08:00): parsing takes the TimeZone as a parameter, so a wrong shift is impossible to hide. → Task 1, test pins minutes with `Asia/Kuala_Lumpur`; runtime always passes `TimeZone.getDefault()`.
- **A stale cache spanning a month rollover**: `coversToday` must say false for last month so the next `load()` refetches; offline it falls back to cache even when that cache is stale, with the status line naming JAKIM TRG01 and the fetch time. → Task 1, tests `coversToday` matrix.
- **API shape change** (a prayer key removed): parse must raise to the existing `unexpected_error` handling, not crash the page. → Task 1, test `missingKeyThrows`.
- **Alarms silently lost after `am force-stop`** (AlarmManager clears pending intents): no code change can fix it; the README already warns — leave the warning, and in Task 4 extend the warning text to mention the waktusolat source too. Test: none (manual TV check).

---

### Task 1: `Waktusolat` — pure parsing, Hijri, coverage, cache

**Files:**
- Create: `addons/prayer-times/app/src/main/java/com/majnioui/tv/prayer/Waktusolat.java`
- Create: `addons/prayer-times/app/src/test/java/com/majnioui/tv/prayer/WaktusolatTest.java`
- Modify: `addons/prayer-times/app/build.gradle`

**Interfaces:**
- Consumes: nothing (pure Java; `org.json`, `java.net.HttpURLConnection`).
- Produces (used by Tasks 2 and 3):
  - `public static final String[] KEYS` — `{"fajr","syuruk","dhuhr","asr","maghrib","isha"}`, positional to `NAMES` (Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha).
  - `public static String url(String zone)` — `"https://api.waktusolat.app/v2/solat/" + zone.toLowerCase(Locale.ROOT)`.
  - `public static final class Day { public String[] times; public int[] minutes; public String hijriIso; }`
  - `public static Day parse(JSONObject root, int dayOfMonth, TimeZone tz) throws Exception` — finds the entry whose `prayers[].day` equals `dayOfMonth` (throw `IllegalStateException` when absent); each of `KEYS` read as epoch seconds, `minutes[i] = cal.get(HOUR_OF_DAY)*60 + MINUTE` via `Calendar.getInstance(tz)`, `times[i] = String.format(Locale.ROOT,"%02d:%02d", h, m)`; extra keys (`imsak`, `dhuha`) ignored; `hijriIso` = the entry's `hijri` string.
  - `public static String hijri(String iso)` — `"1448-05-21"` → `"21 Jumada al-Awwal 1448 AH"` (month names array: Muharram, Safar, Rabi al-Awwal, Rabi al-Thani, Jumada al-Awwal, Jumada al-Thani, Rajab, Sha'ban, Ramadan, Shawwal, Dhu al-Qadah, Dhu al-Hijjah; a month number outside 0–11 throws).
  - `public static boolean coversToday(JSONObject root, int year, int monthNumber, int dayOfMonth)` — true iff `root.year == year && root.month_number == monthNumber` and an entry with `day == dayOfMonth` exists.
  - `public static String fetch(String url) throws Exception` — same shape as `MainActivity.fetch` (8 s timeouts, UTF-8 read).
  - `public static String readCache(File dir)` — content of `dir/waktusolat-month.json`, or `null` when absent.
  - `public static void writeCache(File dir, String json) throws Exception` — overwrites that file.
- Test fixture (embedded in the test, abridged from the live response of 2026-11-01):
  ```json
  {"zone":"TRG01","year":2026,"month":"NOV","month_number":11,"prayers":[
    {"day":1,"hijri":"1448-05-21","imsak":1793482380,"fajr":1793482980,"syuruk":1793487180,"dhuha":1793488680,"dhuhr":1793508780,"asr":1793520780,"maghrib":1793530200,"isha":1793534460},
    {"day":2,"hijri":"1448-05-22","imsak":1793568780,"fajr":1793569380,"syuruk":1793573580,"dhuha":1793575080,"dhuhr":1793595180,"asr":1793607240,"maghrib":1793616600,"isha":1793620860}
  ]}
  ```

- [ ] **Step 1: Write the failing tests**

In `WaktusolatTest.java`, with fixture loaded via `new JSONObject(FIXTURE)`, all times parsed with `TimeZone.getTimeZone("Asia/Kuala_Lumpur")`:

```java
@Test public void day1ParsesToLocalTimes() {
    Waktusolat.Day d = Waktusolat.parse(fixture(), 1, TZ);
    assertArrayEquals(new int[]{343, 413, 773, 973, 1130, 1201}, d.minutes);
    assertArrayEquals(new String[]{"05:43","06:53","12:53","16:13","18:50","20:01"}, d.times);
    assertEquals("1448-05-21", d.hijriIso);
}
@Test public void day2FajrIsFiveFortyThree() {   // the live table repeats 05:43 on 2 Nov
    assertEquals(343, Waktusolat.parse(fixture(), 2, TZ).minutes[0]);
}
@Test public void missingDayThrows() { Waktusolat.parse(fixture(), 3, TZ); }
@Test public void missingKeyThrows() { /* same fixture minus the "maghrib" key of day 1 */ }
@Test public void hijriRendersMonthName() {
    assertEquals("21 Jumada al-Awwal 1448 AH", Waktusolat.hijri("1448-05-21"));
}
@Test public void coversTodayMatchesYearMonthAndDay() {
    assertTrue(Waktusolat.coversToday(f, 2026, 11, 1));
    assertFalse(Waktusolat.coversToday(f, 2025, 11, 1));
    assertFalse(Waktusolat.coversToday(f, 2026, 12, 1));
    assertFalse(Waktusolat.coversToday(f, 2026, 11, 3));
}
@Test public void cacheRoundTrip() { /* @Rule TemporaryFolder: writeCache then readCache returns the same string; readCache on a fresh dir returns null */ }
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd addons/prayer-times && ./gradlew --no-daemon testReleaseUnitTest`
Expected: FAIL — `Waktusolat` does not exist yet (add `testImplementation 'junit:junit:4.13.2'` to `app/build.gradle` first, same as `addons/weather/app/build.gradle`).

- [ ] **Step 3: Implement `Waktusolat` per the Interfaces block**

Signatures and mapping above decide everything; the epoch→minutes algorithm is pinned in the test. Keep the class free of any Android imports (only `org.json` and `java.*`), so local unit tests need no stubs.

- [ ] **Step 4: Run tests, expect PASS, run lint**
- [ ] **Step 5: Commit**

```bash
git add addons/prayer-times/app/src/main/java/com/majnioui/tv/prayer/Waktusolat.java \
        addons/prayer-times/app/src/test/java/com/majnioui/tv/prayer/WaktusolatTest.java \
        addons/prayer-times/app/build.gradle
git commit -m "prayer-times: pure parser for JAKIM's waktusolat month table"
```

---

### Task 2: `MainActivity` switch on `source`

**Files:**
- Modify: `addons/prayer-times/app/src/main/res/values/config.xml` (add)
- Modify: `addons/prayer-times/app/src/main/java/com/majnioui/tv/prayer/MainActivity.java:131-191` (the `load()`/`parse()` area) and `:248` (status line)

**Interfaces:**
- Consumes: `Waktusolat.url/parse/hijri/coversToday/readCache/writeCache/fetch` (Task 1).
- Produces: build behaviour only (no new public API).

- [ ] **Step 1: Add the two config strings (defaults keep today's behaviour)**

```xml
<!-- Data source: "aladhan" (coordinates + method) or "waktusolat" (JAKIM E-Solat table by zone). -->
<string name="source" translatable="false">aladhan</string>
<!-- The JAKIM zone, e.g. trg01 = Kuala Terengganu/Marang; used only when source is "waktusolat". -->
<string name="zone" translatable="false">trg01</string>
```

- [ ] **Step 2: In `load()`, branch first: when `getString(R.string.source).equals("waktusolat")`, run `loadSolat()` and return**

`loadSolat()` runs on the existing background thread pattern with the same `loading`/`loadedFor` guards: read `Waktusolat.readCache(getFilesDir())` → if not null and `coversToday(parse, year, monthNumber, today's day from TimeZone.getDefault())` → parse from cache, else `Waktusolat.fetch(Waktusolat.url(zone))` → `writeCache` on success → `Waktusolat.parse(..., TimeZone.getDefault())`; on `Exception` post `offline` status exactly like the Aladhan miss. On success post, assign `minutes`, `times`, then the same `PrayerScheduler.saveTimingsAndSchedule(...)/cancel(...)` block `load()` uses (keep the exact call,Parameters from `MainActivity.java:156-159`), then `render()`.

- [ ] **Step 3: Dates and status lines for the waktusolat case**

Dates text unchanged except the Hijri half comes from `Waktusolat.hijri(d.hijriIso)`. In `render()`, replace the hardcoded `"Aladhan"` on line 248 with a source label: `"JAKIM · " + zone.toUpperCase(Locale.ROOT)` when source is waktusolat, else today's `"Aladhan" + methodName` string exactly as it is.

- [ ] **Step 4: Verify**

Run: `cd addons/prayer-times && ./gradlew --no-daemon testReleaseUnitTest assembleRelease`
Expected: PASS/BUILD SUCCESSFUL; APK at `app/build/outputs/apk/release/app-release.apk`.

- [ ] **Step 5: Commit**

```bash
git add addons/prayer-times/app/src/main/res/values/config.xml \
        addons/prayer-times/app/src/main/java/com/majnioui/tv/prayer/MainActivity.java
git commit -m "prayer-times: a waktusolat (JAKIM) source switch for the page"
```

---

### Task 3: `PrayerScheduler` background fetch follows the source

**Files:**
- Modify: `addons/prayer-times/app/src/main/java/com/majnioui/tv/prayer/PrayerScheduler.java:220-267` (`fetchTimingsAndSchedule`)

**Interfaces:**
- Consumes: Task 1 signatures; Task 2's `"waktusolat"` string and `R.string.source/zone`.
- Produces: build behaviour only.

- [ ] **Step 1: In `fetchTimingsAndSchedule`, when the source is `"waktusolat"`, resolve today's `times`/`minutes` from the same cache-or-fetch flow as Task 2 and call `saveTimingsAndSchedule(appContext, day, NAMES, times, minutes, appContext.getString(R.string.place))`; the Aladhan branch keeps every line it has today. Share nothing new: the cache-or-fetch reads/writes go through `Waktusolat.readCache/coversToday/fetch/writeCache`, with the cache file living in `appContext.getFilesDir()`.**

- [ ] **Step 2: Verify**

Run: `cd addons/prayer-times && ./gradlew --no-daemon testReleaseUnitTest assembleRelease`
Expected: PASS/BUILD SUCCESSFUL. Manual TV check (after the real-TV task): with `reminders=true`, the alert fires at the published minute.

- [ ] **Step 3: Commit**

```bash
git add addons/prayer-times/app/src/main/java/com/majnioui/tv/prayer/PrayerScheduler.java
git commit -m "prayer-times: background reminders refresh from the waktusolat source"
```

---

### Task 4: This TV's config, README, full gate

**Files:**
- Modify: `addons/prayer-times/app/src/main/res/values/config.xml:4-13` (this TV's values)
- Modify: `addons/prayer-times/README.md` (Data and Permissions notes)

**Interfaces:** none new.

- [ ] **Step 1: Set this TV's personal values in `config.xml`** — `latitude 5.3302`, `longitude 103.1408`, `place Kuala Terengganu`, `source waktusolat`, `zone trg01` (coordinates stay only as Aladhan's fallback values; commit message marks the personal config). Leave `reminders` off unless the human asks at the plan review.

- [ ] **Step 2: README** — under "**Data**", document: `source` config, `waktusolat` = JAKIM's official E-Solat table via `api.waktusolat.app`, the `zone` string, the month cache file, and keep the existing force-stop/alarms warning covering both sources. Permissions table gains `INTERNET` > "Aladhan, or JAKIM E-Solat when `source=waktusolat`".

- [ ] **Step 3: Full verification, as CI runs it**

Run: `cd addons/prayer-times && ./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease`
Expected: all pass, zero lint errors.

- [ ] **Step 4: Real-TV install and verify (needs the TV on and reachable)**

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --push addons/prayer-times/app/build/outputs/apk/release/app-release.apk /data/local/tmp/addon.apk
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk'
```
Screenshot with `tools/tv.py --screenshot shots/waktusolat.png`, then compare two cards against `curl https://api.waktusolat.app/v2/solat/trg01 | jq '.prayers[<today>-1]'` — status must read `JAKIM · TRG01`. Note: the repo's own `.gitignore` covers anything under `backup/`, `shots/`, or `work/`.

- [ ] **Step 5: Commit and hand off** — mention the tested TV/model + Android version in the commit body; the PR body requires screenshots and the "Tested on" line per `CONTRIBUTING.md`.
