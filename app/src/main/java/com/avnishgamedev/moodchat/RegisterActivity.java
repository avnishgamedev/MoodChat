package com.avnishgamedev.moodchat;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class RegisterActivity extends AppCompatActivity {
    private static final String TAG = "RegisterActivity";

    // Views
    EditText etEmail;
    EditText etPassword;
    EditText etConfirmPassword;
    Button btnRegisterWithEmail;
    Button btnLogin;
    RelativeLayout rlLoading;

    // Meta data
    FirebaseAuth auth;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();

        setupViews();
    }

    private void setupViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegisterWithEmail = findViewById(R.id.btnRegisterWithEmail);
        btnLogin = findViewById(R.id.btnLogin);
        rlLoading = findViewById(R.id.rlLoading);

        btnRegisterWithEmail.setOnClickListener(v -> registerWithEmail());
        btnLogin.setOnClickListener(v -> { startActivity(new Intent(RegisterActivity.this, LoginActivity.class)); finish(); });
    }

    private void registerWithEmail() {
        String email = etEmail.getText().toString();
        String password = etPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();

        if (email.isEmpty() || password.isEmpty()) {
            Snackbar.make(findViewById(android.R.id.content), "Please fill all the fields", Snackbar.LENGTH_SHORT).show();
            return;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Snackbar.make(findViewById(android.R.id.content), "Please enter a valid email", Snackbar.LENGTH_SHORT).show();
            return;
        } else if (password.length() < 4) {
            Snackbar.make(findViewById(android.R.id.content), "Password must be at least 4 characters long", Snackbar.LENGTH_SHORT).show();
            return;
        } else if (!password.equals(confirmPassword)) {
            Snackbar.make(findViewById(android.R.id.content), "Passwords do not match", Snackbar.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "registerWithEmail:started");

        setLoading(true);
        handleAuthResult(auth.createUserWithEmailAndPassword(email, password));
    }

    private void handleAuthResult(Task<AuthResult> task) {
        task.addOnSuccessListener(result -> {
            Log.d(TAG, "handleAuthResult:success");
            runOnUiThread(() -> {
                FirebaseUser user = result.getUser();
                Toast.makeText(this, "Registration Successful! Please login", Toast.LENGTH_SHORT).show();

                Intent i = new Intent(RegisterActivity.this, LoginActivity.class);
                i.putExtra("email", user.getEmail());
                i.putExtra("password", etPassword.getText().toString());
                startActivity(i);
                finish();
            });
        })
        .addOnFailureListener(e -> {
            Log.w(TAG, "handleAuthResult:failure", e);
            Snackbar.make(findViewById(android.R.id.content), "Registration Failed: " + e.getLocalizedMessage(), Snackbar.LENGTH_SHORT).show();
        })
        .addOnCompleteListener(x -> {
            setLoading(false);
        });
    }

    private void setLoading(boolean status) {
        runOnUiThread(() -> {
            etEmail.setEnabled(!status);
            etPassword.setEnabled(!status);
            etConfirmPassword.setEnabled(!status);
            btnRegisterWithEmail.setEnabled(!status);
            btnLogin.setEnabled(!status);
            rlLoading.setVisibility(status ? RelativeLayout.VISIBLE : RelativeLayout.GONE);
        });
    }
}
