package com.pushgram.app.music.model;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists playlists locally via SharedPreferences + Gson.
 * No server, no account required. No playlist limit.
 */
public class PlaylistStore {

    private static final String PREFS_NAME = "pushgram_playlists";
    private static final String KEY_PLAYLISTS = "playlists_json";

    private static PlaylistStore instance;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private List<Playlist> playlists;

    private PlaylistStore(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        load();
    }

    public static synchronized PlaylistStore getInstance(Context ctx) {
        if (instance == null) instance = new PlaylistStore(ctx);
        return instance;
    }

    private void load() {
        String json = prefs.getString(KEY_PLAYLISTS, null);
        if (json == null) {
            playlists = new ArrayList<>();
        } else {
            Type type = new TypeToken<List<Playlist>>() {}.getType();
            playlists = gson.fromJson(json, type);
            if (playlists == null) playlists = new ArrayList<>();
        }
    }

    private void save() {
        prefs.edit().putString(KEY_PLAYLISTS, gson.toJson(playlists)).apply();
    }

    public List<Playlist> getAll() {
        return new ArrayList<>(playlists);
    }

    public Playlist getById(String id) {
        for (Playlist p : playlists) if (p.getId().equals(id)) return p;
        return null;
    }

    public void createPlaylist(Playlist playlist) {
        playlists.add(playlist);
        save();
    }

    public void updatePlaylist(Playlist updated) {
        for (int i = 0; i < playlists.size(); i++) {
            if (playlists.get(i).getId().equals(updated.getId())) {
                playlists.set(i, updated);
                save();
                return;
            }
        }
    }

    public void deletePlaylist(String id) {
        playlists.removeIf(p -> p.getId().equals(id));
        save();
    }

    public void addSongToPlaylist(String playlistId, Song song) {
        Playlist p = getById(playlistId);
        if (p != null) {
            p.addSong(song);
            updatePlaylist(p);
        }
    }

    public void removeSongFromPlaylist(String playlistId, int index) {
        Playlist p = getById(playlistId);
        if (p != null) {
            p.removeSong(index);
            updatePlaylist(p);
        }
    }

    public int getTotalSongCount() {
        int count = 0;
        for (Playlist p : playlists) count += p.getSongCount();
        return count;
    }
}
