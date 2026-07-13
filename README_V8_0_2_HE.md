# Gal Family Trips – V8.0.2 Google Routes

גרסה זו מקבלת זמן מסלול אמיתי ופירוט דרך מ-Google Routes API דרך Cloudflare Worker.

## סודות GitHub הנדרשים
- ROUTES_WORKER_URL – כתובת ה-Worker, ללא /route בסוף.
- ROUTES_APP_TOKEN – אותו טוקן שהוגדר ב-Worker.

## סודות Cloudflare Worker
- GOOGLE_ROUTES_API_KEY – מפתח Google Cloud המוגבל ל-Routes API.
- APP_ROUTES_TOKEN – טוקן אקראי ארוך שמגן על ה-Worker.

## התנהגות האפליקציה
- מסלול מחושב אוטומטית רק כאשר זוג פעילויות השתנה.
- תוצאה נשמרת באפליקציה ל-12 שעות.
- ה-Worker שומר Cache ל-6 שעות.
- במקרה כשל נשאר חישוב ההערכה הקודם.
- זמן Google נכנס לחישוב ציר הזמן.
- תחבורה ציבורית מציגה קווים, תחנות, כיוון ומספר תחנות כאשר Google מחזירה אותם.
- בכל מעבר נשארים קישורי Google Maps ו-Waze.

Artifact:
Gal-Family-Trips-V8-0-2-Google-Routes-APK
