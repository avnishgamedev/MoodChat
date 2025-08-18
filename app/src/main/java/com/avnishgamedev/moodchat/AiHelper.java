package com.avnishgamedev.moodchat;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerativeBackend;

import java.util.concurrent.Executors;

public class AiHelper {
    private static final String TAG = "AiHelper";

    private static AiHelper instance;

    private final GenerativeModel ai;
    private final GenerativeModelFutures model;

    public static AiHelper getInstance() {
        if (instance == null) {
            instance = new AiHelper();
        }
        return instance;
    }

    private AiHelper() {
        ai = FirebaseAI.getInstance(GenerativeBackend.googleAI()).generativeModel("gemini-2.5-flash");
        model = GenerativeModelFutures.from(ai);
    }

    public Task<ConversationThemeData> promptForTheme(String message) {
        TaskCompletionSource<ConversationThemeData> res = new TaskCompletionSource<>();

        Content prompt = new Content.Builder()
                .addText("You are an assistant for a chat app. Given a chat message, generate a conversation theme for the chat UI. \n" +
                        "Return the result as a single JSON object.\n" +
                        "\n" +
                        "The JSON object must include these keys (all colors should be hex format, e.g., \"#FFFFFF\"):\n" +
                        "- \"background\": chat header background color\n" +
                        "- \"surrounding\": background color of main chat area\n" +
                        "- \"messageBackground\": background color for the message input field\n" +
                        "- \"messageTextColour\": text color for the message input field (should be readable on messageBackground)\n" +
                        "- \"sendBackground\": background color for the send button container\n" +
                        "- \"sendIconTint\": tint color for the send button icon\n" +
                        "- \"sentBubbleColour\": background color for messages sent by the user\n" +
                        "- \"sentTextColour\": text color for sent messages (should be readable on sentBubbleColour)\n" +
                        "- \"receivedBubbleColour\": background color for messages received from others\n" +
                        "- \"receivedTextColour\": text color for received messages (should be readable on receivedBubbleColour)")
                .addText("Ensure good contrast between text colors and their respective background colors for readability.")
                .addText("Message: \"" + message + "\"")
                .addText("Generate a JSON object with appropriate theme colors for this mood.")
                .addText("Only return the JSON object, nothing else.")
                .build();

        Log.d(TAG, "prompt: " + prompt);

        ListenableFuture<GenerateContentResponse> response = model.generateContent(prompt);
        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                Log.d(TAG, "theme: " + result.getText());

                ConversationThemeData data = ConversationThemeData.parseThemeDataFromAI(result.getText());
                res.setResult(data);
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "onFailure: ", t);
                res.setException(new Exception(t.getMessage()));
            }
        }, Executors.newSingleThreadExecutor());

        return res.getTask();
    }
}
