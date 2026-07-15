# Gal Family Trips – V8.6.1.1

## תיקון תאימות Kotlin/Firebase

מקור התקלה:
Firebase BoM 34.15.0 משך `firebase-auth 24.1.0`, שנבנה עם metadata של Kotlin 2.3.
הפרויקט משתמש ב-Kotlin 2.0 ולכן הקומפילציה נכשלה.

התיקון:
- הוסר Firebase BoM 34.15.0.
- הוצמד `firebase-auth:23.2.1`.
- הוצמד `firebase-firestore:25.1.4`.
- שאר שילוב Firebase נשאר ללא שינוי.

Artifact:
Gal-Family-Trips-V8-6-1-1-Kotlin-Firebase-Compat-APK
