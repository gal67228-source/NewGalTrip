# תיקון JVM Target

הבנייה נכשלה כי:
- Java השתמש ב-1.8
- Kotlin השתמש ב-17

בגרסה זו הוגדרו שניהם ל-17:

```gradle
compileOptions {
    sourceCompatibility JavaVersion.VERSION_17
    targetCompatibility JavaVersion.VERSION_17
}

kotlinOptions {
    jvmTarget = '17'
}

kotlin {
    jvmToolchain(17)
}
```

ה-Artifact החדש:
`Gal-Family-Trips-Clean-JVM17-APK`
