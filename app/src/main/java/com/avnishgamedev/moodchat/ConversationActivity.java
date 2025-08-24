package com.avnishgamedev.moodchat;

import static com.avnishgamedev.moodchat.MessagesAdapter.base64ToBitmap;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class ConversationActivity extends AppCompatActivity implements CallManager.CallListener {
    private static final String TAG = "ConversationActivity";

    // Views
    ConstraintLayout constraintLayout;
    View viewBackground;
    ImageView ivBack;
    ImageButton btnVideoCall;
    ImageView ivProfilePic;
    TextView tvChatName;
    TextView tvChatUsername;
    ImageView ivStatus;
    RecyclerView rvMessages;
    MessagesAdapter adapter;
    EditText etMessage;
    FloatingActionButton flSend;
    CircularProgressIndicator progressIndicator;
    LinearLayout loadingContainer;

    // Meta data
    Conversation conversation;
    List<Message> messages;
    ListenerRegistration messagesRegistration;
    User thisUser;
    User otherUser;

    // Call
    private CallManager callManager;
    // Required permissions for video calling
    private String[] requiredPermissions = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
    };
    private static final int PERMISSION_REQUEST_CODE = 1000;
    private AlertDialog incomingCallDialog = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        if (!getIntent().hasExtra("conversation")) {
            Toast.makeText(this, "ConversationActivity started without conversation!", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "ConversationActivity started without conversation!");
            finish();
            return;
        }

        conversation = (Conversation) getIntent().getSerializableExtra("conversation");
        messages = new ArrayList<>();

        setupViews();
        loadInitialData();
        setupVideoCall();

        MainActivity.conversationsUpdated = new EventListener<Conversation>() {
            @Override
            public void onEvent(@Nullable Conversation value, @Nullable FirebaseFirestoreException error) {
                if (value != null) {
                    if (conversation.getId().equals(value.getId())) {
                        conversation = value;
                        setThemeFromData(conversation.getThemeData());
                    }
                }
            }
        };
    }

    @Override
    protected void onDestroy() {
        stopMessagesListener();
        super.onDestroy();

        // Dismiss any active call dialog
        if (incomingCallDialog != null && incomingCallDialog.isShowing()) {
            incomingCallDialog.dismiss();
            incomingCallDialog = null;
        }

        // Clean up call manager listener
        if (callManager != null) {
            callManager.setCallListener(null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Dismiss dialog when leaving the activity
        if (incomingCallDialog != null && incomingCallDialog.isShowing()) {
            incomingCallDialog.dismiss();
            incomingCallDialog = null;
        }
    }


    private void setupViews() {
        constraintLayout = findViewById(R.id.constraintLayout);
        viewBackground = findViewById(R.id.viewBackground);
        ivBack = findViewById(R.id.ivBack);
        btnVideoCall = findViewById(R.id.btnVideoCall);
        ivProfilePic = findViewById(R.id.ivProfilePic);
        tvChatName = findViewById(R.id.tvChatName);
        tvChatUsername = findViewById(R.id.tvChatUsername);
        ivStatus = findViewById(R.id.ivStatus);
        rvMessages = findViewById(R.id.rvMessages);
        etMessage = findViewById(R.id.etMessage);
        flSend = findViewById(R.id.flSend);
        progressIndicator = findViewById(R.id.progressIndicator);
        loadingContainer = findViewById(R.id.loadingContainer);

        rvMessages.setLayoutManager(new LinearLayoutManager(this));

        ivBack.setOnClickListener(v -> finish());
        flSend.setOnClickListener(v -> sendMessage());
        btnVideoCall.setOnClickListener(v -> initiateVideoCall());
    }


    private void setupVideoCall() {
        callManager = CallManager.getInstance(this);
        callManager.setCallListener(this);
        callManager.setCurrentUserId(thisUser.getUsername()); // Set your current user ID

        // Clean up any old calls when opening chat
        callManager.cleanupOldCalls();
    }

    private void initiateVideoCall() {
        // Check permissions first
        if (checkVideoCallPermissions()) {
            startVideoCall();
        } else {
            requestPermissions();
        }
    }

    private void startVideoCall() {
        Intent intent = new Intent(this, VideoCallActivity.class);
        intent.putExtra("target_user_id", otherUser.getUsername());
        intent.putExtra("is_outgoing", true);
        startActivity(intent);
    }

    private boolean checkVideoCallPermissions() {
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        List<String> permissionsNeedingRationale = new ArrayList<>();

        // Check which permissions are missing and which need rationale
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);

                if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                    permissionsNeedingRationale.add(permission);
                }
            }
        }

        // Show rationale dialog if needed
        if (!permissionsNeedingRationale.isEmpty()) {
            showPermissionRationaleDialog(permissionsToRequest);
        } else {
            // Request permissions directly
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE
            );
        }
    }

    private void showPermissionRationaleDialog(List<String> permissionsToRequest) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Permissions Required");
        builder.setMessage("To make video calls, this app needs access to:\n\n" +
                "• Camera: To show your video during calls\n" +
                "• Microphone: To transmit your voice during calls\n" +
                "• Audio Settings: To optimize call quality\n\n" +
                "Please grant these permissions to continue.");

        builder.setPositiveButton("Grant Permissions", (dialog, which) -> {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE
            );
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            Toast.makeText(this, "Permissions are required for video calling", Toast.LENGTH_SHORT).show();
        });

        builder.setCancelable(false);
        builder.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            Map<String, Integer> permissionResults = new HashMap<>();

            // Store results in a map for easier processing
            for (int i = 0; i < permissions.length; i++) {
                permissionResults.put(permissions[i], grantResults[i]);
            }

            // Check if all permissions were granted
            boolean allPermissionsGranted = true;
            List<String> deniedPermissions = new ArrayList<>();
            List<String> permanentlyDeniedPermissions = new ArrayList<>();

            for (String permission : requiredPermissions) {
                Integer result = permissionResults.get(permission);
                if (result == null || result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    deniedPermissions.add(permission);

                    // Check if permission was permanently denied
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                        permanentlyDeniedPermissions.add(permission);
                    }
                }
            }

            if (allPermissionsGranted) {
                // All permissions granted - start video call
                startVideoCall();
            } else {
                // Handle denied permissions
                handleDeniedPermissions(deniedPermissions, permanentlyDeniedPermissions);
            }
        }
    }

    private void handleDeniedPermissions(List<String> deniedPermissions, List<String> permanentlyDeniedPermissions) {
        if (!permanentlyDeniedPermissions.isEmpty()) {
            // Some permissions were permanently denied - show settings dialog
            showPermissionSettingsDialog();
        } else {
            // Permissions were just denied this time - show retry option
            showPermissionDeniedDialog();
        }
    }

    /**
     * Show dialog for permanently denied permissions - redirect to settings
     */
    private void showPermissionSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Permissions Required");
        builder.setMessage("Video calling requires camera and microphone permissions. " +
                "Please enable them in the app settings to continue.\n\n" +
                "Go to Settings > Apps > " + getString(R.string.app_name) + " > Permissions");

        builder.setPositiveButton("Open Settings", (dialog, which) -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            Toast.makeText(this, "Video calling is not available without permissions", Toast.LENGTH_SHORT).show();
        });

        builder.show();
    }

    private void showPermissionDeniedDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Permissions Denied");
        builder.setMessage("Camera and microphone access are required for video calls. " +
                "Would you like to grant these permissions now?");

        builder.setPositiveButton("Try Again", (dialog, which) -> {
            requestPermissions();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            Toast.makeText(this, "Permissions are required for video calling", Toast.LENGTH_SHORT).show();
        });

        builder.show();
    }

    // CallManager.CallListener implementations
    @Override
    public void onIncomingCall(String callerName, String callId) {
        runOnUiThread(() -> {
            // Dismiss any existing dialog first
            if (incomingCallDialog != null && incomingCallDialog.isShowing()) {
                incomingCallDialog.dismiss();
            }

            // Check permissions before showing dialog
            if (checkVideoCallPermissions()) {
                showIncomingCallDialog(callerName, callId);
            } else {
                // Auto-decline if permissions not available
                declineCall(callId);
                Toast.makeText(this, "Cannot accept call - permissions required", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showIncomingCallDialog(String callerName, String callId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Incoming Video Call");
        builder.setMessage(callerName + " is calling you...");

        builder.setPositiveButton("Answer", (dialog, which) -> {
            Intent intent = new Intent(this, VideoCallActivity.class);
            intent.putExtra("call_id", callId);
            intent.putExtra("is_incoming", true);
            startActivity(intent);
            incomingCallDialog = null;
        });

        builder.setNegativeButton("Decline", (dialog, which) -> {
            declineCall(callId);
            incomingCallDialog = null;
        });

        builder.setOnDismissListener(dialog -> {
            incomingCallDialog = null;
        });

        builder.setCancelable(false);
        incomingCallDialog = builder.create();
        incomingCallDialog.show();
    }

    private void declineCall(String callId) {
        // Update call status to declined in Firestore
        Map<String, Object> declineData = new HashMap<>();
        declineData.put("status", "declined");
        declineData.put("declinedBy", thisUser.getUsername());
        declineData.put("declinedAt", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance().collection("calls").document(callId)
                .update(declineData)
                .addOnSuccessListener(aVoid -> {
                    Log.d("ChatActivity", "Call declined successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e("ChatActivity", "Failed to decline call", e);
                });
    }


    @Override
    public void onCallConnected() {}

    @Override
    public void onCallEnded() {}

    @Override
    public void onCallFailed(String error) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Call failed: " + error, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onRemoteStreamReceived() {}

    private void sendMessage() {
        String text = etMessage.getText().toString();
        if (text.isEmpty()) {
            Snackbar.make(flSend, "Message cannot be empty", Snackbar.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        ConversationHelpers.sendMessage(
                conversation.getId(),
                text,
                UserManager.getInstance().getUser().getUsername(),
                UserManager.getInstance().getUser().getName()
        ).addOnCompleteListener(task -> {
           setLoading(false);

           if (task.isSuccessful()) {
               etMessage.setText("");
               Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show();
           } else {
               Log.e(TAG, "Failed to send message", task.getException());
               Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show();
           }
        });

        AiHelper.getInstance().promptForTheme(text).addOnSuccessListener(theme -> {
            setThemeFromData(theme);
            conversation.setThemeData(theme);
            ConversationHelpers.updateConversation(conversation.getId(), new HashMap<String, Object>() {{
                put("themeData", theme);
            }});
        });
    }

    private void loadInitialData() {
        setLoading(true);
        thisUser = UserManager.getInstance().getUser();
        ConversationHelpers.bindUserByUsername(ConversationHelpers.getOtherUsername(conversation.getId(), thisUser.getUsername()), (snap, e) -> {
            if (e != null) {
                Log.e(TAG, "Failed to load otherUser!", e);
                Toast.makeText(this, "Failed to load otherUser!", Toast.LENGTH_SHORT).show();
                setLoading(false);
                return;
            }
            if (snap == null) {
                Log.w(TAG, "otherUser snapshot is null");
                setLoading(false);
                return;
            }

            otherUser = snap.toObject(User.class);

            // UI updates
            tvChatName.setText(otherUser.getName());
            tvChatUsername.setText(otherUser.getUsername());
            ivProfilePic.setImageBitmap(base64ToBitmap(otherUser.getProfilePicture()));
            ivStatus.setImageResource(otherUser.isOnline() ? R.drawable.circle_online : R.drawable.circle_offline);

            if (adapter == null) {
                initialiseAdapter(); // This will call setLoading(false) when done
            } else {
                setLoading(false);
            }
        });

        setThemeFromData(conversation.getThemeData());
    }

    private void initialiseAdapter() {
        adapter = new MessagesAdapter(messages, thisUser, otherUser);
        rvMessages.setAdapter(adapter);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));

        if (conversation.getThemeData() != null) {
            setThemeFromData(conversation.getThemeData());
        }

        ConversationHelpers.getMessagesQuery(conversation.getId()).limit(10).get()
                .addOnSuccessListener(snap -> {
                    Log.d(TAG, "Initial messages loaded: " + snap.size());

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Message m = d.toObject(Message.class);
                        if (m != null) {
                            m.setId(d.getId());
                            messages.add(m);

                            if (m.getStatus().equalsIgnoreCase("sent")) {
                                ConversationHelpers.updateMessageStatus(conversation.getId(), m.getId(), "read")
                                        .addOnFailureListener(e -> Log.e(TAG, "Failed to update message status:", e));
                            }
                        }
                    }

                    // Critical: Stop loading here
                    setLoading(false);
                    adapter.notifyDataSetChanged();

                    if (messages.size() > 0) {
                        rvMessages.scrollToPosition(messages.size() - 1);
                    }

                    startMessagesListener();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load initial messages", e);
                    Toast.makeText(this, "Failed to load initial messages", Toast.LENGTH_SHORT).show();
                    setLoading(false); // Critical: Stop loading on failure too
                });
    }

    private void startMessagesListener() {
        Query query = ConversationHelpers.getMessagesQuery(conversation.getId());
        messagesRegistration = query.addSnapshotListener((snap, e) -> {
            if (e != null) {
                Log.e(TAG, "Messages listen failed", e);
                return;
            }
            if (snap == null) {
                Log.w(TAG, "Messages snapshot is null");
                return;
            }

            for (DocumentChange dc : snap.getDocumentChanges()) {
                if (dc.getType() == DocumentChange.Type.ADDED) {
                    Message newMessage = dc.getDocument().toObject(Message.class);
                    newMessage.setId(dc.getDocument().getId());

                    // Prevent duplicates from initial load
                    boolean exists = messages.stream().anyMatch(m -> m.getId().equals(newMessage.getId()));
                    if (!exists) {
                        messages.add(newMessage);
                        Log.d(TAG, "New message from listener: " + newMessage.getMessage());

                        adapter.notifyItemInserted(messages.size() - 1);
                        rvMessages.scrollToPosition(messages.size() - 1);

                        if (newMessage.getSenderUsername().equals(ConversationHelpers.getOtherUsername(conversation.getId(), UserManager.getInstance().getUser().getUsername()))) {
                            ConversationHelpers.updateMessageStatus(conversation.getId(), newMessage.getId(), "read")
                                    .addOnFailureListener(e1 -> Log.e(TAG, "Failed to update message status:", e1));
                        }
                    }
                } else if (dc.getType() == DocumentChange.Type.MODIFIED) {
                    Message modifiedMessage = dc.getDocument().toObject(Message.class);
                    modifiedMessage.setId(dc.getDocument().getId());

                    int index = IntStream.range(0, messages.size())
                            .filter(i -> messages.get(i).getId().equals(modifiedMessage.getId()))
                            .findFirst()
                            .orElse(-1);
                    if (index != -1) {
                        messages.set(index, modifiedMessage);
                        adapter.notifyItemChanged(index);
                    }
                }
            }
        });
    }

    private void stopMessagesListener() {
        if (messagesRegistration != null) {
            messagesRegistration.remove();
            messagesRegistration = null;
        }
    }

    private void setLoading(boolean status) {
        if (status) {
            rvMessages.setVisibility(View.GONE);
            loadingContainer.setVisibility(View.VISIBLE);
        } else {
            rvMessages.setVisibility(View.VISIBLE);
            loadingContainer.setVisibility(View.GONE);
        }
    }


    private void setThemeFromData(ConversationThemeData data) {
        if (data != null) {
            viewBackground.setBackgroundColor(Color.parseColor(data.getBackground()));
            constraintLayout.setBackgroundColor(Color.parseColor(data.getSurrounding()));

            // For TextInputEditText
            etMessage.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(data.getMessageBackground())));
            etMessage.setTextColor(Color.parseColor(data.getMessageTextColour()));

            // Set hint color based on message background brightness
            int hintColor = getContrastingHintColor(data.getMessageBackground());
            etMessage.setHintTextColor(hintColor);

            // For FloatingActionButton
            flSend.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(data.getSendBackground())));
            flSend.setImageTintList(ColorStateList.valueOf(Color.parseColor(data.getSendIconTint())));

            if (adapter != null)
                adapter.setBubbleColours(data.getSentBubbleColour(), data.getReceivedBubbleColour(), data.getSentTextColour(), data.getReceivedTextColour());
        }
    }

    private int getContrastingHintColor(String backgroundColor) {
        int bgColor = Color.parseColor(backgroundColor);
        double luminance = ColorUtils.calculateLuminance(bgColor);

        if (luminance > 0.5) {
            return Color.parseColor("#666666"); // Dark hint for light backgrounds
        } else {
            return Color.parseColor("#CCCCCC"); // Light hint for dark backgrounds
        }
    }

}
