# FamilyGo – V9.7.2

## תיקון קומפילציה

תוקנו שתי השגיאות מהבנייה:

1. חלון ההתראות הוכנס בטעות לתוך `LaunchedEffect`,
   ולכן נוצרה קריאת Composable מתוך coroutine.
   החלון הועבר חזרה לאזור ה-Composable המתאים.

2. `AppState` לא היה מסומן ב-`@Serializable`,
   ולכן `AppState.serializer()` ב-TripStore לא היה זמין.

כל יכולות V9.7 נשמרו.

Artifact:
FamilyGo-V9-7-2-Compose-Serialization-Fix-APK
