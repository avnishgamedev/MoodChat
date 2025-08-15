package com.avnishgamedev.moodchat;

import static com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL;

import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Log;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.gms.tasks.Task;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";

    // Views
    EditText etEmail;
    EditText etPassword;
    Button btnSignInWithEmail;
    Button btnRegister;
    Button btnSIWG;
    RelativeLayout rlLoading;

    // Meta data
    FirebaseAuth auth;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();

        setupViews();
        loadOptionalData();
    }

    private void setupViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnSignInWithEmail = findViewById(R.id.btnSignInWithEmail);
        btnRegister = findViewById(R.id.btnRegister);
        btnSIWG = findViewById(R.id.btnSIWG);
        rlLoading = findViewById(R.id.rlLoading);

        btnSignInWithEmail.setOnClickListener(v -> signInWithEmail());
        btnRegister.setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
        btnSIWG.setOnClickListener(v -> signInWithGoogle());
    }

    private void loadOptionalData() {
        Intent i = getIntent();
        if (i.hasExtra("email")) {
            etEmail.setText(i.getStringExtra("email"));
        }
        if (i.hasExtra("password")) {
            etPassword.setText(i.getStringExtra("password"));
        }
    }

    private void signInWithEmail() {
        String email = etEmail.getText().toString();
        String password = etPassword.getText().toString();

        if (email.isEmpty() || password.isEmpty()) {
            Snackbar.make(findViewById(android.R.id.content), "Please fill all the fields", Snackbar.LENGTH_SHORT).show();
            return;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Snackbar.make(findViewById(android.R.id.content), "Please enter a valid email", Snackbar.LENGTH_SHORT).show();
            return;
        } else if (password.length() < 4) {
            Snackbar.make(findViewById(android.R.id.content), "Password must be at least 4 characters long", Snackbar.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "signInWithEmail:started");

        setLoading(true);
        handleAuthResult(auth.signInWithEmailAndPassword(email, password));
    }

    private void signInWithGoogle() {

        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setServerClientId(getString(R.string.default_web_client_id))
                .setFilterByAuthorizedAccounts(false)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        Log.d(TAG, "signInWithGoogle:started");

        setLoading(true);
        CredentialManager.create(this).getCredentialAsync(
                this,
                request,
                new CancellationSignal(),
                Executors.newSingleThreadExecutor(),
                new CredentialManagerCallback<>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        Log.d(TAG, "signInWithGoogle:onResult");

                        Credential credential = result.getCredential();

                        if (credential instanceof CustomCredential && credential.getType().equals(TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {
                            CustomCredential customCredential = (CustomCredential) credential;

                            Bundle credentialData = customCredential.getData();
                            GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credentialData);

                            Log.d(TAG, "signInWithGoogle:onResult:CustomCredential");

                            // Use with Firebase
                            handleAuthResult(auth.signInWithCredential(GoogleAuthProvider.getCredential(googleIdTokenCredential.getIdToken(), null)));
                        } else {
                            Snackbar.make(findViewById(android.R.id.content), "Google SignIn Failed", Snackbar.LENGTH_SHORT).show();
                            Log.w(TAG, "signInWithGoogle:onResult:failure - Credential is not of type GOOGLE_ID_TOKEN_CREDENTIAL");
                            setLoading(false);
                        }
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        Snackbar.make(findViewById(android.R.id.content), "Google SignIn Failed: " + e.getLocalizedMessage(), Snackbar.LENGTH_SHORT).show();
                        Log.w(TAG, "SIWG Failed", e);

                        setLoading(false);
                    }
                }
        );
    }

    private void handleAuthResult(Task<AuthResult> task) {
        task.addOnSuccessListener(result -> {
            Log.d(TAG, "handleAuthResult:success");
            runOnUiThread(() -> {
                Toast.makeText(this, "Logged in", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(LoginActivity.this, LoadMainActivity.class));
                finish();
            });
        })
        .addOnFailureListener(e -> {
            Log.w(TAG, "handleAuthResult:failure", e);
            Snackbar.make(findViewById(android.R.id.content), "Login Failed: " + e.getLocalizedMessage(), Snackbar.LENGTH_SHORT).show();
        })
        .addOnCompleteListener(x -> {
            setLoading(false);
        });
    }

    private void setLoading(boolean status) {
        runOnUiThread(() -> {
            etEmail.setEnabled(!status);
            etPassword.setEnabled(!status);
            btnSignInWithEmail.setEnabled(!status);
            btnRegister.setEnabled(!status);
            btnSIWG.setEnabled(!status);
            rlLoading.setVisibility(status ? RelativeLayout.VISIBLE : RelativeLayout.GONE);
        });
    }
}
