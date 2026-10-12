use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BaseMessage {
    pub version: u32,
    #[serde(rename = "type")]
    pub msg_type: String,
    pub timestamp: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HelloMessage {
    pub version: u32,
    #[serde(rename = "type")]
    pub msg_type: String,
    pub timestamp: u64,
    #[serde(rename = "deviceId")]
    pub device_id: String,
    #[serde(rename = "deviceName")]
    pub device_name: String,
    #[serde(rename = "authProof")]
    pub auth_proof: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PairRequestMessage {
    pub version: u32,
    #[serde(rename = "type")]
    pub msg_type: String,
    pub timestamp: u64,
    #[serde(rename = "deviceId")]
    pub device_id: String,
    #[serde(rename = "deviceName")]
    pub device_name: String,
    #[serde(rename = "challengeResponse")]
    pub challenge_response: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PairConfirmMessage {
    pub version: u32,
    #[serde(rename = "type")]
    pub msg_type: String,
    pub timestamp: u64,
    #[serde(rename = "macDeviceId")]
    pub mac_device_id: String,
    #[serde(rename = "macDeviceName")]
    pub mac_device_name: String,
    #[serde(rename = "authToken")]
    pub auth_token: Option<String>,
    pub success: bool,
    #[serde(rename = "errorReason")]
    pub error_reason: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct OtpInfo {
    pub code: String,
    pub confidence: String,
    #[serde(rename = "expiresInSeconds")]
    pub expires_in_seconds: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NotificationPayload {
    pub version: u32,
    #[serde(rename = "type")]
    pub msg_type: String,
    pub timestamp: u64,
    pub id: String,
    #[serde(rename = "packageName")]
    pub package_name: String,
    #[serde(rename = "appName")]
    pub app_name: String,
    pub title: String,
    pub text: String,
    #[serde(rename = "postTime")]
    pub post_time: u64,
    #[serde(rename = "hasOtp")]
    pub has_otp: bool,
    pub otp: Option<OtpInfo>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PairingSessionInfo {
    pub ip: String,
    pub port: u16,
    pub fingerprint: String,
    pub challenge: String,
    pub mac_name: String,
    pub expires_at: u64,
}
