package com.avnishgamedev.moodchat;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.List;

public class Conversation {
    @DocumentId
    private String id;
    private List<String> members;
    private String lastMessage;
    private Timestamp lastMessageTimestamp;

    public Conversation() {}
    public Conversation(List<String> members, String lastMessage, Timestamp lastMessageTimestamp) {
        this.members = members;
        this.lastMessage = lastMessage;
        this.lastMessageTimestamp = lastMessageTimestamp;
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public List<String> getMembers() {
        return members;
    }
    public void setMembers(List<String> members) {
        this.members = members;
    }

    public String getLastMessage() {
        return lastMessage;
    }
    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public Timestamp getLastMessageTimestamp() {
        return lastMessageTimestamp;
    }
    public void setLastMessageTimestamp(Timestamp lastMessageTimestamp) {
        this.lastMessageTimestamp = lastMessageTimestamp;
    }
}
