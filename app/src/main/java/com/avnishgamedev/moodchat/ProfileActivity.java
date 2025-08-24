package com.avnishgamedev.moodchat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {
    private static final String TAG = "ProfileActivity";

    // Views
    ImageView ivProfilePic;
    EditText etName;
    EditText etUsername;
    EditText etEmail;
    Button btnUpdate;
    Button btnChangePassword;
    TextView tvRegisteredOn;
    RelativeLayout rlLoading;

    // Meta data
    FirebaseAuth auth;
    Bitmap updatedProfilePic;
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
        tvRegisteredOn = findViewById(R.id.tvRegisteredOn);
        rlLoading = findViewById(R.id.llLoading);

        ivProfilePic.setOnClickListener(v -> showImagePicker());

        btnUpdate.setOnClickListener(v -> updateProfile());

        etUsername.setOnClickListener(v -> {
            promptUsernameAndCheck(etUsername.getText().toString()).addOnSuccessListener(username -> {
                etUsername.setText(username);
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
        User u = UserManager.getInstance().getUser();
        if (u == null) {
            setLoading(true);
            UserManager.getInstance().loadUser().addOnCompleteListener(task -> loadInitialData());
            return;
        }

        setLoading(false);
        etName.setText(u.getName());
        etUsername.setText(u.getUsername());
        etEmail.setText(u.getEmail());
        String base64ProfilePicture = u.getProfilePicture();
        if (base64ProfilePicture != null)
            ivProfilePic.setImageBitmap(base64ToBitmap(base64ProfilePicture));
        tvRegisteredOn.setText(new SimpleDateFormat("dd MMMM yyyy hh:mm a", Locale.getDefault()).format(u.getRegisteredOn().toDate()));
    }

    private void showImagePicker() {
        pickMedia.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }
    private void handleSelectedImage(Uri uri) {
        try {
            updatedProfilePic = getCenterSquareBitmapFromUri(this, uri);
            ivProfilePic.setImageBitmap(updatedProfilePic);
        } catch (IOException e) {
            Snackbar.make(findViewById(android.R.id.content), "Image not compatible!", Snackbar.LENGTH_SHORT).show();
        }
    }

    private void updateProfile() {
        User u = UserManager.getInstance().getUser();

        if (!u.getUsername().equals(etUsername.getText().toString()))
            u.setUsername(etUsername.getText().toString());

        if (!u.getName().equals(etName.getText().toString()))
            u.setName(etName.getText().toString());

        if (updatedProfilePic != null)
            u.setProfilePicture(bitmapToBase64(updatedProfilePic, 100));

        setLoading(true);
        UserManager.getInstance().createOrUpdateUserDocument(u)
                .addOnSuccessListener(aVoid -> {
                    Snackbar.make(findViewById(android.R.id.content), "Profile updated successfully", Snackbar.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update username:", e);
                    Snackbar.make(findViewById(android.R.id.content), "Failed to update profile: " + e.getLocalizedMessage(), Snackbar.LENGTH_SHORT).show();
                })
                .addOnCompleteListener(task -> setLoading(false));
    }

    // --------------------- Helpers -------------------------
    public Bitmap getCenterSquareBitmapFromUri(Context context, Uri imageUri) throws IOException {
        // Decode the bitmap from the Uri
        Bitmap srcBmp = MediaStore.Images.Media.getBitmap(context.getContentResolver(), imageUri);

        int width = srcBmp.getWidth();
        int height = srcBmp.getHeight();

        // If already square, return original
        if (width == height) {
            return srcBmp;
        }

        // Use ThumbnailUtils for center cropping (handles memory better)
        int size = Math.min(width, height);
        return ThumbnailUtils.extractThumbnail(srcBmp, size, size, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
    }
    public static String bitmapToBase64(Bitmap bitmap, int quality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        byte[] bytes = baos.toByteArray();
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }
    public static Bitmap base64ToBitmap(String base64Str) {
        byte[] decodedBytes = Base64.decode(base64Str, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
    }

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
        return FirebaseFirestore.getInstance().collection("users").whereEqualTo("username", username).get().continueWith(task -> {
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

    private void setLoading(boolean status) {
        runOnUiThread(() -> {
            etName.setEnabled(!status);
            btnUpdate.setEnabled(!status);
            rlLoading.setVisibility(status ? RelativeLayout.VISIBLE : RelativeLayout.GONE);
        });
    }
}
