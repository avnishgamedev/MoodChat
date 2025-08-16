package com.avnishgamedev.moodchat;

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
        RelativeLayout rlLoading;
        public ViewHolder(View itemView) {
            super(itemView);

            ivProfilePic = itemView.findViewById(R.id.ivProfilePic);
            tvName = itemView.findViewById(R.id.tvName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvLastMessageTime = itemView.findViewById(R.id.tvLastMessageTime);
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
        ConversationHelpers.getUserByUsername(ConversationHelpers.getOtherUsername(conversation.getId(), UserManager.getInstance().getUser().getUsername()))
                .addOnSuccessListener(user -> {
                    holder.tvName.setText(user.getName());
                    holder.tvLastMessage.setText(conversation.getLastMessage());
                    holder.tvLastMessageTime.setText(new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(conversation.getLastMessageTimestamp()));
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
}
