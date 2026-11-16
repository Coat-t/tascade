package com.coatt.tascade.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class BackendCache {
    private static final long FRESH_MS = 60_000;
    private static final int MAX_ANSWERS = 64;

    private static final Map<String, Answer> ANSWERS = new LinkedHashMap<String, Answer>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Answer> eldest) {
            return size() > MAX_ANSWERS;
        }
    };

    static final class Answer {
        final JSONObject data;
        private final long receivedAtMs = System.currentTimeMillis();

        private Answer(JSONObject data) {
            this.data = data;
        }

        boolean isFresh() {
            return System.currentTimeMillis() - receivedAtMs < FRESH_MS;
        }
    }

    // counts clears, so an answer requested before one isn't kept after it
    private static int clears = 0;

    private BackendCache() {}

    static Answer get(String key) {
        return ANSWERS.get(key);
    }

    static int clears() {
        return clears;
    }

    static void put(String key, JSONObject data, int clearsWhenAsked) {
        if (clearsWhenAsked == clears) ANSWERS.put(key, new Answer(data));
    }

    public static void clear() {
        ANSWERS.clear();
        clears++;
    }
}
