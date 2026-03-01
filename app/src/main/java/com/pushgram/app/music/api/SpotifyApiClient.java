package com.pushgram.app.music.api;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pushgram.app.music.model.Song;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Spotify playlist import + playback.
 *
 * ── Playback modes ────────────────────────────────────────────────────
 *  1. PREVIEW mode (no Spotify account needed):
 *     Spotify provides free 30-second preview URLs (128kbps MP3) for most tracks.
 *     We fetch these via the Spotify Web API using Client Credentials flow
 *     (app-only, no user login needed). ~384 KB per preview.
 *
 *  2. FULL TRACK mode (Spotify app deep-link):
 *     For full track playback, we launch spotify:track:ID deep-link.
 *     This opens the Spotify app (if installed) and plays the song there.
 *     Zero bandwidth in PushGram — Spotify handles everything.
 *
 * ── Setup ─────────────────────────────────────────────────────────────
 *  Register a free Spotify app at developer.spotify.com
 *  Add clientId + clientSecret to strings.xml:
 *    spotify_client_id / spotify_client_secret
 *  The Client Credentials token is app-only — no user login required.
 */
public class SpotifyApiClient {

    private static final String TAG = "SpotifyApiClient";
    private static final String TOKEN_URL  = "https://accounts.spotify.com/api/token";
    private static final String API_BASE   = "https://api.spotify.com/v1";
    private static final int    PAGE_LIMIT = 50;

    private final OkHttpClient http;
    private final String clientId;
    private final String clientSecret;

    private String accessToken;
    private long tokenExpiresAt = 0;

