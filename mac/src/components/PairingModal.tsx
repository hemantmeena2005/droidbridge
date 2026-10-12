import React from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { X, Wifi, RefreshCw } from 'lucide-react';
import type { PairingInfo } from '../types';

interface PairingModalProps {
  pairingInfo: PairingInfo | null;
  onClose: () => void;
  onRefresh: () => void;
}

export const PairingModal: React.FC<PairingModalProps> = ({
  pairingInfo,
  onClose,
  onRefresh,
}) => {
  if (!pairingInfo) return null;

  const qrPayload = JSON.stringify({
    v: 1,
    ip: pairingInfo.ip,
    port: pairingInfo.port,
    fingerprint: pairingInfo.fingerprint,
    challenge: pairingInfo.challenge,
    macName: pairingInfo.macName,
    expiresAt: pairingInfo.expiresAt,
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
      <div className="bg-slate-900 border border-slate-700 rounded-2xl max-w-md w-full p-6 shadow-2xl relative">
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-slate-400 hover:text-slate-100 p-1 rounded-lg hover:bg-slate-800"
        >
          <X className="w-5 h-5" />
        </button>

        <div className="text-center mb-5">
          <h3 className="text-lg font-bold text-slate-100">Pair Android Device</h3>
          <p className="text-xs text-slate-400 mt-1">
            Open DroidBridge on your phone and scan this single-use QR code.
          </p>
        </div>

        <div className="flex justify-center p-4 bg-white rounded-xl mx-auto w-fit mb-4 shadow-inner">
          <QRCodeSVG value={qrPayload} size={220} level="M" />
        </div>

        <div className="space-y-2 text-xs text-slate-400 bg-slate-950 p-3.5 rounded-xl border border-slate-800">
          <div className="flex items-center justify-between">
            <span className="flex items-center space-x-1.5">
              <Wifi className="w-3.5 h-3.5 text-cyan-400" />
              <span>Host Address:</span>
            </span>
            <span className="font-mono text-slate-200">
              {pairingInfo.ip}:{pairingInfo.port}
            </span>
          </div>
          <div className="flex items-center justify-between">
            <span>TLS Fingerprint:</span>
            <span className="font-mono text-slate-200 truncate max-w-[180px]">
              {pairingInfo.fingerprint.slice(0, 16)}...
            </span>
          </div>
          <div className="flex items-center justify-between">
            <span>Session:</span>
            <span className="text-emerald-400">Single-use (Expiring)</span>
          </div>
        </div>

        <div className="mt-5 flex items-center justify-between">
          <button
            onClick={onRefresh}
            className="flex items-center space-x-1.5 text-xs text-slate-300 hover:text-white px-3 py-2 rounded-lg hover:bg-slate-800 transition-colors"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            <span>Regenerate Code</span>
          </button>
          <button
            onClick={onClose}
            className="bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs px-4 py-2 rounded-lg font-medium transition-colors"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};
