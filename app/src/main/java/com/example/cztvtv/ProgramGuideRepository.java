package com.example.cztvtv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ProgramGuideRepository implements ProgramGuideManager.Source {
    private static final String API = "https://p.cztv.com/api/paas/";

    @Override
    public Map<String, String> loadCurrentPrograms() throws IOException, JSONException {
        return parseCurrentPrograms(read(API + "channel/tv"));
    }

    @Override
    public ProgramGuide loadSchedule(String code, String date) throws IOException, JSONException {
        if (!code.matches("[0-9]+") || !date.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("Invalid schedule channel or date");
        }
        return parseSchedule(date, read(API + "program/" + code + "/" + date));
    }

    static Map<String, String> parseCurrentPrograms(String body) throws JSONException {
        JSONArray list = new JSONObject(body).getJSONObject("content").getJSONArray("list");
        Map<String, String> programs = new LinkedHashMap<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null || item.isNull("station_code") || item.isNull("station_playing")) {
                continue;
            }
            String code = item.optString("station_code", "").trim();
            String title = item.optString("station_playing", "").trim();
            if (!code.isEmpty() && !title.isEmpty()) {
                programs.put(code, title);
            }
        }
        if (programs.isEmpty()) {
            throw new JSONException("No current program information");
        }
        return programs;
    }

    static ProgramGuide parseSchedule(String date, String body) throws JSONException {
        JSONArray channels = new JSONObject(body).getJSONObject("content").getJSONArray("list");
        // This endpoint returns one station; an empty station list means no published schedule.
        if (channels.length() == 0) {
            return new ProgramGuide(date, new ArrayList<>());
        }
        JSONArray list = channels.getJSONObject(0).getJSONArray("list");
        List<ProgramGuide.Program> programs = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item != null && !item.isNull("program_title")) {
                programs.add(new ProgramGuide.Program(
                        item.optString("program_title", ""),
                        item.optLong("play_time", -1),
                        item.optLong("duration", 0)
                ));
            }
        }
        ProgramGuide guide = new ProgramGuide(date, programs);
        if (list.length() > 0 && guide.programs.isEmpty()) {
            throw new JSONException("No valid programs for the requested broadcast date");
        }
        return guide;
    }

    private String read(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        try {
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Cache-Control", "no-cache");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("Program guide HTTP " + status);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder body = new StringBuilder();
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    body.append(buffer, 0, count);
                    if (body.length() > 2 * 1024 * 1024) {
                        throw new IOException("Program guide response is too large");
                    }
                }
                return body.toString();
            }
        } finally {
            connection.disconnect();
        }
    }
}
