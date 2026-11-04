# Sync Player - Build & CI Guide

Sync Player is configured with automated GitHub Actions CI/CD to compile Debug and Release APKs automatically upon pushes, pull requests, and manual triggers.

---

## 🚀 GitHub Actions Automated Build

The repository includes a ready-to-run GitHub Actions workflow located at:
`.github/workflows/build-apk.yml`

### What the Workflow Does
1. **Automated Triggering**: Runs on every `push` to `main`, `master`, `dev`, or version tags (`v*`), as well as pull requests.
2. **Manual Execution (`workflow_dispatch`)**: Can be triggered manually from GitHub's **Actions** tab with choice of `all`, `debug`, or `release` builds.
3. **Java 21 & Gradle Caching**: Automatically configures OpenJDK 21 (Temurin) with dependency and wrapper caching for lightning-fast build times.
4. **Keystore Management**:
   - Automatically decodes the base64-encoded debug keystore bundled in the project.
   - If optional GitHub repository secrets (`KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`) are present, it signs release APKs with your production certificate.
   - Otherwise, it generates a fallback upload certificate so release builds succeed without crashing.
5. **Artifact Upload**: Uploads both `SyncPlayer-Debug-APK` and `SyncPlayer-Release-APK` as downloadable artifacts in the workflow run summary (retained for 30 days).

---

## 📥 How to Download APKs from GitHub

1. Push this project to your GitHub repository.
2. Go to the **Actions** tab in your GitHub repository.
3. Click on the latest run under **Build Sync Player APK**.
4. Scroll down to the **Artifacts** section at the bottom of the page.
5. Download `SyncPlayer-Debug-APK` (or `SyncPlayer-Release-APK`) directly onto your phone or emulator.

---

## 🛠️ Local Build Instructions

To build the APK locally from terminal:

### Debug APK:
```bash
gradle assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Release APK:
```bash
gradle assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

---

## 🔒 Custom Release Signing (Optional)

To sign release APKs with your custom key on GitHub:
1. Base64-encode your `.jks` file:
   ```bash
   base64 -w 0 my-upload-key.jks > key.base64
   ```
2. In your GitHub repository, navigate to **Settings > Secrets and variables > Actions**.
3. Add the following repository secrets:
   - `KEYSTORE_BASE64`: The full content of `key.base64`
   - `STORE_PASSWORD`: Your keystore password
   - `KEY_PASSWORD`: Your key alias password
