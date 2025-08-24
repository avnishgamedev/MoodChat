package com.avnishgamedev.moodchat;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

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
        TextView tvStatus;

        public ViewHolder(View itemView) {
            super(itemView);

            ivProfile = itemView.findViewById(R.id.ivProfile);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvStatus = itemView.findViewById(R.id.tvStatus); // May be null for received messages

            Log.d("ViewHolder", "Created ViewHolder - tvMessage: " + (tvMessage != null) +
                    ", tvTimestamp: " + (tvTimestamp != null));
        }
    }

    private final List<Message> messages;
    private final User thisUser;
    private final User otherUser;
    private int lastIndex = -1;
    private String sentBubbleColour = "@null";
    private String receivedBubbleColour = "@null";
    private String senTextColour = "@null";
    private String receivedTextColour = "@null";
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
        Log.d("MessagesAdapter", "Binding position " + position + ": " + message.getMessage());

        // Set message text and timestamp
        holder.tvMessage.setText(message.getMessage().trim());
        holder.tvTimestamp.setText(new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(message.getSentAt().toDate()));

        // Handle last message status
        boolean isSentMessage = message.getSenderUsername().equals(UserManager.getInstance().getUser().getUsername());
        if (holder.getAdapterPosition() == messages.size() - 1 && isSentMessage) {
            lastIndex = holder.getAdapterPosition();
        }

        // Set profile image safely
        Bitmap profileBitmap = null;
        if (isSentMessage) {
            profileBitmap = base64ToBitmap(thisUser.getProfilePicture());
        } else {
            profileBitmap = base64ToBitmap(otherUser.getProfilePicture());
        }
        if (profileBitmap != null) {
            holder.ivProfile.setImageBitmap(profileBitmap);
        }

        // Set bubble colors on the MaterialCardView instead of TextView
        MaterialCardView cardView = holder.itemView.findViewById(R.id.mcvMessage);
        if (cardView != null) {
            if (!sentBubbleColour.equals("@null") && !receivedBubbleColour.equals("@null")) {
                String bubbleColor = isSentMessage ? sentBubbleColour : receivedBubbleColour;
                String textColor = isSentMessage ? senTextColour : receivedTextColour;

                cardView.setCardBackgroundColor(Color.parseColor(bubbleColor));
                holder.tvMessage.setTextColor(Color.parseColor(textColor));
            } else {
                // Set default colors
                if (isSentMessage) {
                    cardView.setCardBackgroundColor(Color.parseColor("#8080FF"));
                    holder.tvMessage.setTextColor(Color.WHITE);
                } else {
                    cardView.setCardBackgroundColor(Color.parseColor("#2A2A40"));
                    holder.tvMessage.setTextColor(Color.WHITE);
                }
            }
        }

        // Handle status text for sent messages only
        if (holder.tvStatus != null && isSentMessage) {
            if (holder.getAdapterPosition() == lastIndex) {
                holder.tvStatus.setVisibility(View.VISIBLE);
                holder.tvStatus.setText(message.getStatus());
            } else {
                holder.tvStatus.setVisibility(View.GONE);
            }
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

    public void setBubbleColours(String sentBubbleColour, String receivedBubbleColour, String senTextColour, String receivedTextColour) {
        Log.d("MessagesAdapter", "setBubbleColours: " + sentBubbleColour + " " + receivedBubbleColour + " " + senTextColour + " " + receivedTextColour);
        this.sentBubbleColour = sentBubbleColour;
        this.receivedBubbleColour = receivedBubbleColour;
        this.senTextColour = senTextColour;
        this.receivedTextColour = receivedTextColour;
        notifyDataSetChanged();
    }
}
