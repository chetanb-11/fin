# Finbox: IndusInd Credit Card Gmail Interceptor & Finance Manager

Finbox is a privacy-first, lightweight Android personal finance manager designed specifically to intercept and parse Gmail notification payloads for IndusInd bank credit card transactions. By processing incoming alerts entirely on-device, Finbox offers a seamless, automatic expense tracking system without compromising user security or relying on external cloud parsing engines.

---

## 🚀 Key Features

*   **Secure Gmail Interceptor (`NotificationListener`: `com.google.android.gm`)**  
    Continuously monitors incoming Gmail notification streams. It filters payloads dynamically, rejecting all non-Gmail notifications and applying a strict guardrail that discards any alert not matching "IndusInd" keyword signatures.
*   **Highly Optimized Extractors (`NotificationParser`)**  
    Processes complex Gmail text snippets with high-fidelity regular expressions. Specifically handles line breaks, extra whitespaces injected by mail wraps, and currency conversions (e.g., `INR` / `Rs.`), cleanly extracting the **merchant name** and **transaction amount**.
*   **Monthly Donut Chart & Category Spending Breakdown**  
    Provides an interactive Material 3 donut visualization that allows you to click categories to focus specific breakdown percentages, aggregate sum tallies, and clean aesthetic highlights.
*   **On-Device Room Database SQLite Persistence**  
    All parsed transactions are compiled instantly into local structured tables using Android's SQLite Room architecture. Zero data leaves your device.
*   **Live SMS / Gmail Sandbox Simulator**  
    Comes equipped with custom-built transaction presets matching standard bank layouts to let developers and users test the real-time parsing engine directly.

---

## 🛠 Architecture Overview

The application follows clean architectural guidelines split across well-defined layers:

1.  **SmsNotificationListenerService (`com.chetan.minfinance.service`)**
    *   Subclasses `NotificationListenerService`.
    *   Restricted to `com.google.android.gm`.
    *   Consolidates `EXTRA_TITLE`, `EXTRA_TEXT`, and `EXTRA_BIG_TEXT` into a single payload, applying early drops for non-IndusInd threads.
2.  **NotificationParser**
    *   Removes redundant mail formatting and applies regex matches.
    *   Parses matching patterns like: `"Transaction at SWIGGY is Approved on your IndusInd CC for INR 450.00."`
3.  **AppDatabase & ExpenseDao (`com.chetan.minfinance.data`)**
    *   Standardized Room Schema caching the unique `id`, `merchantName`, `amount`, `category`, `timestamp`, and `isIncome`.
4.  **DashboardScreen & ExpenseViewModel (`com.chetan.minfinance.ui`)**
    *   Renders Material 3 interface with dark ambient highlights, dynamic text sizes, modern chips, status badges, and custom touch-targeted graphic canvases.

---

## ⚙️ Development & Quickstart

### Prerequisites
*   Android Studio Ladybug (or higher)
*   Android SDK 34+
*   JDK 17

### Permissions Required
To activate background parsing, the app requires:
*   `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` (Manually granted in Android Settings -> Special App Access -> Notification Access).

### Testing inside Sandbox
Simply launch the application, navigate to the **Live Sandbox** section at the bottom, select a preset IndusInd Gmail notification snippet, and tap **Parse Notification Stream**. The expense will immediately reflect in the main financial ledger and update the monthly donut chart automatically.
