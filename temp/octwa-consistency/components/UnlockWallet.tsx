import React, { useState, useEffect } from 'react';
import { WalletManager } from '../utils/walletManager';
import { Wallet } from '../types/wallet';
import { AlertTriangle, Clock, Eye, EyeOff } from 'lucide-react';
import { ExtensionStorageManager } from '../utils/extensionStorage';
import { resetModeSwitchReminder } from './ModeSwitchConfirmDialog';
import { resetOnboardingState } from './OnboardingOverlay';
import { startMinDuration } from '../utils/minLoading';
import { Button } from '@/components/ui/button';

interface UnlockWalletProps {
  onUnlock: (wallets: Wallet[]) => void;
  isPopupMode?: boolean;
}

export function UnlockWallet({ onUnlock, isPopupMode = false }: UnlockWalletProps) {
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [capsLockOn, setCapsLockOn] = useState(false);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [showResetConfirm, setShowResetConfirm] = useState(false);
  const [resetConfirmText, setResetConfirmText] = useState('');
  const [rateLimitInfo, setRateLimitInfo] = useState<{
    limited: boolean;
    remainingMs?: number;
    remainingAttempts: number;
  } | null>(null);
  const [countdown, setCountdown] = useState(0);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      setCapsLockOn(e.getModifierState('CapsLock'));
    };
    const handleKeyUp = (e: KeyboardEvent) => {
      setCapsLockOn(e.getModifierState('CapsLock'));
    };
    window.addEventListener('keydown', handleKeyDown);
    window.addEventListener('keyup', handleKeyUp);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      window.removeEventListener('keyup', handleKeyUp);
    };
  }, []);

  const checkRateLimit = () => {
    const info = WalletManager.checkRateLimit();
    setRateLimitInfo(info);
    if (info.limited && info.remainingMs) {
      setCountdown(Math.ceil(info.remainingMs / 1000));
    }
  };

  useEffect(() => { checkRateLimit(); }, []);

  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => {
        setCountdown(countdown - 1);
        if (countdown === 1) checkRateLimit();
      }, 1000);
      return () => clearTimeout(timer);
    }
  }, [countdown]);

  const handleUnlock = async (e: React.FormEvent) => {
    e.preventDefault();
    const rateInfo = WalletManager.checkRateLimit();
    setRateLimitInfo(rateInfo);
    if (rateInfo.limited) {
      if (rateInfo.remainingMs) setCountdown(Math.ceil(rateInfo.remainingMs / 1000));
      return;
    }
    if (!password.trim()) {
      setError('Password is required');
      return;
    }
    setIsLoading(true);
    setError('');
    const done = startMinDuration(300);
    try {
      const wallets = await WalletManager.unlockWallets(password);
      await done();
      onUnlock(wallets);
    } catch (err) {
      const message = err instanceof Error ? err.message : '';
      if (message.includes('Too many attempts')) {
        setError(message);
        checkRateLimit();
      } else {
        setError(message === 'Invalid password' ? 'Invalid password' : 'Failed to unlock wallet');
        checkRateLimit();
      }
    } finally {
      setIsLoading(false);
      setPassword('');
    }
  };

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  const isLocked = rateLimitInfo?.limited && countdown > 0;

  const handleForgotPassword = async () => {
    if (resetConfirmText !== 'RESET') return;
    const keys = [
      'wallets', 'encryptedWallets', 'walletPasswordHash', 'walletPasswordSalt',
      'isWalletLocked', 'activeWalletId', 'unlockAttempts', 'lockoutUntil',
      'walletRateLimitState',
    ];
    keys.forEach(k => localStorage.removeItem(k));
    await Promise.all([
      ExtensionStorageManager.remove('wallets'),
      ExtensionStorageManager.remove('encryptedWallets'),
      ExtensionStorageManager.remove('walletPasswordHash'),
      ExtensionStorageManager.remove('walletPasswordSalt'),
      ExtensionStorageManager.remove('isWalletLocked'),
      ExtensionStorageManager.remove('activeWalletId'),
    ]);
    resetModeSwitchReminder();
    resetOnboardingState();
    window.location.reload();
  };

  // ── Reset confirmation screen ──
  if (showResetConfirm) {
    return (
      <div className={isPopupMode ? 'poctra-unlock-screen' : 'flex items-center justify-center min-h-screen bg-background'}>
        <div className={isPopupMode ? 'poctra-unlock-panel' : 'w-full max-w-md p-8 space-y-5'}>

          {/* Danger icon */}
          <div className="flex justify-center">
            <div className={`${isPopupMode ? 'poctra-unlock-danger-icon' : 'p-3'} bg-oc-danger`}>
              <AlertTriangle className={`${isPopupMode ? 'h-5 w-5' : 'h-8 w-8'} text-white`} />
            </div>
          </div>

          {/* Title */}
          <div className="text-center">
            <div
              className="font-bold text-oc-danger"
              style={{ fontSize: isPopupMode ? 'var(--oct-type-size-03)' : '1.25rem' }}
            >
              Reset Wallet
            </div>
            <p
              className="text-muted-foreground mt-2"
              style={{ fontSize: 'var(--oct-type-size-02)' }}
            >
              This permanently deletes local wallet data. Recovery requires your
              mnemonic or private key.
            </p>
          </div>

          {/* Warning box */}
          <div
            className={isPopupMode ? 'poctra-unlock-warning' : 'px-3 py-2 border border-oc-danger'}
            style={{ background: 'var(--oct-color-danger-bg)' }}
          >
            <p
              className="text-oc-danger text-center"
              style={{ fontSize: 'var(--oct-type-size-02)' }}
            >
              Type <span className="font-bold font-mono">RESET</span> to confirm
            </p>
          </div>

          <input
            type="text"
            value={resetConfirmText}
            onChange={(e) => setResetConfirmText(e.target.value.toUpperCase())}
            placeholder="Type RESET"
            className="octra-input text-center font-mono"
            aria-label="Type RESET to confirm wallet reset"
          />

          <div className={isPopupMode ? 'poctra-mobile-footer-actions' : 'flex gap-2'}>
            <Button
              variant="secondary"
              onClick={() => { setShowResetConfirm(false); setResetConfirmText(''); }}
              className="flex-1"
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={handleForgotPassword}
              disabled={resetConfirmText !== 'RESET'}
              className="flex-1"
            >
              Reset Wallet
            </Button>
          </div>
        </div>
      </div>
    );
  }

  // ── Unlock screen ──
  return (
    <div className={isPopupMode ? 'poctra-unlock-screen' : 'flex items-center justify-center min-h-screen bg-background'}>
      <div className={isPopupMode ? 'poctra-unlock-panel' : 'w-full max-w-md p-8 space-y-5'}>

        {/* Logo / locked icon */}
        <div className="flex justify-center">
          {isLocked ? (
            <div className={`${isPopupMode ? 'poctra-unlock-danger-icon' : 'p-3'} bg-oc-danger`}>
              <Clock className={`${isPopupMode ? 'h-5 w-5' : 'h-8 w-8'} text-white`} />
            </div>
          ) : (
            <svg
              viewBox="0 0 50 50"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
              width={isPopupMode ? 48 : 72}
              height={isPopupMode ? 48 : 72}
              role="img"
              aria-label="OctWa Logo"
              className="text-primary"
            >
              <circle cx="25" cy="25" r="21" stroke="currentColor" strokeWidth="8" fill="none" />
              <circle cx="25" cy="25" r="9" fill="currentColor" />
            </svg>
          )}
        </div>

        {/* Title */}
        <div className="text-center">
          <div
            className={isPopupMode ? 'poctra-unlock-title' : 'font-bold text-foreground'}
            style={{ fontSize: isPopupMode ? 'var(--oct-type-size-03)' : '1.1rem' }}
          >
            {isLocked ? 'Temporarily Locked' : 'Unlock Wallet'}
          </div>
          <p
            className="text-muted-foreground mt-1"
            style={{ fontSize: 'var(--oct-type-size-02)' }}
          >
            {isLocked
              ? 'Too many failed attempts. Please wait.'
              : 'Enter your password to access your wallets.'}
          </p>
        </div>

        {/* Lockout timer */}
        {isLocked && (
          <div
            className={isPopupMode ? 'poctra-unlock-lockout' : 'flex flex-col items-center gap-2 px-3 py-4 border border-oc-danger'}
            style={{ background: 'var(--oct-color-danger-bg)' }}
          >
            <AlertTriangle className="h-5 w-5 text-oc-danger" />
            <div className="text-center">
              <p className="text-oc-danger" style={{ fontSize: 'var(--oct-type-size-02)' }}>
                Try again in
              </p>
              <p
                className="font-mono font-bold text-oc-danger"
                style={{ fontSize: isPopupMode ? '1.5rem' : '2rem' }}
              >
                {formatTime(countdown)}
              </p>
            </div>
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleUnlock} className={isPopupMode ? 'poctra-unlock-form' : 'space-y-3'}>
          <div className="relative">
            <input
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter password"
              className={`octra-input pr-10 ${isPopupMode ? 'poctra-mobile-input' : ''}`}
              disabled={isLoading || isLocked}
              aria-label="Wallet password"
            />
            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className={`absolute right-0 top-0 h-full text-muted-foreground hover:text-foreground disabled:opacity-50 ${isPopupMode ? 'poctra-field-icon-button' : 'px-2.5'}`}
              disabled={isLoading || isLocked}
              aria-label={showPassword ? 'Hide password' : 'Show password'}
            >
              {showPassword
                ? <EyeOff className="h-3.5 w-3.5" />
                : <Eye className="h-3.5 w-3.5" />}
            </button>
          </div>

          {/* Caps lock warning */}
          {capsLockOn && !isLocked && (
            <div
              className={isPopupMode ? 'poctra-inline-status is-warning' : 'flex items-center gap-2 px-2 py-1.5 border'}
              style={{
                background: 'var(--oct-color-warning-bg)',
                borderColor: 'hsl(var(--oc-warning))',
                color: 'hsl(var(--oc-warning))',
                fontSize: 'var(--oct-type-size-02)',
              }}
            >
              <AlertTriangle className="h-3.5 w-3.5 flex-shrink-0" />
              <span>Caps Lock is on</span>
            </div>
          )}

          {/* Remaining attempts warning */}
          {!isLocked && rateLimitInfo && rateLimitInfo.remainingAttempts < 5 && rateLimitInfo.remainingAttempts > 0 && (
            <div
              className="flex items-center gap-2"
              style={{ color: 'hsl(var(--oc-warning))', fontSize: 'var(--oct-type-size-02)' }}
            >
              <AlertTriangle className="h-3.5 w-3.5" />
              <span>{rateLimitInfo.remainingAttempts} attempt(s) remaining before lockout</span>
            </div>
          )}

          {/* Error */}
          {error && !isLocked && (
            <div
              className="text-center"
              style={{ color: 'hsl(var(--oc-danger))', fontSize: 'var(--oct-type-size-02)' }}
            >
              {error}
            </div>
          )}

          <Button
            type="submit"
            disabled={isLoading || isLocked}
            className={isPopupMode ? 'poctra-unlock-submit' : 'w-full'}
          >
            {isLoading ? 'Unlocking...' : isLocked ? 'Locked' : 'Unlock'}
          </Button>
        </form>

        {/* Forgot password */}
        <Button
          type="button"
          variant="ghost"
          onClick={() => setShowResetConfirm(true)}
          className={isPopupMode ? 'poctra-unlock-forgot' : 'w-full'}
        >
          Forgot Password?
        </Button>
      </div>
    </div>
  );
}
