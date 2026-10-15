import { Router } from 'express';
import crypto from 'crypto';
import { authenticateUser, AuthenticatedRequest } from '../middleware/auth';
import { store, Profile, PasswordReset } from '../repositories/store';
import { supabaseAdmin, isSupabaseConfigured } from '../services/supabase';

export const authRouter = Router();

// Register new user with Terms & Privacy Policy consent validation
authRouter.post('/register', (req, res) => {
  const { name, email, password, confirmPassword, termsAccepted } = req.body;

  if (!name || typeof name !== 'string' || name.trim().length === 0) {
    res.status(400).json({ success: false, error: { message: 'Full name is required' } });
    return;
  }

  if (!email || typeof email !== 'string' || !email.includes('@')) {
    res.status(400).json({ success: false, error: { message: 'A valid email address is required' } });
    return;
  }

  if (!password || typeof password !== 'string' || password.length < 6) {
    res.status(400).json({ success: false, error: { message: 'Password must be at least 6 characters' } });
    return;
  }

  if (password !== confirmPassword) {
    res.status(400).json({ success: false, error: { message: 'Passwords do not match' } });
    return;
  }

  if (termsAccepted !== true) {
    res.status(400).json({
      success: false,
      error: { message: 'You must agree to the Terms & Conditions and acknowledge the Privacy Policy.' }
    });
    return;
  }

  // Duplicate email check
  const existing = Array.from(store.profiles.values()).find(
    p => p.email.toLowerCase() === email.trim().toLowerCase()
  );
  if (existing) {
    res.status(409).json({ success: false, error: { message: 'An account with this email already exists' } });
    return;
  }

  const newId = `usr-${Date.now()}`;
  const nowIso = new Date().toISOString();

  const newProfile: Profile = {
    id: newId,
    email: email.trim().toLowerCase(),
    displayName: name.trim(),
    accountStatus: 'verified',
    termsConsent: {
      termsVersion: '2026.1',
      privacyVersion: '2026.1',
      consentedAt: nowIso
    },
    createdAt: nowIso
  };

  store.profiles.set(newId, newProfile);
  store.passwords.set(newId, password);
  
  const token = `demo-user-${newId}`;
  store.activeTokens.add(token);

  // Initialize basic default accounts for the new user
  store.accounts.set(`acc-cash-${newId}`, {
    id: `acc-cash-${newId}`,
    userId: newId,
    name: 'Cash',
    type: 'cash',
    openingBalanceMinor: 0,
    currencyCode: 'INR',
    createdAt: nowIso
  });

  store.recordActivity(newId, 'ACCOUNT_REGISTERED', 'success');
  store.updatePresence(newId, 'android');

  res.status(201).json({
    success: true,
    message: 'Registration successful',
    data: {
      user: newProfile,
      token
    }
  });
});

// Login / Session establishment
authRouter.post('/login', async (req, res) => {
  const { email, password } = req.body;
  if (!email || !password) {
    res.status(400).json({ success: false, error: { message: 'Email and password required' } });
    return;
  }

  const normalizedEmail = email.trim().toLowerCase();
  let profile = Array.from(store.profiles.values()).find(
    p => p.email.toLowerCase() === normalizedEmail
  );

  // If not found in memory store, lookup Supabase PostgreSQL profiles directly
  if (!profile && isSupabaseConfigured() && supabaseAdmin) {
    try {
      const { data: dbUser } = await supabaseAdmin
        .from('profiles')
        .select('*')
        .eq('email', normalizedEmail)
        .maybeSingle();

      if (dbUser) {
        profile = {
          id: dbUser.id,
          email: dbUser.email,
          displayName: dbUser.display_name || 'User',
          accountStatus: dbUser.account_status || 'verified',
          termsConsent: {
            termsVersion: dbUser.terms_version || '2026.1',
            privacyVersion: '2026.1',
            consentedAt: dbUser.created_at || new Date().toISOString()
          },
          createdAt: dbUser.created_at
        };
        store.profiles.set(dbUser.id, profile);
        store.passwords.set(dbUser.id, password); // Cache provided password
      }
    } catch (e) {
      console.warn('Supabase auth lookup error:', e);
    }
  }

  if (!profile) {
    res.status(401).json({ success: false, error: { message: 'Invalid email or password' } });
    return;
  }

  const storedPassword = store.passwords.get(profile.id);
  if (storedPassword && storedPassword !== password) {
    store.recordActivity(profile.id, 'LOGIN_FAILURE', 'failure');
    res.status(401).json({ success: false, error: { message: 'Invalid email or password' } });
    return;
  }

  const token = `demo-user-${profile.id}`;
  store.activeTokens.add(token);

  profile.lastLoginAt = new Date().toISOString();
  store.recordActivity(profile.id, 'LOGIN_SUCCESS', 'success');
  store.updatePresence(profile.id, 'android');

  res.status(200).json({
    success: true,
    data: {
      user: profile,
      token
    }
  });
});

// Logout endpoint for session termination
authRouter.post('/logout', authenticateUser, (req: AuthenticatedRequest, res) => {
  const authHeader = req.headers.authorization;
  if (authHeader && authHeader.startsWith('Bearer ')) {
    const token = authHeader.split(' ')[1];
    store.activeTokens.delete(token);
  }

  if (req.user?.id) {
    store.recordActivity(req.user.id, 'LOGOUT', 'success');
  }

  res.status(200).json({
    success: true,
    message: 'Logged out successfully'
  });
});

