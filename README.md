# KiranaSync 🛒🇮🇳

**KiranaSync** is a highly polished, privacy-first, client-side Android application designed to streamline modern Indian pantry, dairy, and grocery management. By pairing high-performance local database persistence with innovative background notification listening, KiranaSync bridges the gap between digital quick-commerce delivery apps and physical pantry utilization.

The application is built on cutting-edge **Jetpack Compose**, **Material 3 (M3) organic theming**, and a robust custom architecture that requires no heavy backend footprint.

---

## Technical Highlights & Key Features

### 1. High-Precision Q-Commerce Interception Engine
* **Automatic Parsing Service**: Integrates an Android `NotificationListenerService` (`QComListenerService`) that intercepts local push-delivery payloads from major Indian express commerce services including **Zepto**, **Blinkit**, and **Swiggy Instamart**.
* **Zero-Touch Syncing**: On detecting "on the way" or "delivered" notifications, the service surfaces intuitive import prompts to automatically populate your active local inventory, cutting down manual list-building.

### 2. Live Pantry Inventory & Expiry Alerts
* **Dynamic Custom Categories**: Organizes pantry items across high-frequency sectors like *Staples*, *Dairy*, *Snacks*, *Spices*, and custom priorities from the local DB.
* **Intelligent Expiry Thresholds**: Highlights expiring goods within configurable warning windows (e.g., `<3` days, `<5` days) so precious items are consumed instead of wasted.

### 3. Integrated Barcode Ingestion via Google ML Kit
* **On-Device Barcode Recognition**: Integrates `ML Kit Barcode Scanning` directly with high-frequency camera frames (`androidx.camera.core`) to instantaneously register grocery barcodes and match existing products.

### 4. Interactive Shopping Board
* **One-Tap Transfers**: Instantly transfer expiring or low-stock items from your pantry to a smart Checklist Board.
* **Mock checkout simulation**: Select items to instantly place a virtual checkout order that auto-repopulates items into the pantry catalog with fresh expiry date presets (7 days).

---

## Architectural Layout

```
com.example/
│
├── data/
│   ├── GroceryItem.kt        # Room database entity representing pantry inventory assets
│   ├── ShoppingItem.kt       # Entity detailing checklist board requirements
│   ├── UserPreference.kt     # Shared state model for thresholds, currency, and names
│   ├── GroceryDao.kt         # Type-safe Room database access interfaces
│   ├── GroceryDatabase.kt    # Main database builder & migrations manager
│   └── GroceryRepository.kt  # Centralized transactional synchronization source
│
├── ui/
│   # Material 3 typography definitions, organic palette settings, and UI themes
│
├── BarcodeAnalyzer.kt        # Frame-by-frame Barcode analysis binding via ML Kit
├── ExpiryAlarmReceiver.kt    # Triggers system-wide alarms for pending expiry deadlines
├── QComListenerService.kt    # Standby Notification interception layer
├── GroceryViewModel.kt       # Central state VM managing inventory operations, settings, and flows
└── MainActivity.kt           # Main Compose layout orchestrator holding main tabs
```

---

## Local Development & Setup

### Prerequisites
- **Android Studio** (Koala or newer recommended)
- **Android SDK Level 36+** (Compile target is SDK 36, minimum target is SDK 24)
- **Java Development Kit (JDK) 17**

### Clone & Build
```bash
# Clone the repository
git clone https://github.com/your-username/KiranaSync.git
cd KiranaSync

# Set execution rights on gradle wrapper wrapper if present
chmod +x gradlew

# Perform standard compilation check
./gradlew compileDebugSources
```

### Injected Configuration & Secrets
API keys and properties are decoupled from source code repository tracking.
Create an `.env` file in your root workspace containing standard environment variables (or rely on `.env.example`).
The `Secrets Gradle Plugin` handles automatic generation of safe `BuildConfig` fields at build-time.

---

## Continuous Delivery Pipeline on GitHub

KiranaSync is configured with a fully automated Release and Asset compiling pipeline on GitHub. 

### How GitHub Releases Work
Every time a new version tag (e.g., `v1.0.0`) is pushed to your GitHub repository, the release workflow runs:
1. **Runner Instantiation**: Sets up an Ubuntu image with Java 17 and caches Gradle to maximize execution speed.
2. **Key Security Check**: Looks for a `KEYSTORE_BASE64` certificate secret.
3. **Signed Release Compilation**:
   - If `KEYSTORE_BASE64` is configured, it dynamically outputs a securely signed, production-ready `KiranaSync-v*.apk`.
   - If missing, it builds a pre-aligned, fully secure testing-ready APK (`KiranaSync-testing-v*.apk`), perfect for quick test distribution.
4. **Automated Publishing**: Generates a professional Changelog, creates a Git Release Tag, and pins the assets directly inside your repository's Releases page.

### Manual Actions Dispatch
This execution pipeline can also be run manually at any time by selecting **"Run workflow"** under the **Actions** tab on your GitHub repository.

---

## Security Compliance & Reporting
We value the security of the pantry states kept in this application. For policies, version updates, and reporting instructions regarding vulnerabilities, see **[SECURITY.md](SECURITY.md)**.
