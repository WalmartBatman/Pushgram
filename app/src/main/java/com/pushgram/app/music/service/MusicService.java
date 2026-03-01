package com.pushgram.app.music.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.pushgram.app.R;
import com.pushgram.app.music.model.PlaylistStore;
import com.pushgram.app.music.model.Song;
import com.pushgram.app.music.api.SpotifyApiClient;
import com.pushgram.app.music.api.YouTubeApiClient;
import com.pushgram.app.music.ui.MusicActivity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground service handling audio playback.
 *
 * Bandwidth optimizations:
 *  - Streams audio-only (no video) for YouTube (itag 140, ~1 MB/min)
 *  - Streams 30-sec previews OR launches Spotify app for full playback
 *  - Does NOT buffer more than what Android's MediaPlayer needs
 *  - Stream URL is resolved lazily and cached per-song session
 */
public class MusicService extends Service {

    private static final String TAG = "MusicService";
    public  static final String CHANNEL_ID = "pushgram_music";
    public  static final int    NOTIF_ID   = 42;

    // Actions
    public static final String ACTION_PLAY_PAUSE = "com.pushgram.PLAY_PAUSE";
    public static final String ACTION_NEXT       = "com.pushgram.NEXT";
    public static final String ACTION_PREV       = "com.pushgram.PREV";
    public static final String ACTION_STOP       = "com.pushgram.STOP";

    // ── State ──────────────────────────────────────────────────────────
    private MediaPlayer mediaPlayer;
    private List<Song> queue = new ArrayList<>();
    private int currentIndex = -1;
    private boolean shuffle = false;
    private boolean repeat  = false;
    private boolean isPlaying = false;

    // Bound clients
    private final IBinder binder = new MusicBinder();
    private StateListener stateListener;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private MediaSessionCompat mediaSession;

    // API clients — lazy-init on first use
    private YouTubeApiClient ytClient;
    private SpotifyApiClient spClient;

