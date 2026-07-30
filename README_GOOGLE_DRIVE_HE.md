# שמירת מסמכים ב-Google Drive

המסמכים נשמרים ב-`appDataFolder` הפרטי של האפליקציה בחשבון
Google של המשתמש. הם אינם גלויים ברשימת הקבצים הרגילה ב-Drive, אך
נשארים בחשבון וזמינים לאפליקציה גם לאחר התקנה מחדש.

## הגדרה חד-פעמית

1. פתחו את פרויקט Google Cloud שמקושר ל-`google-services.json`.
2. הפעילו את **Google Drive API** ב-APIs & Services.
3. במסך OAuth consent הוסיפו את ההרשאה
   `https://www.googleapis.com/auth/drive.appdata`.
4. ודאו שללקוח Android מוגדרים שם החבילה
   `com.gal.familytrips` וחתימות SHA-1 של debug ושל release.

אין צורך להפעיל Firebase Storage, לפרוס `storage.rules` או להעביר את
פרויקט Firebase למסלול Blaze עבור שמירת המסמכים הזו.
