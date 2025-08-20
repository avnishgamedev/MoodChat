package com.avnishgamedev.moodchat;

import android.content.Context;
import android.util.Log;
import com.google.firebase.firestore.*;
import org.webrtc.*;
import java.util.*;

public class CallManager {
    private static final String TAG = "CallManager";
    private static CallManager instance;

    private Context context;
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private EglBase eglBase;
    private VideoCapturer videoCapturer;
    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;
    private VideoSource localVideoSource;
    private AudioSource localAudioSource;

    private SurfaceViewRenderer localSurfaceView;
    private SurfaceViewRenderer remoteSurfaceView;

    private FirebaseFirestore firestore;
    private String currentUserId;
    private String callId;
    private String targetUserId;
    private boolean isInitiator = false;
    private ListenerRegistration callListener;
    private ListenerRegistration iceCandidatesListener;

    public interface CallListener {
        void onIncomingCall(String callerName, String callId);
        void onCallConnected();
        void onCallEnded();
        void onCallFailed(String error);
        void onRemoteStreamReceived();
    }

    private CallListener callListenerInterface;

    public static CallManager getInstance(Context context) {
        if (instance == null) {
            instance = new CallManager(context);
        }
        return instance;
    }

    private CallManager(Context context) {
        this.context = context.getApplicationContext();
        this.firestore = FirebaseFirestore.getInstance();
        initializeWebRTC();
    }

