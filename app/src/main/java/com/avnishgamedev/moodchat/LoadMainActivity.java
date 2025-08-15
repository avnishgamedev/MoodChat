package com.avnishgamedev.moodchat;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoadMainActivity extends AppCompatActivity {
    private static final String TAG = "LoadMainActivity";

    FirebaseAuth auth;
    FirebaseFirestore db;
    FirebaseUser user;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_main);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(LoadMainActivity.this, LoginActivity.class));
            finish();
            return;
        }

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        startActivity(new Intent(LoadMainActivity.this, MainActivity.class));
                        finish();
                        return;
                    }

                    // Most probably, we're coming from Google Login if we are here
                    // Get username via Dialog

                    promptUsernameAndCheck().addOnSuccessListener(username -> {
                        createUserDocument(user, username);
                    });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load user data: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(LoadMainActivity.this, LoginActivity.class));
                    finish();
                });
    }

    private Task<Boolean> isUsernameTaken(String username) {
        return db.collection("users").whereEqualTo("username", username).get().continueWith(task -> {
            if (!task.isSuccessful()) {
                Snackbar.make(findViewById(android.R.id.content), "Failed to check username: " + task.getException().getLocalizedMessage(), Snackbar.LENGTH_SHORT).show();
                Log.e(TAG, "Failed to check username: " + task.getException().getLocalizedMessage());
                return true;
            }

            return !task.getResult().isEmpty();
        });
    }

    public Task<String> promptUsernameAndCheck() {
        final TaskCompletionSource<String> taskCompletionSource = new TaskCompletionSource<>();

        // Create TextInputLayout with TextInputEditText for Material styling
        TextInputLayout textInputLayout = new TextInputLayout(this);
        TextInputEditText usernameEditText = new TextInputEditText(textInputLayout.getContext());
        usernameEditText.setHint("Username");
        textInputLayout.addView(usernameEditText);

        // Create the MaterialAlertDialog
        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(this)
                .setTitle("Choose Username")
                .setView(textInputLayout)
                .setCancelable(false)
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
                isUsernameTaken(username).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Boolean isTaken = task.getResult();
                        if (Boolean.TRUE.equals(isTaken)) {
                            textInputLayout.setError("Username already taken");
                            // Don't dismiss dialog, let user try again
                        } else {
                            // Username is valid and available
                            dialog.dismiss();
                            taskCompletionSource.setResult(username);
                        }
                    } else {
                        // Handle error from isUsernameTaken function
                        textInputLayout.setError("Error checking username. Please try again.");
                    }
                });
            });
        });

        dialog.show();

        return taskCompletionSource.getTask();
    }

    private void createUserDocument(FirebaseUser user, String username) {
        HashMap<String, Object> data = new HashMap<>();
        data.put("name", user.getDisplayName());
        data.put("email", user.getEmail());
        data.put("username", username);

        Uri firebaseAuthPhotoUri = user.getPhotoUrl();

        if (firebaseAuthPhotoUri != null) {
            loadUserProfileImage(firebaseAuthPhotoUri, data, user);
        } else {
            saveUserToFirestore(data, user);
        }
    }
    private void loadUserProfileImage(Uri photoUri, HashMap<String, Object> data, FirebaseUser user) {
        loadBitmapFromUri(photoUri)
                .addOnSuccessListener(bitmap -> {
                    String base64Image = bitmapToBase64(bitmap, 70);
                    data.put("profile_picture", base64Image);
                    saveUserToFirestore(data, user);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load profile image: " + e.getLocalizedMessage());
                    // Save without image on failure
                    saveUserToFirestore(data, user);
                });
    }
    private void saveUserToFirestore(HashMap<String, Object> data, FirebaseUser user) {
        db.collection("users").document(user.getUid()).set(data)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User document created successfully");
                    startActivity(new Intent(LoadMainActivity.this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save user document: " + e.getLocalizedMessage());
                    Toast.makeText(this, "Failed to save user document: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(LoadMainActivity.this, LoginActivity.class));
                    finish();
                });
    }
    public static Task<Bitmap> loadBitmapFromUri(Uri uri) {
        TaskCompletionSource<Bitmap> taskCompletionSource = new TaskCompletionSource<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.submit(() -> {
            try {
                URL url = new URL(uri.toString());
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();

                InputStream input = connection.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                input.close();

                taskCompletionSource.setResult(bitmap);
            } catch (Exception e) {
                taskCompletionSource.setException(e);
            }
        });

        return taskCompletionSource.getTask();
    }
    public static String bitmapToBase64(Bitmap bitmap, int quality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        byte[] bytes = baos.toByteArray();
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }
}
