package com.pushgram.app.music.ui;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.pushgram.app.databinding.ActivityPlaylistDetailBinding;
import com.pushgram.app.music.model.Playlist;
import com.pushgram.app.music.model.PlaylistStore;
import com.pushgram.app.music.model.Song;
import com.pushgram.app.music.service.MusicService;

public class PlaylistDetailActivity extends AppCompatActivity
        implements MusicService.StateListener {

    private ActivityPlaylistDetailBinding binding;
    private PlaylistStore store;
    private Playlist playlist;
    private SongAdapter songAdapter;

    private MusicService musicService;
    private boolean bound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicService = ((MusicService.MusicBinder) service).getService();
            musicService.setStateListener(PlaylistDetailActivity.this);
            bound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            musicService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlaylistDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        store = PlaylistStore.getInstance(this);
        String playlistId = getIntent().getStringExtra("playlist_id");
        playlist = store.getById(playlistId);

        if (playlist == null) { finish(); return; }

        setupUI();

        Intent svc = new Intent(this, MusicService.class);
        bindService(svc, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bound) {
            if (musicService != null) musicService.setStateListener(null);
            unbindService(connection);
        }
    }

    private void setupUI() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.tvPlaylistName.setText(playlist.getName());
        binding.tvPlaylistInfo.setText(
                playlist.getSongCount() + " songs • " + playlist.getFormattedDuration());

        binding.btnPlayAll.setOnClickListener(v -> {
            if (!playlist.getSongs().isEmpty()) playFrom(0);
            else Toast.makeText(this, "No songs in this playlist", Toast.LENGTH_SHORT).show();
        });

        binding.btnShuffle.setOnClickListener(v -> {
            if (!playlist.getSongs().isEmpty()) {
                int randIdx = (int)(Math.random() * playlist.getSongs().size());
                if (bound && musicService != null) musicService.setShuffle(true);
                playFrom(randIdx);
            }
        });

        songAdapter = new SongAdapter(this,
                playlist.getSongs(),
                (song, idx) -> playFrom(idx),
                (song, idx) -> showSongOptions(song, idx));

        binding.rvSongs.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSongs.setAdapter(songAdapter);
    }

    private void playFrom(int index) {
        if (!bound || musicService == null) {
            Toast.makeText(this, "Music service not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        musicService.setQueue(playlist.getSongs(), index);
        songAdapter.setCurrentPlayingIndex(index);
        Toast.makeText(this, "▶ " + playlist.getSongs().get(index).getTitle(),
                Toast.LENGTH_SHORT).show();
    }

    private void showSongOptions(Song song, int index) {
        new AlertDialog.Builder(this)
                .setTitle(song.getTitle())
                .setItems(new String[]{"Remove from playlist"}, (d, w) -> {
                    store.removeSongFromPlaylist(playlist.getId(), index);
                    playlist = store.getById(playlist.getId());
                    songAdapter.updateData(playlist.getSongs());
                    binding.tvPlaylistInfo.setText(
                            playlist.getSongCount() + " songs • " + playlist.getFormattedDuration());
                }).show();
    }

    @Override public void onSongChanged(Song song) {
        if (song == null) return;
        for (int i = 0; i < playlist.getSongs().size(); i++) {
            if (playlist.getSongs().get(i).getId().equals(song.getId())) {
                songAdapter.setCurrentPlayingIndex(i);
                return;
            }
        }
    }
    @Override public void onPlayStateChanged(boolean playing) {}
    @Override public void onLoading(Song song) {}
    @Override public void onError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
