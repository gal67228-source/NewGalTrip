# FamilyGo – V9.7.0

גרסה משולבת של V9.5, V9.6 ו-V9.7.

## התראות
- מרכז התראות בתוך האפליקציה.
- התראות נשמרות לפי משתמש ב-Firestore.
- סימון התראה כנקראה.
- כיבוד הגדרת ההתראות הקיימת.

## קישורים מקצועיים
- קישורים בפורמט:
  https://familygo.app/invite/XXXXXX
- Android App Links עם autoVerify.
- תמיכה מקבילה בקישור familygo:// הישן.
- נוספה תבנית assetlinks.json ועמוד fallback.

## סנכרון עמיד
- תור סנכרון נשמר ב-SharedPreferences.
- שינוי נכשל נשמר גם לאחר סגירת האפליקציה.
- שינויים לאותו טיול מתאחדים.
- ניסיון חוזר אוטומטי בפתיחת האפליקציה.
- נשמרים מספר ניסיונות והשגיאה האחרונה.

## CI
- ROUTES_WORKER_URL אינו חוסם יותר את הבנייה.
- אם הסוד חסר, מתקבלת אזהרה בלבד.

חשוב:
1. לפרסם מחדש firestore.rules.
2. לפרסם את web/.well-known/assetlinks.json לאחר החלפת טביעת SHA-256.
3. להפנות familygo.app/invite/* לעמוד ה-fallback.

Artifact:
FamilyGo-V9-7-0-Notifications-Links-Reliable-Sync-APK
