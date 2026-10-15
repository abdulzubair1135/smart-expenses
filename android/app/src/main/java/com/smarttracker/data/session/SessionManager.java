package com.smarttracker.data.session;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Thread-safe local session management.
 * Preserves user authentication state and consent metadata.
 * Isolates user session across app restarts and cleans state on logout.
 */
public class SessionManager {

    private static final String PREF_NAME = "smart_expenses_session";
    private static final String KEY_IS_LOGGED_IN = "key_is_logged_in";
    private static final String KEY_AUTH_TOKEN = "key_auth_token";
    private static final String KEY_USER_ID = "key_user_id";
    private static final String KEY_USER_NAME = "key_user_name";
    private static final String KEY_USER_EMAIL = "key_user_email";
    private static final String KEY_TERMS_ACCEPTED_AT = "key_terms_accepted_at";
    private static final String KEY_THEME_DARK = "key_theme_dark";

    private static SessionManager instance;
    private final SharedPreferences prefs;

    private SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context);
        }
        return instance;
    }

    public void saveLoginSession(String token, String userId, String name, String email) {
        prefs.edit()
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putString(KEY_AUTH_TOKEN, token != null ? token : "")
                .putString(KEY_USER_ID, userId != null ? userId : "")
                .putString(KEY_USER_NAME, name != null && !name.trim().isEmpty() ? name : "")
                .putString(KEY_USER_EMAIL, email != null ? email : "")
                .apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    public String getUserName() {
        return prefs.getString(KEY_USER_NAME, "");
    }

    public String getUserEmail() {
        return prefs.getString(KEY_USER_EMAIL, "");
    }

    public String getAuthToken() {
        return prefs.getString(KEY_AUTH_TOKEN, "");
    }

    public void recordConsent(String termsVersion) {
        prefs.edit()
                .putString(KEY_TERMS_ACCEPTED_AT, String.valueOf(System.currentTimeMillis()))
                .apply();
    }

    public String getTermsAcceptedAt() {
        return prefs.getString(KEY_TERMS_ACCEPTED_AT, "");
    }

    public boolean isDarkMode() {
        return prefs.getBoolean(KEY_THEME_DARK, false);
    }

    public void setDarkMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_THEME_DARK, enabled).apply();
    }

    public void clearSession() {
        // Keep theme preference if desired, but wipe credentials and login state
        boolean dark = isDarkMode();
        prefs.edit()
                .clear()
                .putBoolean(KEY_THEME_DARK, dark)
                .apply();
    }
}
