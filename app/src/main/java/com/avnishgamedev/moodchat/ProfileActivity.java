package com.avnishgamedev.moodchat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.Toolbar;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import android.util.Base64;

public class ProfileActivity extends AppCompatActivity {

    // Views
    ImageView ivProfilePic;
    EditText etName;
    EditText etEmail;
    Button btnUpdate;
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
        etEmail = findViewById(R.id.etEmail);
        btnUpdate = findViewById(R.id.btnUpdate);
        rlLoading = findViewById(R.id.rlLoading);

        ivProfilePic.setOnClickListener(v -> showImagePicker());

        btnUpdate.setOnClickListener(v -> updateProfile());
    }

    private void loadInitialData() {
        setLoading(true);
        db.collection("users").document(auth.getCurrentUser().getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("name");
                        String email = doc.getString("email");
                        etName.setText(name);
                        etEmail.setText(email);
                        Drawable profilePic = convertBase64ToDrawable(doc.getString("image"), this);
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
            updates.put("image", base64Image);
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
