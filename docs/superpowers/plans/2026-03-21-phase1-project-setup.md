# Phase 1: Project Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the scaffold package to `com.familymeal.assistant`, update build configuration with all required dependencies (Hilt, Room, KSP, Navigation, OkHttp, Security Crypto), and create the `FamilyMealApp` Application class.

**Architecture:** Single-module Android app. AGP 9.1.0 / Kotlin 2.2.10. KSP replaces kapt for annotation processing (required for Hilt + Room on modern Kotlin). All dependency versions go in `libs.versions.toml`; nothing hardcoded in `build.gradle.kts`.

**Tech Stack:** Kotlin 2.2.10, AGP 9.1.0, Hilt 2.52, Room 2.7.1, KSP, Navigation Compose 2.8.9, OkHttp 4.12.0, Security Crypto 1.0.0, Gson 2.11.0, Coroutines 1.9.0, Turbine 1.2.0, MockK 1.13.13

---

## File Map

| Action | File |
|---|---|
| Modify | `gradle/libs.versions.toml` |
| Modify | `build.gradle.kts` (root) |
| Modify | `app/build.gradle.kts` |
| Modify | `app/src/main/AndroidManifest.xml` |
| Rename dir | `app/src/main/java/com/wtc/whatscooking/` → `app/src/main/java/com/familymeal/assistant/` |
| Rename dir | `app/src/test/java/com/wtc/whatscooking/` → `app/src/test/java/com/familymeal/assistant/` |
| Rename dir | `app/src/androidTest/java/com/wtc/whatscooking/` → `app/src/androidTest/java/com/familymeal/assistant/` |
| Modify | `app/src/main/java/com/familymeal/assistant/MainActivity.kt` (update package) |
| Modify | `app/src/main/res/values/themes.xml` (rename theme) |
| Create | `app/src/main/java/com/familymeal/assistant/FamilyMealApp.kt` |

---

### Task 1: Update `libs.versions.toml` with all dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Replace the entire `libs.versions.toml` with the full dependency catalog**

```toml
[versions]
agp = "9.1.0"
kotlin = "2.2.10"
ksp = "2.2.10-1.0.31"
coreKtx = "1.16.0"
junit = "4.13.2"
junitVersion = "1.2.1"
espressoCore = "3.6.1"
lifecycleRuntimeKtx = "2.9.0"
activityCompose = "1.10.1"
composeBom = "2025.05.00"
navigationCompose = "2.9.0"
hilt = "2.56.1"
hiltNavigationCompose = "1.2.0"
room = "2.7.1"
coroutines = "1.10.2"
okhttp = "4.12.0"
gson = "2.11.0"
securityCrypto = "1.0.0"
turbine = "1.2.0"
mockk = "1.13.13"
robolectric = "4.13"
cameraX = "1.4.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
gson = { group = "com.google.code.gson", name = "gson", version.ref = "gson" }
security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "securityCrypto" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
camera-core = { group = "androidx.camera", name = "camera-core", version.ref = "cameraX" }
camera-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "cameraX" }
camera-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "cameraX" }
camera-view = { group = "androidx.camera", name = "camera-view", version.ref = "cameraX" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 2: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build: add all MVP dependencies to version catalog"
```

---

### Task 2: Update root `build.gradle.kts`

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Replace root build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
}
```

- [ ] **Step 2: Commit**

```bash
git add build.gradle.kts
git commit -m "build: add Hilt and KSP plugins to root build script"
```

---

### Task 3: Update `app/build.gradle.kts`

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Replace app/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.familymeal.assistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.familymeal.assistant"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.coroutines.android)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Network + JSON
    implementation(libs.okhttp)
    implementation(libs.gson)

    // Security
    implementation(libs.security.crypto)

    // Camera
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.room.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

- [ ] **Step 2: Sync project in Android Studio / run `./gradlew :app:dependencies`**

Expected: BUILD SUCCESSFUL with no unresolved dependency errors.

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: configure app module with Hilt, Room, KSP, Navigation, Camera, OkHttp"
```

---

### Task 4: Rename package directories and update package declarations

