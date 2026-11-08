# Releasing

Pushing a tag builds, signs and publishes the APKs. Nothing else to run.

```
git tag v0.2.0            # or v0.2.0-beta.1 for a pre-release
git push origin v0.2.0
```

The workflow (`.github/workflows/release.yml`) runs the Rust tests, builds the Rust bridge for
arm64 and 32-bit ARM, builds one signed APK per CPU type, checks the signature and that the
Rust library is inside, writes `SHA256SUMS.txt`, attaches a build-provenance attestation, and
creates the GitHub release. Asset names are `artcraft-launcher-<version>-arm64-v8a.apk` and
`...-armeabi-v7a.apk`, which is exactly what the launcher's own self-update picks from.

## One-time setup

1. **Make a signing key and keep it safe.** Every future update must be signed with the same
   key, or Android refuses to install it over the old one. Back it up somewhere other than GitHub.
   ```
   keytool -genkeypair -v -keystore release.jks -alias artcraft-launcher \
     -keyalg RSA -keysize 4096 -validity 10000
   base64 -w0 release.jks        # macOS: base64 -i release.jks
   ```
2. **Add four repository secrets** (Settings → Secrets and variables → Actions):
   `ANDROID_KEYSTORE_BASE64` (the base64 output), `ANDROID_KEYSTORE_PASSWORD`,
   `ANDROID_KEY_ALIAS` (`artcraft-launcher` above) and `ANDROID_KEY_PASSWORD`.
   The release job refuses to run without them rather than shipping a debug-signed APK.
3. **Let Actions write**: Settings → Actions → General → Workflow permissions → *Read and write*.
   The catalog workflow needs this to commit `catalog.json`.
4. **Commit `Cargo.lock`** after your first local `cd rust && cargo test` (and run `cargo fmt`).
5. Optional: run `gradle wrapper --gradle-version 8.9` once and commit the wrapper.

## Version numbers

`versionName` is the tag without the `v`. `versionCode` is `major*1000000 + minor*10000 +
patch*100 + n`, where `n` is 99 for a final release and the pre-release number otherwise (so
`v0.2.0-beta.3` < `v0.2.0`). Never reuse a tag.

## The saved catalog

`update-catalog.yml` runs every 30 minutes and after each release. It asks the GitHub API (with
the workflow's own 5,000/hour token) for the newest release of every repo in `tools/sources.json`,
and commits `catalog.json` with direct download links and SHA-256 checksums. Phones read that
file from `raw.githubusercontent.com`, so they don't call the rate-limited API. If the file or
host is unavailable, the launcher falls back to the API, then the public release pages, then the
links it saved on the phone. Add repos in `tools/sources.json`. GitHub pauses scheduled workflows
after 60 days without repository activity; a release or any commit wakes it up.

The launcher only accepts `https://github.com/.../releases/download/...` links from the catalog.
Android still checks every APK's signature on install, so a tampered catalog can't replace an
app with one signed by a different key.
