pub mod crypto;
pub mod protocol;
pub mod server;

use rand::distributions::Alphanumeric;
use rand::Rng;
use std::time::{SystemTime, UNIX_EPOCH};
use tauri::State;

use crate::protocol::PairingSessionInfo;
use crate::server::SharedState;

pub async fn get_pairing_session_internal(state: State<'_, SharedState>) -> Result<PairingSessionInfo, String> {
    let mut s = state.lock().await;

    let challenge: String = rand::thread_rng()
        .sample_iter(&Alphanumeric)
        .take(16)
        .map(char::from)
        .collect();

    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_secs();

    let expires_at = now + 60;

    s.current_challenge = Some(challenge.clone());
    s.challenge_expiry = expires_at;

    Ok(PairingSessionInfo {
        ip: s.ip.clone(),
        port: s.port,
        fingerprint: s.fingerprint.clone(),
        challenge,
        mac_name: "MacBook Air".to_string(),
        expires_at,
    })
}

pub async fn unpair_device_internal(state: State<'_, SharedState>) -> Result<(), String> {
    let mut s = state.lock().await;
    s.paired_device_id = None;
    s.auth_token = None;
    s.current_challenge = None;
    Ok(())
}
