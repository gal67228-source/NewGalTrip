# Gal Family Trips – V7.6 Google Places Hotel Search

## מה השתנה
- חיפוש המלונות עבר מ-Android Geocoder ל-Google Places Autocomplete.
- נפתח חלון חיפוש רשמי של Google.
- תוצאות החיפוש כוללות בתי מלון ועסקים אמיתיים מ-Google Maps.
- בחירת מלון מחזירה:
  - שם המלון
  - כתובת
  - Place ID
  - מיקום
- נוצר קישור Google Maps מדויק באמצעות `query_place_id`.
- היעד של תאריך הצ׳ק-אין מתווסף לשאילתת החיפוש הראשונית.
- נשארה אפשרות לתקן שם וכתובת ידנית.

## הגדרת GitHub
יש ליצור Repository Secret בשם:

`PLACES_API_KEY`

ב-GitHub:
1. Settings
2. Secrets and variables
3. Actions
4. New repository secret
5. Name: `PLACES_API_KEY`
6. Value: מפתח Google Maps Platform

יש להפעיל בפרויקט Google Cloud:
- Places API
- Billing

מומלץ להגביל את המפתח:
- Application restriction: Android apps
- Package name: `com.gal.familytrips.debug` לבניית Debug
- להוסיף SHA-1 של מפתח החתימה
- API restriction: Places API בלבד

ה-Workflow ייכשל עם הודעה ברורה אם הסוד אינו מוגדר.

Artifact:
Gal-Family-Trips-Professional-UI-V7-6-Google-Places-Hotel-Search-APK
