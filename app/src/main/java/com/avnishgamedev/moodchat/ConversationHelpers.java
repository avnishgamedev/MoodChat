package com.avnishgamedev.moodchat;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConversationHelpers {
    public static final String TAG = "ConversationHelpers";

    public static Task<Void> createConversation(
            String currentUsername,
            String otherUsername
    ) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        String convId = conversationIdFor(currentUsername, otherUsername);
        DocumentReference convRef = conversationRef(db, convId);

        Conversation conv = new Conversation(
                Arrays.asList(currentUsername, otherUsername),
                "", // lastMessage initially empty
                Timestamp.now().toDate()
        );

        return convRef.set(conv, SetOptions.merge());
    }

    public static Task<Void> sendMessage(
            String conversationId,
            String text,
            String senderUsername,
            String senderName
    ) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        DocumentReference convRef = conversationRef(db, conversationId);
        DocumentReference msgRef = messagesRef(db, conversationId).document();

        Message msg = new Message(text, senderUsername, senderName, Timestamp.now(), "sent");
        msg.setStatus("sent");

        WriteBatch batch = db.batch();
        batch.set(msgRef, msg);

        Map<String, Object> convUpdate = new HashMap<>();
        convUpdate.put("lastMessage", text);
        convUpdate.put("updatedAt", FieldValue.serverTimestamp());
        batch.update(convRef, convUpdate);

        return batch.commit();
    }

    public static Task<Void> updateMessageStatus(
            String conversationId,
            String messageId,
            String newStatus
    ) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        return messagesRef(db, conversationId)
                .document(messageId)
                .update("status", newStatus);
    }

    public static Task<User> getUserByUsername(String username) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        return db.collection("users").whereEqualTo("username", username).get().continueWithTask(task -> {
            if (task.isSuccessful()) {
                if (!task.getResult().isEmpty()) {
                    DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                    return Tasks.forResult(doc.toObject(User.class));
                }
                return Tasks.forException(new UserNotFoundException());
            } else {
                return Tasks.forException(task.getException());
            }
        });
    }

    public static Task<ListenerRegistration> bindUserByUsername(String username, EventListener<DocumentSnapshot> userListener) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        TaskCompletionSource<ListenerRegistration> res = new TaskCompletionSource<>();

        getUserByUsername(username)
                .addOnSuccessListener(user -> {
                    res.setResult(db.collection("users").document(user.getDocumentId()).addSnapshotListener(userListener));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "bindUserByUsername: ", e);
                    res.setException(e);
                });

        return res.getTask();
    }


    public static String getOtherUsername(String conversationId, String currentUsername) {
        return conversationId.replace(currentUsername, "").replace("_", "");
    }

    public static Query getConversationsQuery(String username) {
        return FirebaseFirestore.getInstance()
                .collection("conversation")
                .whereArrayContains("members", username)
                .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING);
    }

    public static Query getMessagesQuery(String conversationId) {
        return FirebaseFirestore.getInstance()
                .collection("conversation")
                .document(conversationId)
                .collection("messages")
                .orderBy("sentAt", Query.Direction.ASCENDING);
    }

    // ----------------- Helpers ----------------

    // Deterministic conversation id for 1-to-1 (prevents duplicates)
    private static String conversationIdFor(String uidA, String uidB) {
        return (uidA.compareTo(uidB) < 0) ? uidA + "_" + uidB : uidB + "_" + uidA;
    }
    private static DocumentReference conversationRef(FirebaseFirestore db, String conversationId) {
        return db.collection("conversation").document(conversationId);
    }

    private static CollectionReference messagesRef(FirebaseFirestore db, String conversationId) {
        return conversationRef(db, conversationId).collection("messages");
    }

    private static class UserNotFoundException extends Exception {
        public UserNotFoundException() {
            super("User not found!");
        }
    }
}
