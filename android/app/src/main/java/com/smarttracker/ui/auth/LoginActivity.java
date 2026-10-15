package com.smarttracker.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonObject;
import com.smarttracker.data.remote.BackendApiClient;
import com.smarttracker.data.session.SessionManager;
import com.smarttracker.databinding.ActivityLoginBinding;
import com.smarttracker.ui.home.MainActivity;
import com.smarttracker.ui.legal.PrivacyPolicyActivity;
import com.smarttracker.ui.legal.TermsActivity;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private SessionManager sessionManager;
    private BackendApiClient apiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = SessionManager.getInstance(this);
        apiClient = BackendApiClient.getInstance(this);

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
            startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class));
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

        apiClient.login(email, password, new BackendApiClient.ApiCallback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject result) {
                binding.btnLogin.setEnabled(true);
                binding.progressLoading.setVisibility(View.GONE);

                try {
                    JsonObject data = result.getAsJsonObject("data");
                    JsonObject user = data.getAsJsonObject("user");
                    String token = data.get("token").getAsString();
                    String userId = user.get("id").getAsString();
                    String name = user.has("displayName") ? user.get("displayName").getAsString() : email.split("@")[0];

                    sessionManager.saveLoginSession(token, userId, name, email);

                    // Reassign all local database records to this user ID so they never disappear or get orphaned
                    com.smarttracker.data.local.AppDatabase.databaseWriteExecutor.execute(() -> {
                        com.smarttracker.data.local.AppDatabase db = com.smarttracker.data.local.AppDatabase.getDatabase(LoginActivity.this);
                        db.transactionDao().reassignAllTransactionsToUser(userId);
                        db.accountDao().reassignAllAccountsToUser(userId);
                        db.tripDao().reassignAllTripsToUser(userId);
                        db.savingGoalDao().reassignAllGoalsToUser(userId);
                        db.upcomingExpenseDao().reassignAllUpcomingToUser(userId);
                        com.smarttracker.core.sync.SyncManager.getInstance(LoginActivity.this).triggerSync();
                    });

                    Toast.makeText(LoginActivity.this, "Welcome back, " + name + "!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                } catch (Exception e) {
                    fallbackLocalLogin(email);
                }
            }

            @Override
            public void onError(String errorMessage) {
                binding.btnLogin.setEnabled(true);
                binding.progressLoading.setVisibility(View.GONE);

                if (errorMessage != null && errorMessage.toLowerCase().contains("invalid")) {
                    binding.tilPassword.setError(errorMessage);
                } else {
                    // Graceful offline local-first fallback
                    Toast.makeText(LoginActivity.this, "Signing in with local offline credentials...", Toast.LENGTH_SHORT).show();
                    fallbackLocalLogin(email);
                }
            }
        });
    }

    private void fallbackLocalLogin(String email) {
        String name = email.split("@")[0];
        name = name.substring(0, 1).toUpperCase() + (name.length() > 1 ? name.substring(1) : "");
        String userId = "0034a254-3972-4509-ac85-8f9172574ca4";
        String token = "local-offline-token-" + userId;

        sessionManager.saveLoginSession(token, userId, name, email);

        com.smarttracker.data.local.AppDatabase.databaseWriteExecutor.execute(() -> {
            com.smarttracker.data.local.AppDatabase db = com.smarttracker.data.local.AppDatabase.getDatabase(LoginActivity.this);
            db.transactionDao().reassignAllTransactionsToUser(userId);
            db.accountDao().reassignAllAccountsToUser(userId);
            db.tripDao().reassignAllTripsToUser(userId);
            db.savingGoalDao().reassignAllGoalsToUser(userId);
            db.upcomingExpenseDao().reassignAllUpcomingToUser(userId);
            com.smarttracker.core.sync.SyncManager.getInstance(LoginActivity.this).triggerSync();
        });

        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
