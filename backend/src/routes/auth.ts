import { Router } from 'express';
import { authenticateUser, AuthenticatedRequest } from '../middleware/auth';
import { store, Profile } from '../repositories/store';

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
authRouter.post('/login', (req, res) => {
  const { email, password } = req.body;
  if (!email || !password) {
    res.status(400).json({ success: false, error: { message: 'Email and password required' } });
    return;
  }

  const normalizedEmail = email.trim().toLowerCase();
  const profile = Array.from(store.profiles.values()).find(
    p => p.email.toLowerCase() === normalizedEmail
  );

  if (!profile) {
    res.status(401).json({ success: false, error: { message: 'Invalid email or password' } });
    return;
  }

  const storedPassword = store.passwords.get(profile.id);
  if (storedPassword && storedPassword !== password) {
    res.status(401).json({ success: false, error: { message: 'Invalid email or password' } });
    return;
  }

  const token = `demo-user-${profile.id}`;
  store.activeTokens.add(token);

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
