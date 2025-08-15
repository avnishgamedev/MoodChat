package com.avnishgamedev.moodchat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import android.util.Base64;
import android.widget.Toast;

public class ProfileActivity extends AppCompatActivity {
    private static final String TAG = "ProfileActivity";

    // Views
    ImageView ivProfilePic;
    EditText etName;
    EditText etUsername;
    EditText etEmail;
    Button btnUpdate;
    Button btnChangePassword;
    RelativeLayout rlLoading;

    // Meta data
    FirebaseAuth auth;
    FirebaseFirestore db;
    boolean isProfileUpdated = false;
    Uri newProfilePicUri;
    private final ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    Log.d("PhotoPicker", "Selected URI: " + uri);
                    handleSelectedImage(uri);
                } else {
                    Log.d("PhotoPicker", "No media selected");
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        Toolbar toolbar = findViewById(R.id.materialToolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setupViews();
        loadInitialData();
    }

    private void setupViews() {
        ivProfilePic = findViewById(R.id.ivProfilePic);
        etName = findViewById(R.id.etName);
        etUsername = findViewById(R.id.etUsername);
        etEmail = findViewById(R.id.etEmail);
        btnUpdate = findViewById(R.id.btnUpdate);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        rlLoading = findViewById(R.id.rlLoading);

        ivProfilePic.setOnClickListener(v -> showImagePicker());

        btnUpdate.setOnClickListener(v -> updateProfile());

        etUsername.setOnClickListener(v -> {
            promptUsernameAndCheck(etUsername.getText().toString()).addOnSuccessListener(username -> {
                HashMap<String, Object> data = new HashMap<>();
                data.put("username", username);
                db.collection("users").document(auth.getCurrentUser().getUid()).set(data, SetOptions.merge())
                        .addOnSuccessListener(x -> {
                            Snackbar.make(findViewById(android.R.id.content), "Username updated successfully", Snackbar.LENGTH_SHORT).show();
                            etUsername.setText(username);
                        })
                        .addOnFailureListener(e -> {
                            Snackbar.make(findViewById(android.R.id.content), "Failed to update username: " + e.getLocalizedMessage(), Snackbar.LENGTH_SHORT).show();
                        });
            });
        });

        btnChangePassword.setOnClickListener(v -> changePassword());
    }

    private void changePassword() {
        if (isUserSignedInWithGoogle()) {
            Snackbar.make(findViewById(android.R.id.content), "Password is managed through your Google account", Snackbar.LENGTH_SHORT).show();
            return;
        }

        showChangePasswordDialog();
    }

    private void loadInitialData() {
        setLoading(true);
        db.collection("users").document(auth.getCurrentUser().getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("name");
                        String username = doc.getString("username");
                        String email = doc.getString("email");
                        etName.setText(name);
                        etUsername.setText(username);
                        etEmail.setText(email);
                        Drawable profilePic = convertBase64ToDrawable(doc.getString("profile_picture"), this);
                        if (profilePic != null) {
                            ivProfilePic.setImageDrawable(profilePic);
                        }
                    } else {
                        Snackbar.make(findViewById(android.R.id.content), "User not found", Snackbar.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Snackbar.make(findViewById(android.R.id.content), "Failed to load data", Snackbar.LENGTH_SHORT).show();
                })
                .addOnCompleteListener(x -> setLoading(false));
    }

    private void showImagePicker() {
        pickMedia.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }
    private void handleSelectedImage(Uri uri) {
        if (isValidProfileImage(uri, this)) {
            isProfileUpdated = true;
            newProfilePicUri = uri;
            ivProfilePic.setImageURI(uri);
        } else {
            Snackbar.make(findViewById(android.R.id.content), "Image not compatible!", Snackbar.LENGTH_SHORT).show();
        }
    }

    private void updateProfile() {
        String name = etName.getText().toString();

        if (name.isEmpty()) {
            Snackbar.make(findViewById(android.R.id.content), "Please fill all fields", Snackbar.LENGTH_SHORT).show();
            return;
        } else if (name.length() < 4) {
            Snackbar.make(findViewById(android.R.id.content), "Name must be at least 4 characters", Snackbar.LENGTH_SHORT).show();
            return;
        }

        HashMap<String, Object> updates = new HashMap<>();
        updates.put("name", name);

        if (isProfileUpdated) {
            String base64Image = getBase64ImageFromUri(newProfilePicUri);
            updates.put("profile_picture", base64Image);
        }

        setLoading(true);
        db.collection("users").document(auth.getCurrentUser().getUid()).set(updates, SetOptions.merge())
                .addOnSuccessListener(x -> {
                    isProfileUpdated = false;
                    Snackbar.make(findViewById(android.R.id.content), "Profile updated successfully", Snackbar.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Snackbar.make(findViewById(android.R.id.content), "Failed to update profile: " + e.getLocalizedMessage(), Snackbar.LENGTH_SHORT).show();
                })
                .addOnCompleteListener(x -> setLoading(false));
    }

    // Helpers

    private void showChangePasswordDialog() {
        // Create the dialog layout
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(50, 50, 50, 50);

        // Current password input
        TextInputLayout currentPasswordLayout = new TextInputLayout(this);
        TextInputEditText currentPasswordEditText = new TextInputEditText(currentPasswordLayout.getContext());
        currentPasswordEditText.setHint("Current Password");
        currentPasswordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        currentPasswordLayout.addView(currentPasswordEditText);

        // New password input
        TextInputLayout newPasswordLayout = new TextInputLayout(this);
        TextInputEditText newPasswordEditText = new TextInputEditText(newPasswordLayout.getContext());
        newPasswordEditText.setHint("New Password");
        newPasswordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        newPasswordLayout.addView(newPasswordEditText);

        // Confirm password input
        TextInputLayout confirmPasswordLayout = new TextInputLayout(this);
        TextInputEditText confirmPasswordEditText = new TextInputEditText(confirmPasswordLayout.getContext());
        confirmPasswordEditText.setHint("Confirm New Password");
        confirmPasswordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirmPasswordLayout.addView(confirmPasswordEditText);

        // Add all inputs to dialog layout
        dialogLayout.addView(currentPasswordLayout);
        dialogLayout.addView(newPasswordLayout);
        dialogLayout.addView(confirmPasswordLayout);

        // Create and show dialog
        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(this)
                .setTitle("Change Password")
                .setView(dialogLayout)
                .setPositiveButton("Change Password", null) // Will override later
                .setNegativeButton("Cancel", null);

        AlertDialog dialog = dialogBuilder.create();

        // Override positive button to prevent auto-dismiss
        dialog.setOnShowListener(dialogInterface -> {
            Button changeButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            changeButton.setOnClickListener(v -> {
                String currentPassword = currentPasswordEditText.getText().toString().trim();
                String newPassword = newPasswordEditText.getText().toString().trim();
                String confirmPassword = confirmPasswordEditText.getText().toString().trim();

                // Clear previous errors
                currentPasswordLayout.setError(null);
                newPasswordLayout.setError(null);
                confirmPasswordLayout.setError(null);

                // Validate inputs
                if (currentPassword.isEmpty()) {
                    currentPasswordLayout.setError("Enter current password");
                    return;
                }

                if (newPassword.length() < 6) {
                    newPasswordLayout.setError("Password must be at least 6 characters");
                    return;
                }

                if (!newPassword.equals(confirmPassword)) {
                    confirmPasswordLayout.setError("Passwords don't match");
                    return;
                }

                if (currentPassword.equals(newPassword)) {
                    newPasswordLayout.setError("New password must be different from current password");
                    return;
                }

                // All validations passed - change password
                changeUserPassword(currentPassword, newPassword, dialog);
            });
        });

        dialog.show();
    }

    private void changeUserPassword(String currentPassword, String newPassword, AlertDialog dialog) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Re-authenticate user with current password
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);

        user.reauthenticate(credential).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // Update to new password
                user.updatePassword(newPassword).addOnCompleteListener(updateTask -> {
                    if (updateTask.isSuccessful()) {
                        dialog.dismiss();
                        Toast.makeText(this, "Password changed successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Failed to update password: " + updateTask.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                Toast.makeText(this, "Current password is incorrect", Toast.LENGTH_SHORT).show();
            }
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

    public Task<String> promptUsernameAndCheck(String defaultUsername) {
        final TaskCompletionSource<String> taskCompletionSource = new TaskCompletionSource<>();

        // Create TextInputLayout with TextInputEditText for Material styling
        TextInputLayout textInputLayout = new TextInputLayout(this);
        TextInputEditText usernameEditText = new TextInputEditText(textInputLayout.getContext());
        usernameEditText.setHint("Username");
        usernameEditText.setText(defaultUsername);
        textInputLayout.addView(usernameEditText);

        // Create the MaterialAlertDialog
        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(this)
                .setTitle("Change Username")
                .setView(textInputLayout)
                .setCancelable(false)
                .setPositiveButton("Save", null) // Will override later
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // User cancelled - fail the task
                    dialog.dismiss();
                    taskCompletionSource.setException(new Exception("User cancelled"));
                });

        AlertDialog dialog = dialogBuilder.create();

        // Override the positive button click to prevent auto-dismiss
        dialog.setOnShowListener(dialogInterface -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(v -> {
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
    public boolean isUserSignedInWithGoogle() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return false;

        for (UserInfo userInfo : user.getProviderData()) {
            if ("google.com".equals(userInfo.getProviderId())) {
                return true;
            }
        }
        return false;
    }


    public static Drawable convertBase64ToDrawable(String base64Image, Context context) {
        try {
            if (base64Image == null || base64Image.isEmpty()) {
                return null;
            }

            byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

            if (bitmap != null) {
                return new BitmapDrawable(context.getResources(), bitmap);
            }

        } catch (Exception e) {
            Log.e("Base64Convert", "Failed to convert Base64 to drawable", e);
        }

        return null;
    }
    private String getBase64ImageFromUri(Uri imageUri) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            Bitmap compressedBitmap = compressImage(bitmap, 200); // 200x200 max

            // Convert to Base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos);
            String base64Image = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
            return base64Image;
        } catch (IOException e) {
            e.printStackTrace();
            return "error";
        }
    }

    private static Bitmap compressImage(Bitmap bitmap, int maxSize) {
        if (bitmap.getWidth() > maxSize || bitmap.getHeight() > maxSize) {
            float ratio = Math.min((float) maxSize / bitmap.getWidth(),
                    (float) maxSize / bitmap.getHeight());
            return Bitmap.createScaledBitmap(bitmap,
                    Math.round(ratio * bitmap.getWidth()),
                    Math.round(ratio * bitmap.getHeight()), true);
        }
        return bitmap;
    }
    public static boolean isValidProfileImage(Uri imageUri, Context context) {
        try {
            // Check if it's actually an image file
            String mimeType = context.getContentResolver().getType(imageUri);
            if (mimeType == null || !mimeType.startsWith("image/")) {
                return false;
            }

            // Get image dimensions without loading full image
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;

            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            BitmapFactory.decodeStream(inputStream, null, options);
            inputStream.close();

            // Check if decoding was successful
            if (options.outWidth == -1 || options.outHeight == -1) {
                return false;
            }

            // Check minimum size (50x50) and maximum size (2000x2000)
            return options.outWidth >= 50 && options.outHeight >= 50 &&
                    options.outWidth <= 2000 && options.outHeight <= 2000;

        } catch (Exception e) {
            return false;
        }
    }

    private void setLoading(boolean status) {
        runOnUiThread(() -> {
            etName.setEnabled(!status);
            btnUpdate.setEnabled(!status);
            rlLoading.setVisibility(status ? RelativeLayout.VISIBLE : RelativeLayout.GONE);
        });
    }
}
