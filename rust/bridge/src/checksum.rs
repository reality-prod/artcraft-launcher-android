//! SHA-256 helpers: stream a file, compare, and read the `SHA256SUMS.txt` files the apps publish.
use sha2::{Digest, Sha256};
use std::fs::File;
use std::io::Read;

fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}

pub fn sha256_file(path: &str) -> Result<String, String> {
    let mut f = File::open(path).map_err(|e| format!("cannot open file: {e}"))?;
    let mut h = Sha256::new();
    let mut buf = vec![0u8; 64 * 1024];
    loop {
        let n = f.read(&mut buf).map_err(|e| format!("read failed: {e}"))?;
        if n == 0 {
            break;
        }
        match buf.get(..n) {
            Some(chunk) => h.update(chunk),
            None => return Err("read returned an impossible length".to_string()),
        }
    }
    Ok(hex(&h.finalize()))
}

pub fn verify_file(path: &str, expected_hex: &str) -> Result<(), String> {
    let expected = expected_hex.trim();
    if expected.len() != 64 || !expected.bytes().all(|b| b.is_ascii_hexdigit()) {
        return Err("expected checksum is not a SHA-256 hex string".to_string());
    }
    let got = sha256_file(path)?;
    if got.eq_ignore_ascii_case(expected) {
        Ok(())
    } else {
        Err("checksum mismatch".to_string())
    }
}

/// Lines look like `<64 hex>  name` or `<64 hex> *name` (sha256sum text or binary mode).
pub fn find_in_sums(text: &str, file_name: &str) -> Option<String> {
    for line in text.lines() {
        let line = line.trim();
        let Some((digest, rest)) = line.split_once(char::is_whitespace) else {
            continue; // blank or malformed line: keep looking
        };
        if digest.len() != 64 || !digest.bytes().all(|b| b.is_ascii_hexdigit()) {
            continue;
        }
        let name = rest.trim().trim_start_matches('*').trim_start_matches("./");
        if name == file_name {
            return Some(digest.to_ascii_lowercase());
        }
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn finds_both_sha256sum_styles() {
        let a = "a".repeat(64);
        let b = "b".repeat(64);
        let sums = format!("{a}  one.apk\n{b} *two.apk\n");
        assert_eq!(find_in_sums(&sums, "one.apk"), Some(a));
        assert_eq!(find_in_sums(&sums, "two.apk"), Some(b));
        assert_eq!(find_in_sums(&sums, "three.apk"), None);
    }

    #[test]
    fn verifies_a_real_file() {
        let dir = std::env::temp_dir().join("artcraft-bridge-test.bin");
        std::fs::write(&dir, b"abc").unwrap();
        // sha256("abc")
        let abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        let p = dir.to_str().unwrap();
        assert!(verify_file(p, abc).is_ok());
        assert!(verify_file(p, &"0".repeat(64)).is_err());
        assert!(verify_file(p, "short").is_err());
    }
}
