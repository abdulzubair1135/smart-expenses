package com.smarttracker.ui.auth;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.gson.JsonObject;
import com.smarttracker.R;
import com.smarttracker.data.remote.BackendApiClient;
import com.smarttracker.data.session.SessionManager;
import com.smarttracker.databinding.ActivityRegisterBinding;
import com.smarttracker.ui.home.MainActivity;
import com.smarttracker.ui.legal.PrivacyPolicyActivity;
import com.smarttracker.ui.legal.TermsActivity;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private SessionManager sessionManager;
    private BackendApiClient apiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = SessionManager.getInstance(this);
        apiClient = BackendApiClient.getInstance(this);

        setupConsentText();

        binding.btnRegister.setOnClickListener(v -> handleRegistration());

        binding.tvSwitchLogin.setOnClickListener(v -> finish());
    }

    private void setupConsentText() {
        String fullText = "I agree to the Terms & Conditions and acknowledge the Privacy Policy.";
        SpannableString spannable = new SpannableString(fullText);

        int termsStart = fullText.indexOf("Terms & Conditions");
        int termsEnd = termsStart + "Terms & Conditions".length();

        int privacyStart = fullText.indexOf("Privacy Policy");
        int privacyEnd = privacyStart + "Privacy Policy".length();

        int linkColor = ContextCompat.getColor(this, R.color.brand_primary);

        // Clickable Terms & Conditions
        ClickableSpan termsSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                startActivity(new Intent(RegisterActivity.this, TermsActivity.class));
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(linkColor);
                ds.setUnderlineText(true);
            }
        };

        // Clickable Privacy Policy
        ClickableSpan privacySpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                startActivity(new Intent(RegisterActivity.this, PrivacyPolicyActivity.class));
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(linkColor);
                ds.setUnderlineText(true);
            }
        };

        if (termsStart >= 0) {
            spannable.setSpan(termsSpan, termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (privacyStart >= 0) {
            spannable.setSpan(privacySpan, privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        binding.tvTermsConsent.setText(spannable);
        binding.tvTermsConsent.setMovementMethod(LinkMovementMethod.getInstance());
        binding.tvTermsConsent.setHighlightColor(Color.TRANSPARENT);
        binding.tvTermsConsent.setClickable(true);
        binding.tvTermsConsent.setFocusable(false);
    }

    private void handleRegistration() {
        // Clear previous errors
        binding.tilName.setError(null);
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
        binding.tilConfirmPassword.setError(null);

        String name = binding.etName.getText() != null ? binding.etName.getText().toString().trim() : "";
        String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString().trim() : "";
        String confirm = binding.etConfirmPassword.getText() != null ? binding.etConfirmPassword.getText().toString().trim() : "";

        boolean valid = true;

        if (name.isEmpty()) {
            binding.tilName.setError("Full name is required");
            valid = false;
        }

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError("Please enter a valid email address");
            valid = false;
        }

        if (password.length() < 6) {
            binding.tilPassword.setError("Password must be at least 6 characters");
            valid = false;
        }

        if (!password.equals(confirm)) {
            binding.tilConfirmPassword.setError("Passwords do not match");
            valid = false;
        }

        if (!binding.cbTermsConsent.isChecked()) {
            Toast.makeText(this, "Please agree to the Terms & Conditions and acknowledge Privacy Policy", Toast.LENGTH_LONG).show();
            valid = false;
        }

        if (!valid) {
            return;
        }

        // Prevent duplicate submission
        binding.btnRegister.setEnabled(false);
        binding.progressLoading.setVisibility(View.VISIBLE);

        apiClient.register(name, email, password, confirm, true, new BackendApiClient.ApiCallback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                binding.btnRegister.setEnabled(true);
                binding.progressLoading.setVisibility(View.GONE);

                try {
                    JsonObject data = result.getAsJsonObject("data");
                    JsonObject user = data.getAsJsonObject("user");
                    String token = data.get("token").getAsString();
                    String userId = user.get("id").getAsString();
                    String displayName = user.has("displayName") ? user.get("displayName").getAsString() : name;

                    sessionManager.saveLoginSession(token, userId, displayName, email);
                    sessionManager.recordConsent("2026.1");

                    Toast.makeText(RegisterActivity.this, "Welcome " + displayName + "! Account created successfully.", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                } catch (Exception e) {
                    fallbackLocalRegistration(name, email);
                }
            }

            @Override
            public void onError(String errorMessage) {
                binding.btnRegister.setEnabled(true);
                binding.progressLoading.setVisibility(View.GONE);

                if (errorMessage != null && errorMessage.toLowerCase().contains("already exists")) {
                    binding.tilEmail.setError("An account with this email already exists");
                } else {
                    // Offline fallback
                    Toast.makeText(RegisterActivity.this, "Network offline: creating local-first account", Toast.LENGTH_SHORT).show();
                    fallbackLocalRegistration(name, email);
                }
            }
        });
    }

    private void fallbackLocalRegistration(String name, String email) {
        String generatedUserId = "usr-" + System.currentTimeMillis();
        String token = "local-token-" + generatedUserId;

        sessionManager.saveLoginSession(token, generatedUserId, name, email);
        sessionManager.recordConsent("2026.1");

        Toast.makeText(this, "Welcome " + name + "! Account created locally.", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
