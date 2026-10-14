package com.smarttracker.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.smarttracker.data.session.SessionManager;
import com.smarttracker.databinding.ActivityLoginBinding;
import com.smarttracker.ui.home.MainActivity;
import com.smarttracker.ui.legal.PrivacyPolicyActivity;
import com.smarttracker.ui.legal.TermsActivity;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = SessionManager.getInstance(this);

        binding.btnLogin.setOnClickListener(v -> handleLogin());

        binding.tvSwitchRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });

        binding.tvTermsLink.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, TermsActivity.class));
        });

        binding.tvPrivacyLink.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, PrivacyPolicyActivity.class));
        });

        binding.tvForgotPassword.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Password Recovery")
                    .setMessage("This application uses local offline credential storage. If you forgot your password, please contact local system administration or create a new profile.")
                    .setPositiveButton("OK", null)
                    .show();
        });
    }

    private void handleLogin() {
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);

        String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString().trim() : "";

        boolean valid = true;
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError("Please enter a valid email address");
            valid = false;
        }

        if (password.isEmpty()) {
            binding.tilPassword.setError("Password cannot be blank");
            valid = false;
        }

        if (!valid) {
            return;
        }

        // Prevent duplicate submissions
        binding.btnLogin.setEnabled(false);
        binding.progressLoading.setVisibility(View.VISIBLE);

        // Derive name from email prefix or saved profile
        String name = email.split("@")[0];
        name = name.substring(0, 1).toUpperCase() + (name.length() > 1 ? name.substring(1) : "");

        String userId = "usr-" + Math.abs(email.hashCode());
        String token = "demo-user-" + userId;

        final String finalName = name;
        binding.progressLoading.postDelayed(() -> {
            sessionManager.saveLoginSession(token, userId, finalName, email);

            Toast.makeText(this, "Welcome back, " + finalName + "!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }, 500);
    }
}
