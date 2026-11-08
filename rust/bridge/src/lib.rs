//! JNI bridge for the Android ArtCraft Launcher.
//!
//! Same rules as storytold/craft-launcher: nothing here may panic or crash the app. Every
//! entry point catches panics and answers with an error value, and all input is treated as
//! hostile (it comes from the network).
//!
//! The pure logic lives in `assets`, `version` and `checksum` and is unit-tested without a JVM.
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

pub mod assets;
pub mod checksum;
pub mod text;
pub mod version;

#[allow(unsafe_code)] // `#[no_mangle]` JNI exports are the only unsafe-flagged items.
mod jni_glue {
    use crate::{assets, checksum, text, version};
    use jni::objects::{JClass, JString};
    use jni::sys::{jint, jlong, jstring};
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
        let out = guarded(
            "internal error".to_string(),
            move || match checksum::verify_file(&path, &expected) {
                Ok(()) => String::new(),
                Err(e) => e,
            },
        );
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

    /// `String humanSize(long bytes)` -> "78.4 MB".
    #[no_mangle]
    pub extern "system" fn Java_app_artcraft_launcher_bridge_NativeCore_humanSize<'l>(
        mut env: JNIEnv<'l>,
        _class: JClass<'l>,
        bytes: jlong,
    ) -> jstring {
        let out = guarded(String::new(), move || {
            text::human_size(u64::try_from(bytes).unwrap_or(0))
        });
        reply(&mut env, &out)
    }

    /// `String plainNotes(String markdown, int maxChars)` -> readable text, 0 = no limit.
    #[no_mangle]
    pub extern "system" fn Java_app_artcraft_launcher_bridge_NativeCore_plainNotes<'l>(
        mut env: JNIEnv<'l>,
        _class: JClass<'l>,
        markdown: JString<'l>,
        max_chars: jint,
    ) -> jstring {
        let Some(md) = read(&mut env, &markdown) else {
            return reply(&mut env, "");
        };
        let max = usize::try_from(max_chars).unwrap_or(0);
        let out = guarded(String::new(), move || text::plain_notes(&md, max));
        reply(&mut env, &out)
    }

    /// `String relativeTime(String iso8601, long nowEpochSeconds)` -> "3 days ago", or "".
    #[no_mangle]
    pub extern "system" fn Java_app_artcraft_launcher_bridge_NativeCore_relativeTime<'l>(
        mut env: JNIEnv<'l>,
        _class: JClass<'l>,
        iso: JString<'l>,
        now_epoch: jlong,
    ) -> jstring {
        let Some(iso) = read(&mut env, &iso) else {
            return reply(&mut env, "");
        };
        let out = guarded(String::new(), move || text::relative_time(&iso, now_epoch));
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
}
