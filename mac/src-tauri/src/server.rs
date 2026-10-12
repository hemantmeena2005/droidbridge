use futures_util::{SinkExt, StreamExt};
use local_ip_address::local_ip;
use rand::distributions::Alphanumeric;
use rand::Rng;
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};
use tauri::{AppHandle, Emitter};
use tokio::net::TcpListener;
use tokio::sync::Mutex;
use tokio_rustls::TlsAcceptor;
use tokio_tungstenite::accept_async;

use crate::crypto::{compute_hmac_sha256, generate_self_signed_tls};
use crate::protocol::*;

pub struct ServerState {
    pub current_challenge: Option<String>,
    pub challenge_expiry: u64,
    pub paired_device_id: Option<String>,
    pub auth_token: Option<String>,
    pub fingerprint: String,
    pub port: u16,
    pub ip: String,
}

pub type SharedState = Arc<Mutex<ServerState>>;

pub async fn start_bridge_server(
    app: AppHandle,
    state: SharedState,
) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    let tls = generate_self_signed_tls()?;
    let acceptor = TlsAcceptor::from(tls.server_config);

    let my_ip = local_ip()
        .map(|ip| ip.to_string())
        .unwrap_or_else(|_| "127.0.0.1".to_string());

    let listener = TcpListener::bind("0.0.0.0:8765").await?;
    let port = 8765;

    {
        let mut s = state.lock().await;
        s.fingerprint = tls.sha256_fingerprint.clone();
        s.port = port;
        s.ip = my_ip.clone();
    }

    log::info!("DroidBridge WSS server running on wss://{}:{}/ws", my_ip, port);

    let acceptor_clone = acceptor.clone();
    let app_clone = app.clone();
    let state_clone = state.clone();

    tokio::spawn(async move {
        while let Ok((stream, addr)) = listener.accept().await {
            let acceptor = acceptor_clone.clone();
            let app = app_clone.clone();
            let state = state_clone.clone();

            tokio::spawn(async move {
                let tls_stream = match acceptor.accept(stream).await {
                    Ok(s) => s,
                    Err(e) => {
                        log::warn!("TLS handshake failed from {}: {}", addr, e);
                        return;
                    }
                };

                let ws_stream = match accept_async(tls_stream).await {
                    Ok(ws) => ws,
                    Err(e) => {
                        log::warn!("WebSocket handshake failed: {}", e);
                        return;
                    }
                };

                let (mut ws_sender, mut ws_receiver) = ws_stream.split();

                while let Some(msg) = ws_receiver.next().await {
                    match msg {
                        Ok(tokio_tungstenite::tungstenite::Message::Text(text)) => {
                            if let Ok(base) = serde_json::from_str::<BaseMessage>(&text) {
                                match base.msg_type.as_str() {
                                    "pair_request" => {
                                        if let Ok(req) = serde_json::from_str::<PairRequestMessage>(&text) {
                                            let mut s = state.lock().await;
                                            let now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs();

                                            let is_valid = if let Some(ch) = &s.current_challenge {
                                                if now <= s.challenge_expiry {
                                                    let expected = compute_hmac_sha256(ch, ch);
                                                    expected == req.challenge_response
                                                } else {
                                                    false
                                                }
                                            } else {
                                                false
                                            };

                                            if is_valid {
                                                let auth_token: String = rand::thread_rng()
                                                    .sample_iter(&Alphanumeric)
                                                    .take(32)
                                                    .map(char::from)
                                                    .collect();

                                                s.auth_token = Some(auth_token.clone());
                                                s.paired_device_id = Some(req.device_id.clone());
                                                s.current_challenge = None; // Invalidate single-use challenge

                                                let confirm = PairConfirmMessage {
                                                    version: 1,
                                                    msg_type: "pair_confirm".to_string(),
                                                    timestamp: now,
                                                    mac_device_id: "mac_bridge_1".to_string(),
                                                    mac_device_name: "MacBook Air".to_string(),
                                                    auth_token: Some(auth_token),
                                                    success: true,
                                                    error_reason: None,
                                                };

                                                let _ = ws_sender
                                                    .send(tokio_tungstenite::tungstenite::Message::Text(
                                                        serde_json::to_string(&confirm).unwrap().into(),
                                                    ))
                                                    .await;

                                                let _ = app.emit("device-paired", &req.device_name);
                                            } else {
                                                let confirm = PairConfirmMessage {
                                                    version: 1,
                                                    msg_type: "pair_confirm".to_string(),
                                                    timestamp: now,
                                                    mac_device_id: "mac_bridge_1".to_string(),
                                                    mac_device_name: "MacBook Air".to_string(),
                                                    auth_token: None,
                                                    success: false,
                                                    error_reason: Some("Invalid or expired challenge".to_string()),
                                                };
                                                let _ = ws_sender
                                                    .send(tokio_tungstenite::tungstenite::Message::Text(
                                                        serde_json::to_string(&confirm).unwrap().into(),
                                                    ))
                                                    .await;
                                            }
                                        }
                                    }
                                    "notification" => {
                                        log::info!("Received notification payload from phone: {}", text);
                                        match serde_json::from_str::<NotificationPayload>(&text) {
                                            Ok(notif) => {
                                                let _ = app.emit("notification-received", &notif);
                                            }
                                            Err(e) => {
                                                log::warn!("Failed to deserialize NotificationPayload ({}). Emitting raw JSON value.", e);
                                                if let Ok(raw_val) = serde_json::from_str::<serde_json::Value>(&text) {
                                                    let _ = app.emit("notification-received", &raw_val);
                                                }
                                            }
                                        }
                                    }
                                    "heartbeat" => {
                                        // Pong back
                                        let pong = serde_json::json!({
                                            "version": 1,
                                            "type": "heartbeat",
                                            "timestamp": SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs(),
                                            "ping": false
                                        });
                                        let _ = ws_sender
                                            .send(tokio_tungstenite::tungstenite::Message::Text(
                                                pong.to_string().into(),
                                            ))
                                            .await;
                                    }
                                    _ => {}
                                }
                            }
                        }
                        Ok(tokio_tungstenite::tungstenite::Message::Close(_)) => {
                            let _ = app.emit("device-disconnected", ());
                            break;
                        }
                        Err(_) => break,
                        _ => {}
                    }
                }
            });
        }
    });

    Ok(())
}
