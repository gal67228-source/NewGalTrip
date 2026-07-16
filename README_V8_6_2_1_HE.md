# Gal Family Trips – V8.6.2.1

## תיקון Google Sign-In

מקור התקלה:
GitHub Actions יצר מפתח Debug חדש בכל בנייה.
כתוצאה מכך חתימת ה-APK השתנתה ו-Firebase לא זיהה אותה.

## התיקון

- נוסף `app/debug.keystore` קבוע.
- כל בניות ה-Debug נחתמות באותו מפתח.
- GitHub Actions מציג את החתימות הקבועות.
- נוסף fallback לזרימת Google המפורשת כאשר
  Credential Manager מחזיר `NoCredentialException`.

## חתימות Firebase החדשות

SHA-1:
85:01:09:D8:12:D6:2C:B2:B1:0D:FA:A5:C8:BF:20:81:A3:29:0B:1D

SHA-256:
D9:D9:A8:6F:16:29:5C:16:95:47:FB:B8:64:A2:BD:94:AF:6F:0E:F7:2A:71:3C:BD:22:63:2B:EA:E2:4E:D5:64

יש להוסיף את שתיהן ב-Firebase ולהוריד מחדש
את `google-services.json`.

Artifact:
Gal-Family-Trips-V8-6-2-1-Stable-Google-SignIn-APK
