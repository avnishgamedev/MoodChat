package com.avnishgamedev.moodchat;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;

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

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // Views
    FloatingActionButton fab;

    // Meta data
    FirebaseAuth auth;
    FirebaseFirestore db;
    DocumentSnapshot userDoc;

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

        UserManager.getInstance().loadUser();
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

    private void setupToolbar() {
        setSupportActionBar(findViewById(R.id.materialToolbar));
    }

    private void setupViews() {
        fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> {
            promptUsername().addOnSuccessListener(user -> {
                // TODO: start a conversation
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

        if (userDoc.getString("username").equals(username)) {
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
}