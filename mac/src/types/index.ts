export interface OtpData {
  code: string;
  confidence: 'high' | 'medium' | 'low';
  expiresInSeconds: number;
}

export interface NotificationItem {
  id: string;
  packageName: string;
  appName: string;
  title: string;
  text: string;
  postTime: number;
  hasOtp: boolean;
  otp?: OtpData;
  receivedAt: number;
  copied?: boolean;
}

export interface PairingInfo {
  ip: string;
  port: number;
  fingerprint: string;
  challenge: string;
  macName: string;
  expiresAt: number;
}

export interface ConnectionStatus {
  paired: boolean;
  connected: boolean;
  deviceName?: string;
  deviceId?: string;
  ip?: string;
  port?: number;
}
