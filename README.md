# Mega GPS Simulator

Simulate a GPS location, or movement along a route, on Android **without root**, using Android's
official *mock location* mechanism (`LocationManager` test providers).
Kotlin · Jetpack Compose · Material 3 · Foreground Service · Coroutines/Flow · DataStore · osmdroid (OpenStreetMap).

## Build the APK on GitHub (no Android Studio needed)
1. Create a new GitHub repository and upload **all** files of this folder (including the hidden `.github` folder).
2. Push to `main` (or open the **Actions** tab > *Build APK* > **Run workflow**).
3. When the run finishes, open it and download the artifact **MegaGPSSimulator-debug-apk** (a zip containing `app-debug.apk`).
4. Copy the APK to the phone and install it (allow "install unknown apps" for your file manager/browser).

## Build locally
- Android Studio (Koala or newer) > *Open* this folder > *Run*. Or from a terminal with JDK 17 and Gradle 8.9+:
  `gradle :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- To create the Gradle wrapper once: `gradle wrapper --gradle-version 8.9`

## First-time setup on the phone
1. Settings > About phone > tap the OS / MIUI version 7 times (enables Developer options).
2. Developer options > **Select mock location app** > *Mega GPS Simulator*.
3. Open the app, allow location and notifications, turn Location (GPS) on.
4. Pick a point on the map (or enter coordinates) > **Simulate here**, or build a route in **Planner** > **Start route**.
The app guides you through this and shows warnings when something is missing. It never enables mock location by itself.

### Xiaomi / MIUI / HyperOS
Settings > Apps > Mega GPS Simulator: allow **Autostart**, set Battery saver to **No restrictions**, allow notifications.
*Settings > Settings screen in the app* has buttons that open these system pages (they only open the pages).

## Architecture
```
domain/   GeoPoint, Geo (distance/bearing), RoutePath, MovementEngine (pure Kotlin, unit-tested)
data/     Repository (DataStore), Json, GpxParser, PlaceSearchService + RoutingService (optional, behind interfaces)
service/  SimulationService (foreground, type=location), MockLocationProvider, SimulationHub, RealLocation
ui/       Compose screens (Map, Planner, Places, Routes, Settings, About), MainViewModel, OsmMap
```
- **Movement**: distance-along-route model; 4 km/h = 1.11 m/s. Speed ramps with per-mode acceleration, heading is
  rate-limited, speed drops before sharp corners, braking before the end. Pause/resume, reverse, restart/replay, live speed change.
- **Human walking** (2–6 km/h): mean-reverting speed noise (max ±20 %) + optional short stops. Position never leaves the route.
- **Background**: foreground service with notification (Pause/Resume, Stop) + partial wake lock. `START_NOT_STICKY`:
  if the system or user kills it, it does **not** restart on its own. Nothing starts after reboot.
- **No API keys** anywhere. Map tiles: OpenStreetMap. Search: Nominatim. Online routing: FOSSGIS OSRM. Replace
  `NominatimSearch` / `OsrmRouting` in `data/Net.kt` to use another provider.
- Language: English + Persian (RTL). On Android 13+ use Settings > Language in the app (per-app language); older versions follow the system language.

## Limitations (also shown in the app)
- Mock location is a development/testing feature. Apps can detect it and may also use Wi-Fi, cellular, IP or sensors.
  This app does not claim to hide your real location from every app.
- The simulation ends if the app is force-stopped or Android terminates the service.
- Location is delivered through GPS + network test providers (no Google Play Services needed). `FusedLocationProviderClient`
  is intentionally not used, so the app also works on devices without GMS; apps using the fused provider still receive the mocked fixes.
- Place search and online routing need internet; the public servers have fair-use limits.

## Tests
`gradle :app:testDebugUnitTest` runs the engine tests (speed 4 km/h ≈ 1.11 m/s, no jumps, completion, reverse, speed ramp).