// User profile retrieval
authRouter.get('/profile', authenticateUser, (req: AuthenticatedRequest, res) => {
  const user = store.profiles.get(req.user!.id);
  if (!user) {
    res.status(404).json({ success: false, error: { message: 'User profile not found' } });
    return;
  }
  res.status(200).json({ success: true, data: user });
});

// Request Password Reset OTP
authRouter.post('/forgot-password', (req, res) => {
  const { email } = req.body;
  if (!email || typeof email !== 'string' || !email.includes('@')) {
    res.status(400).json({ success: false, error: { message: 'A valid email address is required' } });
    return;
  }

  const normalizedEmail = email.trim().toLowerCase();
  const profile = Array.from(store.profiles.values()).find(
    p => p.email.toLowerCase() === normalizedEmail
  );

  // Security guardrail: Strictly prohibit fixed OTP in real production environments
  const resetMode = process.env.PASSWORD_RESET_MODE || 'development_fixed_otp';
  const isProduction = process.env.NODE_ENV === 'production';

  if (isProduction && resetMode === 'development_fixed_otp') {
    res.status(403).json({
      success: false,
      error: { message: 'Development fixed OTP is strictly disabled in production environment.' }
    });
    return;
  }

  if (!profile) {
    // Return standard message to prevent account enumeration
    res.status(200).json({
      success: true,
      message: 'If an account exists with this email, a verification code has been dispatched.'
    });
    return;
  }

  const resetId = `reset-${Date.now()}`;
  const fixedOtp = '1234';
  const otpHash = crypto.createHash('sha256').update(fixedOtp).digest('hex');
  const expiresAt = new Date(Date.now() + 5 * 60 * 1000).toISOString(); // 5 minutes validity

  const resetRecord: PasswordReset = {
    id: resetId,
    email: normalizedEmail,
    otpHash,
    expiresAt,
    isUsed: false,
    attempts: 0,
    createdAt: new Date().toISOString()
  };

  store.passwordResets.set(resetId, resetRecord);
  store.recordActivity(profile.id, 'PASSWORD_RESET_REQUESTED', 'success');

  res.status(200).json({
    success: true,
    message: 'If an account exists with this email, a verification code has been dispatched.',
    data: {
      email: normalizedEmail,
      mode: resetMode,
      expiresInSeconds: 300
    }
  });
});

// Verify Password Reset OTP
authRouter.post('/verify-otp', (req, res) => {
  const { email, otp } = req.body;
  if (!email || !otp) {
    res.status(400).json({ success: false, error: { message: 'Email and OTP code are required' } });
    return;
  }

  const normalizedEmail = email.trim().toLowerCase();
  const now = new Date();

  // Find latest active reset record
  const matching = Array.from(store.passwordResets.values())
    .filter(r => r.email === normalizedEmail && !r.isUsed)
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())[0];

  if (!matching || new Date(matching.expiresAt) <= now) {
    res.status(400).json({
      success: false,
      error: { message: 'The reset code has expired or does not exist. Please request a new code.' }
    });
    return;
  }

  if (matching.attempts >= 3) {
    res.status(429).json({
      success: false,
      error: { message: 'Maximum verification attempts exceeded. Please request a new code.' }
    });
    return;
  }

  matching.attempts += 1;

  const providedHash = crypto.createHash('sha256').update(String(otp).trim()).digest('hex');
  if (providedHash !== matching.otpHash) {
    res.status(400).json({
      success: false,
      error: { message: 'Invalid verification code. Please check and try again.' }
    });
    return;
  }

  // Issue single-use reset token
  const resetToken = crypto.randomBytes(32).toString('hex');
  matching.resetToken = resetToken;

  res.status(200).json({
    success: true,
    message: 'Code verified successfully',
    data: { resetToken }
  });
});

// Reset Password with single-use Token
authRouter.post('/reset-password', (req, res) => {
  const { resetToken, newPassword, confirmPassword } = req.body;

  if (!resetToken) {
    res.status(400).json({ success: false, error: { message: 'Reset token is required' } });
    return;
  }

  if (!newPassword || typeof newPassword !== 'string' || newPassword.length < 6) {
    res.status(400).json({ success: false, error: { message: 'Password must be at least 6 characters' } });
    return;
  }

  if (newPassword !== confirmPassword) {
    res.status(400).json({ success: false, error: { message: 'Passwords do not match' } });
    return;
  }

  const now = new Date();
  const matching = Array.from(store.passwordResets.values()).find(
    r => r.resetToken === resetToken && !r.isUsed && new Date(r.expiresAt) > now
  );

  if (!matching) {
    res.status(400).json({
      success: false,
      error: { message: 'Invalid or expired reset token. Please restart password recovery.' }
    });
    return;
  }

  const profile = Array.from(store.profiles.values()).find(
    p => p.email.toLowerCase() === matching.email.toLowerCase()
  );

  if (!profile) {
    res.status(404).json({ success: false, error: { message: 'Associated user account not found' } });
    return;
  }

  // Invalidate reset token permanently
  matching.isUsed = true;
  matching.resetToken = undefined;

  // Update password
  store.passwords.set(profile.id, newPassword);
  store.recordActivity(profile.id, 'PASSWORD_RESET_SUCCEEDED', 'success');

  res.status(200).json({
    success: true,
    message: 'Password has been updated successfully. Please sign in with your new password.'
  });
});