    public SpotifyApiClient(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    // ── Token ─────────────────────────────────────────────────────────

    private synchronized void ensureToken() throws IOException {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiresAt - 60_000) return;

        String credentials = clientId + ":" + clientSecret;
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        FormBody body = new FormBody.Builder()
                .add("grant_type", "client_credentials")
                .build();

        Request req = new Request.Builder()
                .url(TOKEN_URL)
                .post(body)
                .addHeader("Authorization", "Basic " + encoded)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();

        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("Token error: " + resp.code());
            JsonObject json = JsonParser.parseString(resp.body().string()).getAsJsonObject();
            accessToken = json.get("access_token").getAsString();
            int expiresIn = json.get("expires_in").getAsInt();
            tokenExpiresAt = System.currentTimeMillis() + expiresIn * 1000L;
            Log.d(TAG, "Spotify token refreshed, expires in " + expiresIn + "s");
        }
    }

    // ── Playlist Import ───────────────────────────────────────────────

    /**
     * Import all tracks from a Spotify playlist URL or ID.
     * Runs on calling thread — use from background executor.
     */
    public List<Song> importPlaylist(String urlOrId, String targetPlaylistId) throws IOException {
        ensureToken();
        String playlistId = extractPlaylistId(urlOrId);
        if (playlistId == null) throw new IOException("Invalid Spotify playlist URL/ID");

        List<Song> songs = new ArrayList<>();
        int offset = 0;

        while (true) {
            String url = API_BASE + "/playlists/" + playlistId + "/tracks" +
                    "?limit=" + PAGE_LIMIT +
                    "&offset=" + offset +
                    "&fields=next,items(track(id,name,preview_url,duration_ms," +
                    "artists(name),album(images)))";

            String json = getAuthed(url);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray items = root.getAsJsonArray("items");
            if (items == null || items.size() == 0) break;

            for (JsonElement item : items) {
                try {
                    JsonObject track = item.getAsJsonObject().getAsJsonObject("track");
                    if (track == null || track.get("id").isJsonNull()) continue;

                    String trackId = track.get("id").getAsString();
                    String title = track.get("name").getAsString();
                    long durationMs = track.get("duration_ms").getAsLong();

                    // Artist name(s)
                    JsonArray artists = track.getAsJsonArray("artists");
                    StringBuilder artistBuf = new StringBuilder();
                    for (int i = 0; i < artists.size(); i++) {
                        if (i > 0) artistBuf.append(", ");
                        artistBuf.append(artists.get(i).getAsJsonObject()
                                .get("name").getAsString());
                    }

                    // Album art — use the smallest available image (300px) to save bandwidth
                    String thumbUrl = "";
                    JsonArray images = track.getAsJsonObject("album").getAsJsonArray("images");
                    if (images != null && images.size() > 0) {
                        // Last image is usually smallest (64px), use second-to-last (300px)
                        int imgIdx = Math.max(0, images.size() - 2);
                        JsonObject img = images.get(imgIdx).getAsJsonObject();
                        thumbUrl = img.get("url").getAsString();
                    }

                    // 30-second preview URL (may be null for some tracks)
                    String previewUrl = null;
                    if (!track.get("preview_url").isJsonNull()) {
                        previewUrl = track.get("preview_url").getAsString();
                    }

                    Song song = new Song(trackId, Song.Source.SPOTIFY,
                            title, artistBuf.toString(), thumbUrl,
                            durationMs, targetPlaylistId);
                    if (previewUrl != null) song.setPreviewUrl(previewUrl);
                    songs.add(song);

                } catch (Exception e) {
                    Log.w(TAG, "Skipped track: " + e.getMessage());
                }
            }

            if (root.get("next").isJsonNull()) break;
            offset += PAGE_LIMIT;
        }

        Log.d(TAG, "Imported " + songs.size() + " tracks from Spotify playlist");
        return songs;
    }

    /**
     * Fetch a single Spotify track.
     */
    public Song importTrack(String urlOrId, String targetPlaylistId) throws IOException {
        ensureToken();
        String trackId = extractTrackId(urlOrId);
        if (trackId == null) throw new IOException("Invalid Spotify track URL/ID");

        String json = getAuthed(API_BASE + "/tracks/" + trackId);
        JsonObject track = JsonParser.parseString(json).getAsJsonObject();

        String title = track.get("name").getAsString();
        long durationMs = track.get("duration_ms").getAsLong();

        JsonArray artists = track.getAsJsonArray("artists");
        String artist = artists.size() > 0
                ? artists.get(0).getAsJsonObject().get("name").getAsString() : "";

        String thumbUrl = "";
        JsonArray images = track.getAsJsonObject("album").getAsJsonArray("images");
        if (images != null && images.size() > 0) {
            int idx = Math.max(0, images.size() - 2);
            thumbUrl = images.get(idx).getAsJsonObject().get("url").getAsString();
        }

        String previewUrl = null;
        if (!track.get("preview_url").isJsonNull()) {
            previewUrl = track.get("preview_url").getAsString();
        }

        Song song = new Song(trackId, Song.Source.SPOTIFY,
                title, artist, thumbUrl, durationMs, targetPlaylistId);
        if (previewUrl != null) song.setPreviewUrl(previewUrl);
        return song;
    }

    /** Build a Spotify deep-link URI to open in the Spotify app for full playback */
    public static String buildSpotifyDeepLink(String trackId) {
        return "spotify:track:" + trackId;
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private String getAuthed(String url) throws IOException {
        Request req = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Accept-Encoding", "gzip")
                .build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("API error " + resp.code() + " " + url);
            return resp.body().string();
        }
    }

    public static String extractPlaylistId(String input) {
        if (input == null) return null;
        input = input.trim();
        // Already an ID
        if (input.matches("^[A-Za-z0-9]{22}$")) return input;
        // open.spotify.com/playlist/ID or spotify:playlist:ID
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("playlist[:/]([A-Za-z0-9]{22})").matcher(input);
        if (m.find()) return m.group(1);
        return null;
    }

    public static String extractTrackId(String input) {
        if (input == null) return null;
        input = input.trim();
        if (input.matches("^[A-Za-z0-9]{22}$")) return input;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("track[:/]([A-Za-z0-9]{22})").matcher(input);
        if (m.find()) return m.group(1);
        return null;
    }
}
