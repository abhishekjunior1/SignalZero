package com.medic.app.ui.calm

/**
 * The app's top-level destinations.
 *
 * Extracted from SafeGuideApp.kt during the multiplatform migration. It was
 * declared inside a Compose file, but it is plain data with no UI dependency,
 * and non-UI code (DemoScenarios) needs it — so it belongs in shared code.
 * The package is deliberately unchanged so existing imports still resolve.
 */
enum class SgScreen(val title: String) {
    HOME("SignalZero"),
    ASSISTANT("Assistant"),
    TRANSLATE("Translate"),
    LOCATION("My location"),
    MEDICAL("Medical help"),
    HOSPITAL("Nearby hospital"),
    MESH("Nearby people")
}
