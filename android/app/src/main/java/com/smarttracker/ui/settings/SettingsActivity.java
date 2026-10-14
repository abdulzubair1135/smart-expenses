package com.smarttracker.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.smarttracker.data.local.AppDatabase;
import com.smarttracker.data.repository.TransactionRepository;
import com.smarttracker.data.session.SessionManager;
import com.smarttracker.databinding.ActivitySettingsBinding;
import com.smarttracker.ui.auth.LoginActivity;
import com.smarttracker.ui.legal.PrivacyPolicyActivity;
import com.smarttracker.ui.legal.TermsActivity;
import com.smarttracker.ui.voice.SpeechRecognitionManager;
import com.smarttracker.ui.voice.TextToSpeechManager;
import com.smarttracker.ui.voice.VoiceAssistantBottomSheetDialog;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = SessionManager.getInstance(this);

        setupProfileHeader();
        setupAiAndOfflineSection();

        binding.btnBack.setOnClickListener(v -> finish());

        binding.layoutTerms.setOnClickListener(v -> {
            startActivity(new Intent(SettingsActivity.this, TermsActivity.class));
        });

        binding.layoutPrivacy.setOnClickListener(v -> {
            startActivity(new Intent(SettingsActivity.this, PrivacyPolicyActivity.class));
        });

        binding.btnLogout.setOnClickListener(v -> showLogoutConfirmation());
    }

    private void setupProfileHeader() {
        String name = sessionManager.getUserName();
        String email = sessionManager.getUserEmail();

        binding.tvUserName.setText(name);
        binding.tvUserEmail.setText(email + " • Local Account");

        if (name != null && !name.trim().isEmpty()) {
            binding.tvAvatarInitials.setText(name.substring(0, 1).toUpperCase());
        } else {
            binding.tvAvatarInitials.setText("U");
        }
    }

    private void setupAiAndOfflineSection() {
        // Speech recognition offline check
        SpeechRecognitionManager sttManager = new SpeechRecognitionManager(this, null);
        binding.tvSttStatus.setText("• Speech-to-Text: Multi-Language (Hindi, Hinglish, English)");

        // TTS offline check
        TextToSpeechManager ttsManager = new TextToSpeechManager(this);
        binding.tvTtsStatus.setText("• Text-to-Speech: Voice Feedback & Audio Confirmation");

        // Switch toggle
        binding.switchLocalAi.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Toast.makeText(this, isChecked ? "Voice assistant enabled" : "Voice assistant paused", Toast.LENGTH_SHORT).show();
        });

        // Test Voice button
        binding.btnTestOfflineAi.setOnClickListener(v -> {
            TransactionRepository repo = new TransactionRepository(getApplication());
            VoiceAssistantBottomSheetDialog dialog = new VoiceAssistantBottomSheetDialog(this, repo);
            dialog.show();
        });
    }

    private void showLogoutConfirmation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Confirm Logout")
                .setMessage("Are you sure you want to log out? Your local transaction records will remain safely saved on your device.")
                .setPositiveButton("Log Out", (dialog, which) -> {
                    // Clear active session
                    sessionManager.clearSession();

                    Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();

                    // Completely clear task stack to prevent Back button reopening authenticated screens
                    Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finishAffinity();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                })
                .show();
    }
}
