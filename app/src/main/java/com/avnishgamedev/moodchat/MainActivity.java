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
    Button btnSignOut;

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

        loadUserDocument();
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
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupToolbar() {
        setSupportActionBar(findViewById(R.id.materialToolbar));
    }

    private void setupViews() {
        btnSignOut = findViewById(R.id.btnSignOut);
        btnSignOut.setOnClickListener(v -> signOut());
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

    // Save user to Firestore stuff
    private void loadUserDocument() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(doc -> {
                        if (!doc.exists()) {
                            createUserDocument(user);
                            return;
                        }

                        userDoc = doc;
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to load user document: " + e.getLocalizedMessage());
                    });
        }
    }

    // Helpers
    private void createUserDocument(FirebaseUser user) {
        HashMap<String, Object> data = new HashMap<>();
        data.put("name", user.getDisplayName());
        data.put("email", user.getEmail());

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
                    String base64Image = convertBitmapToBase64(bitmap);
                    data.put("image", base64Image);
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
                    loadUserDocument();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save user document: " + e.getLocalizedMessage());
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
    public static String convertBitmapToBase64(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }

        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            return Base64.encodeToString(byteArray, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e("BitmapToBase64", "Failed to convert bitmap to Base64", e);
            return null;
        }
    }
}