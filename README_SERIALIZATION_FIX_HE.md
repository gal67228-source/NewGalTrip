# תיקון Kotlin Serialization

הקוד משתמש ב:
- `@Serializable`
- `AppState.serializer()`
- `Trip.serializer()`

לכן חייבים להפעיל את פלאגין Kotlin Serialization.

נוסף ל-build.gradle הראשי:

```gradle
id 'org.jetbrains.kotlin.plugin.serialization' version '2.0.21' apply false
```

ונוסף ל-app/build.gradle:

```gradle
id 'org.jetbrains.kotlin.plugin.serialization'
```

ה-Artifact החדש:
`Gal-Family-Trips-Native-Features-Fixed-APK`

אם הבנייה עדיין נכשלת, GitHub יעלה Artifact נוסף בשם:
`Kotlin-Build-Log`
