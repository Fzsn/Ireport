package com.example.iresponderapp.adapter;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.iresponderapp.R;
import com.squareup.picasso.Picasso;

import java.io.IOException;
import java.util.List;

public class MediaPreviewAdapter extends RecyclerView.Adapter<MediaPreviewAdapter.MediaViewHolder> {

    private final List<Uri> mediaUris;
    private final Context context;
    private final OnMediaRemoveListener removeListener;
    private boolean isReadOnly = false;

    public interface OnMediaRemoveListener {
        void onRemove(int position, Uri uri);
    }

    public MediaPreviewAdapter(Context context, List<Uri> mediaUris, OnMediaRemoveListener removeListener) {
        this.context = context;
        this.mediaUris = mediaUris;
        this.removeListener = removeListener;
    }
    
    public void setReadOnly(boolean readOnly) {
        this.isReadOnly = readOnly;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_media_preview, parent, false);
        return new MediaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        Uri uri = mediaUris.get(position);
        
        // Hide remove button in read-only mode
        if (isReadOnly) {
            holder.btnRemoveMedia.setVisibility(View.GONE);
        } else {
            holder.btnRemoveMedia.setVisibility(View.VISIBLE);
        }
        
        // Determine if video based on mime type or extension
        String mimeType = context.getContentResolver().getType(uri);
        boolean isVideo = (mimeType != null && mimeType.startsWith("video")) || 
                          uri.toString().endsWith(".mp4") || 
                          uri.toString().contains("video");
        
        if (isVideo) {
            holder.imgVideoIndicator.setVisibility(View.VISIBLE);
            // Load video thumbnail
            if (uri.getScheme() != null && (uri.getScheme().startsWith("http") || uri.getScheme().startsWith("https"))) {
                // Remote video - just show placeholder for now as extracting thumbnail from remote URL is complex on main thread
                // Could use a library like Glide/Coil which supports this better, but sticking to Picasso + placeholder
                holder.imgMediaPreview.setImageResource(android.R.drawable.ic_media_play);
                holder.imgMediaPreview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            } else {
                loadVideoThumbnail(holder.imgMediaPreview, uri);
                holder.imgMediaPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            }
        } else {
            holder.imgVideoIndicator.setVisibility(View.GONE);
            holder.imgMediaPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            // Load image
            Picasso.get()
                    .load(uri)
                    .fit()
                    .centerCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(holder.imgMediaPreview);
        }
        
        holder.btnRemoveMedia.setOnClickListener(v -> {
            if (removeListener != null && !isReadOnly) {
                removeListener.onRemove(holder.getAdapterPosition(), uri);
            }
        });
        
        // Open media on click in read-only mode
        if (isReadOnly) {
            holder.itemView.setOnClickListener(v -> {
                // TODO: Implement full screen view
                // For now, no-op or simple intent view
            });
        }
    }

    private void loadVideoThumbnail(ImageView imageView, Uri uri) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = context.getContentResolver();
                Bitmap thumbnail = resolver.loadThumbnail(uri, new Size(200, 200), null);
                imageView.setImageBitmap(thumbnail);
            } else {
                // Fallback for older versions
                Bitmap thumbnail = ThumbnailUtils.createVideoThumbnail(
                        uri.getPath(), MediaStore.Video.Thumbnails.MINI_KIND);
                if (thumbnail != null) {
                    imageView.setImageBitmap(thumbnail);
                } else {
                    imageView.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            }
        } catch (IOException e) {
            imageView.setImageResource(android.R.drawable.ic_menu_gallery);
        }
    }

    @Override
    public int getItemCount() {
        return mediaUris.size();
    }

    public static class MediaViewHolder extends RecyclerView.ViewHolder {
        ImageView imgMediaPreview;
        ImageView imgVideoIndicator;
        ImageButton btnRemoveMedia;

        public MediaViewHolder(@NonNull View itemView) {
            super(itemView);
            imgMediaPreview = itemView.findViewById(R.id.imgMediaPreview);
            imgVideoIndicator = itemView.findViewById(R.id.imgVideoIndicator);
            btnRemoveMedia = itemView.findViewById(R.id.btnRemoveMedia);
        }
    }
}
