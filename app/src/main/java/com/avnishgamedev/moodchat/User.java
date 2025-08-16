package com.avnishgamedev.moodchat;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

public class User {
    @DocumentId
    private String documentId;
    private String email;
    private String name;
    private boolean online;
    private String profilePicture;
    private String username;
    private Timestamp lastSeen;
    private Timestamp registeredOn;

    public User() {}
    public User(String email, String name, boolean online, String profilePicture, String username, Timestamp lastSeen, Timestamp registeredOn) {
        this.email = email;
        this.name = name;
        this.online = online;
        this.profilePicture = profilePicture;
        this.username = username;
        this.lastSeen = lastSeen;
        this.registeredOn = registeredOn;
    }

    public String getDocumentId() {
        return documentId;
    }
    public void setDocumentId(String userId) {
        this.documentId = userId;
    }

    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public boolean isOnline() {
        return online;
    }
    public void setOnline(boolean online) {
        this.online = online;
    }

    public String getProfilePicture() {
        return profilePicture;
    }
    public void setProfilePicture(String profilePicture) {
        this.profilePicture = profilePicture;
    }

    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }

    public Timestamp getLastSeen() {
        return lastSeen;
    }
    public void setLastSeen(Timestamp lastSeen) {
        this.lastSeen = lastSeen;
    }

    public Timestamp getRegisteredOn() {
        return registeredOn;
    }
    public void setRegisteredOn(Timestamp registeredOn) {
        this.registeredOn = registeredOn;
    }
}
