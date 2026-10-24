package app.hearth.launcher;

import java.util.Calendar;

/** A few well-known US holidays, computed so it works for any year. */
final class Holidays {

    private Holidays() {
    }

    static String name(Calendar c) {
        int m = c.get(Calendar.MONTH), d = c.get(Calendar.DAY_OF_MONTH);
        int dow = c.get(Calendar.DAY_OF_WEEK), nth = (d - 1) / 7 + 1;
        boolean last = d + 7 > c.getActualMaximum(Calendar.DAY_OF_MONTH);
        if (m == Calendar.JANUARY && d == 1) return "New Year's Day";
        if (m == Calendar.JANUARY && dow == Calendar.MONDAY && nth == 3) return "MLK Day";
        if (m == Calendar.FEBRUARY && d == 14) return "Valentine's Day";
        if (m == Calendar.FEBRUARY && dow == Calendar.MONDAY && nth == 3) return "Presidents' Day";
        if (m == Calendar.MAY && dow == Calendar.MONDAY && last) return "Memorial Day";
        if (m == Calendar.JUNE && d == 19) return "Juneteenth";
        if (m == Calendar.JULY && d == 4) return "Independence Day";
        if (m == Calendar.SEPTEMBER && dow == Calendar.MONDAY && nth == 1) return "Labor Day";
        if (m == Calendar.OCTOBER && d == 31) return "Halloween";
        if (m == Calendar.NOVEMBER && d == 11) return "Veterans Day";
        if (m == Calendar.NOVEMBER && dow == Calendar.THURSDAY && nth == 4) return "Thanksgiving";
        if (m == Calendar.DECEMBER && d == 24) return "Christmas Eve";
        if (m == Calendar.DECEMBER && d == 25) return "Christmas";
        if (m == Calendar.DECEMBER && d == 31) return "New Year's Eve";
        return null;
    }

    /** "Halloween in 8 days", "Christmas tomorrow", or "Halloween today". */
    static String next(Calendar from) {
        Calendar c = (Calendar) from.clone();
        for (int i = 0; i < 366; i++) {
            String n = name(c);
            if (n != null) {
                return n + (i == 0 ? " today" : i == 1 ? " tomorrow" : " in " + i + " days");
            }
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return "";
    }
}
