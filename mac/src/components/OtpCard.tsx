import React, { useState, useEffect } from 'react';
import { ShieldCheck, Copy, Check, Clock, Trash2, Smartphone } from 'lucide-react';
import type { NotificationItem } from '../types';

interface OtpCardProps {
  item: NotificationItem;
  onCopy: (code: string, id: string) => void;
  onDismiss: (id: string) => void;
  hideCodeByDefault?: boolean;
}

export const OtpCard: React.FC<OtpCardProps> = ({
  item,
  onCopy,
  onDismiss,
  hideCodeByDefault = false,
}) => {
  const [copied, setCopied] = useState(false);
  const [revealed, setRevealed] = useState(!hideCodeByDefault);
  const [timeLeft, setTimeLeft] = useState(() => {
    if (!item.otp) return 0;
    const elapsed = Math.floor((Date.now() - item.receivedAt) / 1000);
    return Math.max(0, item.otp.expiresInSeconds - elapsed);
  });

  useEffect(() => {
    const timer = setInterval(() => {
      if (!item.otp) return;
      const elapsed = Math.floor((Date.now() - item.receivedAt) / 1000);
      const remaining = Math.max(0, item.otp.expiresInSeconds - elapsed);
      setTimeLeft(remaining);
      if (remaining <= 0) {
        clearInterval(timer);
        onDismiss(item.id);
      }
    }, 1000);
    return () => clearInterval(timer);
  }, [item, onDismiss]);

  const handleCopy = () => {
    if (!item.otp) return;
    onCopy(item.otp.code, item.id);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  if (!item.otp) {
    return (
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-4 shadow-md backdrop-blur-md">
        <div className="flex items-center justify-between mb-2">
          <div className="flex items-center space-x-2">
            <div className="w-7 h-7 rounded-lg bg-slate-800 text-slate-300 flex items-center justify-center font-bold text-xs">
              <Smartphone className="w-3.5 h-3.5" />
            </div>
            <div>
              <h4 className="text-xs font-semibold text-slate-200">{item.appName}</h4>
              <p className="text-[10px] text-slate-400">{item.title}</p>
            </div>
          </div>
          <button
            onClick={() => onDismiss(item.id)}
            className="p-1.5 text-slate-400 hover:text-slate-200 rounded-lg hover:bg-slate-800 transition-colors"
            title="Dismiss"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        </div>
        <p className="text-xs text-slate-300 line-clamp-3">{item.text}</p>
      </div>
    );
  }

  if (timeLeft <= 0) return null;

  return (
    <div className="bg-slate-900 border border-indigo-500/30 rounded-xl p-5 shadow-xl relative overflow-hidden backdrop-blur-md">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center space-x-2">
          <div className="w-8 h-8 rounded-lg bg-indigo-500/20 text-indigo-400 flex items-center justify-center font-bold text-sm">
            <Smartphone className="w-4 h-4" />
          </div>
          <div>
            <h4 className="text-sm font-semibold text-slate-100">{item.appName}</h4>
            <p className="text-xs text-slate-400">{item.packageName}</p>
          </div>
        </div>
        <div className="flex items-center space-x-1.5 text-xs font-mono text-amber-400 bg-amber-400/10 px-2.5 py-1 rounded-full border border-amber-400/20">
          <Clock className="w-3.5 h-3.5" />
          <span>{timeLeft}s</span>
        </div>
      </div>

      <p className="text-xs text-slate-300 mb-4 line-clamp-2">{item.text}</p>

      <div className="bg-slate-950 rounded-lg p-3 border border-slate-800 flex items-center justify-between">
        <div className="flex items-center space-x-3">
          <ShieldCheck className="w-5 h-5 text-emerald-400" />
          <span className="font-mono text-2xl font-bold tracking-widest text-emerald-400 select-all">
            {revealed ? item.otp.code : '••••••'}
          </span>
          {!revealed && (
            <button
              onClick={() => setRevealed(true)}
              className="text-xs text-indigo-400 hover:text-indigo-300 underline ml-2"
            >
              Reveal
            </button>
          )}
        </div>
        <div className="flex items-center space-x-2">
          <button
            onClick={handleCopy}
            className="flex items-center space-x-1.5 bg-emerald-500 hover:bg-emerald-600 text-slate-950 font-medium text-xs px-3.5 py-2 rounded-lg transition-colors shadow-sm"
          >
            {copied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
            <span>{copied ? 'Copied!' : 'Copy Code'}</span>
          </button>
          <button
            onClick={() => onDismiss(item.id)}
            className="p-2 text-slate-400 hover:text-slate-200 rounded-lg hover:bg-slate-800 transition-colors"
            title="Dismiss"
          >
            <Trash2 className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
};
