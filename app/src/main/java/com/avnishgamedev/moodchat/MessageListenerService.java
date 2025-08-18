package com.avnishgamedev.moodchat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Date;

public class MessageListenerService extends Service {
    private static final String TAG = "MessageListenerService";

    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "MessageListenerServiceChannel";

    private ArrayList<ListenerRegistration> listeners;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        listeners = new ArrayList<>();

        Log.d(TAG, "Service Created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if ("STOP_SERVICE".equals(action)) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        createNotificationAndStartForeground();
        
        startFirebaseListeners();

        return START_STICKY;
    }

    private void startFirebaseListeners() {
        UserManager.getInstance().loadUser()
                .addOnSuccessListener(user -> {
                    String currentUsername = user.getUsername();
                    ConversationHelpers.getConversationsQuery(currentUsername).get()
                            .addOnSuccessListener(querySnap -> {
                                for (DocumentSnapshot doc : querySnap.getDocuments()) {
                                    if (doc.exists()) {
                                        Conversation conv = doc.toObject(Conversation.class);
                                        if (conv != null) {
                                            ListenerRegistration listener = ConversationHelpers.getMessagesQuery(conv.getId())
                                                    .whereEqualTo("status", "sent")
                                                    .addSnapshotListener((snap, e) -> {
                                                        if (e != null) {
                                                            Log.e(TAG, "Message listen failed for conv: " + conv.getId(), e);
                                                            return;
                                                        }
                                                        if (snap == null) {
                                                            Log.w(TAG, "Messages snapshot is null for conv: " + conv.getId());
                                                            return;
                                                        }

                                                        Log.d(TAG, "Received " + snap.getDocumentChanges().size() + " document changes");

                                                        for (DocumentChange dc : snap.getDocumentChanges()) {
                                                            if (dc.getType() == DocumentChange.Type.ADDED) {
                                                                Message message = dc.getDocument().toObject(Message.class);

                                                                if (message.getSenderUsername().equals(currentUsername)) {
                                                                    Log.d(TAG, "Skipping own message");
                                                                    continue;
                                                                }

                                                                Log.d(TAG, "Showing notification for message: " + message.getMessage());

                                                                ConversationHelpers.getUserByUsername(message.getSenderUsername())
                                                                        .addOnSuccessListener(otherUser -> {
                                                                            Log.d(TAG, "Loaded other user: " + otherUser.getName());
                                                                            showMessageNotification(getBaseContext(),
                                                                                    base64ToBitmap(otherUser.getProfilePicture()),
                                                                                    otherUser.getUsername(),
                                                                                    otherUser.getName(),
                                                                                    message.getMessage(),
                                                                                    conv);
                                                                        })
                                                                        .addOnFailureListener(err -> {
                                                                            Log.e(TAG, "Failed to load other user for message listener", err);
                                                                        });

                                                                ConversationHelpers.updateMessageStatus(conv.getId(), message.getId(), "delivered");
                                                            }
                                                        }
                                                    });
                                            listeners.add(listener);
                                            Log.d(TAG, "Added listener for conversation: " + conv.getId());
                                        }
                                    }
                                }
                            })
                            .addOnFailureListener(err -> {
                                Log.e(TAG, "Failed to load conversations for message listener", err);
                            });
                })
                .addOnFailureListener(err -> {
                    Log.e(TAG, "Failed to load user for message listener", err);
                });
    }

    public void showMessageNotification(Context context, Bitmap userProfilePic, String username, String actualName, String message, Conversation conversation) {
        // Notification channel ID
        String MESSAGE_CHANNEL_ID = "chat_message_channel";
        int notificationId = (int) System.currentTimeMillis();

        // Intent to launch ConversationActivity
        Intent intent = new Intent(context, ConversationActivity.class);
        intent.putExtra("conversation", conversation);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
                .setContentTitle(actualName + " (" + username + ")")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_person)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE);

        if (userProfilePic != null)
            builder.setLargeIcon(userProfilePic);

        NotificationChannel channel = new NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
        );
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);

        try {
            notificationManager.notify(notificationId, builder.build());
        } catch (SecurityException e) {
            Log.w(TAG, "SecurityException while trying to show notification", e);
        }
    }


    private void createNotificationAndStartForeground() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MoodChat Messages")
                .setContentText("Listening for new messages")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Message Listener Service Channel",
                NotificationManager.IMPORTANCE_MIN
        );

        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        for (ListenerRegistration listener : listeners) {
            if (listener != null) {
                listener.remove();
            }
        }

        Log.d(TAG, "Service Destroyed");
        super.onDestroy();
    }

    // Helpers
    public static Bitmap base64ToBitmap(String base64Str) {
        if (base64Str == null) {
            return null;
        }
        byte[] decodedBytes = Base64.decode(base64Str, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
    }
}
