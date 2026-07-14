# Gal Family Trips – V8.6.1

## Firebase Authentication + Firestore

- שולב `google-services.json`.
- נוסף Google Services Gradle Plugin.
- נוספו Firebase Authentication ו-Cloud Firestore.
- הכניסה ל-Google משתמשת ב-Credential Manager.
- ניתן להעלות את הטיול הפעיל ל-Firestore.
- ניתן לבצע סנכרון ידני.
- האפליקציה מאזינה לשינויים בטיול בענן.
- נוסף קובץ `firestore.rules` עם הרשאות:
  - owner
  - editor
  - viewer
- הוסר `applicationIdSuffix .debug` כדי להתאים לחבילה
  הרשומה ב-Firebase: `com.gal.familytrips`.

## לפני שימוש משותף

יש לפרסם את `firestore.rules` ב-Firebase Console.
הזמנת בן משפחה וקוד הצטרפות ייכנסו ל-V8.6.2.

Artifact:
Gal-Family-Trips-V8-6-1-Firebase-Auth-Firestore-APK
