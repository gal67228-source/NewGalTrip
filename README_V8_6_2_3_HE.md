# Gal Family Trips – V8.6.2.3

## תיקון GitHub Actions

הכשל היה בשלב הצגת חתימות Firebase:
`grep` לא מצא את פורמט השורות של `keytool` והחזיר exit code 1.

### התיקון
- הוסר `grep`.
- פלט `keytool` נשמר ומוצג במלואו.
- נוספה בדיקה שה-keystore הקבוע קיים.
- החתימות הקבועות מודפסות במפורש.
- שלב הבנייה ממשיך לאחר הדוח.

Artifact:
Gal-Family-Trips-V8-6-2-3-SHA-Workflow-Fix-APK
