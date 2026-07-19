# FamilyGo – V9.4.1

## תיקון בנייה

הכשל נגרם מכך שהוספת `TripActivityEvent`
יצרה שתי אנוטציות `@Serializable` רצופות
לפני `ManagedTripMember`.

### התיקון
- כל אנוטציות serialization נורמלו ל-`@Serializable`.
- כל מופע כפול הוסר.
- בוצעה בדיקה ייעודית לכל מודלי השיתוף וה-Activity Feed.
- כל יכולות V9.4.0 נשמרו.

Artifact:
FamilyGo-V9-4-1-Activity-Feed-Annotation-Fix-APK
