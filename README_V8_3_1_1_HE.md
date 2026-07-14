# Gal Family Trips – V8.3.1.1

## תיקון קומפילציה

מקור התקלה:
`ModalBottomSheet` מוגדר בגרסת Material 3 של הפרויקט כ-API ניסיוני.

התיקון:
- נוסף `@OptIn(ExperimentalMaterial3Api::class)` ל-`TripsScreen`.
- לא שונתה לוגיקת מנהל הטיולים.
- לא שונה פורמט `.gtrip`.
- מספר הגרסה עודכן ל-4.3.1.1.

Artifact:
Gal-Family-Trips-V8-3-1-1-Material3-OptIn-Fix-APK
