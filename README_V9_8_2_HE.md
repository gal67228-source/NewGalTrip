# FamilyGo – V9.8.2

## תיקון בנייה

הכשל נגרם מהייבוא:

`import androidx.compose.foundation.layout.weight`

בגרסת Compose של הפרויקט, הייבוא הזה נפתר
לסמל פנימי `RowColumnParentData?.weight`.

### התיקון
- הוסר הייבוא הישיר של `weight`.
- קריאות `Modifier.weight(...)` נשארו בתוך
  `RowScope` ו-`ColumnScope`, שבהם הן זמינות כחוק.
- כל תכולת מודול המסמכים הנקי נשמרה.

Artifact:
FamilyGo-V9-8-2-Documents-Weight-Fix-APK
