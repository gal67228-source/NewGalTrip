# Gal Family Trips – V8.6.2.5

## תיקון YAML ושמירת חתימה קבועה

- הוסר בלוק Base64 רב-שורות שגרם לשגיאת YAML.
- ה-keystore נשמר כמחרוזת Base64 בשורת env חוקית.
- בכל בנייה הוא משוחזר אל `app/debug.keystore`.
- החתימה נשארת קבועה בין בניות.
- שלב keytool מציג את החתימות בלי grep.
- מספר הגרסה עודכן ל-4.6.2.5.

Artifact:
Gal-Family-Trips-V8-6-2-5-YAML-Stable-Keystore-Fix-APK
