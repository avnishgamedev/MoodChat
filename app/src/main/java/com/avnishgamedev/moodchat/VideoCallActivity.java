package com.avnishgamedev.moodchat;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.webrtc.SurfaceViewRenderer;

public class VideoCallActivity extends AppCompatActivity implements CallManager.CallListener {

    private SurfaceViewRenderer localVideoView, remoteVideoView;
    private CallManager callManager;
    private ImageButton btnEndCall, btnMute, btnVideo, btnSwitchCamera;
    private TextView tvCallStatus;

    private boolean isOutgoing;
    private boolean isMuted = false;
    private boolean isVideoEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_call);

        initViews();
        setupCallManager();
        handleIntent();
    }

    private void initViews() {
        localVideoView = findViewById(R.id.local_video_view);
        remoteVideoView = findViewById(R.id.remote_video_view);
        btnEndCall = findViewById(R.id.btn_end_call);
        btnMute = findViewById(R.id.btn_mute);
        btnVideo = findViewById(R.id.btn_video);
        btnSwitchCamera = findViewById(R.id.btn_switch_camera);
        tvCallStatus = findViewById(R.id.tv_call_status);

        btnEndCall.setOnClickListener(v -> endCall());
        btnMute.setOnClickListener(v -> toggleMute());
        btnVideo.setOnClickListener(v -> toggleVideo());
        btnSwitchCamera.setOnClickListener(v -> switchCamera());
    }

    private void setupCallManager() {
        callManager = CallManager.getInstance(this);
        callManager.setCallListener(this);
    }

    private void handleIntent() {
        isOutgoing = getIntent().getBooleanExtra("is_outgoing", false);

        if (isOutgoing) {
            String targetUserId = getIntent().getStringExtra("target_user_id");
            tvCallStatus.setText("Calling...");
            callManager.startCall(targetUserId, localVideoView, remoteVideoView);
        } else {
            String callId = getIntent().getStringExtra("call_id");
            tvCallStatus.setText("Connecting...");
            callManager.answerCall(callId, localVideoView, remoteVideoView);
        }
    }

    private void toggleMute() {
        isMuted = !isMuted;
        callManager.toggleMute();
        btnMute.setImageResource(isMuted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
    }

    private void toggleVideo() {
        isVideoEnabled = !isVideoEnabled;
        callManager.toggleVideo();
        btnVideo.setImageResource(isVideoEnabled ? R.drawable.ic_videocam : R.drawable.ic_videocam_off);
    }

    private void switchCamera() {
        callManager.switchCamera();
    }

    private void endCall() {
        callManager.endCall();
        finish();
    }

    @Override
    public void onIncomingCall(String callerName, String callId) {
        // Not used in this activity
    }

    @Override
    public void onCallConnected() {
        runOnUiThread(() -> {
            tvCallStatus.setText("Connected");
            tvCallStatus.setVisibility(View.GONE);
        });
    }

    @Override
    public void onCallEnded() {
        runOnUiThread(() -> {
            finish();
        });
    }

    @Override
    public void onCallFailed(String error) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Call failed: " + error, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    public void onRemoteStreamReceived() {
        runOnUiThread(() -> {
            tvCallStatus.setText("Connected");
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // End the call first
        if (callManager != null) {
            callManager.endCall();
        }

        // Release surface views in a try-catch to prevent crashes
        try {
            if (localVideoView != null) {
                localVideoView.clearImage();
                localVideoView.release();
                localVideoView = null;
            }
        } catch (Exception e) {
            Log.w("VideoCallActivity", "Error releasing local video view: " + e.getMessage());
        }

        try {
            if (remoteVideoView != null) {
                remoteVideoView.clearImage();
                remoteVideoView.release();
                remoteVideoView = null;
            }
        } catch (Exception e) {
            Log.w("VideoCallActivity", "Error releasing remote video view: " + e.getMessage());
        }
    }

}

