package com.avnishgamedev.moodchat;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Log;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.credentials.CredentialManager;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.exceptions.ClearCredentialException;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    Button btnSignOut;

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

        setupViews();
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        } else if (!user.isEmailVerified()) {
            Intent i = new Intent(MainActivity.this, EmailVerificationActivity.class);
            i.putExtra("email", user.getEmail());
            startActivity(i);
            finish();
        }
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
}