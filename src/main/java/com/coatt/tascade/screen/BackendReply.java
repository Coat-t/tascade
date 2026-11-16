package com.coatt.tascade.screen;

import com.coatt.tascade.network.RunState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.json.JSONObject;

import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public final class BackendReply {
    private static final long TIMEOUT_MS = 10_000;

    // counts requests, so answers to superseded ones are ignored
    private int generation = 0;
    private long requestedAtMs = 0;
    private JSONObject reply = null;

    public Consumer<JSONObject> request() {
        int request = ++generation;
        requestedAtMs = System.currentTimeMillis();
        reply = null;
        return answer -> {
            if (request != generation) return;
            reply = answer;
            requestedAtMs = 0;
        };
    }

    public boolean requested() {
        return generation > 0;
    }

    public JSONObject data() {
        return reply != null && !reply.has("error") ? reply : null;
    }

    public String failure() {
        if (reply != null) return reply.has("error") ? reply.optString("error") : null;
        if (System.currentTimeMillis() - requestedAtMs < TIMEOUT_MS) return null;
        return RunState.connected ? "Failed to load." : "Couldn't connect to Tascade.";
    }
}
