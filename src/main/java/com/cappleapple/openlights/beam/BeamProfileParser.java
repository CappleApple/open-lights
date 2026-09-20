package com.cappleapple.openlights.beam;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** Parses partial datapack entries against the server's configured defaults. */
public final class BeamProfileParser {
    private BeamProfileParser() {}

    public static BeamProfile parse(JsonElement input, BeamProfile defaults) {
        if (input == null || !input.isJsonObject()) {
            throw new IllegalArgumentException("Beam profile must be a JSON object");
        }
        JsonObject json = input.getAsJsonObject();
        return new BeamProfile(
                layer(object(json, "inner"), defaults.inner()),
                layer(object(json, "outer"), defaults.outer()),
                number(json, "fogDensity", defaults.fogDensity()),
                dust(object(json, "dust"), defaults.dust()),
                bool(json, "shadows", defaults.shadows()));
    }

    private static BeamProfile.Layer layer(JsonObject json, BeamProfile.Layer fallback) {
        if (json == null) return fallback;
        var color = fallback.color();
        if (json.has("color")) {
            JsonElement field = json.get("color");
            if (!(field instanceof JsonPrimitive primitive) || !primitive.isString()) {
                throw new IllegalArgumentException("color must be a #RRGGBB string");
            }
            color = BeamProfile.color(primitive.getAsString());
        }
        return new BeamProfile.Layer(color,
                number(json, "intensity", fallback.intensity()),
                number(json, "angleDegrees", fallback.angleDegrees()),
                number(json, "range", fallback.range()),
                number(json, "edgeSoftness", fallback.edgeSoftness()),
                number(json, "falloff", fallback.falloff()));
    }

    private static BeamProfile.Dust dust(JsonObject json, BeamProfile.Dust fallback) {
        if (json == null) return fallback;
        float lifetime = number(json, "lifetimeTicks", fallback.lifetimeTicks());
        if (lifetime != Math.rint(lifetime)) {
            throw new IllegalArgumentException("dust.lifetimeTicks must be an integer");
        }
        return new BeamProfile.Dust(number(json, "rate", fallback.rate()),
                number(json, "size", fallback.size()), (int) lifetime,
                number(json, "speed", fallback.speed()));
    }

    private static JsonObject object(JsonObject json, String key) {
        if (!json.has(key)) return null;
        if (!json.get(key).isJsonObject()) {
            throw new IllegalArgumentException(key + " must be an object");
        }
        return json.getAsJsonObject(key);
    }

    private static float number(JsonObject json, String key, float fallback) {
        if (!json.has(key)) return fallback;
        JsonElement field = json.get(key);
        if (!(field instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        float value = primitive.getAsFloat();
        if (!Float.isFinite(value)) throw new IllegalArgumentException(key + " must be finite");
        return value;
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        if (!json.has(key)) return fallback;
        JsonElement field = json.get(key);
        if (!(field instanceof JsonPrimitive primitive) || !primitive.isBoolean()) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        return primitive.getAsBoolean();
    }
}
