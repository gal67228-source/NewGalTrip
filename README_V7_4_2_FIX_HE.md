# Gal Family Trips – V7.4.2

תוקנה שגיאת קומפילציה במנגנון הגרירה.

## מקור התקלה
הפרויקט משתמש בגרסת Compose שבה:
- `ExperimentalFoundationApi` לא היה זמין דרך ה-import הקיים.
- `animateFloatAsState` ו-`animateColorAsState` לא זוהו.
- `animateItemPlacement` דרש API ניסיוני.
- כתוצאה מכך גם חישובי scale ו-shadow נכשלו.

## התיקון
- הוסרה התלות ב-ExperimentalFoundationApi.
- הוסר animateItemPlacement.
- הוסרו animateFloatAsState ו-animateColorAsState.
- תנועת הכרטיס משתמשת ב-offset תואם.
- הידית עדיין משנה צבע בזמן גרירה.
- גרירה מעל מספר פעילויות וחישוב השעות נשארו.

Artifact:
Gal-Family-Trips-Professional-UI-V7-4-2-Drag-Compatibility-Fix-APK
