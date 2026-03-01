package com.pushgram.app.music.ui;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.pushgram.app.databinding.ActivityMusicBinding;
import com.pushgram.app.music.model.Playlist;
import com.pushgram.app.music.model.PlaylistStore;
import com.pushgram.app.music.model.Song;
import com.pushgram.app.music.service.MusicService;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MusicActivity extends AppCompatActivity
        implements MusicService.StateListener {

    private ActivityMusicBinding binding;
    private PlaylistStore store;
    private PlaylistAdapter playlistAdapter;

    // Service connection
    private MusicService musicService;
    private boolean bound = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressUpdater = this::updateProgress;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicService = ((MusicService.MusicBinder) service).getService();
            musicService.setStateListener(MusicActivity.this);
            bound = true;
            refreshNowPlaying();
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
        binding = ActivityMusicBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        store = PlaylistStore.getInstance(this);

        setupToolbar();
        setupPlaylistList();
        setupNowPlayingBar();

        // Bind to music service
        Intent serviceIntent = new Intent(this, MusicService.class);
        startService(serviceIntent);
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bound) {
            if (musicService != null) musicService.setStateListener(null);
            unbindService(connection);
        }
        mainHandler.removeCallbacks(progressUpdater);
    }

    // ── Setup ──────────────────────────────────────────────────────────

    private void setupToolbar() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnNewPlaylist.setOnClickListener(v -> showCreatePlaylistDialog());
        binding.btnImport.setOnClickListener(v -> showImportDialog());
    }

    private void setupPlaylistList() {
        playlistAdapter = new PlaylistAdapter(this,
                store.getAll(),
                this::onPlaylistClicked,
                this::onPlaylistLongClicked);

        binding.rvPlaylists.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPlaylists.setAdapter(playlistAdapter);
        refreshPlaylists();
    }

    private void setupNowPlayingBar() {
        binding.nowPlayingBar.setOnClickListener(v -> {
            if (musicService != null && musicService.getCurrentSong() != null) {
                // Expand to full player
                showFullPlayer();
            }
        });

        binding.btnPlayPause.setOnClickListener(v -> {
            if (bound && musicService != null) musicService.playPause();
        });

        binding.btnNext.setOnClickListener(v -> {
            if (bound && musicService != null) musicService.next();
        });

        binding.btnPrev.setOnClickListener(v -> {
            if (bound && musicService != null) musicService.prev();
        });

        binding.seekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && bound && musicService != null) {
                    musicService.seekTo(progress);
                }
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar sb) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar sb) {}
        });
    }

    // ── Playlist actions ──────────────────────────────────────────────

    private void onPlaylistClicked(Playlist playlist) {
        Intent intent = new Intent(this, PlaylistDetailActivity.class);
        intent.putExtra("playlist_id", playlist.getId());
        startActivity(intent);
    }

    private void onPlaylistLongClicked(Playlist playlist) {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(playlist.getName())
                .setItems(new String[]{"Rename", "Delete"}, (dialog, which) -> {
                    if (which == 0) showRenameDialog(playlist);
                    else showDeleteConfirm(playlist);
                }).show();
    }

    private void refreshPlaylists() {
        List<Playlist> all = store.getAll();
        playlistAdapter.updateData(all);

        if (all.isEmpty()) {
            binding.emptyState.setVisibility(View.VISIBLE);
            binding.rvPlaylists.setVisibility(View.GONE);
        } else {
            binding.emptyState.setVisibility(View.GONE);
            binding.rvPlaylists.setVisibility(View.VISIBLE);
        }

        binding.tvTotalStats.setText(
                store.getTotalSongCount() + " songs across " + all.size() + " playlists");
    }

    // ── Dialogs ────────────────────────────────────────────────────────

    private void showCreatePlaylistDialog() {
        EditText input = new EditText(this);
        input.setHint("Playlist name");
        input.setPadding(48, 32, 48, 8);

        new AlertDialog.Builder(this)
                .setTitle("New Playlist")
                .setView(input)
                .setPositiveButton("Create", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        store.createPlaylist(new Playlist(name));
                        refreshPlaylists();
                        Toast.makeText(this, "Playlist created!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRenameDialog(Playlist playlist) {
        EditText input = new EditText(this);
        input.setText(playlist.getName());
        input.setPadding(48, 32, 48, 8);

        new AlertDialog.Builder(this)
                .setTitle("Rename Playlist")
                .setView(input)
                .setPositiveButton("Rename", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        playlist.setName(name);
                        store.updatePlaylist(playlist);
                        refreshPlaylists();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteConfirm(Playlist playlist) {
        new AlertDialog.Builder(this)
                .setTitle("Delete \"" + playlist.getName() + "\"?")
                .setMessage("This will remove the playlist and all its songs.")
                .setPositiveButton("Delete", (d, w) -> {
                    store.deletePlaylist(playlist.getId());
                    refreshPlaylists();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showImportDialog() {
        // Choose playlist to import into
        List<Playlist> playlists = store.getAll();
        if (playlists.isEmpty()) {
            Toast.makeText(this, "Create a playlist first!", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = playlists.stream().map(Playlist::getName).toArray(String[]::new);
        final int[] selectedPlaylist = {0};

        View view = LayoutInflater.from(this).inflate(
                com.pushgram.app.R.layout.dialog_import, null);
        EditText etUrl = view.findViewById(com.pushgram.app.R.id.etImportUrl);
        android.widget.Spinner spinner = view.findViewById(com.pushgram.app.R.id.spinnerPlaylist);
        android.widget.RadioGroup rgSource = view.findViewById(com.pushgram.app.R.id.rgSource);

        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        new AlertDialog.Builder(this)
                .setTitle("Import Playlist / Song")
                .setView(view)
                .setPositiveButton("Import", (d, w) -> {
                    String url = etUrl.getText().toString().trim();
                    int sourceId = rgSource.getCheckedRadioButtonId();
                    boolean isSpotify = (sourceId == com.pushgram.app.R.id.rbSpotify);
                    String playlistId = playlists.get(spinner.getSelectedItemPosition()).getId();

                    if (url.isEmpty()) {
                        Toast.makeText(this, "Enter a URL", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    startImport(url, isSpotify, playlistId);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startImport(String url, boolean isSpotify, String targetPlaylistId) {
        binding.progressImport.setVisibility(View.VISIBLE);
        binding.tvImportStatus.setVisibility(View.VISIBLE);
        binding.tvImportStatus.setText("Importing...");

        ExecutorService ex = Executors.newSingleThreadExecutor();
        ex.execute(() -> {
            try {
                List<Song> songs;
                if (isSpotify) {
                    String clientId = getString(com.pushgram.app.R.string.spotify_client_id);
                    String clientSecret = getString(com.pushgram.app.R.string.spotify_client_secret);
                    com.pushgram.app.music.api.SpotifyApiClient spotify =
                            new com.pushgram.app.music.api.SpotifyApiClient(clientId, clientSecret);
                    // Try as playlist first, then single track
                    try {
                        songs = spotify.importPlaylist(url, targetPlaylistId);
                    } catch (Exception e) {
                        Song single = spotify.importTrack(url, targetPlaylistId);
                        songs = new java.util.ArrayList<>();
                        songs.add(single);
                    }
                } else {
                    String apiKey = getString(com.pushgram.app.R.string.youtube_api_key);
                    com.pushgram.app.music.api.YouTubeApiClient yt =
                            new com.pushgram.app.music.api.YouTubeApiClient(apiKey);
                    // Try as playlist first, then single video
                    try {
                        songs = yt.importPlaylist(url, targetPlaylistId);
                    } catch (Exception e) {
                        Song single = yt.importSingleVideo(url, targetPlaylistId);
                        songs = new java.util.ArrayList<>();
                        songs.add(single);
                    }
                }

                final int count = songs.size();
                for (Song s : songs) {
                    store.addSongToPlaylist(targetPlaylistId, s);
                }

                mainHandler.post(() -> {
                    binding.progressImport.setVisibility(View.GONE);
                    binding.tvImportStatus.setVisibility(View.GONE);
                    refreshPlaylists();
                    Toast.makeText(this,
                            "✅ Imported " + count + " song" + (count == 1 ? "" : "s") + "!",
                            Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    binding.progressImport.setVisibility(View.GONE);
                    binding.tvImportStatus.setVisibility(View.GONE);
                    Toast.makeText(this, "Import failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ── Now Playing ────────────────────────────────────────────────────

    private void refreshNowPlaying() {
        if (!bound || musicService == null) return;
        Song song = musicService.getCurrentSong();
        if (song != null) {
            showNowPlayingBar(song, musicService.isPlaying());
        }
    }

    private void showNowPlayingBar(Song song, boolean playing) {
        binding.nowPlayingBar.setVisibility(View.VISIBLE);
        binding.tvNowPlayingTitle.setText(song.getTitle());
        binding.tvNowPlayingArtist.setText(song.getDisplaySubtitle());
        binding.btnPlayPause.setText(playing ? "⏸" : "▶");

        if (song.getAlbumArtUrl() != null && !song.getAlbumArtUrl().isEmpty()) {
            Glide.with(this).load(song.getAlbumArtUrl())
                    .placeholder(android.R.drawable.ic_media_play)
                    .into(binding.ivNowPlayingArt);
        }

        int duration = musicService.getDuration();
        if (duration > 0) binding.seekBar.setMax(duration);

        mainHandler.removeCallbacks(progressUpdater);
        if (playing) mainHandler.post(progressUpdater);
    }

    private void updateProgress() {
        if (bound && musicService != null && musicService.isPlaying()) {
            binding.seekBar.setProgress(musicService.getCurrentPosition());
            mainHandler.postDelayed(progressUpdater, 500);
        }
    }

    private void showFullPlayer() {
        // Open PlaylistDetailActivity focused on the now-playing song
        // (Could also open a dedicated NowPlayingActivity for a richer experience)
        Toast.makeText(this, "Now playing: " + musicService.getCurrentSong().getTitle(),
                Toast.LENGTH_SHORT).show();
    }

    // ── MusicService.StateListener ────────────────────────────────────

    @Override
    public void onSongChanged(Song song) {
        if (song != null) showNowPlayingBar(song, true);
    }

    @Override
    public void onPlayStateChanged(boolean playing) {
        binding.btnPlayPause.setText(playing ? "⏸" : "▶");
        if (playing) {
            mainHandler.removeCallbacks(progressUpdater);
            mainHandler.post(progressUpdater);
        } else {
            mainHandler.removeCallbacks(progressUpdater);
        }
    }

    @Override
    public void onLoading(Song song) {
        binding.tvNowPlayingTitle.setText("Loading: " + song.getTitle());
        binding.nowPlayingBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void onError(String message) {
        Toast.makeText(this, "⚠️ " + message, Toast.LENGTH_LONG).show();
    }
}
