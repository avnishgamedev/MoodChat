package com.avnishgamedev.moodchat;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.Map;

public class UserManager {
    private static final String TAG = "UserManager";

    private static volatile UserManager instance;

    private final FirebaseFirestore db;
    private boolean pendingUserOnlineStatusUpdate = false;

    private FirebaseUser firebaseUser;
    private User user;

    private UserManager() {
        db = FirebaseFirestore.getInstance();

        firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        FirebaseAuth.getInstance().addAuthStateListener(auth -> {
            firebaseUser = auth.getCurrentUser();
            Log.d(TAG, "AuthStateChange - Login status: " + (firebaseUser != null));

            setUserOnlineStatus(firebaseUser != null);
            if (firebaseUser == null) {
                user = null;
            }
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

    public User getUser() {
        return user;
    }

    public Task<User> loadUser() {
        TaskCompletionSource<User> completionSource = new TaskCompletionSource<>();
        if (firebaseUser != null) {
            db.collection("users").document(firebaseUser.getUid()).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            user = doc.toObject(User.class);
                            completionSource.setResult(user);

                            if (pendingUserOnlineStatusUpdate) {
                                setUserOnlineStatus(true);
                            }
                        } else {
                            Log.w(TAG, "loadUser: User Document doesn't exist!");
                            completionSource.setException(new UserDocumentDoesntExistException());
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to load user document: " + e.getLocalizedMessage());
                        completionSource.setException(e);
                    });
        } else {
            Log.e(TAG, "loadUser: User is null!");
            completionSource.setException(new Exception("User is null!"));
        }
        return completionSource.getTask();
    }

    public void setUserOnlineStatus(boolean online) {
        Map<String, Object> updates = Map.of("online", online, "lastSeen", Timestamp.now());
        if (user != null) {
            db.collection("users").document(user.getDocumentId()).set(updates, SetOptions.merge())
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to update user online status: " + e.getLocalizedMessage());
                    });
            pendingUserOnlineStatusUpdate = false;
        } else {
            pendingUserOnlineStatusUpdate = online;
        }
    }

    public Task<Void> createOrUpdateUserDocument(User u) {
        TaskCompletionSource<Void> completionSource = new TaskCompletionSource<>();
        if (firebaseUser != null) {
            u.setDocumentId(firebaseUser.getUid());
            return db.collection("users").document(firebaseUser.getUid()).set(u)
                    .continueWithTask(task -> {
                        if (task.isSuccessful()) {
                            user = u;
                            return Tasks.forResult(null);
                        }
                        return Tasks.forException(task.getException());
                    });
        }
        completionSource.setException(new Exception("User is null!"));
        return completionSource.getTask();
    }

    public static class UserDocumentDoesntExistException extends Exception {
        public UserDocumentDoesntExistException(String message) {
            super(message);
        }
        public UserDocumentDoesntExistException() {
            super("User Document Doesn't Exist!");
        }
    }
}
