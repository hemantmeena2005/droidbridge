use hmac::{Hmac, Mac};
use rcgen::{CertificateParams, KeyPair};
use rustls::pki_types::{CertificateDer, PrivateKeyDer};
use sha2::{Digest, Sha256};
use std::sync::Arc;

pub struct TlsIdentity {
    pub server_config: Arc<rustls::ServerConfig>,
    pub sha256_fingerprint: String,
}

pub fn generate_self_signed_tls() -> Result<TlsIdentity, Box<dyn std::error::Error + Send + Sync>> {
    let key_pair = KeyPair::generate()?;
    let mut params = CertificateParams::new(vec!["localhost".to_string(), "127.0.0.1".to_string()])?;
    params.distinguished_name.push(rcgen::DnType::CommonName, "DroidBridge");

    let cert = params.self_signed(&key_pair)?;
    let cert_der = cert.der().to_vec();

    // Compute SHA-256 fingerprint for QR pinning
    let mut hasher = Sha256::new();
    hasher.update(&cert_der);
    let digest = hasher.finalize();
    let fingerprint = digest
        .iter()
        .map(|b| format!("{:02X}", b))
        .collect::<Vec<String>>()
        .join(":");

    let cert_chain = vec![CertificateDer::from(cert_der)];
    let key_der_vec = key_pair.serialized_der().to_vec();
    let key_der = PrivateKeyDer::Pkcs8(key_der_vec.into());

    let server_config = rustls::ServerConfig::builder()
        .with_no_client_auth()
        .with_single_cert(cert_chain, key_der)?;

    Ok(TlsIdentity {
        server_config: Arc::new(server_config),
        sha256_fingerprint: fingerprint,
    })
}

pub fn compute_hmac_sha256(challenge: &str, secret: &str) -> String {
    type HmacSha256 = Hmac<Sha256>;
    let mut mac = HmacSha256::new_from_slice(secret.as_bytes()).expect("HMAC can take key of any size");
    mac.update(challenge.as_bytes());
    let result = mac.finalize();
    hex::encode(result.into_bytes())
}