**Files:**
- Rename: `app/src/main/java/com/wtc/whatscooking/` → `app/src/main/java/com/familymeal/assistant/`
- Rename: `app/src/test/java/com/wtc/whatscooking/` → `app/src/test/java/com/familymeal/assistant/`
- Rename: `app/src/androidTest/java/com/wtc/whatscooking/` → `app/src/androidTest/java/com/familymeal/assistant/`

- [ ] **Step 1: Create new directory structure and move files**

Use Android Studio "Refactor → Rename" on the `com.wtc.whatscooking` package, or run:
```bash
# From project root
cp -r "app/src/main/java/com/wtc/whatscooking/." \
      "app/src/main/java/com/familymeal/assistant/"
cp -r "app/src/test/java/com/wtc/whatscooking/." \
      "app/src/test/java/com/familymeal/assistant/"
cp -r "app/src/androidTest/java/com/wtc/whatscooking/." \
      "app/src/androidTest/java/com/familymeal/assistant/"
```

- [ ] **Step 2: Update package declarations in every moved file**

In each file, change `package com.wtc.whatscooking` → `package com.familymeal.assistant` (and sub-packages accordingly).

Files to update:
- `app/src/main/java/com/familymeal/assistant/MainActivity.kt`
- `app/src/main/java/com/familymeal/assistant/ui/theme/Color.kt`
- `app/src/main/java/com/familymeal/assistant/ui/theme/Theme.kt`
- `app/src/main/java/com/familymeal/assistant/ui/theme/Type.kt`
- `app/src/test/java/com/familymeal/assistant/ExampleUnitTest.kt`
- `app/src/androidTest/java/com/familymeal/assistant/ExampleInstrumentedTest.kt`

- [ ] **Step 3: Delete old package directories**

```bash
rm -rf "app/src/main/java/com/wtc"
rm -rf "app/src/test/java/com/wtc"
rm -rf "app/src/androidTest/java/com/wtc"
```

- [ ] **Step 4: Update AndroidManifest.xml**

In `app/src/main/AndroidManifest.xml`, update theme reference:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />

    <application
        android:name=".FamilyMealApp"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.FamilyMealAssistant">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.FamilyMealAssistant">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 5: Update `res/values/themes.xml`**

Rename `Theme.WhatsCooking` → `Theme.FamilyMealAssistant` in both `themes.xml` files.

- [ ] **Step 6: Update `res/values/strings.xml`**

```xml
<string name="app_name">What's Cooking?</string>
```

- [ ] **Step 7: Verify build compiles**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: rename package to com.familymeal.assistant"
```

---

### Task 5: Create `FamilyMealApp` Application class with Hilt

**Files:**
- Create: `app/src/main/java/com/familymeal/assistant/FamilyMealApp.kt`
- Modify: `app/src/main/java/com/familymeal/assistant/MainActivity.kt`

- [ ] **Step 1: Create FamilyMealApp.kt**

```kotlin
package com.familymeal.assistant

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FamilyMealApp : Application()
```

- [ ] **Step 2: Add `@AndroidEntryPoint` to MainActivity**

```kotlin
package com.familymeal.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.familymeal.assistant.ui.theme.FamilyMealAssistantTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FamilyMealAssistantTheme {
                // Navigation host added in Phase 4
            }
        }
    }
}
```

- [ ] **Step 3: Rename theme in `Theme.kt`**

In `ui/theme/Theme.kt`, rename the composable from `WhatsCookingTheme` to `FamilyMealAssistantTheme`.

- [ ] **Step 4: Build and verify Hilt compiles**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL — Hilt generates `FamilyMealApp_HiltComponents.java` without errors.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/FamilyMealApp.kt \
        app/src/main/java/com/familymeal/assistant/MainActivity.kt \
        app/src/main/java/com/familymeal/assistant/ui/theme/Theme.kt
git commit -m "feat: add FamilyMealApp with Hilt, annotate MainActivity"
```

---

### Task 6: Create `.gitignore` entries for generated files

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: Append to `.gitignore`**

```
# Superpowers brainstorm
.superpowers/
```

- [ ] **Step 2: Commit**

```bash
git add .gitignore
git commit -m "chore: ignore .superpowers brainstorm dir"
```
