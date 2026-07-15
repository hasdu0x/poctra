import { lazy, Suspense, useState } from 'react';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog';
import { PageTransition } from './PageTransition';
import { Loader2, Plus, FileText, Key, ArrowLeft, CircleDollarSign, Download } from 'lucide-react';
import { Wallet } from '../types/wallet';

// Lazy-load the heavy wallet generation / import surface. These pull
// bip39 + crypto primitives, which should not block the dashboard render.
const GenerateWallet = lazy(() =>
  import('./GenerateWallet').then((m) => ({ default: m.GenerateWallet })),
);
const ImportWallet = lazy(() =>
  import('./ImportWallet').then((m) => ({ default: m.ImportWallet })),
);

function AddWalletFallback() {
  return (
    <div className="flex items-center justify-center min-h-[200px]">
      <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
    </div>
  );
}

interface AddWalletPopupProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onWalletCreated: (wallet: Wallet) => void;
  isPopupMode?: boolean;
  initialScreen?: 'menu' | 'create' | 'import';
}

type Screen = 'menu' | 'create' | 'import-menu' | 'import-mnemonic' | 'import-privatekey' | 'import-evm-privatekey';

function initialView(value: AddWalletPopupProps['initialScreen']): Screen {
  return value === 'import' ? 'import-menu' : value || 'menu';
}