    // ── Lifecycle ──────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        createNotificationChannel();
        setupMediaSession();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            handleAction(intent.getAction());
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releasePlayer();
        if (mediaSession != null) mediaSession.release();
        executor.shutdown();
    }

    // ── Public API (called via binder) ────────────────────────────────

    public void setQueue(List<Song> songs, int startIndex) {
        queue = new ArrayList<>(songs);
        currentIndex = startIndex;
        playCurrentSong();
    }

    public void playPause() {
        if (mediaPlayer == null) {
            playCurrentSong();
            return;
        }
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
        } else {
            mediaPlayer.start();
            isPlaying = true;
        }
        updateNotification();
        notifyStateChanged();
    }

    public void next() {
        if (queue.isEmpty()) return;
        if (shuffle) {
            currentIndex = (int)(Math.random() * queue.size());
        } else {
            currentIndex = (currentIndex + 1) % queue.size();
        }
        playCurrentSong();
    }

    public void prev() {
        if (queue.isEmpty()) return;
        // If >3s into song, restart; else go back
        if (mediaPlayer != null && mediaPlayer.getCurrentPosition() > 3000) {
            mediaPlayer.seekTo(0);
        } else {
            currentIndex = (currentIndex - 1 + queue.size()) % queue.size();
            playCurrentSong();
        }
    }

    public void seekTo(int ms) {
        if (mediaPlayer != null) mediaPlayer.seekTo(ms);
    }

    public void setShuffle(boolean on) { shuffle = on; }
    public void setRepeat(boolean on) { repeat = on; }

    public boolean isPlaying() { return mediaPlayer != null && mediaPlayer.isPlaying(); }
    public int getCurrentPosition() { return mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0; }
    public int getDuration() { return mediaPlayer != null ? mediaPlayer.getDuration() : 0; }

    public Song getCurrentSong() {
        if (currentIndex < 0 || currentIndex >= queue.size()) return null;
        return queue.get(currentIndex);
    }

    public List<Song> getQueue() { return Collections.unmodifiableList(queue); }
    public int getCurrentIndex() { return currentIndex; }

    public void setStateListener(StateListener listener) {
        this.stateListener = listener;
    }

    // ── Playback Core ──────────────────────────────────────────────────

    private void playCurrentSong() {
        if (queue.isEmpty() || currentIndex < 0) return;
        Song song = queue.get(currentIndex);

        notifyLoading(song);

        executor.execute(() -> {
            try {
                String streamUrl = resolveStreamUrl(song);
                if (streamUrl == null) throw new IOException("Could not resolve stream for " + song.getTitle());

                mainHandler.post(() -> startPlayback(streamUrl, song));
            } catch (Exception e) {
                Log.e(TAG, "Stream resolve failed: " + e.getMessage());
                mainHandler.post(() -> notifyError("Could not play: " + song.getTitle()));
            }
        });
    }

    /**
     * Resolve the actual audio URL for a song.
     * For YouTube: fetch audio-only stream (itag 140).
     * For Spotify: use 30s preview URL if available, else open Spotify app.
     */
    private String resolveStreamUrl(Song song) throws IOException {
        // Use cached URL if still valid (within 5h for YouTube)
        if (song.getStreamUrl() != null && song.getSource() == Song.Source.YOUTUBE) {
            // YouTube URLs embed an "expire" param — check it
            String expire = extractParam(song.getStreamUrl(), "expire");
            if (expire != null) {
                long expireTs = Long.parseLong(expire) * 1000L;
                if (System.currentTimeMillis() < expireTs - 300_000) {
                    return song.getStreamUrl(); // Still valid
                }
            }
        }

        switch (song.getSource()) {
            case YOUTUBE: {
                String apiKey = getString(R.string.youtube_api_key);
                if (ytClient == null) ytClient = new YouTubeApiClient(apiKey);
                String url = ytClient.resolveAudioStreamUrl(song.getId());
                song.setStreamUrl(url); // cache
                return url;
            }
            case SPOTIFY: {
                // Use 30-second preview if available (free, no auth)
                if (song.getPreviewUrl() != null) {
                    return song.getPreviewUrl();
                }
                // No preview → open Spotify app via deep-link on main thread
                mainHandler.post(() -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW)
                            .setData(android.net.Uri.parse(
                                    SpotifyApiClient.buildSpotifyDeepLink(song.getId())))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { startActivity(intent); }
                    catch (Exception e) { notifyError("Spotify app not installed"); }
                });
                return null; // Will be handled by Spotify app
            }
            default:
                return null;
        }
    }

    private void startPlayback(String url, Song song) {
        releasePlayer();

        if (!requestAudioFocus()) {
            Log.w(TAG, "Could not get audio focus");
            return;
        }

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());

            mediaPlayer.setDataSource(url);
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                isPlaying = true;
                notifyStateChanged();
                updateNotification();
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                if (repeat) {
                    mp.seekTo(0);
                    mp.start();
                } else {
                    next();
                }
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: " + what + " extra=" + extra);
                notifyError("Playback error");
                return true;
            });
            mediaPlayer.prepareAsync(); // non-blocking — starts buffering on bg thread

            startForeground(NOTIF_ID, buildNotification(song));

        } catch (IOException e) {
            Log.e(TAG, "MediaPlayer setup failed", e);
            notifyError("Playback failed: " + e.getMessage());
        }
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
            isPlaying = false;
        }
    }

    private boolean requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener(focusChange -> {
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                            playPause();
                        }
                    }).build();
            return audioManager.requestAudioFocus(audioFocusRequest)
                    == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
        return true;
    }

    // ── Notifications ─────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "PushGram Music",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Workout music controls");
            ch.setSound(null, null);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(Song song) {
        Intent openIntent = new Intent(this, MusicActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent prevPi = actionIntent(ACTION_PREV, 1);
        PendingIntent playPi = actionIntent(ACTION_PLAY_PAUSE, 2);
        PendingIntent nextPi = actionIntent(ACTION_NEXT, 3);
        PendingIntent stopPi = actionIntent(ACTION_STOP, 4);

        String title = song != null ? song.getTitle() : "No track";
        String artist = song != null ? song.getDisplaySubtitle() : "";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(artist)
                .setContentIntent(openPi)
                .addAction(android.R.drawable.ic_media_previous, "Prev", prevPi)
                .addAction(isPlaying ? android.R.drawable.ic_media_pause
                        : android.R.drawable.ic_media_play,
                        isPlaying ? "Pause" : "Play", playPi)
                .addAction(android.R.drawable.ic_media_next, "Next", nextPi)
                .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2)
                        .setMediaSession(mediaSession.getSessionToken()))
                .setOngoing(isPlaying)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .build();
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildNotification(getCurrentSong()));
    }

    private PendingIntent actionIntent(String action, int reqCode) {
        Intent intent = new Intent(this, MusicService.class).setAction(action);
        return PendingIntent.getService(this, reqCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void handleAction(String action) {
        switch (action) {
            case ACTION_PLAY_PAUSE: playPause(); break;
            case ACTION_NEXT: next(); break;
            case ACTION_PREV: prev(); break;
            case ACTION_STOP:
                releasePlayer();
                stopForeground(true);
                stopSelf();
                break;
        }
    }

    // ── MediaSession ──────────────────────────────────────────────────

    private void setupMediaSession() {
        mediaSession = new MediaSessionCompat(this, "PushGramMusic");
        mediaSession.setActive(true);
    }

    // ── Listener callbacks ────────────────────────────────────────────

    public interface StateListener {
        void onSongChanged(Song song);
        void onPlayStateChanged(boolean playing);
        void onLoading(Song song);
        void onError(String message);
    }

    private void notifyStateChanged() {
        if (stateListener != null) {
            stateListener.onPlayStateChanged(isPlaying);
            stateListener.onSongChanged(getCurrentSong());
        }
    }

    private void notifyLoading(Song song) {
        if (stateListener != null) stateListener.onLoading(song);
    }

    private void notifyError(String msg) {
        if (stateListener != null) stateListener.onError(msg);
    }

    // ── Binder ────────────────────────────────────────────────────────

    public class MusicBinder extends Binder {
        public MusicService getService() { return MusicService.this; }
    }

    // ── Util ──────────────────────────────────────────────────────────

    private static String extractParam(String url, String key) {
        try {
            java.net.URL u = new java.net.URL(url);
            for (String pair : u.getQuery().split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && kv[0].equals(key)) return kv[1];
            }
        } catch (Exception ignored) {}
        return null;
    }
}
