package com.tpkarras.mirror2rearultra;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * The hours in which Always on Display gives way to a dark panel.
 *
 * <p>Either from sunset to sunrise where the phone is, or between two times
 * of day. Outside them AOD dims as it always has; inside them the panel goes
 * all the way out instead, and a touch still brings it back.
 *
 * <p>The sun is worked out here rather than taken from the weather forecast:
 * the forecast is only fetched while a weather widget is on, and the panel
 * has to know when night falls whether or not one is.
 */
final class QuietHours {
    enum Mode { OFF, NIGHT, SCHEDULE }

    /** No change coming that a clock could see. */
    static final long NEVER = Long.MAX_VALUE;

    private static final String PLACE_PREFS = "panel_quiet_place";
    private static final String LATITUDE = "latitude";
    private static final String LONGITUDE = "longitude";
    /** Set when the place came from the phone's own fix, not the weather city. */
    private static final String FROM_DEVICE = "from_device";

    private static final long DAY_MILLIS = 86_400_000L;
    /** Julian day of the Unix epoch. */
    private static final double JULIAN_EPOCH = 2440587.5;
    /** Julian day of 2000-01-01 12:00 UTC, which the solar terms count from. */
    private static final double J2000 = 2451545.0;

    /** Whether the panel should be dark, and when that next changes. */
    static final class Verdict {
        final boolean quiet;
        final long nextChangeMillis;

        Verdict(boolean quiet, long nextChangeMillis) {
            this.quiet = quiet;
            this.nextChangeMillis = nextChangeMillis;
        }
    }

    /** One day's sun: when it rises and sets, or that it does neither. */
    static final class Sun {
        final long riseMillis;
        final long setMillis;
        /** Above the horizon all day: the midnight sun. */
        final boolean alwaysUp;
        /** Below it all day: the polar night. */
        final boolean alwaysDown;

        Sun(long riseMillis, long setMillis, boolean alwaysUp, boolean alwaysDown) {
            this.riseMillis = riseMillis;
            this.setMillis = setMillis;
            this.alwaysUp = alwaysUp;
            this.alwaysDown = alwaysDown;
        }
    }

    /** What the stored settings say about this moment. */
    static Verdict now(Context context, long nowMillis) {
        return evaluate(DashboardWidgetLayout.loadQuietMode(context),
                DashboardWidgetLayout.quietFromMinutes(context),
                DashboardWidgetLayout.quietToMinutes(context),
                place(context), nowMillis, TimeZone.getDefault());
    }

    /**
     * @param place latitude and longitude in degrees, or null when unknown -
     *     night then never falls, rather than falling at a guess
     */
    static Verdict evaluate(Mode mode, int fromMinutes, int toMinutes,
            @Nullable double[] place, long nowMillis, TimeZone zone) {
        switch (mode) {
            case SCHEDULE:
                return schedule(fromMinutes, toMinutes, nowMillis, zone);
            case NIGHT:
                return place == null
                        ? new Verdict(false, NEVER)
                        : night(place[0], place[1], nowMillis, zone);
            default:
                return new Verdict(false, NEVER);
        }
    }

