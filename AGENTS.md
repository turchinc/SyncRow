# AI Agent Guidelines for SyncRow

This document outlines the rules and standards that AI agents must follow when contributing to the SyncRow project.

## 1. Localization & Tone (i18n)
- **Informal Address**: Always use the informal version of "you" in all translations (e.g., *tu* in French, *du* in German, *tú* in Spanish). Avoid formal address (*vous/Sie/Usted*).
- **Supported Languages**: The app officially supports English (EN), French (FR), German (DE), Spanish (ES), and Italian (IT).
- **No Hardcoded Strings**: All user-facing text must be placed in `res/values/strings.xml` and translated in the corresponding `values-xx` folders.
- **Dynamic Content**: Use string placeholders (e.g., `%1$s`) for dynamic values to ensure proper sentence structure across languages.

## 2. Architectural Standards
- **UI Layer**: Built exclusively with **Jetpack Compose**. Use `Material3` components.
- **State Management**: Use `AndroidViewModel` with `StateFlow` and `SharedFlow`. 
- **Hardware Layer (HAL)**: Bluetooth interactions must use **RxAndroidBle**. Implementations must satisfy the `IRowingMachine` or `IHeartRateMonitor` interfaces.
- **Persistence**: Use **Room DB**. Always increment the database version and provide a migration path (or fallback) when changing entities.

## 3. Code Style & Formatting
- **Spotless**: The project uses Spotless with `ktfmt` (Google Style). You **must** run `./gradlew spotlessApply` after modifying any Kotlin files to ensure formatting consistency and avoid noisy git diffs.
- **Tone**: Keep code concise, modern (Kotlin-first), and consistent with existing patterns.

## 4. Hardware Parsing
- **FTMS (0x2AD1)**: The rower parser is dynamic. Always read the 2-byte flags first to determine field offsets. Pay special attention to the **UINT24 Total Distance** field which shifts subsequent offsets.
- **Persistence**: Hardware MAC addresses must be saved per-user in `SharedPreferences`.

## 5. Documentation & Maintenance
- **Status Tracking**: After implementing a major feature, update `STATUS.md`.
- **Project Vision**: Ensure `README.md` remains the "Source of Truth" for the project's technical specification and roadmap.
- **Clean Exits**: Always ensure Bluetooth resources and background jobs are released in `onCleared()` or via lifecycle-aware components to prevent system-level security exceptions.
