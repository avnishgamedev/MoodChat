package com.avnishgamedev.moodchat;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.Map;

public class UserManager {
    private static final String TAG = "UserManager";

    private static volatile UserManager instance;

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;
    private FirebaseUser user;

    private DocumentSnapshot userDoc;
    private boolean pendingUserOnlineStatusUpdate = false;

    private UserManager() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        user = auth.getCurrentUser();
        auth.addAuthStateListener(auth -> {
            user = auth.getCurrentUser();
            Log.d(TAG, "AuthStateChange - Login status: " + (user != null));

            setUserOnlineStatus(user != null);
        });
    }

    public static UserManager getInstance() {
        if (instance == null) {
            synchronized (UserManager.class) {
                if (instance == null) {
                    instance = new UserManager();
                }
            }
        }
        return instance;
    }

    public Task<DocumentSnapshot> tryLoadUserDocument() {
        TaskCompletionSource<DocumentSnapshot> completionSource = new TaskCompletionSource<>();
        if (user != null) {
            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            userDoc = doc;
                            completionSource.setResult(doc);

                            if (pendingUserOnlineStatusUpdate) {
                                setUserOnlineStatus(true);
                            }
                        } else {
                            Log.w(TAG, "tryLoadUserDocument: User Document doesn't exist!");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to load user document: " + e.getLocalizedMessage());
                        completionSource.setException(e);
                    });
        } else {
            Log.e(TAG, "tryLoadUserDocument: User is null!");
            completionSource.setException(new Exception("User is null!"));
        }
        return completionSource.getTask();
    }

    public Task<Void> updateUserDocument(Map<String, Object> updates) {
        TaskCompletionSource<Void> completionSource = new TaskCompletionSource<>();
        if (userDoc != null) {
            return db.collection("users").document(user.getUid()).set(updates, SetOptions.merge());
        } else {
            completionSource.setException(new Exception("User document doesn't exist!"));
            return completionSource.getTask();
        }
    }

    public void setUserOnlineStatus(boolean online) {
        Map<String, Object> updates = Map.of("online", online);
        if (user != null) {
            db.collection("users").document(user.getUid()).set(updates, SetOptions.merge())
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to update user online status: " + e.getLocalizedMessage());
                    });
            pendingUserOnlineStatusUpdate = false;
        } else if (userDoc != null) {
            userDoc.getReference().set(updates)
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to update user online status via userDoc: " + e.getLocalizedMessage());
                    });
            pendingUserOnlineStatusUpdate = false;
        } else {
            pendingUserOnlineStatusUpdate = online;
        }
    }
}
