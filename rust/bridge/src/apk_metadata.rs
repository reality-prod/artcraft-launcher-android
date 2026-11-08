//! APK metadata utilities for the ArtCraft Launcher.
//!
//! Provides functionality to work with APK files and determine compatibility.
use std::fs;

/// Result of APK validation
#[derive(Debug, Clone, serde::Serialize)]
pub struct ApkValidation {
    pub is_valid: bool,
    pub file_size: u64,
    pub error: Option<String>,
}

/// Validates an APK file by checking basic properties.
pub fn validate_apk(path: &str) -> Result<(), String> {
    // Check file exists and is readable
    let metadata = fs::metadata(path).map_err(|e| format!("file access error: {e}"))?;
    
    if metadata.len() == 0 {
        return Err("APK file is empty".to_string());
    }
    
    // Check file extension
    if !path.to_lowercase().ends_with(".apk") {
        return Err("file does not have .apk extension".to_string());
    }
    
    // Check it's a valid ZIP by reading the magic bytes
    let data = fs::read(&path[..std::cmp::min(path.len(), 4)])
        .map_err(|e| format!("cannot read file: {e}"))?;
    
    // ZIP file signature: 0x04034b50 (PK\x03\x04)
    const ZIP_SIGNATURE: u32 = 0x04034b50;
    if data.len() >= 4 {
        let signature = u32::from_le_bytes([data[0], data[1], data[2], data[3]]);
        if signature != ZIP_SIGNATURE {
            return Err("not a valid ZIP/APK file".to_string());
        }
    }
    
    Ok(())
}

/// Checks if APK filename indicates compatibility with given ABIs.
pub fn is_apk_compatible_with_abi(apk_name: &str, abis: &[&str]) -> bool {
    let apk_name_lower = apk_name.to_lowercase();
    
    // If no specific ABI in filename, it's usually universal
    if !abis.iter().any(|abi| apk_name_lower.contains(abi)) {
        return true; // Universal APK
    }
    
    // Check for specific ABI matches
    for abi in abis {
        match *abi {
            "arm64-v8a" => if apk_name_lower.contains("arm64") || apk_name_lower.contains("aarch64") {
                return true;
            },
            "armeabi-v7a" | "armeabi" => if apk_name_lower.contains("armeabi") || apk_name_lower.contains("armv7") {
                return true;
            },
            "x86_64" => if apk_name_lower.contains("x86_64") {
                return true;
            },
            "x86" => if apk_name_lower.contains("x86") && !apk_name_lower.contains("x86_64") {
                return true;
            },
            _ => {}
        }
    }
    
    false
}

/// Get ABI from APK filename.
pub fn get_abi_from_apk_name(name: &str) -> Option<String> {
    let name_lower = name.to_lowercase();
    
    if name_lower.contains("arm64") || name_lower.contains("aarch64") {
        Some("arm64-v8a".to_string())
    } else if name_lower.contains("armeabi-v7a") || name_lower.contains("armv7") || name_lower.contains("armeabi") {
        Some("armeabi-v7a".to_string())
    } else if name_lower.contains("x86_64") {
        Some("x86_64".to_string())
    } else if name_lower.contains("x86") {
        Some("x86".to_string())
    } else {
        None // Universal
    }
}

/// Get file size of an APK.
pub fn get_apk_file_size(path: &str) -> Result<u64, String> {
    fs::metadata(path)
        .map(|m| m.len())
        .map_err(|e| format!("cannot read file: {e}"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_abi_compatibility() {
        // Universal APK
        assert!(is_apk_compatible_with_abi("app.apk", &["arm64-v8a", "armeabi-v7a"]));
        
        // arm64 specific
        assert!(is_apk_compatible_with_abi("app-arm64.apk", &["arm64-v8a"]));
        assert!(!is_apk_compatible_with_abi("app-arm64.apk", &["x86"]));
        
        // armeabi-v7a specific
        assert!(is_apk_compatible_with_abi("app-armeabi-v7a.apk", &["armeabi-v7a"]));
        
        // x86_64 specific
        assert!(is_apk_compatible_with_abi("app-x86_64.apk", &["x86_64"]));
    }

    #[test]
    fn test_get_abi_from_name() {
        assert_eq!(get_abi_from_apk_name("app.apk"), None);
        assert_eq!(get_abi_from_apk_name("app-arm64-v8a.apk"), Some("arm64-v8a".to_string()));
        assert_eq!(get_abi_from_apk_name("app-aarch64.apk"), Some("arm64-v8a".to_string()));
        assert_eq!(get_abi_from_apk_name("app-armeabi-v7a.apk"), Some("armeabi-v7a".to_string()));
        assert_eq!(get_abi_from_apk_name("app-armv7.apk"), Some("armeabi-v7a".to_string()));
        assert_eq!(get_abi_from_apk_name("app-x86_64.apk"), Some("x86_64".to_string()));
        assert_eq!(get_abi_from_apk_name("app-x86.apk"), Some("x86".to_string()));
    }
}