    public void setCallListener(CallListener listener) {
        this.callListenerInterface = listener;
    }

    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
        listenForIncomingCalls();
    }

    private void initializeWebRTC() {
        try {
            PeerConnectionFactory.InitializationOptions options =
                    PeerConnectionFactory.InitializationOptions.builder(context)
                            .setEnableInternalTracer(true)
                            .createInitializationOptions();
            PeerConnectionFactory.initialize(options);

            eglBase = EglBase.create();

            PeerConnectionFactory.Options factoryOptions = new PeerConnectionFactory.Options();
            peerConnectionFactory = PeerConnectionFactory.builder()
                    .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBase.getEglBaseContext()))
                    .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true))
                    .setOptions(factoryOptions)
                    .createPeerConnectionFactory();

            Log.d(TAG, "WebRTC initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize WebRTC", e);
            if (callListenerInterface != null) {
                callListenerInterface.onCallFailed("Failed to initialize WebRTC: " + e.getMessage());
            }
        }
    }

    public void startCall(String targetUserId, SurfaceViewRenderer localView, SurfaceViewRenderer remoteView) {
        this.targetUserId = targetUserId;
        this.callId = currentUserId + "_" + targetUserId + "_" + System.currentTimeMillis();
        this.isInitiator = true;

        Log.d(TAG, "Starting call to: " + targetUserId);

        setupVideoViews(localView, remoteView);
        startLocalMediaCapture();
        createPeerConnection();
        listenForCallStatus();
        createOffer();
    }

    public void answerCall(String incomingCallId, SurfaceViewRenderer localView, SurfaceViewRenderer remoteView) {
        this.callId = incomingCallId;
        this.isInitiator = false;

        Log.d(TAG, "Answering call: " + incomingCallId);

        setupVideoViews(localView, remoteView);
        startLocalMediaCapture();
        createPeerConnection();
        listenForCallStatus();
        acceptIncomingCall();
    }

    private void setupVideoViews(SurfaceViewRenderer localView, SurfaceViewRenderer remoteView) {
        this.localSurfaceView = localView;
        this.remoteSurfaceView = remoteView;

        localSurfaceView.init(eglBase.getEglBaseContext(), null);
        remoteSurfaceView.init(eglBase.getEglBaseContext(), null);

        localSurfaceView.setMirror(true);
        remoteSurfaceView.setMirror(false);

        localSurfaceView.setEnableHardwareScaler(true);
        remoteSurfaceView.setEnableHardwareScaler(true);
    }

    private void startLocalMediaCapture() {
        try {
            // Video capture
            Camera2Enumerator enumerator = new Camera2Enumerator(context);
            String deviceName = null;

            // Find front camera
            for (String name : enumerator.getDeviceNames()) {
                if (enumerator.isFrontFacing(name)) {
                    deviceName = name;
                    break;
                }
            }

            // Fallback to any camera
            if (deviceName == null && enumerator.getDeviceNames().length > 0) {
                deviceName = enumerator.getDeviceNames()[0];
            }

            if (deviceName != null) {
                videoCapturer = enumerator.createCapturer(deviceName, null);

                SurfaceTextureHelper surfaceHelper = SurfaceTextureHelper.create(
                        "CaptureThread", eglBase.getEglBaseContext());
                localVideoSource = peerConnectionFactory.createVideoSource(false);

                // Use the standard approach - no custom observer needed
                videoCapturer.initialize(surfaceHelper, context, localVideoSource.getCapturerObserver());
                videoCapturer.startCapture(1024, 720, 30);

                localVideoTrack = peerConnectionFactory.createVideoTrack("local_video_track", localVideoSource);
                localVideoTrack.addSink(localSurfaceView);

                Log.d(TAG, "Video capture started");
            }

            // Audio capture
            MediaConstraints audioConstraints = new MediaConstraints();
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
            audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl", "true"));

            localAudioSource = peerConnectionFactory.createAudioSource(audioConstraints);
            localAudioTrack = peerConnectionFactory.createAudioTrack("local_audio_track", localAudioSource);

            Log.d(TAG, "Audio capture started");

        } catch (Exception e) {
            Log.e(TAG, "Failed to start local media capture", e);
            if (callListenerInterface != null) {
                callListenerInterface.onCallFailed("Failed to access camera/microphone: " + e.getMessage());
            }
        }
    }

    private void createPeerConnection() {
        try {
            List<PeerConnection.IceServer> iceServers = Arrays.asList(
                    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                    PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
            );

            PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(iceServers);
            config.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
            config.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
            config.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
            config.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;

            // Fix: Set SDP semantics to PLAN_B instead of UNIFIED_PLAN
            config.sdpSemantics = PeerConnection.SdpSemantics.PLAN_B;

            peerConnection = peerConnectionFactory.createPeerConnection(config, new PeerConnectionObserver());

            // Use the traditional addStream approach (works with PLAN_B)
            MediaStream localStream = peerConnectionFactory.createLocalMediaStream("local_stream");
            if (localVideoTrack != null) {
                localStream.addTrack(localVideoTrack);
            }
            if (localAudioTrack != null) {
                localStream.addTrack(localAudioTrack);
            }
            peerConnection.addStream(localStream);

            Log.d(TAG, "PeerConnection created successfully with PLAN_B semantics");

        } catch (Exception e) {
            Log.e(TAG, "Failed to create peer connection", e);
            if (callListenerInterface != null) {
                callListenerInterface.onCallFailed("Failed to create connection: " + e.getMessage());
            }
        }
    }

    private void createOffer() {
        try {
            MediaConstraints constraints = new MediaConstraints();
            constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
            constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

            peerConnection.createOffer(new SdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    Log.d(TAG, "Offer created successfully");
                    peerConnection.setLocalDescription(new SdpObserver() {
                        @Override
                        public void onSetSuccess() {
                            Log.d(TAG, "Local description set successfully");
                            sendCallInvitation(sessionDescription);
                        }

                        @Override
                        public void onSetFailure(String error) {
                            Log.e(TAG, "Failed to set local description: " + error);
                            if (callListenerInterface != null) {
                                callListenerInterface.onCallFailed("Failed to set local description: " + error);
                            }
                        }

                        @Override public void onCreateSuccess(SessionDescription sessionDescription) {}
                        @Override public void onCreateFailure(String s) {}
                    }, sessionDescription);
                }

                @Override
                public void onCreateFailure(String error) {
                    Log.e(TAG, "Failed to create offer: " + error);
                    if (callListenerInterface != null) {
                        callListenerInterface.onCallFailed("Failed to create offer: " + error);
                    }
                }

                @Override public void onSetSuccess() {}
                @Override public void onSetFailure(String s) {}
            }, constraints);
        } catch (Exception e) {
            Log.e(TAG, "Exception in createOffer", e);
            if (callListenerInterface != null) {
                callListenerInterface.onCallFailed("Exception in createOffer: " + e.getMessage());
            }
        }
    }

    private void sendCallInvitation(SessionDescription offer) {
        Map<String, Object> callData = new HashMap<>();
        callData.put("callId", callId);
        callData.put("caller", currentUserId);
        callData.put("callee", targetUserId);
        callData.put("offer", offer.description);
        callData.put("status", "calling");
        callData.put("timestamp", FieldValue.serverTimestamp());

        firestore.collection("calls").document(callId)
                .set(callData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Call invitation sent");
                    listenForCallAnswer();
                    listenForIceCandidates();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send call invitation", e);
                    if (callListenerInterface != null) {
                        callListenerInterface.onCallFailed("Failed to send call invitation: " + e.getMessage());
                    }
                });
    }

    private void acceptIncomingCall() {
        firestore.collection("calls").document(callId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String offerSdp = documentSnapshot.getString("offer");
                        if (offerSdp != null) {
                            SessionDescription offer = new SessionDescription(SessionDescription.Type.OFFER, offerSdp);
                            peerConnection.setRemoteDescription(new SdpObserver() {
                                @Override
                                public void onSetSuccess() {
                                    Log.d(TAG, "Remote description set successfully");
                                    createAnswer();
                                }

                                @Override
                                public void onSetFailure(String error) {
                                    Log.e(TAG, "Failed to set remote description: " + error);
                                    if (callListenerInterface != null) {
                                        callListenerInterface.onCallFailed("Failed to set remote description: " + error);
                                    }
                                }

                                @Override public void onCreateSuccess(SessionDescription sessionDescription) {}
                                @Override public void onCreateFailure(String s) {}
                            }, offer);

                            listenForIceCandidates();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get call data", e);
                    if (callListenerInterface != null) {
                        callListenerInterface.onCallFailed("Failed to get call data: " + e.getMessage());
                    }
                });
    }

    private void createAnswer() {
        try {
            MediaConstraints constraints = new MediaConstraints();
            constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
            constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

            peerConnection.createAnswer(new SdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    Log.d(TAG, "Answer created successfully");
                    peerConnection.setLocalDescription(new SdpObserver() {
                        @Override
                        public void onSetSuccess() {
                            Log.d(TAG, "Local description set for answer");
                            sendAnswer(sessionDescription);
                        }

                        @Override
                        public void onSetFailure(String error) {
                            Log.e(TAG, "Failed to set local description for answer: " + error);
                            if (callListenerInterface != null) {
                                callListenerInterface.onCallFailed("Failed to set local description: " + error);
                            }
                        }

                        @Override public void onCreateSuccess(SessionDescription sessionDescription) {}
                        @Override public void onCreateFailure(String s) {}
                    }, sessionDescription);
                }

                @Override
                public void onCreateFailure(String error) {
                    Log.e(TAG, "Failed to create answer: " + error);
                    if (callListenerInterface != null) {
                        callListenerInterface.onCallFailed("Failed to create answer: " + error);
                    }
                }

                @Override public void onSetSuccess() {}
                @Override public void onSetFailure(String s) {}
            }, constraints);
        } catch (Exception e) {
            Log.e(TAG, "Exception in createAnswer", e);
            if (callListenerInterface != null) {
                callListenerInterface.onCallFailed("Exception in createAnswer: " + e.getMessage());
            }
        }
    }

    private void sendAnswer(SessionDescription answer) {
        Map<String, Object> answerData = new HashMap<>();
        answerData.put("answer", answer.description);
        answerData.put("status", "answered");

        firestore.collection("calls").document(callId)
                .update(answerData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Answer sent successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send answer", e);
                    if (callListenerInterface != null) {
                        callListenerInterface.onCallFailed("Failed to send answer: " + e.getMessage());
                    }
                });
    }

    private void listenForCallAnswer() {
        callListener = firestore.collection("calls").document(callId)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening for call answer", error);
                        return;
                    }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        String status = documentSnapshot.getString("status");
                        String answerSdp = documentSnapshot.getString("answer");

                        if ("answered".equals(status) && answerSdp != null) {
                            Log.d(TAG, "Call answered, processing answer");
                            SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, answerSdp);

                            peerConnection.setRemoteDescription(new SdpObserver() {
                                @Override
                                public void onSetSuccess() {
                                    Log.d(TAG, "Remote answer description set successfully");
                                }

                                @Override
                                public void onSetFailure(String error) {
                                    Log.e(TAG, "Failed to set remote answer: " + error);
                                    if (callListenerInterface != null) {
                                        callListenerInterface.onCallFailed("Failed to set remote answer: " + error);
                                    }
                                }

                                @Override public void onCreateSuccess(SessionDescription sessionDescription) {}
                                @Override public void onCreateFailure(String s) {}
                            }, answer);
                        }
                    }
                });
    }

    private void listenForIncomingCalls() {
        firestore.collection("calls")
                .whereEqualTo("callee", currentUserId)
                .whereEqualTo("status", "calling") // Only listen for active calls
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening for incoming calls", error);
                        return;
                    }

                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            String callerName = doc.getString("caller");
                            String incomingCallId = doc.getString("callId");
                            String status = doc.getString("status");

                            // Only show dialog for active calls
                            if ("calling".equals(status)) {
                                Log.d(TAG, "Incoming call from: " + callerName);

                                if (callListenerInterface != null) {
                                    callListenerInterface.onIncomingCall(callerName, incomingCallId);
                                }
                            }
                        }
                    }
                });
    }

    public void cleanupOldCalls() {
        // Clean up calls older than 2 minutes that are still in "calling" status
        long twoMinutesAgo = System.currentTimeMillis() - (2 * 60 * 1000);

        firestore.collection("calls")
                .whereEqualTo("callee", currentUserId)
                .whereEqualTo("status", "calling")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        Long timestamp = doc.getLong("timestamp");
                        if (timestamp != null && timestamp < twoMinutesAgo) {
                            // Auto-decline old calls
                            doc.getReference().update("status", "expired");
                        }
                    }
                });
    }

    private void listenForIceCandidates() {
        iceCandidatesListener = firestore.collection("calls").document(callId)
                .collection("ice_candidates")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening for ICE candidates", error);
                        return;
                    }

                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            Map<String, Object> data = doc.getData();
                            if (data != null) {
                                String from = (String) data.get("from");

                                // Only process candidates from the other peer
                                if (!currentUserId.equals(from)) {
                                    String sdpMid = (String) data.get("sdpMid");
                                    Long sdpMLineIndexLong = (Long) data.get("sdpMLineIndex");
                                    String candidate = (String) data.get("candidate");

                                    if (sdpMid != null && sdpMLineIndexLong != null && candidate != null) {
                                        int sdpMLineIndex = sdpMLineIndexLong.intValue();
                                        IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, candidate);

                                        if (peerConnection != null) {
                                            peerConnection.addIceCandidate(iceCandidate);
                                            Log.d(TAG, "ICE candidate added from: " + from);
                                        }
                                    }
                                }
                            }
                        }
                    }
                });
    }

    private void sendIceCandidate(IceCandidate candidate) {
        Map<String, Object> candidateData = new HashMap<>();
        candidateData.put("sdpMid", candidate.sdpMid);
        candidateData.put("sdpMLineIndex", candidate.sdpMLineIndex);
        candidateData.put("candidate", candidate.sdp);
        candidateData.put("from", currentUserId);
        candidateData.put("timestamp", FieldValue.serverTimestamp());

        firestore.collection("calls").document(callId)
                .collection("ice_candidates")
                .add(candidateData)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "ICE candidate sent successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send ICE candidate", e);
                });
    }

    public void endCall() {
        Log.d(TAG, "Ending call");

        // Update call status in Firestore FIRST to notify the other party
        if (callId != null) {
            Map<String, Object> endCallData = new HashMap<>();
            endCallData.put("status", "ended");
            endCallData.put("endedBy", currentUserId);
            endCallData.put("endedAt", FieldValue.serverTimestamp());

            firestore.collection("calls").document(callId)
                    .update(endCallData)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Call status updated to ended"))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to update call status", e));
        }

        // Clean up local resources
        cleanupCall();
    }

    private void cleanupCall() {
        // Clean up listeners first
        if (callListener != null) {
            callListener.remove();
            callListener = null;
        }

        if (iceCandidatesListener != null) {
            iceCandidatesListener.remove();
            iceCandidatesListener = null;
        }

        // Stop video capture with better error handling
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
                videoCapturer.dispose();
            } catch (InterruptedException | RuntimeException e) {
                Log.w(TAG, "Error stopping video capture: " + e.getMessage());
            }
            videoCapturer = null;
        }

        // Close peer connection
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }

        // Clean up video sources
        if (localVideoSource != null) {
            localVideoSource.dispose();
            localVideoSource = null;
        }

        if (localAudioSource != null) {
            localAudioSource.dispose();
            localAudioSource = null;
        }

        // Release surface views safely
        try {
            if (localSurfaceView != null) {
                localSurfaceView.release();
                localSurfaceView = null;
            }

            if (remoteSurfaceView != null) {
                remoteSurfaceView.release();
                remoteSurfaceView = null;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error releasing surface views: " + e.getMessage());
        }

        // Reset state
        callId = null;
        targetUserId = null;
        isInitiator = false;

        if (callListenerInterface != null) {
            callListenerInterface.onCallEnded();
        }
    }

    private void listenForCallStatus() {
        if (callId != null) {
            callListener = firestore.collection("calls").document(callId)
                    .addSnapshotListener((documentSnapshot, error) -> {
                        if (error != null) {
                            Log.e(TAG, "Error listening for call status", error);
                            return;
                        }

                        if (documentSnapshot != null && documentSnapshot.exists()) {
                            String status = documentSnapshot.getString("status");
                            String actionBy = documentSnapshot.getString("endedBy");
                            if (actionBy == null) {
                                actionBy = documentSnapshot.getString("declinedBy");
                            }

                            // Handle different call statuses
                            if ("ended".equals(status) && !currentUserId.equals(actionBy)) {
                                Log.d(TAG, "Call ended by remote party");
                                if (callListenerInterface != null) {
                                    callListenerInterface.onCallEnded();
                                }
                                cleanupCall();
                            } else if ("declined".equals(status) && !currentUserId.equals(actionBy)) {
                                Log.d(TAG, "Call declined by remote party");
                                if (callListenerInterface != null) {
                                    callListenerInterface.onCallFailed("Call was declined");
                                }
                                cleanupCall();
                            }
                        }
                    });
        }
    }


    public void switchCamera() {
        if (videoCapturer instanceof CameraVideoCapturer) {
            CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) videoCapturer;
            cameraVideoCapturer.switchCamera(null);
        }
    }

    public void toggleMute() {
        if (localAudioTrack != null) {
            boolean enabled = localAudioTrack.enabled();
            localAudioTrack.setEnabled(!enabled);
            Log.d(TAG, "Audio " + (enabled ? "muted" : "unmuted"));
        }
    }

    public void toggleVideo() {
        if (localVideoTrack != null) {
            boolean enabled = localVideoTrack.enabled();
            localVideoTrack.setEnabled(!enabled);
            Log.d(TAG, "Video " + (enabled ? "disabled" : "enabled"));
        }
    }

    // PeerConnection Observer
    private class PeerConnectionObserver implements PeerConnection.Observer {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.d(TAG, "Signaling state changed: " + signalingState);
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            Log.d(TAG, "ICE connection state changed: " + iceConnectionState);

            switch (iceConnectionState) {
                case CONNECTED:
                    if (callListenerInterface != null) {
                        callListenerInterface.onCallConnected();
                    }
                    break;
                case FAILED:
                case DISCONNECTED:
                    if (callListenerInterface != null) {
                        callListenerInterface.onCallFailed("Connection failed or disconnected");
                    }
                    break;
                case CLOSED:
                    if (callListenerInterface != null) {
                        callListenerInterface.onCallEnded();
                    }
                    break;
            }
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {
            Log.d(TAG, "ICE connection receiving change: " + b);
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            Log.d(TAG, "ICE gathering state changed: " + iceGatheringState);
        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            Log.d(TAG, "New ICE candidate: " + iceCandidate.sdp);
            sendIceCandidate(iceCandidate);
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
            Log.d(TAG, "ICE candidates removed: " + iceCandidates.length);
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            Log.d(TAG, "Remote stream added");

            if (mediaStream.videoTracks.size() > 0) {
                VideoTrack remoteVideoTrack = mediaStream.videoTracks.get(0);
                if (remoteSurfaceView != null) {
                    remoteVideoTrack.addSink(remoteSurfaceView);
                }
            }

            if (callListenerInterface != null) {
                callListenerInterface.onRemoteStreamReceived();
            }
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            Log.d(TAG, "Remote stream removed");
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            Log.d(TAG, "Data channel: " + dataChannel.label());
        }

        @Override
        public void onRenegotiationNeeded() {
            Log.d(TAG, "Renegotiation needed");
        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
            Log.d(TAG, "Track added");
        }
    }
}
