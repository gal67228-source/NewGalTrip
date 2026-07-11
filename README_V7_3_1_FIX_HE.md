# Gal Family Trips – V7.3.1

תוקנה שגיאת קומפילציה במסך הטיסות.

הבעיה:
- `FlightsScreen.kt` השתמש ב-`MetaChip`.
- הפונקציה הוגדרה כ-private בקובץ אחר.
- Kotlin אינו מאפשר גישה לפונקציה private מחוץ לקובץ שבו הוגדרה.

התיקון:
- נוסף רכיב מקומי בשם `FlightMetaChip`.
- כל הקריאות במסך הטיסות הוחלפו לרכיב המקומי.
- לא שונו פונקציות הטיסות, המלונות או בניית השלד.

Artifact:
Gal-Family-Trips-Professional-UI-V7-3-1-MetaChip-Fix-APK
