package com.avnishgamedev.moodchat;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.credentials.CredentialManager;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // Views
    FloatingActionButton fab;
    RecyclerView rvConversations;
    ConversationAdapter adapter;
    RelativeLayout rlLoading;

    // Meta data
    FirebaseAuth auth;
    FirebaseFirestore db;
    List<Conversation> conversations;
    ListenerRegistration conversationsRegistration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        setupToolbar();
        setupViews();
        startConversationsListener();
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        } else if (!user.isEmailVerified()) {
            Intent i = new Intent(MainActivity.this, EmailVerificationActivity.class);
            i.putExtra("email", user.getEmail());
            startActivity(i);
            finish();
            return;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_profile) {
            startActivity(new Intent(MainActivity.this, ProfileActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_logout) {
            signOut();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        stopConversationsListener();
        super.onDestroy();
    }

    private void setupToolbar() {
        setSupportActionBar(findViewById(R.id.materialToolbar));
    }

    private void setupViews() {
        fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> startConversation());

        rvConversations = findViewById(R.id.rvConversations);
        conversations = new ArrayList<>();
        adapter = new ConversationAdapter(conversations, (parent, view, position, id) -> {
            Conversation selectedConversation = conversations.get(position);
            Snackbar.make(view, "Selected conversation: " + selectedConversation.getId(), Snackbar.LENGTH_SHORT).show();
        });
        rvConversations.setAdapter(adapter);

        rlLoading = findViewById(R.id.rlLoading);
    }

    private void startConversationsListener() {
        setLoading(true);

        UserManager.getInstance().loadUser()
                .addOnSuccessListener(user -> {
                    // Obtain a Firestore query for this user's conversations
                    // e.g., whereArrayContains("members", user.getUid()) or deterministic IDs if you store a mirror
                    Query query = ConversationHelpers.getConversationsQuery(user.getUsername());

                    conversationsRegistration = query.addSnapshotListener((snap, e) -> {
                        setLoading(false);

                        if (e != null) {
                            Log.e(TAG, "Conversations listen failed", e);
                            return;
                        }
                        if (snap == null) {
                            Log.w(TAG, "Conversations snapshot is null");
                            return;
                        }

                        // Option A: Replace entire list on each event (simple, stable)
                        List<Conversation> fresh = new ArrayList<>();
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            Conversation c = d.toObject(Conversation.class);
                            if (c != null) {
                                c.setId(d.getId());
                                fresh.add(c);
                            }
                        }
                        conversations.clear();
                        conversations.addAll(fresh);
                        adapter.notifyDataSetChanged();
                        Log.d(TAG, "Conversations updated (full): " + conversations.size());

                        // Option B (advanced): use DocumentChange to incrementally update the adapter
                        // for (DocumentChange dc : snap.getDocumentChanges()) { ... }
                    });
                })
                .addOnFailureListener(err -> {
                    setLoading(false);
                    Log.e(TAG, "Failed to load user for conversations listener", err);
                });
    }
    private void stopConversationsListener() {
        if (conversationsRegistration != null) {
            conversationsRegistration.remove();
            conversationsRegistration = null;
        }
    }


    private void startConversation() {
        promptUsername().addOnSuccessListener(user -> {
            ConversationHelpers.createConversation(UserManager.getInstance().getUser().getUsername(), user.getString("username")).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // startActivity(new Intent(MainActivity.this, ConversationActivity.class));
                    Snackbar.make(fab, "Conversation started", Snackbar.LENGTH_SHORT).show();
                } else {
                    Log.e(TAG, "Couldn't create conversation: " + task.getException().getMessage());
                    Snackbar.make(fab, "Couldn't create conversation", Snackbar.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void signOut() {
        FirebaseAuth.getInstance().signOut();
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ClearCredentialStateRequest clearRequest = new ClearCredentialStateRequest();
            CredentialManager.create(this).clearCredentialStateAsync(
                    clearRequest,
                    new CancellationSignal(),
                    Executors.newSingleThreadExecutor(),
                    new CredentialManagerCallback<>() {
                        @Override
                        public void onResult(Void result) {
                            runOnUiThread(() -> {
                                startActivity(new Intent(MainActivity.this, LoginActivity.class));
                                finish();
                            });
                        }

                        @Override
                        public void onError(ClearCredentialException e) {
                            Log.e(TAG, "Couldn't clear user credentials: " + e.getLocalizedMessage());
                        }
                    }
            );
        } else {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        }
    }

    // Helpers
    public Task<DocumentSnapshot> promptUsername() {
        final TaskCompletionSource<DocumentSnapshot> taskCompletionSource = new TaskCompletionSource<>();

        TextInputLayout textInputLayout = new TextInputLayout(this);
        textInputLayout.setHint("Enter other username");
        TextInputEditText usernameEditText = new TextInputEditText(textInputLayout.getContext());
        textInputLayout.addView(usernameEditText);

        // Create the MaterialAlertDialog
        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(this)
                .setTitle("Start conversation")
                .setView(textInputLayout)
                .setCancelable(false)
                .setNegativeButton("Cancel", (dialog, which) -> taskCompletionSource.setException(new Exception("User cancelled")))
                .setPositiveButton("Continue", null); // Will override later

        AlertDialog dialog = dialogBuilder.create();

        // Override the positive button click to prevent auto-dismiss
        dialog.setOnShowListener(dialogInterface -> {
            Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(v -> {
                String username = usernameEditText.getText().toString().trim();

                // Clear any previous errors
                textInputLayout.setError(null);

                // Check if username is more than 4 letters
                if (username.length() <= 4) {
                    textInputLayout.setError("Username must be more than 4 letters");
                    return; // Don't dismiss dialog, let user try again
                }

                // Call your existing function to check if username is taken
                getUserByUsername(username).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot documentSnapshot = task.getResult();
                        taskCompletionSource.setResult(documentSnapshot);
                        dialog.dismiss();
                    } else {
                        textInputLayout.setError(task.getException().getMessage());
                    }
                });
            });
        });

        dialog.show();

        return taskCompletionSource.getTask();
    }

    private Task<DocumentSnapshot> getUserByUsername(String username) {
        TaskCompletionSource<DocumentSnapshot> taskCompletionSource = new TaskCompletionSource<>();

        if (UserManager.getInstance().getUser().getUsername().equals(username)) {
            taskCompletionSource.setException(new Exception("Cannot start conversation with self!"));
            return taskCompletionSource.getTask();
        }

        db.collection("users").whereEqualTo("username", username).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        taskCompletionSource.setException(new Exception("User not found!"));
                    } else {
                        taskCompletionSource.setResult(queryDocumentSnapshots.getDocuments().get(0));
                    }
                })
                .addOnFailureListener(e -> taskCompletionSource.setException(e));

        return taskCompletionSource.getTask();
    }

    private void setLoading(boolean status) {
        rlLoading.setVisibility(status ? View.VISIBLE : View.GONE);
    }
}
