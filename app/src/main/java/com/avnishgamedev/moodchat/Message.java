package com.avnishgamedev.moodchat;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

public class Message {
    @DocumentId
    private String id;
    private String message;
    private String senderUsername;
    private String senderName;
    private Timestamp sentAt;
    private String status;

    public Message() {}
    public Message(String message, String senderUsername, String senderName, Timestamp sentAt, String status) {
        this.message = message;
        this.senderUsername = senderUsername;
        this.senderName = senderName;
        this.sentAt = sentAt;
        this.status = status;
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }

    public String getSenderUsername() {
        return senderUsername;
    }
    public void setSenderUsername(String senderUsername) {
        this.senderUsername = senderUsername;
    }

    public String getSenderName() {
        return senderName;
    }
    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public Timestamp getSentAt() {
        return sentAt;
    }
    public void setSentAt(Timestamp sentAt) {
        this.sentAt = sentAt;
    }

    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
}
