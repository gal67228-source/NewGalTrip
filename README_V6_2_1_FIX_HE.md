# Gal Family Trips – V6.2.1 Budget Fix

תוקנה שגיאת קומפילציה במסך התקציב.

הבעיה:
- `BudgetAmountDialog` נקרא מתוך `ExpensesScreen`
- אך הפונקציה עצמה לא הוגדרה
- לכן Kotlin לא הצליח להסיק גם את טיפוסי הפרמטרים של `onConfirm`

התיקון:
- נוספה פונקציית `BudgetAmountDialog`
- נוספה בחירת מטבע
- נוספה הזנת סכום עם אימות
- נשמר תאריך הסעיף האוטומטי

Artifact:
Gal-Family-Trips-Professional-UI-V6-2-1-Budget-Fixed-APK