    private static Verdict schedule(int from, int to, long nowMillis, TimeZone zone) {
        if (from == to) {
            return new Verdict(false, NEVER);
        }
        Calendar calendar = Calendar.getInstance(zone);
        calendar.setTimeInMillis(nowMillis);
        int now = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        boolean quiet = PanelTriggers.within(now, from, to);
        int edge = quiet ? to : from;
        calendar.set(Calendar.HOUR_OF_DAY, edge / 60);
        calendar.set(Calendar.MINUTE, edge % 60);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= nowMillis) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        return new Verdict(quiet, calendar.getTimeInMillis());
    }

    private static Verdict night(double latitude, double longitude, long nowMillis,
            TimeZone zone) {
        Sun today = sunOn(latitude, longitude, localNoon(nowMillis, zone, 0));
        long midnight = localNoon(nowMillis, zone, 1) - DAY_MILLIS / 2;
        if (today.alwaysUp || today.alwaysDown) {
            // Nothing to wait for today; look again once it is tomorrow.
            return new Verdict(today.alwaysDown, midnight);
        }
        if (nowMillis < today.riseMillis) {
            return new Verdict(true, today.riseMillis);
        }
        if (nowMillis < today.setMillis) {
            return new Verdict(false, today.setMillis);
        }
        Sun tomorrow = sunOn(latitude, longitude, localNoon(nowMillis, zone, 1));
        return new Verdict(true, tomorrow.alwaysUp || tomorrow.alwaysDown
                ? midnight : tomorrow.riseMillis);
    }

    /** Today's sun where the place is, for showing beside the setting. */
    static Sun today(double[] place, long nowMillis, TimeZone zone) {
        return sunOn(place[0], place[1], localNoon(nowMillis, zone, 0));
    }

    /** Noon on the phone's clock, the given number of days from today. */
    private static long localNoon(long nowMillis, TimeZone zone, int days) {
        Calendar calendar = Calendar.getInstance(zone);
        calendar.setTimeInMillis(nowMillis);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /**
     * Sunrise and sunset for the solar day whose noon is nearest the given
     * instant, by the sunrise equation.
     *
     * <p>Good to a minute or two away from the poles, which is far finer
     * than anyone can tell a panel going dark by. The sun counts as risen
     * when its upper edge clears the horizon through the usual refraction,
     * the same moment the forecast and every almanac call sunrise.
     *
     * @param longitude east positive
     */
    static Sun sunOn(double latitude, double longitude, long noonMillis) {
        double julian = noonMillis / (double) DAY_MILLIS + JULIAN_EPOCH;
        double day = Math.round(julian - J2000 + longitude / 360.0);
        double meanSolarDay = day - longitude / 360.0;
        double anomaly = normalise(357.5291 + 0.98560028 * meanSolarDay);
        double m = Math.toRadians(anomaly);
        double centre = 1.9148 * Math.sin(m) + 0.0200 * Math.sin(2 * m)
                + 0.0003 * Math.sin(3 * m);
        double eclipticLongitude = Math.toRadians(
                normalise(anomaly + centre + 180.0 + 102.9372));
        double transit = J2000 + meanSolarDay + 0.0053 * Math.sin(m)
                - 0.0069 * Math.sin(2 * eclipticLongitude);
        double declinationSine = Math.sin(eclipticLongitude)
                * Math.sin(Math.toRadians(23.4397));
        double declination = Math.asin(declinationSine);
        double phi = Math.toRadians(latitude);
        double hourAngleCosine = (Math.sin(Math.toRadians(-0.833))
                - Math.sin(phi) * declinationSine)
                / (Math.cos(phi) * Math.cos(declination));
        if (hourAngleCosine > 1) {
            return new Sun(0, 0, false, true);
        }
        if (hourAngleCosine < -1) {
            return new Sun(0, 0, true, false);
        }
        double halfDay = Math.toDegrees(Math.acos(hourAngleCosine)) / 360.0;
        return new Sun(toMillis(transit - halfDay), toMillis(transit + halfDay),
                false, false);
    }

    private static long toMillis(double julian) {
        return Math.round((julian - JULIAN_EPOCH) * DAY_MILLIS);
    }

    private static double normalise(double degrees) {
        double value = degrees % 360.0;
        return value < 0 ? value + 360.0 : value;
    }

    /** Latitude and longitude the sun is worked out for, or null. */
    @Nullable
    static double[] place(Context context) {
        SharedPreferences prefs = placePrefs(context);
        if (!prefs.contains(LATITUDE) || !prefs.contains(LONGITUDE)) {
            return null;
        }
        return new double[]{prefs.getFloat(LATITUDE, 0f), prefs.getFloat(LONGITUDE, 0f)};
    }

    /**
     * Takes the weather city's place, unless the phone has said where it is.
     *
     * <p>The phone's own fix wins because it follows the phone about; the
     * city is what there is when location is off-limits.
     */
    static void offerWeatherPlace(Context context, double latitude, double longitude) {
        if (placePrefs(context).getBoolean(FROM_DEVICE, false)) {
            return;
        }
        savePlace(context, latitude, longitude, false);
    }

    /**
     * Takes the phone's last known position, if it may be read.
     *
     * <p>Only the last known one: nothing is switched on to look for a new
     * fix. Sunset moves by minutes over a hundred kilometres, so whatever
     * any app last asked for is close enough.
     *
     * @return whether a position was found
     */
    static boolean refreshPlaceFromDevice(Context context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        LocationManager manager = context.getSystemService(LocationManager.class);
        if (manager == null) {
            return false;
        }
        Location newest = null;
        for (String provider : manager.getProviders(false)) {
            try {
                Location location = manager.getLastKnownLocation(provider);
                if (location != null && (newest == null || location.getTime() > newest.getTime())) {
                    newest = location;
                }
            } catch (SecurityException | IllegalArgumentException ignored) {
                // A provider the permission does not reach; the others may.
            }
        }
        if (newest == null) {
            return false;
        }
        savePlace(context, newest.getLatitude(), newest.getLongitude(), true);
        return true;
    }

    private static void savePlace(Context context, double latitude, double longitude,
            boolean fromDevice) {
        placePrefs(context).edit()
                .putFloat(LATITUDE, (float) latitude)
                .putFloat(LONGITUDE, (float) longitude)
                .putBoolean(FROM_DEVICE, fromDevice)
                .apply();
    }

    /**
     * Kept apart from the panel's settings, which a backup carries and a
     * restore clears: where the phone happens to be is not a setting.
     */
    private static SharedPreferences placePrefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PLACE_PREFS, Context.MODE_PRIVATE);
    }

    private QuietHours() {}
}
