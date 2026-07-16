# Gal Family Trips – V8.6.2.4

## תיקון keystore חסר ב-GitHub

מקור התקלה:
`app/debug.keystore` לא הגיע למאגר GitHub, ולכן שלב
החתימות נכשל לפני תחילת הבנייה.

## התיקון
- ה-keystore הקבוע מקודד בתוך GitHub Actions כ-Base64.
- בכל בנייה הוא משוחזר אוטומטית אל:
  `app/debug.keystore`
- מתבצעת בדיקה שהקובץ קיים ואינו ריק.
- לאחר מכן מוצגות חתימות Firebase וה-APK נבנה
  עם אותו מפתח קבוע.
- ה-SHA אינו משתנה בין בניות.

Artifact:
Gal-Family-Trips-V8-6-2-4-Embedded-Keystore-APK
