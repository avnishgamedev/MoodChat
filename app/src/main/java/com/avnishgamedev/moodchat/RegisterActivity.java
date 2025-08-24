package com.avnishgamedev.moodchat;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class RegisterActivity extends AppCompatActivity {
    private static final String TAG = "RegisterActivity";

    // Views
    EditText etEmail;
    EditText etUsername;
    EditText etName;
    EditText etPassword;
    EditText etConfirmPassword;
    Button btnRegisterWithEmail;
    Button btnLogin;
    RelativeLayout rlLoading;

    // Meta data
    FirebaseAuth auth;
    boolean isUsernameValid = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();

        setupViews();
    }

    private void setupViews() {
        etEmail = findViewById(R.id.etEmail);
        etUsername = findViewById(R.id.etUsername);
        etName = findViewById(R.id.etName);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegisterWithEmail = findViewById(R.id.btnRegisterWithEmail);
        btnLogin = findViewById(R.id.btnLogin);
        rlLoading = findViewById(R.id.rlLoading);

        btnRegisterWithEmail.setOnClickListener(v -> registerWithEmail());
        btnLogin.setOnClickListener(v -> { startActivity(new Intent(RegisterActivity.this, LoginActivity.class)); finish(); });

        etUsername.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                validateUsername();
            }
        });

        etPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                updatePasswordStrengthUI(editable.toString());
            }

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }
        });
    }

    private void validateUsername() {
        String username = etUsername.getText().toString();
        if (username.isEmpty()) {
            etUsername.setError("Username cannot be empty");
            return;
        } else if (username.length() < 4) {
            etUsername.setError("Username must be at least 4 characters long");
            return;
        } else if (username.contains(" ")) {
            etUsername.setError("Username cannot contain spaces");
            return;
        }

        setLoading(true);
        FirebaseFirestore.getInstance().collection("users").whereEqualTo("username", username).get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        isUsernameValid = true;
                        etUsername.setError(null);
                    } else {
                        etUsername.setError("Username already exists");
                        isUsernameValid = false;
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to validate username: " + e.getLocalizedMessage());
                    Snackbar.make(findViewById(android.R.id.content), "Failed to validate username: " + e.getLocalizedMessage(), Snackbar.LENGTH_SHORT).show();

                    isUsernameValid = false;
                })
                .addOnCompleteListener(x -> {
                    setLoading(false);
                });
    }

    private void registerWithEmail() {
        String email = etEmail.getText().toString();
        String password = etPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();
        String name = etName.getText().toString();
        String username = etUsername.getText().toString();

        if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || name.isEmpty() || username.isEmpty()) {
            Snackbar.make(findViewById(android.R.id.content), "Please fill all the fields", Snackbar.LENGTH_SHORT).show();
            return;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Snackbar.make(findViewById(android.R.id.content), "Please enter a valid email", Snackbar.LENGTH_SHORT).show();
            return;
        } else if (password.length() < 6) {
            Snackbar.make(findViewById(android.R.id.content), "Password must be at least 4 characters long", Snackbar.LENGTH_SHORT).show();
            return;
        } else if (!password.equals(confirmPassword)) {
            Snackbar.make(findViewById(android.R.id.content), "Passwords do not match", Snackbar.LENGTH_SHORT).show();
            return;
        } else if (!isUsernameValid) {
            Snackbar.make(findViewById(android.R.id.content), "Please choose a valid username", Snackbar.LENGTH_SHORT).show();
            return;
        } else if (name.length() < 4) {
            Snackbar.make(findViewById(android.R.id.content), "Name must be at least 4 characters long", Snackbar.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "registerWithEmail:started");

        setLoading(true);
        handleAuthTask(auth.createUserWithEmailAndPassword(email, password));
    }

    private void handleAuthTask(Task<AuthResult> task) {
        task.addOnSuccessListener(result -> {
            Log.d(TAG, "handleAuthResult:success");
            runOnUiThread(() -> {
                FirebaseUser user = result.getUser();

                User u = new User(
                        user.getEmail(),
                        etName.getText().toString(),
                        true,
                        null,
                        etUsername.getText().toString(),
                        Timestamp.now(),
                        Timestamp.now()
                );
                UserManager.getInstance().createOrUpdateUserDocument(u)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "handleAuthResult:firestore:success");
                            Toast.makeText(this, "Registration Successful! Please login", Toast.LENGTH_SHORT).show();

                            Intent i = new Intent(RegisterActivity.this, LoginActivity.class);
                            i.putExtra("email", user.getEmail());
                            i.putExtra("password", etPassword.getText().toString());
                            startActivity(i);
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            Log.w(TAG, "handleAuthResult:failure", e);
                            Snackbar.make(findViewById(android.R.id.content), "Failed to save user: " + e.getLocalizedMessage(), Snackbar.LENGTH_SHORT).show();
                            user.delete();
                        });
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
            etUsername.setEnabled(!status);
            etName.setEnabled(!status);
            etPassword.setEnabled(!status);
            etConfirmPassword.setEnabled(!status);
            btnRegisterWithEmail.setEnabled(!status);
            btnLogin.setEnabled(!status);
            rlLoading.setVisibility(status ? RelativeLayout.VISIBLE : RelativeLayout.GONE);
        });
    }

    // ---- Password Strength ----
    public enum PasswordStrength {
        WEAK,
        MEDIUM,
        STRONG
    }

    public PasswordStrength calculatePasswordStrength(String password) {
        boolean hasLower = false, hasUpper = false, hasDigit = false, hasSpecial = false;
        int length = password.length();
        String specialChars = "!@#$%^&*()-_+=<>?/{}[]|:;.,~`'\"\\";
        for (int i = 0; i < length; i++) {
            char c = password.charAt(i);
            if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if (specialChars.indexOf(c) >= 0) hasSpecial = true;
        }
        int score = 0;
        if (length >= 8) score++;
        if (hasLower && hasUpper) score++;
        if (hasDigit) score++;
        if (hasSpecial) score++;

        if (score <= 1) return PasswordStrength.WEAK;
        else if (score == 2 || score == 3) return PasswordStrength.MEDIUM;
        else return PasswordStrength.STRONG;
    }
    private void updatePasswordStrengthUI(String password) {
        PasswordStrength strength = calculatePasswordStrength(password);

        View weakIndicator = findViewById(R.id.weakStrengthIndicator);
        View mediumIndicator = findViewById(R.id.mediumStrengthIndicator);
        View strongIndicator = findViewById(R.id.strongStrengthIndicator);

        weakIndicator.setAlpha(0.3f);
        mediumIndicator.setAlpha(0.3f);
        strongIndicator.setAlpha(0.3f);

        switch (strength) {
            case WEAK:
                weakIndicator.setAlpha(1.0f);
                break;
            case MEDIUM:
                weakIndicator.setAlpha(1.0f);
                mediumIndicator.setAlpha(1.0f);
                break;
            case STRONG:
                weakIndicator.setAlpha(1.0f);
                mediumIndicator.setAlpha(1.0f);
                strongIndicator.setAlpha(1.0f);
                break;
        }
    }
}
