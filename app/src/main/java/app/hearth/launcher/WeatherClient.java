package app.hearth.launcher;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Weather, forecast and air quality from Open-Meteo (free, no account or key).
 * The location is estimated once from the TV's public IP address via GeoJS.
 */
final class WeatherClient {

    /** One forecast hour. */
    static final class Hour {
        String label;   // "3 PM"
        int temp;
        int code;
        boolean day;
    }

    static final class Data {
        String city;
        int temp, feels, high, low, code, aqi = -1;
        boolean day;
        int sunriseMin = 7 * 60, sunsetMin = 19 * 60; // minutes after midnight
        final Hour[] hours = new Hour[6];
        boolean fahrenheit;
    }

    // Shared by every screen, so switching themes doesn't leave threads behind.
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static String lat, lon, city;

    private final boolean fahrenheit = "US".equals(Locale.getDefault().getCountry());

    void fetch(Consumer<Data> onResult) {
        executor.execute(() -> {
            try {
                if (lat == null) {
                    JSONObject geo = new JSONObject(get("https://get.geojs.io/v1/ip/geo.json"));
                    lat = geo.getString("latitude");
                    lon = geo.getString("longitude");
                    city = geo.optString("city", "");
                }
                JSONObject w = new JSONObject(get(String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s"
                                + "&current=temperature_2m,apparent_temperature,weather_code,is_day"
                                + "&hourly=temperature_2m,weather_code,is_day"
                                + "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset"
                                + "&timezone=auto&forecast_days=2&temperature_unit=%s",
                        lat, lon, fahrenheit ? "fahrenheit" : "celsius")));
                Data d = new Data();
                d.city = city;
                d.fahrenheit = fahrenheit;
                JSONObject c = w.getJSONObject("current");
                d.temp = (int) Math.round(c.getDouble("temperature_2m"));
                d.feels = (int) Math.round(c.getDouble("apparent_temperature"));
                d.code = c.getInt("weather_code");
                d.day = c.optInt("is_day", 1) == 1;

                JSONObject daily = w.getJSONObject("daily");
                d.high = (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
                d.low = (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
                d.sunriseMin = minutes(daily.getJSONArray("sunrise").getString(0));
                d.sunsetMin = minutes(daily.getJSONArray("sunset").getString(0));

                // Next six hours after the current one ("2026-10-23T14:15" -> hour "2026-10-23T14").
                JSONObject hourly = w.getJSONObject("hourly");
                JSONArray times = hourly.getJSONArray("time");
                String nowHour = c.getString("time").substring(0, 13);
                int start = 0;
                while (start < times.length() && !times.getString(start).startsWith(nowHour)) start++;
                for (int i = 0; i < 6; i++) {
                    int k = Math.min(start + 1 + i, times.length() - 1);
                    Hour h = new Hour();
                    h.label = hourLabel(times.getString(k));
                    h.temp = (int) Math.round(hourly.getJSONArray("temperature_2m").getDouble(k));
                    h.code = hourly.getJSONArray("weather_code").getInt(k);
                    h.day = hourly.getJSONArray("is_day").optInt(k, 1) == 1;
                    d.hours[i] = h;
                }

                try {
                    JSONObject aq = new JSONObject(get(String.format(Locale.US,
                            "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%s&longitude=%s"
                                    + "&current=us_aqi", lat, lon)));
                    d.aqi = aq.getJSONObject("current").optInt("us_aqi", -1);
                } catch (Exception ignored) {
                    // Air quality is a bonus; show the weather without it.
                }
                main.post(() -> onResult.accept(d));
            } catch (Exception e) {
                main.post(() -> onResult.accept(null));
            }
        });
    }

    static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }

    /** "2026-10-23T18:22" -> 1102 */
    private static int minutes(String iso) {
        return Integer.parseInt(iso.substring(11, 13)) * 60 + Integer.parseInt(iso.substring(14, 16));
    }

    /** "2026-10-23T15:00" -> "3 PM" */
    private static String hourLabel(String iso) {
        int h = Integer.parseInt(iso.substring(11, 13));
        return (h % 12 == 0 ? 12 : h % 12) + (h < 12 ? " AM" : " PM");
    }

    /** WMO weather codes, as used by Open-Meteo. */
    static String describe(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code <= 48) return "Fog";
        if (code <= 57) return "Drizzle";
        if (code <= 67) return "Rain";
        if (code <= 77) return "Snow";
        if (code <= 82) return "Showers";
        if (code <= 86) return "Snow showers";
        return "Thunderstorm";
    }

    /** US EPA AQI bands. */
    static String aqiLabel(int aqi) {
        if (aqi <= 50) return "Good";
        if (aqi <= 100) return "Moderate";
        if (aqi <= 150) return "Unhealthy for some";
        if (aqi <= 200) return "Unhealthy";
        if (aqi <= 300) return "Very unhealthy";
        return "Hazardous";
    }

}
