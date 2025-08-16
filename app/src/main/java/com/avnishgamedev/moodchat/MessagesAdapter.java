package com.avnishgamedev.moodchat;

import android.graphics.Bitmap;
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

    private List<Message> messages;
    private Bitmap selfProfilePic;
    private Bitmap otherProfilePic;
    public MessagesAdapter(List<Message> messages, Bitmap selfProfilePic, Bitmap otherProfilePic) {
        this.messages = messages;
        this.selfProfilePic = selfProfilePic;
        this.otherProfilePic = otherProfilePic;
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

        if (message.getSenderUsername().equals(UserManager.getInstance().getUser().getUsername())) {
            holder.ivProfile.setImageBitmap(selfProfilePic);
            if (messages.get(messages.size() - 1).getId().equals(message.getId())) {
                TextView tvStatus = holder.itemView.findViewById(R.id.tvStatus);
                tvStatus.setVisibility(View.VISIBLE);
                tvStatus.setText(message.getStatus());
            }
        } else {
            holder.ivProfile.setImageBitmap(otherProfilePic);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }
}
