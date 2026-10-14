package com.smarttracker.ui.splash;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.smarttracker.data.session.SessionManager;
import com.smarttracker.databinding.ActivitySplashBinding;
import com.smarttracker.ui.auth.LoginActivity;
import com.smarttracker.ui.home.MainActivity;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Check persistent local session
        SessionManager sessionManager = SessionManager.getInstance(this);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent nextIntent;
            if (sessionManager.isLoggedIn()) {
                nextIntent = new Intent(SplashActivity.this, MainActivity.class);
            } else {
                nextIntent = new Intent(SplashActivity.this, LoginActivity.class);
            }
            startActivity(nextIntent);
            finish();
        }, 1000);
    }
}
