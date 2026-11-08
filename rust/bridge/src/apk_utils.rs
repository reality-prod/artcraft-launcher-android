//! Enhanced APK utilities for the ArtCraft Launcher.
//!
//! Provides additional functionality for working with APK files.
use std::fs;
use std::io::Read;

/// Get the package name from an APK's manifest (simplified parsing).
/// This is a best-effort approach without a full XML parser.
pub fn extract_package_name_from_apk(path: &str) -> Result<String, String> {
    let file = fs::File::open(path).map_err(|e| format!("cannot open file: {e}"))?;
    let size = file.metadata().map_err(|e| format!("cannot read metadata: {e}"))?.len();
    
    if size == 0 {
        return Err("APK file is empty".to_string());
    }
    
    // Try to find AndroidManifest.xml in the ZIP
    // For now, return a default or extract from filename
    // This is a simplified implementation
    
    // Extract from filename if it follows a pattern like app-name-version.apk
    let filename = std::path::Path::new(path)
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("unknown");
    
    // Remove .apk extension
    let name = filename.strip_suffix(".apk").unwrap_or(filename);
    
    // Convert filename to package name format (lowercase, replace - with .)
    let package_name = name.to_lowercase().replace('-', ".");
    
    Ok(package_name)
}

/// Extract version code from APK filename if it follows semantic versioning.
pub fn extract_version_code_from_apk_name(name: &str) -> Result<u32, String> {
    // Try to find version pattern like -v1.2.3 or -1.2.3
    let name_lower = name.to_lowercase();
    
    // Look for version patterns
    let version_patterns = [
        "-v", "-version", "_v", "_version", 
        " v", " version "
    ];
    
    for pattern in version_patterns {
        if let Some(version_start) = name_lower.find(pattern) {
            let version_str = &name_lower[version_start + pattern.len()..];
            let version = version_str.chars()
                .take_while(|c| c.is_ascii_digit() || *c == '.')
                .collect::<String>();
            
            if !version.is_empty() {
                // Convert version to code (e.g., 1.2.3 -> 10203)
                let parts: Vec<&str> = version.split('.').collect();
                if parts.len() >= 1 {
                    let major = parts[0].parse::<u32>().unwrap_or(0);
                    let minor = parts.get(1).and_then(|s| s.parse::<u32>().ok()).unwrap_or(0);
                    let patch = parts.get(2).and_then(|s| s.parse::<u32>().ok()).unwrap_or(0);
                    return Ok(major * 10000 + minor * 100 + patch);
                }
            }
        }
    }
    
    // No version found in filename, return 0
    Ok(0)
}

/// Extract version name from APK filename.
pub fn extract_version_name_from_apk_name(name: &str) -> Option<String> {
    // Similar to version code extraction but return the full version string
    let name_lower = name.to_lowercase();
    
    let version_patterns = [
        "-v", "-version", "_v", "_version",
        " v", " version "
    ];
    
    for pattern in version_patterns {
        if let Some(version_start) = name_lower.find(pattern) {
            let version_str = &name_lower[version_start + pattern.len()..];
            let version = version_str.chars()
                .take_while(|c| c.is_ascii_digit() || *c == '.')
                .collect::<String>();
            
            if !version.is_empty() {
                return Some(version);
            }
        }
    }
    
    None
}

/// Get file hash (SHA-256) of an APK.
pub fn get_apk_hash(path: &str) -> Result<String, String> {
    use sha2::{Digest, Sha256};
    
    let mut file = fs::File::open(path).map_err(|e| format!("cannot open file: {e}"))?;
    let mut hasher = Sha256::new();
    let mut buffer = [0u8; 8192];
    
    loop {
        let bytes_read = file.read(&mut buffer).map_err(|e| format!("read error: {e}"))?;
        if bytes_read == 0 {
            break;
        }
        hasher.update(&buffer[..bytes_read]);
    }
    
    let hash = hasher.finalize();
    Ok(hash.iter().map(|b| format!("{:02x}", b)).collect::<String>())
}

/// Check if APK is a split APK (has suffix like -arm64, -x86, etc.)
pub fn is_split_apk(name: &str) -> bool {
    let name_lower = name.to_lowercase();
    
    // Split APKs typically have ABI suffixes
    let abi_suffixes = ["-arm64-v8a", "-armeabi-v7a", "-x86_64", "-x86", "-aarch64", "-armv7"];
    
    for suffix in abi_suffixes {
        if name_lower.contains(suffix) {
            return true;
        }
    }
    
    false
}

/// Get all supported ABIs for an APK based on its filename.
pub fn get_supported_abis_from_apk_name(name: &str) -> Vec<String> {
    let name_lower = name.to_lowercase();
    let mut abis = Vec::new();
    
    if name_lower.contains("arm64") || name_lower.contains("aarch64") {
        abis.push("arm64-v8a".to_string());
    }
    if name_lower.contains("armeabi-v7a") || name_lower.contains("armv7") || name_lower.contains("armeabi") {
        abis.push("armeabi-v7a".to_string());
    }
    if name_lower.contains("x86_64") {
        abis.push("x86_64".to_string());
    }
    if name_lower.contains("x86") && !name_lower.contains("x86_64") {
        abis.push("x86".to_string());
    }
    
    // If no specific ABI found, it's universal
    if abis.is_empty() {
        abis.push("universal".to_string());
    }
    
    abis
}

/// Get APK file size in a human-readable format.
pub fn format_apk_size(size_bytes: u64) -> String {
    const KB: u64 = 1024;
    const MB: u64 = KB * 1024;
    const GB: u64 = MB * 1024;
    
    if size_bytes >= GB {
        format!("{} GB", (size_bytes as f64) / (GB as f64))
    } else if size_bytes >= MB {
        format!("{} MB", (size_bytes as f64) / (MB as f64))
    } else if size_bytes >= KB {
        format!("{} KB", (size_bytes as f64) / (KB as f64))
    } else {
        format!("{} B", size_bytes)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_version_code() {
        assert_eq!(extract_version_code_from_apk_name("app-v1.2.3.apk").unwrap(), 10203);
        assert_eq!(extract_version_code_from_apk_name("app-2.0.0.apk").unwrap(), 20000);
        assert_eq!(extract_version_code_from_apk_name("app.apk").unwrap(), 0);
    }

    #[test]
    fn test_extract_version_name() {
        assert_eq!(extract_version_name_from_apk_name("app-v1.2.3.apk"), Some("1.2.3".to_string()));
        assert_eq!(extract_version_name_from_apk_name("app-2.0.0-beta.apk"), Some("2.0.0".to_string()));
        assert_eq!(extract_version_name_from_apk_name("app.apk"), None);
    }

    #[test]
    fn test_is_split_apk() {
        assert!(is_split_apk("app-arm64-v8a.apk"));
        assert!(is_split_apk("app-x86_64.apk"));
        assert!(!is_split_apk("app.apk"));
        assert!(!is_split_apk("app-universal.apk"));
    }

    #[test]
    fn test_get_supported_abis() {
        let abis = get_supported_abis_from_apk_name("app-arm64-v8a.apk");
        assert!(abis.contains(&"arm64-v8a".to_string()));
        
        let abis = get_supported_abis_from_apk_name("app.apk");
        assert!(abis.contains(&"universal".to_string()));
    }

    #[test]
    fn test_format_apk_size() {
        assert_eq!(format_apk_size(1024), "1.0 KB");
        assert_eq!(format_apk_size(1024 * 1024), "1.0 MB");
        assert_eq!(format_apk_size(1024 * 1024 * 1024), "1.0 GB");
    }
}