export function AddWalletPopup({
  open,
  onOpenChange,
  onWalletCreated,
  isPopupMode = false,
  initialScreen = 'menu',
}: AddWalletPopupProps) {
  const [screen, setScreen] = useState<Screen>(() => initialView(initialScreen));
  const [transitionKey, setTransitionKey] = useState(0);

  const handleWalletGenerated = (wallet: Wallet) => {
    onWalletCreated(wallet);
    handleClose();
  };

  const handleBack = () => {
    setTransitionKey((prev) => prev + 1);
    setScreen(screen.startsWith('import-') && screen !== 'import-menu' ? 'import-menu' : 'menu');
  };

  const handleNavigate = (newScreen: Screen) => {
    setTransitionKey((prev) => prev + 1);
    setScreen(newScreen);
  };

  const handleClose = () => {
    setScreen('menu');
    setTransitionKey(0);
    onOpenChange(false);
  };

  const handleOpenChange = (isOpen: boolean) => {
    if (!isOpen) handleClose();
    else onOpenChange(isOpen);
  };

  // Popup mode: smaller and simplified
  if (isPopupMode) {
    return (
      <Dialog open={open} onOpenChange={handleOpenChange}>
        <DialogContent
          className="poctra-add-wallet-sheet"
          aria-describedby={screen === 'menu' ? undefined : undefined}
          hideCloseButton={screen !== 'menu'}
          preventCloseOnOutsideClick
        >
          {screen === 'menu' && (
            <PageTransition key={`menu-${transitionKey}`} variant="fade-slide" duration={200}>
              <DialogHeader className="poctra-sheet-heading">
                <DialogTitle>Add Wallet</DialogTitle>
                <DialogDescription>Choose how this wallet should be created.</DialogDescription>
              </DialogHeader>
              <div className="poctra-sheet-options">
                <Button
                  onClick={() => handleNavigate('create')}
                  className="poctra-sheet-primary"
                >
                  <Plus />
                  Create Wallet
                </Button>
                <Button
                  onClick={() => handleNavigate('import-menu')}
                  variant="ghost"
                  className="poctra-sheet-option"
                >
                  <span><Download /></span><span><strong>Import Wallet</strong><small>Mnemonic, Base64, or EVM key</small></span>
                </Button>
              </div>
            </PageTransition>
          )}

          {screen === 'import-menu' && (
            <PageTransition key={`import-menu-${transitionKey}`} variant="slide-left" duration={200}>
              <div className="poctra-sheet-screen">
                <div className="poctra-sheet-screen-header">
                  <Button variant="ghost" size="icon" onClick={handleBack} className="poctra-mobile-back"><ArrowLeft /></Button>
                  <div><DialogTitle>Import Wallet</DialogTitle><DialogDescription>Choose the recovery material.</DialogDescription></div>
                </div>
                <div className="poctra-sheet-options poctra-sheet-screen-scroll">
                  <Button onClick={() => handleNavigate('import-mnemonic')} variant="ghost" className="poctra-sheet-option"><span><FileText /></span><span><strong>Mnemonic Phrase</strong><small>Use a 12 or 24 word phrase</small></span></Button>
                  <Button onClick={() => handleNavigate('import-privatekey')} variant="ghost" className="poctra-sheet-option"><span><Key /></span><span><strong>Octra Private Key</strong><small>Use a 32-byte Base64 key</small></span></Button>
                  <Button onClick={() => handleNavigate('import-evm-privatekey')} variant="ghost" className="poctra-sheet-option"><span><CircleDollarSign /></span><span><strong>EVM Private Key</strong><small>Use a 0x hexadecimal key</small></span></Button>
                </div>
              </div>
            </PageTransition>
          )}

          {screen === 'create' && (
            <PageTransition key={`create-${transitionKey}`} variant="slide-left" duration={200}>
              <div className="poctra-sheet-screen">
                <div className="poctra-sheet-screen-header">
                  <Button variant="ghost" size="icon" onClick={handleBack} className="poctra-mobile-back">
                    <ArrowLeft />
                  </Button>
                  <DialogTitle>Generate Wallet</DialogTitle>
                </div>
                <div className="poctra-sheet-screen-scroll">
                  <Suspense fallback={<AddWalletFallback />}>
                    <GenerateWallet onWalletGenerated={handleWalletGenerated} isCompact />
                  </Suspense>
                </div>
              </div>
            </PageTransition>
          )}

          {screen === 'import-mnemonic' && (
            <PageTransition key={`mnemonic-${transitionKey}`} variant="slide-left" duration={200}>
              <div className="poctra-sheet-screen">
                <div className="poctra-sheet-screen-header">
                  <Button variant="ghost" size="icon" onClick={handleBack} className="poctra-mobile-back">
                    <ArrowLeft />
                  </Button>
                  <DialogTitle>Import Mnemonic</DialogTitle>
                </div>
                <div className="poctra-sheet-screen-scroll">
                  <Suspense fallback={<AddWalletFallback />}>
                    <ImportWallet onWalletImported={handleWalletGenerated} defaultTab="mnemonic" isCompact />
                  </Suspense>
                </div>
              </div>
            </PageTransition>
          )}

          {screen === 'import-privatekey' && (
            <PageTransition key={`privatekey-${transitionKey}`} variant="slide-left" duration={200}>
              <div className="poctra-sheet-screen">
                <div className="poctra-sheet-screen-header">
                  <Button variant="ghost" size="icon" onClick={handleBack} className="poctra-mobile-back">
                    <ArrowLeft />
                  </Button>
                  <DialogTitle>Import Private Key</DialogTitle>
                </div>
                <div className="poctra-sheet-screen-scroll">
                  <Suspense fallback={<AddWalletFallback />}>
                    <ImportWallet onWalletImported={handleWalletGenerated} defaultTab="private-key" isCompact />
                  </Suspense>
                </div>
              </div>
            </PageTransition>
          )}

          {screen === 'import-evm-privatekey' && (
            <PageTransition key={`evm-privatekey-${transitionKey}`} variant="slide-left" duration={200}>
              <div className="poctra-sheet-screen">
                <div className="poctra-sheet-screen-header">
                  <Button variant="ghost" size="icon" onClick={handleBack} className="poctra-mobile-back"><ArrowLeft /></Button>
                  <DialogTitle>Import EVM Key</DialogTitle>
                </div>
                <div className="poctra-sheet-screen-scroll">
                  <Suspense fallback={<AddWalletFallback />}>
                    <ImportWallet onWalletImported={handleWalletGenerated} defaultTab="evm-private-key" isCompact />
                  </Suspense>
                </div>
              </div>
            </PageTransition>
          )}
        </DialogContent>
      </Dialog>
    );
  }

  // Expanded mode: larger dialog with centered text
  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent
        className="sm:max-w-lg max-h-[85vh] overflow-hidden p-0"
        hideCloseButton={screen !== 'menu'}
        preventCloseOnOutsideClick
      >
        {screen === 'menu' && (
          <PageTransition key={`menu-${transitionKey}`} variant="fade-slide" duration={250}>
            <DialogHeader className="p-6 pb-4">
              <DialogTitle className="text-xl">Add New Wallet</DialogTitle>
              <DialogDescription>Choose how you want to add a wallet</DialogDescription>
            </DialogHeader>
            <div className="p-6 pt-2 space-y-3">
              <Button
                onClick={() => handleNavigate('create')}
                className="w-full h-12 text-base bg-[#3B567F] hover:bg-[#6C63FF]/90"
                size="lg"
              >
                <Plus className="h-5 w-5 mr-2" />
                Generate new wallet
              </Button>
              <Button
                onClick={() => handleNavigate('import-menu')}
                variant="outline"
                className="w-full h-12 text-base"
                size="lg"
              >
                <Download className="h-5 w-5 mr-2" />
                Import Wallet
              </Button>
            </div>
          </PageTransition>
        )}

        {screen === 'import-menu' && (
          <PageTransition key={`expanded-import-menu-${transitionKey}`} variant="slide-left" duration={250}>
            <div className="flex flex-col max-h-[85vh]">
              <div className="p-4 flex items-center gap-2"><Button variant="ghost" size="sm" onClick={handleBack} className="h-8 w-8 p-0"><ArrowLeft className="h-4 w-4" /></Button><DialogTitle className="text-lg">Import Wallet</DialogTitle></div>
              <div className="p-4 pt-0 space-y-3">
                <Button onClick={() => handleNavigate('import-mnemonic')} variant="outline" className="w-full h-12 justify-start"><FileText className="h-4 w-4 mr-2" />Mnemonic Phrase</Button>
                <Button onClick={() => handleNavigate('import-privatekey')} variant="outline" className="w-full h-12 justify-start"><Key className="h-4 w-4 mr-2" />Octra Private Key</Button>
                <Button onClick={() => handleNavigate('import-evm-privatekey')} variant="outline" className="w-full h-12 justify-start"><CircleDollarSign className="h-4 w-4 mr-2" />EVM Private Key</Button>
              </div>
            </div>
          </PageTransition>
        )}

        {screen === 'create' && (
          <PageTransition key={`create-${transitionKey}`} variant="slide-left" duration={250}>
            <div className="flex flex-col h-full max-h-[85vh]">
              <div className="p-4 flex items-center gap-2">
                <Button variant="ghost" size="sm" onClick={handleBack} className="h-8 w-8 p-0">
                  <ArrowLeft className="h-4 w-4" />
                </Button>
                <DialogTitle className="text-lg">Generate Wallet</DialogTitle>
              </div>
              <div className="flex-1 overflow-y-auto p-4">
                <Suspense fallback={<AddWalletFallback />}>
                  <GenerateWallet onWalletGenerated={handleWalletGenerated} hideBorder />
                </Suspense>
              </div>
            </div>
          </PageTransition>
        )}

        {screen === 'import-mnemonic' && (
          <PageTransition key={`mnemonic-${transitionKey}`} variant="slide-left" duration={250}>
            <div className="flex flex-col h-full max-h-[85vh]">
              <div className="p-4 flex items-center gap-2">
                <Button variant="ghost" size="sm" onClick={handleBack} className="h-8 w-8 p-0">
                  <ArrowLeft className="h-4 w-4" />
                </Button>
                <DialogTitle className="text-lg">Import from Mnemonic</DialogTitle>
              </div>
              <div className="flex-1 overflow-y-auto p-4">
                <Suspense fallback={<AddWalletFallback />}>
                  <ImportWallet onWalletImported={handleWalletGenerated} defaultTab="mnemonic" hideBorder />
                </Suspense>
              </div>
            </div>
          </PageTransition>
        )}

        {screen === 'import-privatekey' && (
          <PageTransition key={`privatekey-${transitionKey}`} variant="slide-left" duration={250}>
            <div className="flex flex-col h-full max-h-[85vh]">
              <div className="p-4 flex items-center gap-2">
                <Button variant="ghost" size="sm" onClick={handleBack} className="h-8 w-8 p-0">
                  <ArrowLeft className="h-4 w-4" />
                </Button>
                <DialogTitle className="text-lg">Import from Private Key</DialogTitle>
              </div>
              <div className="flex-1 overflow-y-auto p-4">
                <Suspense fallback={<AddWalletFallback />}>
                  <ImportWallet onWalletImported={handleWalletGenerated} defaultTab="private-key" hideBorder />
                </Suspense>
              </div>
            </div>
          </PageTransition>
        )}

        {screen === 'import-evm-privatekey' && (
          <PageTransition key={`expanded-evm-privatekey-${transitionKey}`} variant="slide-left" duration={250}>
            <div className="flex flex-col h-full max-h-[85vh]">
              <div className="p-4 flex items-center gap-2"><Button variant="ghost" size="sm" onClick={handleBack} className="h-8 w-8 p-0"><ArrowLeft className="h-4 w-4" /></Button><DialogTitle className="text-lg">Import EVM Private Key</DialogTitle></div>
              <div className="flex-1 overflow-y-auto p-4"><Suspense fallback={<AddWalletFallback />}><ImportWallet onWalletImported={handleWalletGenerated} defaultTab="evm-private-key" /></Suspense></div>
            </div>
          </PageTransition>
        )}
      </DialogContent>
    </Dialog>
  );
}
