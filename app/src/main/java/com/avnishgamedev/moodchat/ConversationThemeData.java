package com.avnishgamedev.moodchat;

import org.json.JSONObject;

import java.io.Serializable;

public class ConversationThemeData implements Serializable {

    private String background;
    private String surrounding;
    private String messageBackground;
    private String messageTextColour;
    private String sendBackground;
    private String sendIconTint;
    private String sentBubbleColour;
    private String sentTextColour;
    private String receivedBubbleColour;
    private String receivedTextColour;

    public ConversationThemeData() {
    }

    public ConversationThemeData(String background, String surrounding, String messageBackground, String messageTextColour, String sendBackground, String sendIconTint, String sentBubbleColour, String sentTextColour, String receivedBubbleColour, String receivedTextColour) {
        this.background = background;
        this.surrounding = surrounding;
        this.messageBackground = messageBackground;
        this.messageTextColour = messageTextColour;
        this.sendBackground = sendBackground;
        this.sendIconTint = sendIconTint;
        this.sentBubbleColour = sentBubbleColour;
        this.sentTextColour = sentTextColour;
        this.receivedBubbleColour = receivedBubbleColour;
        this.receivedTextColour = receivedTextColour;
    }

    public String getBackground() {
        return background;
    }

    public void setBackground(String background) {
        this.background = background;
    }

    public String getSurrounding() {
        return surrounding;
    }

    public void setSurrounding(String surrounding) {
        this.surrounding = surrounding;
    }


    public String getMessageBackground() {
        return messageBackground;
    }

    public void setMessageBackground(String messageBackground) {
        this.messageBackground = messageBackground;
    }

    public String getMessageTextColour() {
        return messageTextColour;
    }

    public void setMessageTextColour(String messageTextColour) {
        this.messageTextColour = messageTextColour;
    }

    public String getSendBackground() {
        return sendBackground;
    }

    public void setSendBackground(String sendBackground) {
        this.sendBackground = sendBackground;
    }


    public String getSendIconTint() {
        return sendIconTint;
    }

    public void setSendIconTint(String sendIconTint) {
        this.sendIconTint = sendIconTint;
    }


    public String getSentBubbleColour() {
        return sentBubbleColour;
    }

    public void setSentBubbleColour(String sentBubbleColour) {
        this.sentBubbleColour = sentBubbleColour;
    }

    public String getSentTextColour() {
        return sentTextColour;
    }

    public void setSentTextColour(String sentTextColour) {
        this.sentTextColour = sentTextColour;
    }

    public String getReceivedBubbleColour() {
        return receivedBubbleColour;
    }

    public void setReceivedBubbleColour(String receivedBubbleColour) {
        this.receivedBubbleColour = receivedBubbleColour;
    }

    public String getReceivedTextColour() {
        return receivedTextColour;
    }

    public void setReceivedTextColour(String receivedTextColour) {
        this.receivedTextColour = receivedTextColour;
    }

    public static ConversationThemeData parseThemeDataFromAI(String aiResponse) {
        try {
            String cleanJson = aiResponse
                    .replace("```json", "")
                    .replace("```", "")
                    .trim();

            JSONObject json = new JSONObject(cleanJson);
            ConversationThemeData data = new ConversationThemeData();
            data.background = json.optString("background", "#000000");
            data.surrounding = json.optString("surrounding", "#000000");
            data.messageBackground = json.optString("messageBackground", "#000000");
            data.messageTextColour = json.optString("messageTextColour", "#000000");
            data.sendBackground = json.optString("sendBackground", "#000000");
            data.sendIconTint = json.optString("sendIconTint", "#000000");
            data.sentBubbleColour = json.optString("sentBubbleColour", "#000000");
            data.sentTextColour = json.optString("sentTextColour", "#000000");
            data.receivedBubbleColour = json.optString("receivedBubbleColour", "#000000");
            data.receivedTextColour = json.optString("receivedTextColour", "#000000");
            return data;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
