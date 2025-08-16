package com.avnishgamedev.moodchat;

import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ConversationHelpers {

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
                Timestamp.now()
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
}
