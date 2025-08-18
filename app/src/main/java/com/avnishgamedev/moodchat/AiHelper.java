package com.avnishgamedev.moodchat;

import android.util.Log;

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

        // Testing
//        Content prompt = new Content.Builder()
//                .addText("Write a story about a dog named Sheru.")
//                .build();
//
//        ListenableFuture<GenerateContentResponse> response = model.generateContent(prompt);
//        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
//            @Override
//            public void onSuccess(GenerateContentResponse result) {
//                Log.d(TAG, "onSuccess: " + result.getText());
//            }
//
//            @Override
//            public void onFailure(Throwable t) {
//                Log.e(TAG, "onFailure: ", t);
//            }
//        }, Executors.newSingleThreadExecutor());
    }
}
