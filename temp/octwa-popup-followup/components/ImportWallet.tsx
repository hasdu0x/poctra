import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Key, FileText, Loader2, AlertCircle, Eye, EyeOff } from 'lucide-react';
import { Wallet } from '../types/wallet';
import { importWalletFromEvmPrivateKey, importWalletFromPrivateKey, importWalletFromMnemonic } from '../utils/wallet';

interface ImportWalletProps {
  onWalletImported: (wallet: Wallet) => void;
  defaultTab?: 'private-key' | 'mnemonic' | 'evm-private-key';
  isCompact?: boolean;
  hideBorder?: boolean;
}

export function ImportWallet({ onWalletImported, defaultTab = 'private-key', isCompact = false, hideBorder = false }: ImportWalletProps) {
  const [privateKey, setPrivateKey] = useState('');
  const [evmPrivateKey, setEvmPrivateKey] = useState('');
  const [mnemonic, setMnemonic] = useState('');
  const [isImporting, setIsImporting] = useState(false);
  const [privateKeyError, setPrivateKeyError] = useState('');
  const [evmPrivateKeyError, setEvmPrivateKeyError] = useState('');
  const [mnemonicError, setMnemonicError] = useState('');
  const [showMnemonic, setShowMnemonic] = useState(false);
  const [showPrivateKey, setShowPrivateKey] = useState(false);
  const [showEvmPrivateKey, setShowEvmPrivateKey] = useState(false);

  const walletAlreadyExists = (wallet: Wallet) => {
    const existingWallets = JSON.parse(localStorage.getItem('wallets') || '[]') as Wallet[];
    return existingWallets.some((existing) => existing.address === wallet.address);
  };

  const handleImportFromEvmPrivateKey = async () => {
    setEvmPrivateKeyError('');
    if (!evmPrivateKey.trim()) {
      setEvmPrivateKeyError('EVM private key required');
      return;
    }

    setIsImporting(true);
    try {
      const wallet = await importWalletFromEvmPrivateKey(evmPrivateKey);
      if (walletAlreadyExists(wallet)) {
        setEvmPrivateKeyError('This wallet is already in your collection');
        return;
      }
      onWalletImported(wallet);
    } catch (error: unknown) {
      setEvmPrivateKeyError(error instanceof Error ? error.message : 'Failed to import EVM private key');
    } finally {
      setIsImporting(false);
    }
  };

  const handleImportFromPrivateKey = async () => {
    setPrivateKeyError('');
    if (!privateKey.trim()) {
      setPrivateKeyError('Private key required');
      return;
    }

    setIsImporting(true);
    try {
      const wallet = await importWalletFromPrivateKey(privateKey.trim());
      if (walletAlreadyExists(wallet)) {
        setPrivateKeyError('This wallet is already in your collection');
        return;
      }
      onWalletImported(wallet);
    } catch (error: unknown) {
      let errorMessage = "Failed to import wallet";
      if (error instanceof Error) {
        const msg = error.message.toLowerCase();
        if (msg.includes('invalid') || msg.includes('decode') || msg.includes('base64')) {
          errorMessage = "Invalid private key format";
        } else if (msg.includes('length') || msg.includes('size')) {
          errorMessage = "Private key has incorrect length";
        } else {
          errorMessage = error.message || errorMessage;
        }
      }
      setPrivateKeyError(errorMessage);
    } finally {
      setIsImporting(false);
    }
  };

  const handleImportFromMnemonic = async () => {
    setMnemonicError('');
    if (!mnemonic.trim()) {
      setMnemonicError('Mnemonic required');
      return;
    }

    setIsImporting(true);
    try {
      const wallet = await importWalletFromMnemonic(mnemonic.trim());
      if (walletAlreadyExists(wallet)) {
        setMnemonicError('This wallet is already in your collection');
        return;
      }
      onWalletImported(wallet);
    } catch (error: unknown) {
      let errorMessage = "Failed to import wallet";
      if (error instanceof Error) {
        const msg = error.message.toLowerCase();
        if (msg.includes('invalid mnemonic') || msg.includes('invalid word')) {
          errorMessage = "Invalid mnemonic phrase";
        } else if (msg.includes('checksum')) {
          errorMessage = "Mnemonic checksum failed";
        } else if (msg.includes('12') || msg.includes('24') || msg.includes('length')) {
          errorMessage = "Mnemonic must be 12 or 24 words";
        } else {
          errorMessage = error.message || errorMessage;
        }
      }
      setMnemonicError(errorMessage);
    } finally {
      setIsImporting(false);
    }
  };

  if (defaultTab === 'evm-private-key') {
    return (
      <div className="poctra-import-form">
        <div>
          <div className="poctra-field-label-row">
            <Label htmlFor="evm-private-key">EVM Private Key</Label>
            {evmPrivateKey && (
              <Button type="button" variant="ghost" size="sm" className="poctra-field-text-action" onClick={() => setShowEvmPrivateKey((visible) => !visible)}>
                {showEvmPrivateKey ? <EyeOff /> : <Eye />}
                {showEvmPrivateKey ? 'Hide' : 'Show'}
              </Button>
            )}
          </div>
          <Input
            id="evm-private-key"
            type={showEvmPrivateKey ? 'text' : 'password'}
            placeholder="0x..."
            value={evmPrivateKey}
            onChange={(event) => { setEvmPrivateKey(event.target.value); setEvmPrivateKeyError(''); }}
            className={`poctra-mobile-input font-mono ${evmPrivateKeyError ? 'is-error' : ''}`}
            autoComplete="off"
            spellCheck={false}
          />
          <p className="poctra-field-help">The secp256k1 key must also derive a valid Octra address.</p>
          {evmPrivateKeyError && <p className="poctra-field-error"><AlertCircle />{evmPrivateKeyError}</p>}
        </div>
        <Button onClick={handleImportFromEvmPrivateKey} disabled={isImporting || !evmPrivateKey.trim()} className="poctra-form-primary">
          {isImporting ? <><Loader2 className="h-3 w-3 mr-1.5 animate-spin" />Importing...</> : 'Import Wallet'}
        </Button>
      </div>
    );
  }

  if (defaultTab === 'mnemonic') {
    // Compact mode for popup
    if (isCompact) {
      return (
        <div className="poctra-import-form">
          <div>
            <div className="poctra-field-label-row">
              <Label htmlFor="mnemonic">Mnemonic Phrase</Label>
              {mnemonic && (
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="poctra-field-text-action"
                  onClick={() => setShowMnemonic(!showMnemonic)}
                >
                  {showMnemonic ? <EyeOff /> : <Eye />}
                  {showMnemonic ? 'Hide' : 'Show'}
                </Button>
              )}
            </div>
            <div className="relative">
              <Textarea
                id="mnemonic"
                placeholder="Enter 12 or 24 words"
                value={mnemonic}
                onChange={(e) => { setMnemonic(e.target.value); setMnemonicError(''); }}
                rows={3}
                className={`poctra-mobile-textarea font-mono ${mnemonicError ? 'is-error' : ''} ${!showMnemonic && mnemonic ? 'text-transparent' : ''}`}
                style={!showMnemonic && mnemonic ? { textShadow: '0 0 8px currentColor' } : undefined}
              />
              {!showMnemonic && mnemonic && (
                <div 
                  className="poctra-secret-cover"
                  onClick={() => setShowMnemonic(true)}
                >
                  <span><Eye /> Tap to reveal
                  </span>
                </div>
              )}
            </div>
            {mnemonicError && (
              <p className="poctra-field-error">
                <AlertCircle />
                {mnemonicError}
              </p>
            )}
          </div>
          <Button 
            onClick={handleImportFromMnemonic}
            disabled={isImporting || !mnemonic.trim()}
            className="poctra-form-primary"
          >
            {isImporting ? <><Loader2 className="h-3 w-3 mr-1.5 animate-spin" />Importing...</> : "Import Wallet"}
          </Button>
        </div>
      );
    }

    return (
      <Card className={`${hideBorder ? 'bg-transparent border-0 shadow-none' : 'bg-card/95 backdrop-blur-sm'}`}>
        {!hideBorder && (
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <FileText className="h-3.5 w-3.5" />
              Import from Mnemonic
            </CardTitle>
          </CardHeader>
        )}
        <CardContent className={`space-y-3 pt-3 ${hideBorder ? 'p-0' : ''}`}>
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label htmlFor="mnemonic">Mnemonic Phrase</Label>
              {mnemonic && (
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="h-7 px-2 text-xs text-muted-foreground hover:text-foreground"
                  onClick={() => setShowMnemonic(!showMnemonic)}
                >
                  {showMnemonic ? <EyeOff className="h-3.5 w-3.5 mr-1" /> : <Eye className="h-3.5 w-3.5 mr-1" />}
                  {showMnemonic ? 'Hide' : 'Show'}
                </Button>
              )}
            </div>
            <div className="relative">
              <Textarea
                id="mnemonic"
                placeholder="Enter your 12 or 24 word mnemonic phrase"
                value={mnemonic}
                onChange={(e) => { setMnemonic(e.target.value); setMnemonicError(''); }}
                rows={3}
                className={`font-mono text-xs ${mnemonicError ? 'border-destructive' : ''} ${!showMnemonic && mnemonic ? 'text-transparent' : ''}`}
                style={!showMnemonic && mnemonic ? { textShadow: '0 0 8px currentColor' } : undefined}
              />
              {!showMnemonic && mnemonic && (
                <div 
                  className="absolute inset-0 flex items-center justify-center bg-muted/50 rounded-md cursor-pointer backdrop-blur-sm"
                  onClick={() => setShowMnemonic(true)}
                >
                  <span className="text-xs text-muted-foreground flex items-center gap-1.5">
                    <Eye className="h-3.5 w-3.5" />
                    Click to reveal
                  </span>
                </div>
              )}
            </div>
            {mnemonicError && (
              <p className="text-xs text-destructive mt-1.5 flex items-center gap-1">
                <AlertCircle className="h-3.5 w-3.5" />
                {mnemonicError}
              </p>
            )}
          </div>
          <Button 
            onClick={handleImportFromMnemonic}
            disabled={isImporting || !mnemonic.trim()}
            className="w-full"
            size="default"
          >
            {isImporting ? <><Loader2 className="h-4 w-4 mr-2 animate-spin" />Importing...</> : "Import Wallet"}
          </Button>
        </CardContent>
      </Card>
    );
  }

  // Compact mode for private key
  if (isCompact) {
    return (
      <div className="poctra-import-form">
        <div>
          <div className="poctra-field-label-row">
            <Label htmlFor="private-key">Private Key</Label>
            {privateKey && (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="poctra-field-text-action"
                onClick={() => setShowPrivateKey(!showPrivateKey)}
              >
                {showPrivateKey ? <EyeOff /> : <Eye />}
                {showPrivateKey ? 'Hide' : 'Show'}
              </Button>
            )}
          </div>
          <Input
            id="private-key"
            type={showPrivateKey ? "text" : "password"}
            placeholder="Enter private key (Base64)"
            value={privateKey}
            onChange={(e) => { setPrivateKey(e.target.value); setPrivateKeyError(''); }}
            className={`poctra-mobile-input font-mono ${privateKeyError ? 'is-error' : ''}`}
          />
          {privateKeyError && (
            <p className="poctra-field-error">
              <AlertCircle />
              {privateKeyError}
            </p>
          )}
        </div>
        <Button 
          onClick={handleImportFromPrivateKey}
          disabled={isImporting || !privateKey.trim()}
          className="poctra-form-primary"
        >
          {isImporting ? <><Loader2 className="h-3 w-3 mr-1.5 animate-spin" />Importing...</> : "Import Wallet"}
        </Button>
      </div>
    );
  }

  return (
    <Card className={`${hideBorder ? 'bg-transparent border-0 shadow-none' : 'bg-card/95 backdrop-blur-sm'}`}>
      {!hideBorder && (
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Key className="h-3.5 w-3.5" />
            Import from Private Key
          </CardTitle>
        </CardHeader>
      )}
      <CardContent className={`space-y-3 pt-3 ${hideBorder ? 'p-0' : ''}`}>
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <Label htmlFor="private-key">Private Key</Label>
            {privateKey && (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="h-7 px-2 text-xs text-muted-foreground hover:text-foreground"
                onClick={() => setShowPrivateKey(!showPrivateKey)}
              >
                {showPrivateKey ? <EyeOff className="h-3.5 w-3.5 mr-1" /> : <Eye className="h-3.5 w-3.5 mr-1" />}
                {showPrivateKey ? 'Hide' : 'Show'}
              </Button>
            )}
          </div>
          <Input
            id="private-key"
            type={showPrivateKey ? "text" : "password"}
            placeholder="Enter your private key (Base64)"
            value={privateKey}
            onChange={(e) => { setPrivateKey(e.target.value); setPrivateKeyError(''); }}
            className={`font-mono text-sm ${privateKeyError ? 'border-destructive' : ''}`}
          />
          {privateKeyError && (
            <p className="text-sm text-destructive mt-1.5 flex items-center gap-1">
              <AlertCircle className="h-4 w-4" />
              {privateKeyError}
            </p>
          )}
        </div>
        <Button 
          onClick={handleImportFromPrivateKey}
          disabled={isImporting || !privateKey.trim()}
          className="w-full"
          size="default"
        >
          {isImporting ? <><Loader2 className="h-4 w-4 mr-2 animate-spin" />Importing...</> : "Import Wallet"}
        </Button>
      </CardContent>
    </Card>
  );
}
