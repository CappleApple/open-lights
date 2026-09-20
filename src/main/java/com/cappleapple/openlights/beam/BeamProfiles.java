package com.cappleapple.openlights.beam;

import com.cappleapple.openlights.config.ServerConfig;
import com.cappleapple.openlights.network.BeamProfileNetwork;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/** Separate immutable client/server snapshots keep integrated servers from bypassing synchronization. */
public final class BeamProfiles {
    public static final int MAX_PROFILES = 256;
    public static final int MAX_ID_LENGTH = 256;
    public static final int MAX_JSON_LENGTH = 16_384;
    private static volatile Snapshot client = new Snapshot(0, BeamProfile.DEFAULT, Map.of());
    private static volatile Snapshot server = new Snapshot(0, BeamProfile.DEFAULT, Map.of());
    private static Map<ResourceLocation, JsonElement> resources = Map.of();

    private BeamProfiles() {}

    public static void register() {
        BeamProfileNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(BeamProfiles::addReloadListener);
        MinecraftForge.EVENT_BUS.addListener(BeamProfiles::synchronize);
        MinecraftForge.EVENT_BUS.addListener(BeamProfiles::serverStopped);
    }

    public static BeamProfile clientProfile(ResourceLocation id) {
        Snapshot current = client;
        return current.profiles().getOrDefault(id, current.defaults());
    }

    public static BeamProfile clientDefaults() { return client.defaults(); }
    public static long clientRevision() { return client.revision(); }
    public static Map<ResourceLocation, BeamProfile> clientSnapshot() { return client.profiles(); }
    public static Snapshot serverSnapshot() { return server; }

    /** Called only by the client-bound network handler, on the client main thread. */
    public static void receive(Snapshot snapshot) { client = snapshot; }

    /** Disconnect only: profiles must survive dimension changes and renderer resets. */
    public static void resetClient() { client = new Snapshot(0, BeamProfile.DEFAULT, Map.of()); }

    private static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "openlights/beam_profiles") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> loaded, ResourceManager manager,
                                 ProfilerFiller profiler) {
                var bounded = new LinkedHashMap<ResourceLocation, JsonElement>();
                loaded.entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(MAX_PROFILES).forEach(entry -> {
                    if (entry.getKey().toString().length() > MAX_ID_LENGTH
                            || entry.getValue().toString().length() > MAX_JSON_LENGTH) {
                        LogUtils.getLogger().warn("Ignoring oversized Open Lights beam profile {}", entry.getKey());
                    } else {
                        bounded.put(entry.getKey(), entry.getValue().deepCopy());
                    }
                });
                if (loaded.size() > MAX_PROFILES) {
                    LogUtils.getLogger().warn("Open Lights found {} beam profiles; only the first {} sorted IDs are used",
                            loaded.size(), MAX_PROFILES);
                }
                resources = Map.copyOf(bounded);
                rebuild(true);
            }
        });
    }

    private static void rebuild(boolean forceRevision) {
        BeamProfile defaults = ServerConfig.defaults();
        Map<ResourceLocation, BeamProfile> resolved = new LinkedHashMap<>();
        resources.forEach((id, json) -> {
            try {
                resolved.put(id, BeamProfileParser.parse(json, defaults));
            } catch (RuntimeException error) {
                LogUtils.getLogger().warn("Invalid Open Lights beam profile {}: {}; using server defaults",
                        id, error.getMessage());
                resolved.put(id, defaults);
            }
        });
        Snapshot previous = server;
        if (forceRevision || !previous.defaults().equals(defaults) || !previous.profiles().equals(resolved)) {
            server = new Snapshot(previous.revision() + 1, defaults, resolved);
        }
    }

    private static void synchronize(OnDatapackSyncEvent event) {
        // Server configs may load after the initial resource reload, and can be edited before /reload.
        rebuild(false);
        Snapshot snapshot = server;
        if (event.getPlayer() != null) {
            BeamProfileNetwork.send(event.getPlayer(), snapshot);
        } else {
            event.getPlayerList().getPlayers().forEach(player -> BeamProfileNetwork.send(player, snapshot));
        }
    }

    private static void serverStopped(ServerStoppedEvent event) {
        resources = Map.of();
        server = new Snapshot(0, BeamProfile.DEFAULT, Map.of());
    }

    public record Snapshot(long revision, BeamProfile defaults, Map<ResourceLocation, BeamProfile> profiles) {
        public Snapshot {
            if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
            java.util.Objects.requireNonNull(defaults, "defaults");
            if (profiles.size() > MAX_PROFILES) throw new IllegalArgumentException("Too many beam profiles");
            profiles = Map.copyOf(profiles);
            if (profiles.keySet().stream().anyMatch(id -> id.toString().length() > MAX_ID_LENGTH)) {
                throw new IllegalArgumentException("Beam profile ID is too long");
            }
        }
    }
}
