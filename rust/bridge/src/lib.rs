//! JNI bridge for the Android ArtCraft Launcher.
//!
//! Same rules as storytold/craft-launcher: nothing here may panic or crash the app. Every
//! entry point catches panics and answers with an error value, and all input is treated as
//! hostile (it comes from the network).
//!
//! The pure logic lives in `assets`, `version`, `checksum`, and `apk_metadata` and is unit-tested without a JVM.
//! `jni_glue` is the only module that touches JNI.
#![deny(
    clippy::unwrap_used,
    clippy::expect_used,
    clippy::panic,
    clippy::unimplemented,
    clippy::todo,
    clippy::unreachable
)]
#![cfg_attr(test, allow(clippy::unwrap_used, clippy::expect_used, clippy::panic))]
#![deny(unsafe_code)]

pub mod apk_metadata;
pub mod apk_utils;
pub mod assets;
pub mod checksum;
pub mod version;

#[allow(unsafe_code)] // `#[no_mangle]` JNI exports are the only unsafe-flagged items.
mod jni_glue {
    use crate::{apk_metadata, apk_utils, assets, checksum, version};
    use jni::objects::{JClass, JString};
    use jni::sys::{jint, jstring};
    use jni::JNIEnv;
    use std::panic::{catch_unwind, AssertUnwindSafe};

    const INTERNAL_ERROR: &str = r#"{"ok":false,"error":"internal error"}"#;

    fn read(env: &mut JNIEnv, s: &JString) -> Option<String> {
        env.get_string(s).ok().map(String::from)
    }

    fn reply(env: &mut JNIEnv, s: &str) -> jstring {
        env.new_string(s)
            .map(|j| j.into_raw())
            .unwrap_or(std::ptr::null_mut())
    }

    fn guarded<T>(fallback: T, f: impl FnOnce() -> T) -> T {
        catch_unwind(AssertUnwindSafe(f)).unwrap_or(fallback)
    }

