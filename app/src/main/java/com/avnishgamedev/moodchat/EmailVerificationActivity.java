package com.avnishgamedev.moodchat;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Timer;
import java.util.TimerTask;

public class EmailVerificationActivity extends AppCompatActivity {
    private static final int RESEND_WAIT_SECONDS = 30;

    // Views
    TextView tvEmail;
    Button btnVerified;
    Button btnResendEmail;
    RelativeLayout rlLoading;

    // Meta data
    FirebaseAuth auth;
    Timer resendTimer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_verification);

        auth = FirebaseAuth.getInstance();

        setupViews();

        sendVerificationEmail();
    }

    private void setupViews() {
        tvEmail = findViewById(R.id.tvEmail);
        btnVerified = findViewById(R.id.btnVerified);
        btnResendEmail = findViewById(R.id.btnResendEmail);
        rlLoading = findViewById(R.id.llLoading);

        if (getIntent().hasExtra("email")) {
            tvEmail.setText(getIntent().getStringExtra("email"));
        }

        btnVerified.setOnClickListener(v -> checkVerification());
        btnResendEmail.setOnClickListener(v -> sendVerificationEmail());
    }

    private void sendVerificationEmail() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "ERROR: User not signed in!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        } else if (user.isEmailVerified()) {
            Toast.makeText(this, "ERROR: User already verified!", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(EmailVerificationActivity.this, MainActivity.class));
            return;
        }

        setLoading(true);
        user.sendEmailVerification()
                .addOnSuccessListener(aVoid -> {
                    Snackbar.make(findViewById(android.R.id.content), "Verification email sent successfully", Snackbar.LENGTH_SHORT).show();
                    btnResendEmail.setEnabled(false);
                    if (resendTimer != null) {
                        resendTimer.cancel();
                        resendTimer = null;
                    }
                    final int[] count = {0};
                    resendTimer = new Timer();
                    resendTimer.schedule(
                            new TimerTask() {
                                @Override
                                public void run() {
                                    count[0]++;
                                    if (count[0] == RESEND_WAIT_SECONDS) {
                                        resendTimer.cancel();
                                        resendTimer = null;
                                        setResendState(true, "Email not received? Resend");
                                    } else {
                                        setResendState(false, "Email not received? Resend in " + (RESEND_WAIT_SECONDS - count[0]));
                                    }
                                }
                            },
                            0,
                            1000
                    );
                })
                .addOnFailureListener(e -> {
                    Snackbar.make(findViewById(android.R.id.content), "Failed to send verification email: " + e.getLocalizedMessage(), Snackbar.LENGTH_SHORT).show();
                })
                .addOnCompleteListener(aVoid -> setLoading(false));
    }

    private void checkVerification() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            setLoading(true);
            user.reload()
                    .addOnSuccessListener(aVoid -> {
                        if (user.isEmailVerified()) {
                            Toast.makeText(this, "Email verified successfully", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(EmailVerificationActivity.this, MainActivity.class));
                        } else {
                            Snackbar.make(findViewById(android.R.id.content), "Email not verified. Please check your email", Snackbar.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Snackbar.make(findViewById(android.R.id.content), "Failed to check status: " + e.getLocalizedMessage(), Snackbar.LENGTH_SHORT).show();
                    })
                    .addOnCompleteListener(aVoid -> {
                        setLoading(false);
                    });
        }
    }

    private void setLoading(boolean status) {
        runOnUiThread(() -> {
            btnVerified.setEnabled(!status);
            rlLoading.setVisibility(status ? RelativeLayout.VISIBLE : RelativeLayout.GONE);
        });
    }

    private void setResendState(boolean enabled, String text) {
        runOnUiThread(() -> {
            btnResendEmail.setEnabled(enabled);
            btnResendEmail.setText(text);
        });
    }
}
