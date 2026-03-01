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
import com.pushgram.app.music.model.Playlist;

import java.util.ArrayList;
import java.util.List;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.VH> {

    public interface OnClickListener { void onClick(Playlist p); }
    public interface OnLongClickListener { void onLongClick(Playlist p); }

    private final Context context;
    private List<Playlist> data;
    private final OnClickListener clickListener;
    private final OnLongClickListener longClickListener;

    public PlaylistAdapter(Context ctx, List<Playlist> data,
                            OnClickListener click, OnLongClickListener longClick) {
        this.context = ctx;
        this.data = new ArrayList<>(data);
        this.clickListener = click;
        this.longClickListener = longClick;
    }

    public void updateData(List<Playlist> newData) {
        this.data = new ArrayList<>(newData);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.item_playlist, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Playlist p = data.get(position);
        holder.tvName.setText(p.getName());
        holder.tvCount.setText(p.getSongCount() + " songs • " + p.getFormattedDuration());

        if (p.getCoverUrl() != null && !p.getCoverUrl().isEmpty()) {
            Glide.with(context).load(p.getCoverUrl())
                    .centerCrop()
                    .placeholder(android.R.drawable.ic_media_play)
                    .into(holder.ivCover);
        } else {
            holder.ivCover.setImageResource(android.R.drawable.ic_media_play);
        }

        holder.itemView.setOnClickListener(v -> clickListener.onClick(p));
        holder.itemView.setOnLongClickListener(v -> {
            longClickListener.onLongClick(p);
            return true;
        });
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivCover;
        TextView tvName, tvCount;

        VH(View v) {
            super(v);
            ivCover = v.findViewById(R.id.ivPlaylistCover);
            tvName  = v.findViewById(R.id.tvPlaylistName);
            tvCount = v.findViewById(R.id.tvPlaylistCount);
        }
    }
}