    /// `String pickApk(String releaseJson, String abisCsv)`
    /// Returns `{"ok":true,"asset":{name,url,size,sha256}|null}` or `{"ok":false,"error":...}`.
    #[no_mangle]
    pub extern "system" fn Java_app_artcraft_launcher_bridge_NativeCore_pickApk<'l>(
        mut env: JNIEnv<'l>,
        _class: JClass<'l>,
        release_json: JString<'l>,
        abis_csv: JString<'l>,
    ) -> jstring {
        let (Some(json), Some(abis)) = (read(&mut env, &release_json), read(&mut env, &abis_csv))
        else {
            return reply(&mut env, r#"{"ok":false,"error":"bad arguments"}"#);
        };
        let out = guarded(INTERNAL_ERROR.to_string(), move || {
            assets::pick_json(&json, &abis)
        });
        reply(&mut env, &out)
    }

    /// `int compareVersions(String a, String b)` -> -1, 0 or 1.
    #[no_mangle]
    pub extern "system" fn Java_app_artcraft_launcher_bridge_NativeCore_compareVersions<'l>(
        mut env: JNIEnv<'l>,
        _class: JClass<'l>,
        a: JString<'l>,
        b: JString<'l>,
    ) -> jint {
        let (Some(a), Some(b)) = (read(&mut env, &a), read(&mut env, &b)) else {
            return 0;
        };
        guarded(0, move || version::compare(&a, &b) as jint)
    }

    /// `String verifySha256(String path, String expectedHex)` -> "" when it matches, else a message.
    #[no_mangle]
    pub extern "system" fn Java_app_artcraft_launcher_bridge_NativeCore_verifySha256<'l>(
        mut env: JNIEnv<'l>,
        _class: JClass<'l>,
        path: JString<'l>,
        expected: JString<'l>,
    ) -> jstring {
        let (Some(path), Some(expected)) = (read(&mut env, &path), read(&mut env, &expected))
        else {
            return reply(&mut env, "bad arguments");
        };
        let out = guarded("internal error".to_string(), move || {
            match checksum::verify_file(&path, &expected) {
                Ok(()) => String::new(),
                Err(e) => e,
            }
        });
        reply(&mut env, &out)
    }

    /// `String findSha256(String sumsText, String fileName)` -> hex, or "" when not listed.
    /// Understands the `SHA256SUMS.txt` files the Craft apps publish with each release.
    #[no_mangle]
    pub extern "system" fn Java_app_artcraft_launcher_bridge_NativeCore_findSha256<'l>(
        mut env: JNIEnv<'l>,
        _class: JClass<'l>,
        sums: JString<'l>,
        file_name: JString<'l>,
    ) -> jstring {
        let (Some(sums), Some(name)) = (read(&mut env, &sums), read(&mut env, &file_name)) else {
            return reply(&mut env, "");
        };
        let out = guarded(String::new(), move || {
            checksum::find_in_sums(&sums, &name).unwrap_or_default()
        });
        reply(&mut env, &out)
    }

    /// `String bridgeVersion()`
    #[no_mangle]
    pub extern "system" fn Java_app_artcraft_launcher_bridge_NativeCore_bridgeVersion<'l>(
        mut env: JNIEnv<'l>,
        _class: JClass<'l>,
    ) -> jstring {
        reply(&mut env, env!("CARGO_PKG_VERSION"))
    }

    /// `String nativeValidateApk(String path)`
    /// Returns empty string on success, or error message.
    #[no_mangle]
    pub extern "system" fn Java_app_artcraft_launcher_bridge_NativeCore_nativeValidateApk<'l>(
        mut env: JNIEnv<'l>,
        _class: JClass<'l>,
        path: JString<'l>,
    ) -> jstring {
        let Some(path) = read(&mut env, &path) else {
            return reply(&mut env, "bad path");
        };
        let out = guarded(
            "internal error".to_string(),
            move || match apk_metadata::validate_apk(&path) {
                Ok(()) => String::new(),
                Err(e) => e,
            },
        );
        reply(&mut env, &out)
    }

    /// `boolean nativeIsApkCompatible(String apkName, String abisCsv)`
    /// Returns true if APK is compatible with any of the given ABIs.
    #[no_mangle]
    pub extern "system" fn Java_app_artcraft_launcher_bridge_NativeCore_nativeIsApkCompatible<'l>(
        mut env: JNIEnv<'l>,
        _class: JClass<'l>,
        apk_name: JString<'l>,
        abis_csv: JString<'l>,
    ) -> jni::sys::jboolean {
        let (Some(name), Some(abis_csv)) = (read(&mut env, &apk_name), read(&mut env, &abis_csv))
        else {
            return jni::sys::JNI_FALSE;
        };
        let abis: Vec<&str> = abis_csv.split(',').map(str::trim).filter(|s| !s.is_empty()).collect();
        guarded(
            jni::sys::JNI_FALSE,
            move || {
                if apk_metadata::is_apk_compatible_with_abi(&name, &abis) {
                    jni::sys::JNI_TRUE
                } else {
                    jni::sys::JNI_FALSE
                }
            },
        )
    }

    /// `String nativeGetAbiFromApkName(String apkName)`
    /// Returns ABI string or empty string if universal.
    #[no_mangle]
    pub extern "system" fn Java_app_artcraft_launcher_bridge_NativeCore_nativeGetAbiFromApkName<'l>(
        mut env: JNIEnv<'l>,
        _class: JClass<'l>,
        apk_name: JString<'l>,
    ) -> jstring {
        let Some(name) = read(&mut env, &apk_name) else {
            return reply(&mut env, "");
        };
        let out = guarded(
            String::new(),
            move || apk_metadata::get_abi_from_apk_name(&name).unwrap_or_default(),
        );
        reply(&mut env, &out)
    }

    /// `String nativeExtractPackageName(String path)`
    /// Extracts package name from APK file.
    #[no_mangle]
    pub extern "system" fn Java_app_artcraft_launcher_bridge_NativeCore_nativeExtractPackageName<'l>(
        mut env: JNIEnv<'l>,
        _class: JClass<'l>,
        path: JString<'l>,
    ) -> jstring {
        let Some(path) = read(&mut env, &path) else {
            return reply(&mut env, "");
        };
        let out = guarded(
            String::new(),
            move || apk_utils::extract_package_name_from_apk(&path).unwrap_or_default(),
        );
        reply(&mut env, &out)
    }

    /// `int nativeExtractVersionCode(String apkName)`
    /// Extracts version code from APK filename.
    #[no_mangle]
    pub extern "system" fn Java_app_artcraft_launcher_bridge_NativeCore_nativeExtractVersionCode<'l>(
        mut env: JNIEnv<'l>,
        _class: JClass<'l>,
        apk_name: JString<'l>,
    ) -> jint {
        let Some(name) = read(&mut env, &apk_name) else {
            return 0;
        };
        guarded(0, move || apk_utils::extract_version_code_from_apk_name(&name).unwrap_or(0) as jint)
    }

    /// `String nativeExtractVersionName(String apkName)`
    /// Extracts version name from APK filename.
    #[no_mangle]
    pub extern "system" fn Java_app_artcraft_launcher_bridge_NativeCore_nativeExtractVersionName<'l>(
        mut env: JNIEnv<'l>,
        _class: JClass<'l>,
        apk_name: JString<'l>,
    ) -> jstring {
        let Some(name) = read(&mut env, &apk_name) else {
            return reply(&mut env, "");
        };
        let out = guarded(
            String::new(),
            move || apk_utils::extract_version_name_from_apk_name(&name).unwrap_or_default(),
        );
        reply(&mut env, &out)
    }

    /// `String nativeGetApkHash(String path)`
    /// Gets SHA-256 hash of APK file.
    #[no_mangle]
    pub extern "system" fn Java_app_artcraft_launcher_bridge_NativeCore_nativeGetApkHash<'l>(
        mut env: JNIEnv<'l>,
        _class: JClass<'l>,
        path: JString<'l>,
    ) -> jstring {
        let Some(path) = read(&mut env, &path) else {
            return reply(&mut env, "");
        };
        let out = guarded(
            String::new(),
            move || apk_utils::get_apk_hash(&path).unwrap_or_default(),
        );
        reply(&mut env, &out)
    }

    /// `boolean nativeIsSplitApk(String apkName)`
    /// Checks if APK is a split APK.
    #[no_mangle]
    pub extern "system" fn Java_app_artcraft_launcher_bridge_NativeCore_nativeIsSplitApk<'l>(
        mut env: JNIEnv<'l>,
        _class: JClass<'l>,
        apk_name: JString<'l>,
    ) -> jni::sys::jboolean {
        let Some(name) = read(&mut env, &apk_name) else {
            return jni::sys::JNI_FALSE;
        };
        guarded(
            jni::sys::JNI_FALSE,
            move || {
                if apk_utils::is_split_apk(&name) {
                    jni::sys::JNI_TRUE
                } else {
                    jni::sys::JNI_FALSE
                }
            },
        )
    }

    /// `String nativeGetSupportedAbis(String apkName)`
    /// Gets comma-separated list of supported ABIs from APK name.
    #[no_mangle]
    pub extern "system" fn Java_app_artcraft_launcher_bridge_NativeCore_nativeGetSupportedAbis<'l>(
        mut env: JNIEnv<'l>,
        _class: JClass<'l>,
        apk_name: JString<'l>,
    ) -> jstring {
        let Some(name) = read(&mut env, &apk_name) else {
            return reply(&mut env, "");
        };
        let out = guarded(
            String::new(),
            move || apk_utils::get_supported_abis_from_apk_name(&name).join(","),
        );
        reply(&mut env, &out)
    }
}
