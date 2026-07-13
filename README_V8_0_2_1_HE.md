# Gal Family Trips – V8.0.2.1

## תיקון קומפילציה

הבנייה נכשלה משום שהקובץ `GoogleRoutesClient.kt` השתמש
בפונקציה `resolvedTransitionMode`, בזמן שהפונקציה הוגדרה
כ-`private` בתוך `MainActivity.kt`.

התיקון:
- הרשאת הפונקציה שונתה מ-`private` ל-`internal`.
- כעת כל הקבצים בחבילת `com.gal.familytrips` יכולים להשתמש בה.
- לא שונתה לוגיקת Google Routes.
- לא שונו Secrets או הגדרות Cloudflare.
- מספר הגרסה עודכן ל-4.0.2.1.

Artifact:
Gal-Family-Trips-V8-0-2-1-Google-Routes-Compile-Fix-APK
