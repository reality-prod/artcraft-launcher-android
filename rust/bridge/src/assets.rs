//! Choose the right APK from a GitHub release for this phone.
//!
//! The artcraft-android releases ship a plain APK (arm64) and an `-armeabi-v7a` APK.
use serde::{Deserialize, Serialize};

#[derive(Deserialize)]
struct Release {
    #[serde(default)]
    assets: Vec<Asset>,
}

#[derive(Deserialize)]
struct Asset {
    name: String,
    browser_download_url: String,
    #[serde(default)]
    size: u64,
    #[serde(default)]
    digest: Option<String>,
}

#[derive(Serialize)]
struct Picked<'a> {
    name: &'a str,
    url: &'a str,
    size: u64,
    sha256: Option<String>,
}

const ABI_TAGS: [&str; 6] = ["armeabi", "armv7", "arm64", "aarch64", "x86_64", "x86"];

fn has(a: &Asset, s: &str) -> bool {
    a.name.to_ascii_lowercase().contains(s)
}

fn pick<'a>(apks: &[&'a Asset], abis: &[&str]) -> Option<&'a Asset> {
    let plain = apks
        .iter()
        .copied()
        .find(|a| ABI_TAGS.iter().all(|t| !has(a, t)));
    for abi in abis {
        let hit = match *abi {
            "arm64-v8a" => apks
                .iter()
                .copied()
                .find(|a| has(a, "arm64") || has(a, "aarch64"))
                .or(plain),
            "armeabi-v7a" | "armeabi" => apks
                .iter()
                .copied()
                .find(|a| has(a, "armeabi") || has(a, "armv7")),
            "x86_64" => apks.iter().copied().find(|a| has(a, "x86_64")),
            "x86" => apks.iter().copied().find(|a| has(a, "x86") && !has(a, "x86_64")),
            _ => None,
        };
        if hit.is_some() {
            return hit;
        }
    }
    plain.or_else(|| apks.first().copied())
}

fn sha_of(a: &Asset) -> Option<String> {
    a.digest
        .as_deref()
        .and_then(|d| d.strip_prefix("sha256:"))
        .filter(|h| h.len() == 64 && h.bytes().all(|b| b.is_ascii_hexdigit()))
        .map(str::to_ascii_lowercase)
}

/// `release_json` is one release object from the GitHub API; `abis_csv` is `Build.SUPPORTED_ABIS`
/// joined with commas, best first.
pub fn pick_json(release_json: &str, abis_csv: &str) -> String {
    let release: Release = match serde_json::from_str(release_json) {
        Ok(r) => r,
        Err(e) => return serde_json::json!({ "ok": false, "error": format!("bad release json: {e}") }).to_string(),
    };
    let apks: Vec<&Asset> = release
        .assets
        .iter()
        .filter(|a| a.name.to_ascii_lowercase().ends_with(".apk"))
        .collect();
    let abis: Vec<&str> = abis_csv.split(',').map(str::trim).filter(|s| !s.is_empty()).collect();
    let picked = pick(&apks, &abis).map(|a| Picked {
        name: &a.name,
        url: &a.browser_download_url,
        size: a.size,
        sha256: sha_of(a),
    });
    serde_json::json!({ "ok": true, "asset": picked }).to_string()
}

#[cfg(test)]
mod tests {
    use super::pick_json;

    fn release(names: &[&str]) -> String {
        let assets: Vec<_> = names
            .iter()
            .map(|n| {
                serde_json::json!({
                    "name": n,
                    "browser_download_url": format!("https://example.invalid/{n}"),
                    "size": 10,
                    "digest": null
                })
            })
            .collect();
        serde_json::json!({ "assets": assets }).to_string()
    }

    fn name_of(out: &str) -> Option<String> {
        let v: serde_json::Value = serde_json::from_str(out).unwrap();
        v["asset"]["name"].as_str().map(str::to_string)
    }

    #[test]
    fn arm64_phone_gets_the_plain_apk() {
        let r = release(&["app-0.0.5-armeabi-v7a.apk", "app-0.0.5.apk", "SHA256SUMS.txt"]);
        assert_eq!(name_of(&pick_json(&r, "arm64-v8a,armeabi-v7a,armeabi")).as_deref(), Some("app-0.0.5.apk"));
    }

    #[test]
    fn old_32_bit_phone_gets_the_v7a_apk() {
        let r = release(&["app-0.0.5.apk", "app-0.0.5-armeabi-v7a.apk"]);
        assert_eq!(
            name_of(&pick_json(&r, "armeabi-v7a,armeabi")).as_deref(),
            Some("app-0.0.5-armeabi-v7a.apk")
        );
    }

    #[test]
    fn no_apk_means_no_asset() {
        let r = release(&["SHA256SUMS.txt", "app.dmg"]);
        assert_eq!(name_of(&pick_json(&r, "arm64-v8a")), None);
    }

    #[test]
    fn bad_json_is_an_error_not_a_panic() {
        assert!(pick_json("not json", "arm64-v8a").contains("\"ok\":false"));
    }

    #[test]
    fn only_a_valid_sha256_digest_is_returned() {
        let good = "a".repeat(64);
        let r = serde_json::json!({ "assets": [{
            "name": "a.apk", "browser_download_url": "https://example.invalid/a.apk",
            "size": 1, "digest": format!("sha256:{good}") }] })
        .to_string();
        assert!(pick_json(&r, "arm64-v8a").contains(&good));
        let bad = serde_json::json!({ "assets": [{
            "name": "a.apk", "browser_download_url": "https://example.invalid/a.apk",
            "size": 1, "digest": "sha256:xyz" }] })
        .to_string();
        assert!(pick_json(&bad, "arm64-v8a").contains("\"sha256\":null"));
    }
}
