package com.majnioui.tv.prayer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.json.JSONObject;

import java.io.File;
import java.util.TimeZone;

/**
 * The JAKIM month table, pinned against the live TRG01 response of 2026-11-01.
 */
public class WaktusolatTest {
    private static final TimeZone TZ = TimeZone.getTimeZone("Asia/Kuala_Lumpur");
    private static final String FIXTURE =
            "{\"zone\":\"TRG01\",\"year\":2026,\"month\":\"NOV\",\"month_number\":11,\"prayers\":["
            + "{\"day\":1,\"hijri\":\"1448-05-21\",\"imsak\":1793482380,\"fajr\":1793482980,"
            + "\"syuruk\":1793487180,\"dhuha\":1793488680,\"dhuhr\":1793508780,\"asr\":1793520780,"
            + "\"maghrib\":1793530200,\"isha\":1793534460},"
            + "{\"day\":2,\"hijri\":\"1448-05-22\",\"imsak\":1793568780,\"fajr\":1793569380,"
            + "\"syuruk\":1793573580,\"dhuha\":1793575080,\"dhuhr\":1793595180,\"asr\":1793607240,"
            + "\"maghrib\":1793616600,\"isha\":1793620860}]}";

    private static JSONObject fixture() throws Exception {
        return new JSONObject(FIXTURE);
    }

    @Test
    public void day1ParsesToLocalTimes() throws Exception {
        Waktusolat.Day d = Waktusolat.parse(fixture(), 1, TZ);
        assertArrayEquals(new int[]{343, 413, 773, 973, 1130, 1201}, d.minutes);
        assertArrayEquals(new String[]{"05:43", "06:53", "12:53", "16:13", "18:50", "20:01"}, d.times);
        assertEquals("1448-05-21", d.hijriIso);
    }

    @Test
    public void day2FajrIsFiveFortyThree() throws Exception {
        // The live table serves the same 05:43 fajr on 2 Nov as on 1 Nov.
        assertEquals(343, Waktusolat.parse(fixture(), 2, TZ).minutes[0]);
    }

    @Test(expected = IllegalStateException.class)
    public void missingDayThrows() throws Exception {
        Waktusolat.parse(fixture(), 3, TZ);
    }

    @Test(expected = Exception.class)
    public void missingKeyThrows() throws Exception {
        // Day 1 without maghrib: a shape change must raise, not show a wrong time.
        JSONObject cut = new JSONObject(FIXTURE);
        cut.getJSONArray("prayers").getJSONObject(0).remove("maghrib");
        Waktusolat.parse(cut, 1, TZ);
    }

    @Test
    public void hijriRendersMonthName() {
        assertEquals("21 Jumada al-Awwal 1448 AH", Waktusolat.hijri("1448-05-21"));
    }

    @Test
    public void urlIsLowercaseZone() {
        assertEquals("https://api.waktusolat.app/v2/solat/trg01", Waktusolat.url("TRG01"));
    }

    @Test
    public void coversTodayMatchesYearMonthAndDay() throws Exception {
        JSONObject f = fixture();
        assertTrue(Waktusolat.coversToday(f, 2026, 11, 1));
        assertFalse(Waktusolat.coversToday(f, 2025, 11, 1));
        assertFalse(Waktusolat.coversToday(f, 2026, 12, 1));
        assertFalse(Waktusolat.coversToday(f, 2026, 11, 3));
    }

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void zoneCatalogIsPopulated() {
        assertTrue(Waktusolat.ZONES.length > 10);
        boolean found = false;
        for (Waktusolat.Zone z : Waktusolat.ZONES) {
            if ("TRG01".equals(z.code)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }
}
