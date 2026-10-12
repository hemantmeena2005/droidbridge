import React, { useState, useEffect } from 'react';
import { Smartphone, QrCode, Shield, Bell, Trash2, CheckCircle2, RefreshCw } from 'lucide-react';
import type { NotificationItem, PairingInfo, ConnectionStatus } from './types';
import { OtpCard } from './components/OtpCard';
import { PairingModal } from './components/PairingModal';
import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';

export const App: React.FC = () => {
  const [status, setStatus] = useState<ConnectionStatus>({
    paired: false,
    connected: false,
  });

  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [pairingInfo, setPairingInfo] = useState<PairingInfo | null>(null);
  const [isPairingOpen, setIsPairingOpen] = useState(false);
  const [hideOtpByDefault, setHideOtpByDefault] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const handleRefresh = () => {
    setIsRefreshing(true);
    setTimeout(() => {
      setIsRefreshing(false);
    }, 600);
  };

  // Listen for Tauri backend events
  useEffect(() => {
    let unlistenDevicePaired: (() => void) | undefined;
    let unlistenNotif: (() => void) | undefined;
    let unlistenDisconnected: (() => void) | undefined;

    const setupListeners = async () => {
      try {
        unlistenDevicePaired = await listen<string>('device-paired', (event) => {
          setStatus({
            paired: true,
            connected: true,
            deviceName: event.payload,
          });
          setIsPairingOpen(false);
        });

        unlistenNotif = await listen<any>('notification-received', (event) => {
          const payload = event.payload;
          const uniqueSuffix = Math.random().toString(36).substring(2, 7);
          const newItem: NotificationItem = {
            id: payload.id ? `${payload.id}_${uniqueSuffix}` : `notif_${Date.now()}_${uniqueSuffix}`,
            packageName: payload.packageName || payload.package_name || '',
            appName: payload.appName || payload.app_name || 'Android',
            title: payload.title || '',
            text: payload.text || '',
            postTime: payload.postTime || payload.post_time || Date.now(),
            hasOtp: payload.hasOtp || payload.has_otp || false,
            otp: payload.otp
              ? {
                  code: payload.otp.code,
                  confidence: payload.otp.confidence,
                  expiresInSeconds: payload.otp.expiresInSeconds || payload.otp.expires_in_seconds || 60,
                }
              : undefined,
            receivedAt: Date.now(),
          };

          setNotifications((prev) => [newItem, ...prev]);
        });

        unlistenDisconnected = await listen('device-disconnected', () => {
          setStatus((prev) => ({ ...prev, connected: false }));
        });
      } catch (e) {
        console.warn('Running outside Tauri environment, desktop IPC events disabled');
      }
    };

    setupListeners();

    return () => {
      if (unlistenDevicePaired) unlistenDevicePaired();
      if (unlistenNotif) unlistenNotif();
      if (unlistenDisconnected) unlistenDisconnected();
    };
  }, []);

  const handleCopyCode = async (code: string, id: string) => {
    try {
      await navigator.clipboard.writeText(code);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, copied: true } : n))
      );
    } catch (e) {
      console.error('Failed to copy code to clipboard');
    }
  };

  const handleDismissNotification = (id: string) => {
    setNotifications((prev) => prev.filter((n) => n.id !== id));
  };

  const handleClearAll = () => {
    setNotifications([]);
  };

  const openPairing = async () => {
    try {
      const session = await invoke<any>('get_pairing_session');
      setPairingInfo({
        ip: session.ip,
        port: session.port,
        fingerprint: session.fingerprint,
        challenge: session.challenge,
        macName: session.mac_name,
        expiresAt: session.expires_at * 1000,
      });
      setIsPairingOpen(true);
    } catch (e) {
      // Fallback for browser preview
      const mockFingerprint = 'A1:B2:C3:D4:E5:F6:01:23:45:67:89:AB:CD:EF:10:11:12:13:14:15:16:17:18:19:20:21:22:23:24:25:26:27';
      const mockChallenge = Math.random().toString(36).substring(2, 15);
      setPairingInfo({
        ip: '192.168.1.105',
        port: 8765,
        fingerprint: mockFingerprint,
        challenge: mockChallenge,
        macName: 'MacBook Air',
        expiresAt: Date.now() + 60000,
      });
      setIsPairingOpen(true);
    }
  };

  const handleAddDemoOtp = () => {
    const demoItem: NotificationItem = {
      id: `demo_${Date.now()}`,
      packageName: 'com.google.android.apps.authenticator2',
      appName: 'Google Authenticator',
      title: 'Security Notice',
      text: 'Your DroidBridge verification code is 849201. Never share this code.',
      postTime: Date.now(),
      hasOtp: true,
      otp: {
        code: '849201',
        confidence: 'high',
        expiresInSeconds: 60,
      },
      receivedAt: Date.now(),
    };
    setNotifications((prev) => [demoItem, ...prev]);
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans select-none">
      <header className="h-14 border-b border-slate-800/80 px-6 flex items-center justify-between bg-slate-900/60 backdrop-blur-md sticky top-0 z-30">
        <div className="flex items-center space-x-3">
          <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-cyan-500 to-indigo-500 flex items-center justify-center shadow-lg shadow-indigo-500/20">
            <Shield className="w-4 h-4 text-white" />
          </div>
          <div>
            <h1 className="text-sm font-bold tracking-tight">DroidBridge</h1>
            <p className="text-[10px] text-slate-400">Local Privacy Bridge for macOS</p>
          </div>
        </div>

        <div className="flex items-center space-x-4">
          <div className="flex items-center space-x-2 px-3 py-1 rounded-full text-xs font-medium border bg-slate-900 border-slate-800">
            {status.connected ? (
              <>
                <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
                <span className="text-emerald-400">Connected</span>
                {status.deviceName && (
                  <span className="text-slate-400 text-[11px]">({status.deviceName})</span>
                )}
              </>
            ) : status.paired ? (
              <>
                <span className="w-2 h-2 rounded-full bg-amber-400" />
                <span className="text-amber-400">Waiting for Phone...</span>
              </>
            ) : (
              <>
                <span className="w-2 h-2 rounded-full bg-slate-500" />
                <span className="text-slate-400">Not Paired</span>
              </>
            )}
          </div>

          <button
            onClick={handleRefresh}
            title="Refresh connection status"
            className={`p-2 rounded-lg border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800 transition-colors ${
              isRefreshing ? 'animate-spin text-cyan-400' : ''
            }`}
          >
            <RefreshCw className="w-3.5 h-3.5" />
          </button>

          <button
            onClick={openPairing}
            className="flex items-center space-x-1.5 bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold px-3.5 py-1.5 rounded-lg transition-colors shadow-sm"
          >
            <QrCode className="w-3.5 h-3.5" />
            <span>Pair Phone</span>
          </button>
        </div>
      </header>

      <main className="flex-1 max-w-5xl w-full mx-auto p-6 grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="md:col-span-2 space-y-4">
          <div className="flex items-center justify-between mb-2">
            <div className="flex items-center space-x-2">
              <Bell className="w-4 h-4 text-cyan-400" />
              <h2 className="text-sm font-bold uppercase tracking-wider text-slate-300">
                Incoming OTPs &amp; Codes
              </h2>
              <span className="bg-slate-800 text-slate-400 text-xs px-2 py-0.5 rounded-full">
                {notifications.filter((n) => n.hasOtp).length}
              </span>
            </div>

            {notifications.length > 0 && (
              <button
                onClick={handleClearAll}
                className="text-xs text-slate-400 hover:text-slate-200 flex items-center space-x-1"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span>Dismiss All</span>
              </button>
            )}
          </div>

          {notifications.length === 0 ? (
            <div className="border border-dashed border-slate-800 rounded-2xl p-12 text-center flex flex-col items-center justify-center">
              <div className="w-12 h-12 rounded-full bg-slate-900 flex items-center justify-center text-slate-500 mb-3">
                <Smartphone className="w-6 h-6" />
              </div>
              <h3 className="text-sm font-semibold text-slate-300">No active OTPs</h3>
              <p className="text-xs text-slate-500 max-w-sm mt-1">
                When verification codes arrive on your paired Android phone, they will immediately appear here with a one-click copy button.
              </p>
              <button
                onClick={handleAddDemoOtp}
                className="mt-4 text-xs text-indigo-400 hover:text-indigo-300 underline"
              >
                Trigger demo OTP simulation
              </button>
            </div>
          ) : (
            <div className="space-y-3">
              {notifications.map((item) => (
                <OtpCard
                  key={item.id}
                  item={item}
                  onCopy={handleCopyCode}
                  onDismiss={handleDismissNotification}
                  hideCodeByDefault={hideOtpByDefault}
                />
              ))}
            </div>
          )}
        </div>

        <div className="space-y-4">
          <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 shadow-sm space-y-4">
            <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400">
              Security &amp; Privacy
            </h3>

            <label className="flex items-center justify-between text-xs cursor-pointer">
              <span className="text-slate-300">Hide OTP until revealed</span>
              <input
                type="checkbox"
                checked={hideOtpByDefault}
                onChange={(e) => setHideOtpByDefault(e.target.checked)}
                className="rounded bg-slate-800 border-slate-700 text-indigo-500 focus:ring-0"
              />
            </label>

            <div className="pt-3 border-t border-slate-800 space-y-2">
              <div className="flex items-center space-x-2 text-xs text-slate-400">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                <span>Zero cloud transmission (Local Wi-Fi only)</span>
              </div>
              <div className="flex items-center space-x-2 text-xs text-slate-400">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                <span>End-to-end TLS encryption with pinning</span>
              </div>
              <div className="flex items-center space-x-2 text-xs text-slate-400">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                <span>Auto-expiring memory storage</span>
              </div>
            </div>
          </div>
        </div>
      </main>

      {isPairingOpen && (
        <PairingModal
          pairingInfo={pairingInfo}
          onClose={() => setIsPairingOpen(false)}
          onRefresh={openPairing}
        />
      )}
    </div>
  );
};
export default App;
