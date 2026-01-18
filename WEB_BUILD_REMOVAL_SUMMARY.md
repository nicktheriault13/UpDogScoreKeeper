# Web Build Removal Summary

## Date: January 18, 2026

## Changes Made

Successfully removed all web build related files and configurations from the UpDogScoreKeeper project. The project now only supports Android and Desktop (JVM) builds.

### Files Removed:
1. **Source Folders:**
   - `composeApp/src/jsMain/` - Entire Kotlin/JS source set and all files
   - `composeApp/src/wasmJsMain/` - Entire Kotlin/Wasm source set and all files
     - Including custom Voyager shim implementations
     - All platform-specific actual implementations

2. **Build Artifacts:**
   - `composeApp/build/dist/` - Web distribution folders
   - `build/js/` - JS build artifacts and node_modules

3. **Temporary Files:**
   - `tmp/*wasm*.log` - Wasm build logs
   - `tmp/*js*.log` - JS build logs

### Configuration Changes:

1. **build.gradle.kts:**
   - Removed `js(IR)` target configuration
   - Removed `wasmJs` target configuration
   - Removed `jsMain` source set dependencies
   - Removed `wasmJsMain` source set dependencies
   - Removed `@OptIn(ExperimentalWasmDsl::class)` annotation
   - Removed `ExperimentalWasmDsl` import

2. **gradle.properties:**
   - Removed `org.jetbrains.compose.experimental.wasm.enabled=true`

### Remaining Targets:

The project now only supports:
- ✅ **Android** - Full app with all features
- ✅ **Desktop (JVM)** - Full app with all features
- ✅ **iOS** (on macOS only) - Framework build

### Build Commands:

**Android:**
```bash
.\gradlew.bat :composeApp:assembleDebug
.\gradlew.bat :composeApp:assembleRelease
```

**Desktop:**
```bash
.\gradlew.bat :composeApp:run
.\gradlew.bat :composeApp:packageDistributionForCurrentOS
```

### Reason for Removal:

The web builds (both Kotlin/JS and Kotlin/Wasm) had significant compatibility issues with:
- Compose Multiplatform 1.6.0
- Kotlin 1.9.22
- Skiko Canvas renderer integration
- Missing native bindings (`org_jetbrains_skia_Paint__1nMake is not defined`)

These issues would require:
- Upgrading to Kotlin 2.0+ and Compose 1.6.11+
- Extensive testing to ensure Android/Desktop compatibility
- Significant additional development time

### Next Steps:

If web support is needed in the future:
1. Consider upgrading to Kotlin 2.0+ and Compose Multiplatform 1.7+
2. Or create a separate web-specific UI using Compose HTML (DOM-based, not Canvas)
3. Or provide Android/Desktop apps via direct downloads instead of web deployment
