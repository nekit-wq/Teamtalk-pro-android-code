package org.nekit.ttproplus.gui;
import org.nekit.ttproplus.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class IpGeoLocator {

    private static final String CACHE_PREFIX = "geo_";
    private static final Map<String, String> memoryCache = new HashMap<>();

    public interface LocationCallback {
        void onLocationResult(String location);
    }

    public static void getLocation(String ip, Context context, LocationCallback callback) {
        if (ip == null || ip.isEmpty() || ip.equals("127.0.0.1") || ip.equals("localhost")) {
            callback.onLocationResult(null);
            return;
        }

        String cached = memoryCache.get(ip);
        if (cached != null) {
            callback.onLocationResult(cached);
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String saved = prefs.getString(CACHE_PREFIX + ip, null);
        if (saved != null) {
            memoryCache.put(ip, saved);
            callback.onLocationResult(saved);
            return;
        }

        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                try {
                    URL url = new URL("http://ip-api.com/json/" + ip + "?fields=country,city");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setRequestProperty("User-Agent", "TeamTalkAndroid");

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    String json = response.toString();
                    String country = extractJsonValue(json, "country");
                    String city = extractJsonValue(json, "city");

                    if (country != null && city != null && !country.isEmpty()) {
                        String result = city + ", " + country;
                        memoryCache.put(ip, result);
                        prefs.edit().putString(CACHE_PREFIX + ip, result).apply();
                        return result;
                    } else if (country != null && !country.isEmpty()) {
                        memoryCache.put(ip, country);
                        prefs.edit().putString(CACHE_PREFIX + ip, country).apply();
                        return country;
                    }
                } catch (Exception e) {
                    return null;
                }
                return null;
            }

            @Override
            protected void onPostExecute(String result) {
                callback.onLocationResult(result);
            }
        }.execute();
    }

    private static String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : null;
    }
}
