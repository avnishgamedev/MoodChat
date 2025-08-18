package com.avnishgamedev.moodchat;

import com.google.firebase.firestore.DocumentId;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

public class Conversation implements Serializable {
    @DocumentId
    private String id;
    private List<String> members;
    private String lastMessage;
    private Date lastMessageTimestamp;
    private ConversationThemeData themeData;

    public Conversation() {}
    public Conversation(List<String> members, String lastMessage, Date lastMessageTimestamp, ConversationThemeData themeData) {
        this.members = members;
        this.lastMessage = lastMessage;
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.themeData = themeData;
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

    public Date getLastMessageTimestamp() {
        return lastMessageTimestamp;
    }
    public void setLastMessageTimestamp(Date lastMessageTimestamp) {
        this.lastMessageTimestamp = lastMessageTimestamp;
    }

    public ConversationThemeData getThemeData() {
        return themeData;
    }
    public void setThemeData(ConversationThemeData themeData) {
        this.themeData = themeData;
    }
}
