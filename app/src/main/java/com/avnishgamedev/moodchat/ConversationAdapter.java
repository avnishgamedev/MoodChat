package com.avnishgamedev.moodchat;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {
    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivProfilePic;
        TextView tvName;
        TextView tvLastMessage;
        TextView tvLastMessageTime;
        ImageView ivMessageStatus;
        View onlineIndicator;
        RelativeLayout rlLoading;
        public ViewHolder(View itemView) {
            super(itemView);

            ivProfilePic = itemView.findViewById(R.id.ivProfilePic);
            tvName = itemView.findViewById(R.id.tvName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvLastMessageTime = itemView.findViewById(R.id.tvLastMessageTime);
            ivMessageStatus = itemView.findViewById(R.id.ivMessageStatus);
            onlineIndicator = itemView.findViewById(R.id.onlineIndicator);
            rlLoading = itemView.findViewById(R.id.rlLoading);
        }
    }

    private List<Conversation> conversations;
    private AdapterView.OnItemClickListener listener;
    public ConversationAdapter(List<Conversation> conversations, AdapterView.OnItemClickListener listener) {
        this.conversations = conversations;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Conversation conversation = conversations.get(position);
        holder.itemView.setOnClickListener(v -> listener.onItemClick(null, v, position, 0));

        holder.rlLoading.setVisibility(View.VISIBLE);
        holder.onlineIndicator.setVisibility(View.GONE);
        ConversationHelpers.getUserByUsername(ConversationHelpers.getOtherUsername(conversation.getId(), UserManager.getInstance().getUser().getUsername()))
                .addOnSuccessListener(user -> {
                    holder.tvName.setText(user.getName());
                    holder.tvLastMessage.setText(conversation.getLastMessage());
                    holder.tvLastMessageTime.setText(new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(conversation.getLastMessageTimestamp()));
                    holder.ivProfilePic.setImageBitmap(base64ToBitmap(user.getProfilePicture()));

                     if (user.isOnline()) {
                         holder.onlineIndicator.setVisibility(View.VISIBLE);
                     }
                })
                .addOnFailureListener(e -> {
                    holder.tvName.setText("Unknown");
                })
                .addOnCompleteListener(task -> {
                    holder.rlLoading.setVisibility(View.GONE);
                });
    }

    @Override
    public int getItemCount() {
        return conversations.size();
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
