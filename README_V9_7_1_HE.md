# FamilyGo – V9.7.1

## תיקון CI

הכשל נגרם מהזחה שגויה בתוך בלוק `run: |`
בקובץ `.github/workflows/build-apk.yml`.

### התיקון
- תוקנה ההזחה בבדיקת ROUTES_WORKER_URL.
- גם ROUTES_APP_TOKEN הפך לאופציונלי.
- אם אחד הסודות חסר, מתקבלת אזהרה בלבד.
- קובץ ה-YAML עבר בדיקת parsing בהצלחה.
- כל יכולות V9.7.0 נשמרו.

Artifact:
FamilyGo-V9-7-1-YAML-Fix-APK
