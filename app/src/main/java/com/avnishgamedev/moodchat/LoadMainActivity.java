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
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

public class LoadMainActivity extends AppCompatActivity {
    private static final String TAG = "LoadMainActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_main);

        UserManager.getInstance().loadUser()
                .addOnSuccessListener(user -> {
                    startActivity(new Intent(LoadMainActivity.this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    if (e instanceof UserManager.UserDocumentDoesntExistException) {
                        // Assuming we signed in via Google, prompt for username
                        promptUsernameAndCheck().addOnSuccessListener(username -> {                                 // Get Username
                            loadAndCompressImageToBase64(FirebaseAuth.getInstance().getCurrentUser().getPhotoUrl())       // Load Profile Picture
                            .addOnSuccessListener(profilePic -> {
                                UserManager.getInstance().createOrUpdateUserDocument(
                                                new User(
                                                        FirebaseAuth.getInstance().getCurrentUser().getEmail(),
                                                        FirebaseAuth.getInstance().getCurrentUser().getDisplayName(),
                                                        true,
                                                        profilePic,
                                                        username,
                                                        Timestamp.now(),
                                                        Timestamp.now()
                                                )
                                        ).addOnSuccessListener(aVoid -> {
                                            Log.d(TAG, "User document created successfully");
                                            startActivity(new Intent(LoadMainActivity.this, MainActivity.class));
                                            finish();
                                        })
                                        .addOnFailureListener(e1 -> {
                                            Log.e(TAG, "Failed to create user document: " + e1.getLocalizedMessage());
                                            Toast.makeText(this, "Failed to create user document: " + e1.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                                        });
                            })
                            .addOnFailureListener(e1 -> {
                                Log.e(TAG, "Failed to load and compress image: " + e1.getLocalizedMessage());
                                Toast.makeText(this, "Failed to load and compress image: " + e1.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                            });
                        });

                        return;
                    }
                    Log.e(TAG, "Failed to load user document: " + e.getLocalizedMessage());
                    Toast.makeText(this, "Failed to load user document: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    public Task<String> promptUsernameAndCheck() {
        final TaskCompletionSource<String> taskCompletionSource = new TaskCompletionSource<>();

        // Create TextInputLayout with TextInputEditText for Material styling
        TextInputLayout textInputLayout = new TextInputLayout(this);
        textInputLayout.setHint("Username");
        TextInputEditText usernameEditText = new TextInputEditText(textInputLayout.getContext());
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

    private Task<Boolean> isUsernameTaken(String username) {
        return FirebaseFirestore.getInstance().collection("users").whereEqualTo("username", username).get().continueWith(task -> {
            if (!task.isSuccessful()) {
                Snackbar.make(findViewById(android.R.id.content), "Failed to check username: " + task.getException().getLocalizedMessage(), Snackbar.LENGTH_SHORT).show();
                Log.e(TAG, "Failed to check username: " + task.getException().getLocalizedMessage());
                return true;
            }

            return !task.getResult().isEmpty();
        });
    }

    public static Task<String> loadAndCompressImageToBase64(Uri imageUri) {
        return Tasks.call(Executors.newSingleThreadExecutor(), new Callable<String>() {
            @Override
            public String call() throws Exception {
                // Open connection to the remote URL
                URL url = new URL(imageUri.toString());
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.setConnectTimeout(10000); // 10 seconds timeout
                connection.setReadTimeout(15000);    // 15 seconds timeout
                connection.connect();

                // Get input stream from the connection
                InputStream inputStream = connection.getInputStream();

                // Decode the input stream to Bitmap
                Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();
                connection.disconnect();

                if (originalBitmap == null) {
                    throw new Exception("Failed to decode image from URI: " + imageUri);
                }

                // Compress bitmap to JPEG format
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                originalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);

                // Clean up bitmap from memory
                originalBitmap.recycle();

                // Convert compressed bytes to Base64 string
                byte[] compressedBytes = byteArrayOutputStream.toByteArray();
                byteArrayOutputStream.close();

                return Base64.encodeToString(compressedBytes, Base64.DEFAULT);
            }
        });
    }
}
