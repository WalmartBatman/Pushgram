package com.pushgram.app.music.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Playlist implements Serializable {

    private String id;
    private String name;
    private String description;
    private List<Song> songs;
    private long createdAt;
    private long updatedAt;
    /** Cover art URL — auto-set from first song's album art */
    private String coverUrl;

    public Playlist() {
        this.songs = new ArrayList<>();
    }

    public Playlist(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.songs = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = createdAt;
    }

    public void addSong(Song song) {
        if (songs == null) songs = new ArrayList<>();
        songs.add(song);
        updatedAt = System.currentTimeMillis();
        if (coverUrl == null && song.getAlbumArtUrl() != null) {
            coverUrl = song.getAlbumArtUrl();
        }
    }

    public void removeSong(int index) {
        if (songs != null && index >= 0 && index < songs.size()) {
            songs.remove(index);
            updatedAt = System.currentTimeMillis();
        }
    }

    public int getSongCount() {
        return songs == null ? 0 : songs.size();
    }

    public long getTotalDurationMs() {
        if (songs == null) return 0;
        long total = 0;
        for (Song s : songs) total += s.getDurationMs();
        return total;
    }

    public String getFormattedDuration() {
        long totalMin = getTotalDurationMs() / 60000;
        return totalMin + " min";
    }

    // Getters / Setters
    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d; }
    public List<Song> getSongs() { return songs; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String url) { this.coverUrl = url; }
}
