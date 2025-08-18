package com.avnishgamedev.moodchat;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.ViewHolder> {
    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;
    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivProfile;
        TextView tvMessage;
        TextView tvTimestamp;
        public ViewHolder(View itemView) {
            super(itemView);

            ivProfile = itemView.findViewById(R.id.ivProfile);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
        }
    }

    private final List<Message> messages;
    private final User thisUser;
    private final User otherUser;
    private int lastIndex = -1;
    public MessagesAdapter(List<Message> messages, User thisUser, User otherUser) {
        this.messages = messages;
        this.thisUser = thisUser;
        this.otherUser = otherUser;
    }

    @Override
    public int getItemViewType(int position) {
        if (messages.get(position).getSenderUsername().equals(UserManager.getInstance().getUser().getUsername())) {
            return VIEW_TYPE_SENT;
        } else {
            return VIEW_TYPE_RECEIVED;
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(viewType == VIEW_TYPE_SENT ? R.layout.item_container_sent_message : R.layout.item_container_received_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Message message = messages.get(position);
        holder.tvMessage.setText(message.getMessage());
        holder.tvTimestamp.setText(new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(message.getSentAt().toDate()));

        if (position == messages.size() - 1 && message.getSenderUsername().equals(UserManager.getInstance().getUser().getUsername())) {
            if (lastIndex != -1) {
                lastIndex = holder.getAdapterPosition();
                holder.itemView.post(() -> notifyItemChanged(lastIndex));
            }
            lastIndex = holder.getAdapterPosition();
        }

        if (message.getSenderUsername().equals(UserManager.getInstance().getUser().getUsername())) {
            holder.ivProfile.setImageBitmap(base64ToBitmap(thisUser.getProfilePicture()));
            TextView tvStatus = holder.itemView.findViewById(R.id.tvStatus);
            if (holder.getAdapterPosition() == lastIndex) {
                tvStatus.setVisibility(View.VISIBLE);
                tvStatus.setText(message.getStatus());
            } else {
                tvStatus.setVisibility(View.GONE);
            }
        } else {
            holder.ivProfile.setImageBitmap(base64ToBitmap(otherUser.getProfilePicture()));
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // Helpers
    public static Bitmap base64ToBitmap(String base64Str) {
        if (base64Str == null) {
            return null;
        }
        byte[] decodedBytes = Base64.decode(base64Str, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
    }
}
