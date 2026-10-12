use std::sync::Arc;
use tokio::sync::Mutex;
use tauri::State;
use droidbridge_lib::server::{start_bridge_server, ServerState, SharedState};
use droidbridge_lib::protocol::PairingSessionInfo;

#[tauri::command]
async fn get_pairing_session(state: State<'_, SharedState>) -> Result<PairingSessionInfo, String> {
    droidbridge_lib::get_pairing_session_internal(state).await
}

#[tauri::command]
async fn unpair_device(state: State<'_, SharedState>) -> Result<(), String> {
    droidbridge_lib::unpair_device_internal(state).await
}

fn main() {
    let initial_state: SharedState = Arc::new(Mutex::new(ServerState {
        current_challenge: None,
        challenge_expiry: 0,
        paired_device_id: None,
        auth_token: None,
        fingerprint: String::new(),
        port: 8765,
        ip: "127.0.0.1".to_string(),
    }));

    let state_for_server = initial_state.clone();

    tauri::Builder::default()
        .manage(initial_state)
        .invoke_handler(tauri::generate_handler![
            get_pairing_session,
            unpair_device
        ])
        .setup(move |app| {
            let handle = app.handle().clone();
            tauri::async_runtime::spawn(async move {
                if let Err(e) = start_bridge_server(handle, state_for_server).await {
                    eprintln!("Failed to start bridge server: {}", e);
                }
            });
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
