package com.pushgram.app.music.model;

import java.io.Serializable;

/**
 * Represents a single song entry in a PushGram playlist.
 *
 * Streaming strategy (minimal bandwidth):
 *  - YouTube: We store the videoId only. Audio stream URL is resolved
 *    on-demand via YouTubeAudioResolver (using the /get_video_info endpoint
 *    or a lightweight manifest fetch — audio-only itag 140 = m4a 128kbps).
 *  - Spotify: We store the trackId. A 30-second preview URL (free, no auth)
 *    is fetched via the open oEmbed/track endpoint. Full playback requires
 *    the user to have Spotify installed and is launched via deep-link.
 */
public class Song implements Serializable {

    public enum Source { YOUTUBE, SPOTIFY, LOCAL }

    // ── Identification ─────────────────────────────────────────────────
    private String id;           // YouTube videoId OR Spotify trackId
    private Source source;

    // ── Metadata (fetched once, cached locally) ────────────────────────
    private String title;
    private String artist;
    private String albumArtUrl;
    private long durationMs;

    // ── Playback ────────────────────────────────────────────────────────
    /** Resolved audio-only stream URL (cached, expires after ~6h for YT) */
    private String streamUrl;
    /** Spotify 30-s preview URL (always valid) */
    private String previewUrl;

    // ── Playlist membership ────────────────────────────────────────────
    private String playlistId;

    // ── Timestamp ──────────────────────────────────────────────────────
    private long addedAt;

    public Song() {}

    public Song(String id, Source source, String title, String artist,
                String albumArtUrl, long durationMs, String playlistId) {
        this.id = id;
        this.source = source;
        this.title = title;
        this.artist = artist;
        this.albumArtUrl = albumArtUrl;
        this.durationMs = durationMs;
        this.playlistId = playlistId;
        this.addedAt = System.currentTimeMillis();
    }

    // ── Getters / Setters ───────────────────────────────────────────────
    public String getId() { return id; }
    public Source getSource() { return source; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getAlbumArtUrl() { return albumArtUrl; }
    public long getDurationMs() { return durationMs; }
    public String getStreamUrl() { return streamUrl; }
    public void setStreamUrl(String url) { this.streamUrl = url; }
    public String getPreviewUrl() { return previewUrl; }
    public void setPreviewUrl(String url) { this.previewUrl = url; }
    public String getPlaylistId() { return playlistId; }
    public long getAddedAt() { return addedAt; }

    public String getDisplaySubtitle() {
        return artist != null && !artist.isEmpty() ? artist :
               (source == Source.YOUTUBE ? "YouTube" : "Spotify");
    }

    /** Human-readable duration e.g. "3:42" */
    public String getFormattedDuration() {
        if (durationMs <= 0) return "";
        long totalSec = durationMs / 1000;
        return String.format("%d:%02d", totalSec / 60, totalSec % 60);
    }
}
