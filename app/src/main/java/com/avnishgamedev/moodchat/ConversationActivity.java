package com.avnishgamedev.moodchat;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class ConversationActivity extends AppCompatActivity {
    private static final String TAG = "ConversationActivity";

    // Views
    EditText etMessage;
    Button btnSend;
    RelativeLayout rlLoading;

    // Meta data
    Conversation conversation;
    List<Message> messages;
    ListenerRegistration messagesRegistration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        if (!getIntent().hasExtra("conversation")) {
            Toast.makeText(this, "ConversationActivity started without conversation!", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "ConversationActivity started without conversation!");
            finish();
            return;
        }

        conversation = (Conversation) getIntent().getSerializableExtra("conversation");
        messages = new ArrayList<>();

        setupViews();
        loadInitialData();
    }

    @Override
    protected void onDestroy() {
        stopMessagesListener();
        super.onDestroy();
    }

    private void setupViews() {
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        rlLoading = findViewById(R.id.rlLoading);

        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String text = etMessage.getText().toString();
        if (text.isEmpty()) {
            Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        ConversationHelpers.sendMessage(
                conversation.getId(),
                text,
                UserManager.getInstance().getUser().getUsername(),
                UserManager.getInstance().getUser().getName()
        ).addOnCompleteListener(task -> {
           setLoading(false);

           if (task.isSuccessful()) {
               etMessage.setText("");
               Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show();
           } else {
               Log.e(TAG, "Failed to send message", task.getException());
               Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show();
           }
        });
    }

    private void loadInitialData() {
        // First we load last 10 messages, then we start realtime listener
        ConversationHelpers.getMessagesQuery(conversation.getId()).limit(10).get()
                .addOnSuccessListener(snap -> {
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Message m = d.toObject(Message.class);
                        if (m != null) {
                            m.setId(d.getId());
                            messages.add(m);

                            if (m.getStatus().equalsIgnoreCase("sent")) {
                                ConversationHelpers.updateMessageStatus(conversation.getId(), m.getId(), "read")
                                        .addOnFailureListener(e -> Log.e(TAG, "Failed to update message status:", e));
                            }
                        }
                    }

                    startMessagesListener();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load initial messages", e);
                    Toast.makeText(this, "Failed to load initial messages", Toast.LENGTH_SHORT).show();
                });
    }

    private void startMessagesListener() {
        setLoading(true);

        Query query = ConversationHelpers.getMessagesQuery(conversation.getId()).startAfter(conversation.getLastMessageTimestamp());
        messagesRegistration = query.addSnapshotListener((snap, e) -> {
            setLoading(false);

            if (e != null) {
                Log.e(TAG, "Messages listen failed", e);
                return;
            }
            if (snap == null) {
                Log.w(TAG, "Messages snapshot is null");
                return;
            }

            for (DocumentChange dc : snap.getDocumentChanges()) {
                if (dc.getType() == DocumentChange.Type.ADDED) {
                    Message newMessage = dc.getDocument().toObject(Message.class);
                    messages.add(newMessage);

                    // TODO: Update RecyclerView (messages.size() - 1)

                    if (newMessage.getSenderUsername().equals(ConversationHelpers.getOtherUsername(conversation.getId(), UserManager.getInstance().getUser().getUsername()))) {
                        ConversationHelpers.updateMessageStatus(conversation.getId(), newMessage.getId(), "read")
                                .addOnFailureListener(e1 -> Log.e(TAG, "Failed to update message status:", e1));
                    }
                } else if (dc.getType() == DocumentChange.Type.MODIFIED) {
                    Message modifiedMessage = dc.getDocument().toObject(Message.class);
                    int index = IntStream.range(0, messages.size())
                            .filter(i -> messages.get(i).getId().equals(modifiedMessage.getId()))
                            .findFirst()
                            .orElse(-1);
                    if (index != -1) {
                        messages.set(index, modifiedMessage);
                        // adapter.notifyItemChanged(index);
                    } else {
                        Log.w(TAG, "Modified message not found in messages list");
                    }
                }
            }
        });
    }
    private void stopMessagesListener() {
        if (messagesRegistration != null) {
            messagesRegistration.remove();
            messagesRegistration = null;
        }
    }

    private void setLoading(boolean status) {
        rlLoading.setVisibility(status ? View.VISIBLE : View.GONE);
    }
}
