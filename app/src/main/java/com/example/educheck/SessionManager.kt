package com.example.educheck

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * מחלקת SessionManager לניהול מידע המשתמש והתחברות
 * מאפשרת שמירת פרטי המשתמש ומצב Remember Me
 */
class SessionManager(context: Context) {

    // מאפיינים פרטיים
    private val pref: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val editor: SharedPreferences.Editor = pref.edit()

    companion object {
        // קבועים לשמות המפתחות בשמירה
        private const val PREF_NAME = "EduCheckLoginPrefs"
        private const val KEY_IS_LOGGED_IN = "isLoggedIn"
        private const val KEY_USER_ID = "userId"
        private const val KEY_EMAIL = "email"
        private const val KEY_ROLE = "role"
        private const val KEY_REMEMBER_ME = "rememberMe"
        private const val TAG = "SessionManager"
    }

    /**
     * שמירת פרטי התחברות
     * @param userId מזהה המשתמש
     * @param email כתובת האימייל
     * @param role תפקיד המשתמש (student/teacher)
     * @param rememberMe האם לזכור את פרטי המשתמש
     */
    fun createLoginSession(userId: String, email: String, role: String, rememberMe: Boolean) {
        try {
            // אם המשתמש בחר ב-Remember Me, נשמור את כל הפרטים
            if (rememberMe) {
                editor.putBoolean(KEY_IS_LOGGED_IN, true)
                editor.putString(KEY_USER_ID, userId)
                editor.putString(KEY_EMAIL, email)
                editor.putString(KEY_ROLE, role)
                editor.putBoolean(KEY_REMEMBER_ME, true)
            } else {
                // אם המשתמש לא בחר ב-Remember Me, נשמור רק מידע זמני ונסמן שאין לזכור אותו
                editor.putBoolean(KEY_IS_LOGGED_IN, false)  // לא נשמור מצב התחברות
                editor.putString(KEY_EMAIL, "")  // לא נשמור אימייל
                editor.putBoolean(KEY_REMEMBER_ME, false)
            }

            editor.apply()

            Log.d(TAG, "Login session created for user: $userId, role: $role, rememberMe: $rememberMe")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating login session: ${e.message}")
        }
    }

    /**
     * בדיקה האם המשתמש מחובר
     * @return האם המשתמש מחובר
     */
    fun isLoggedIn(): Boolean {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    /**
     * בדיקה האם Remember Me מופעל
     * @return האם Remember Me מופעל
     */
    fun isRememberMeEnabled(): Boolean {
        return pref.getBoolean(KEY_REMEMBER_ME, false)
    }

    /**
     * קבלת פרטי המשתמש המחובר
     * @return מפה עם כל פרטי המשתמש
     */
    fun getUserDetails(): HashMap<String, String?> {
        val user = HashMap<String, String?>()
        user["userId"] = pref.getString(KEY_USER_ID, null)
        user["email"] = pref.getString(KEY_EMAIL, null)
        user["role"] = pref.getString(KEY_ROLE, null)
        return user
    }

    /**
     * קבלת תפקיד המשתמש
     * @return תפקיד המשתמש (student/teacher)
     */
    fun getUserRole(): String {
        return pref.getString(KEY_ROLE, "") ?: ""
    }

    /**
     * קבלת מזהה המשתמש
     * @return מזהה המשתמש
     */
    fun getUserId(): String {
        return pref.getString(KEY_USER_ID, "") ?: ""
    }

    /**
     * קבלת כתובת האימייל
     * @return כתובת האימייל
     */
    fun getUserEmail(): String {
        return pref.getString(KEY_EMAIL, "") ?: ""
    }

    /**
     * ניקוי נתוני ההתחברות (התנתקות)
     */
    fun logoutUser() {
        try {
            // אם Remember Me מופעל, נשמור רק את האימייל
            val rememberMe = isRememberMeEnabled()
            val email = getUserEmail()

            // ניקוי כל הנתונים
            editor.clear()
            editor.apply()

            // אם Remember Me מופעל, נשמור בחזרה את האימייל ומצב הזכירה
            if (rememberMe) {
                editor.putString(KEY_EMAIL, email)
                editor.putBoolean(KEY_REMEMBER_ME, true)
                editor.apply()
                Log.d(TAG, "Logout: Remember Me is enabled, email saved for next login")
            } else {
                Log.d(TAG, "Logout: All session data cleared")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during logout: ${e.message}")
        }
    }
}