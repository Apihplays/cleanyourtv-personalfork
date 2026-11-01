package com.majnioui.tv.prayer;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * JAKIM's official prayer times for one JAKIM zone, from api.waktusolat.app (which serves the
 * E-Solat tables; no account or key). One call returns the whole month as epoch seconds, so the
 * times are whatever JAKIM published — never a calculation.
 */
public final class Waktusolat {
    /** The JSON keys for a prayer, in the app's display order (Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha). */
    public static final String[] KEYS = {"fajr", "syuruk", "dhuhr", "asr", "maghrib", "isha"};
    private static final String HOST = "https://api.waktusolat.app/v2/solat/";
    /** The month table cached alongside the app's files, by MainActivity and PrayerScheduler. */
    public static final String CACHE_FILE = "waktusolat-month.json";
    private static final String[] HIJRI_MONTHS = {"Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani",
            "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Sha'ban", "Ramadan", "Shawwal",
            "Dhu al-Qadah", "Dhu al-Hijjah"};

    private Waktusolat() {
    }

    public static String url(String zone) {
        return HOST + zone.toLowerCase(Locale.ROOT);
    }

    /** One day's times from the month table, in {@link PrayerScheduler#NAMES} order. */
    public static final class Day {
        public String[] times;
        public int[] minutes;
        /** The Hijri date as the API gives it, "1448-05-21". */
        public String hijriIso;
    }

    /**
     * One day's entry of a month table, converted to the given timezone. The extra keys (imsak,
     * dhuha) are ignored. Throws when the day is not in the table, whole.
     */
    public static Day parse(JSONObject root, int dayOfMonth, TimeZone tz) throws Exception {
        JSONObject entry = entry(root, dayOfMonth);
        if (entry == null) {
            throw new IllegalStateException("day " + dayOfMonth + " is not in the table");
        }
        Day d = new Day();
        d.times = new String[KEYS.length];
        d.minutes = new int[KEYS.length];
        for (int i = 0; i < KEYS.length; i++) {
            Calendar c = Calendar.getInstance(tz);
            c.setTimeInMillis(entry.getLong(KEYS[i]) * 1000L);
            d.minutes[i] = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
            d.times[i] = String.format(Locale.ROOT, "%02d:%02d",
                    c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
        }
        d.hijriIso = entry.getString("hijri");
        return d;
    }

    /** "1448-05-21" → "21 Jumada al-Awwal 1448 AH". */
    public static String hijri(String iso) {
        String[] parts = iso.split("-");
        int month;
        try {
            month = Integer.parseInt(parts[1]);
        } catch (Exception e) {
            throw new IllegalArgumentException("bad hijri: " + iso, e);
        }
        if (month < 1 || month > 12) throw new IllegalArgumentException("bad month: " + iso);
        return Integer.parseInt(parts[2]) + " " + HIJRI_MONTHS[month - 1] + " " + parts[0] + " AH";
    }

    /** Whether this month table holds the given day whole, i.e. it needs no refetch. */
    public static boolean coversToday(JSONObject root, int year, int monthNumber, int dayOfMonth) {
        if (root.optInt("year") != year || root.optInt("month_number") != monthNumber) return false;
        return entry(root, dayOfMonth) != null;
    }

    /** The day's entry, or null; an entry missing any prayer key counts as absent. */
    private static JSONObject entry(JSONObject root, int dayOfMonth) {
        if (dayOfMonth < 1) throw new IllegalArgumentException("day: " + dayOfMonth);
        org.json.JSONArray prayers = root.optJSONArray("prayers");
        for (int p = 0; prayers != null && p < prayers.length(); p++) {
            JSONObject e = prayers.optJSONObject(p);
            if (e.optInt("day") != dayOfMonth) continue;
            for (String k : KEYS) {
                if (!e.has(k)) return null;
            }
            return e;
        }
        return null;
    }

    /** The month table over the network; throws on any failure. */
    public static String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    /** The month table cached on the TV's storage, or null when there is none. */
    public static String readCache(File dir) {
        File f = new File(dir, CACHE_FILE);
        if (!f.isFile()) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    public static void writeCache(File dir, String json) throws Exception {
        FileOutputStream out = new FileOutputStream(new File(dir, CACHE_FILE));
        out.write(json.getBytes("UTF-8"));
        out.close();
    }
}
