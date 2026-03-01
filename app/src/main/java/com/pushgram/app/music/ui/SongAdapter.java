package com.pushgram.app.music.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.pushgram.app.R;
import com.pushgram.app.music.model.Song;

import java.util.ArrayList;
import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.VH> {

    public interface OnClickListener { void onClick(Song song, int index); }
    public interface OnLongClickListener { void onLongClick(Song song, int index); }

    private final Context context;
    private List<Song> data;
    private int currentPlayingIndex = -1;
    private final OnClickListener clickListener;
    private final OnLongClickListener longClickListener;

    public SongAdapter(Context ctx, List<Song> data,
                        OnClickListener click, OnLongClickListener longClick) {
        this.context = ctx;
        this.data = new ArrayList<>(data);
        this.clickListener = click;
        this.longClickListener = longClick;
    }

    public void updateData(List<Song> newData) {
        this.data = new ArrayList<>(newData);
        notifyDataSetChanged();
    }

    public void setCurrentPlayingIndex(int index) {
        int old = currentPlayingIndex;
        currentPlayingIndex = index;
        if (old >= 0 && old < data.size()) notifyItemChanged(old);
        if (index >= 0 && index < data.size()) notifyItemChanged(index);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_song, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Song song = data.get(position);

        holder.tvTitle.setText(song.getTitle());
        holder.tvArtist.setText(song.getDisplaySubtitle());
        holder.tvDuration.setText(song.getFormattedDuration());

        // Source badge
        if (song.getSource() == Song.Source.YOUTUBE) {
            holder.tvSource.setText("▶ YT");
            holder.tvSource.setTextColor(0xFFFF0000);
        } else {
            holder.tvSource.setText("♫ SP");
            holder.tvSource.setTextColor(0xFF1DB954);
        }

        // Album art
        if (song.getAlbumArtUrl() != null && !song.getAlbumArtUrl().isEmpty()) {
            Glide.with(context)
                    .load(song.getAlbumArtUrl())
                    .centerCrop()
                    .placeholder(android.R.drawable.ic_media_play)
                    .into(holder.ivArt);
        } else {
            holder.ivArt.setImageResource(android.R.drawable.ic_media_play);
        }

        // Highlight currently playing
        holder.itemView.setAlpha(position == currentPlayingIndex ? 1.0f : 0.85f);
        holder.tvTitle.setTextColor(position == currentPlayingIndex
                ? 0xFFFF6B35 : 0xFFFFFFFF);

        holder.itemView.setOnClickListener(v -> clickListener.onClick(song, position));
        holder.itemView.setOnLongClickListener(v -> {
            longClickListener.onLongClick(song, position);
            return true;
        });
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivArt;
        TextView tvTitle, tvArtist, tvDuration, tvSource;

        VH(View v) {
            super(v);
            ivArt      = v.findViewById(R.id.ivSongArt);
            tvTitle    = v.findViewById(R.id.tvSongTitle);
            tvArtist   = v.findViewById(R.id.tvSongArtist);
            tvDuration = v.findViewById(R.id.tvSongDuration);
            tvSource   = v.findViewById(R.id.tvSongSource);
        }
    }
}
