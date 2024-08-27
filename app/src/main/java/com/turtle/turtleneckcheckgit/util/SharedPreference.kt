package handasoft.mobile.divination.module.pref

import android.content.Context
import androidx.preference.PreferenceManager
import java.util.*

object SharedPreference {
    fun setSharedPreferenceStringArrList(pContext: Context?, pKey: String, pData: Array<String?>) {
        if (pContext == null) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(pContext)
        val editor = prefs.edit()
        try {
            editor.putInt(pKey + "size", pData.size)
            editor.commit()
            for (i in pData.indices) {
                val prefs1 = PreferenceManager.getDefaultSharedPreferences(pContext)
                val editor1 = prefs1.edit()
                editor1.putString(pKey + i, pData[i])
                editor1.commit()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun getSharedPreferenceStringArrList(pContext: Context?, pKey: String): Array<String?>? {
        if (pContext == null) return null
        val prefs = PreferenceManager.getDefaultSharedPreferences(pContext)
        val editor = prefs.edit()
        val size = prefs.getInt(pKey + "size", 0)
        val list = arrayOfNulls<String>(size)
        try {
            for (i in 0 until size) {
                list[i] = PreferenceManager.getDefaultSharedPreferences(pContext).getString(pKey + i, "")
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return list
    }

    fun setSharedPreferenceStringList(pContext: Context?, pKey: String, idx: String) {
        if (pContext == null) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(pContext)
        val editor = prefs.edit()
        try {
            val pData = ArrayList<String?>()
            val size = prefs.getInt(pKey + "size", 0)
            for (i in 0 until size) {
                pData.add(prefs.getString(pKey + i, ""))
            }
            pData.add(idx)
            editor.putInt(pKey + "size", pData.size)
            editor.commit()
            for (i in pData.indices) {
                val prefs1 = PreferenceManager.getDefaultSharedPreferences(pContext)
                val editor1 = prefs1.edit()
                editor1.putString(pKey + i, pData[i])
                editor1.commit()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
    fun setSharedPreferenceStringList(pContext: Context?, pKey: String, pData: ArrayList<String>) {
        if (pContext == null) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(pContext)
        val editor = prefs.edit()
        try {
            editor.putInt(pKey + "size", pData.size)
            editor.commit()
            for (i in pData.indices) {
                val prefs1 = PreferenceManager.getDefaultSharedPreferences(pContext)
                val editor1 = prefs1.edit()
                editor1.putString(pKey + i, pData[i])
                editor1.commit()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
    fun getSharedPreferenceStringList(pContext: Context?, pKey: String): ArrayList<String>? {
        if (pContext == null) return null
        val prefs = PreferenceManager.getDefaultSharedPreferences(pContext)
        val list = ArrayList<String>()
        try {
            val size = prefs.getInt(pKey + "size", 0)
            for (i in 0 until size) {
                list.add(prefs.getString(pKey + i, "")!!)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return list
    }
    fun putSharedPreference(context: Context?, key: String?, value: String?) {
        if (context == null) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        editor.putString(key, value)
        editor.commit()
    }
    fun putSharedPreference(context: Context?, key: String?, value: Boolean) {
        if (context == null) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        editor.putBoolean(key, value)
        editor.commit()
    }
    fun putSharedPreference(context: Context?, key: String?, value: Int) {
        if (context == null) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        editor.putInt(key, value)
        editor.commit()
    }
    fun putSharedPreference(context: Context?, key: String?, value: Float) {
        if (context == null) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        editor.putFloat(key, value)
        editor.commit()
    }
    fun putSharedPreference(context: Context?, key: String?, value: Long) {
        if (context == null) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        editor.putLong(key, value)
        editor.commit()
    }


    fun getLongSharedPreference(context: Context?, key: String?): Long {
        if (context == null) return 0
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getLong(key, 0)
    }
    fun getSharedPreference(context: Context?, key: String?): String? {
        if (context == null) return null
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString(key, null)
    }
    fun getSharedPreference(context: Context?, key: String?, defaultValue: String?): String? {
        if (context == null) return null
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString(key, defaultValue)
    }
    fun getBooleanSharedPreference(context: Context?, key: String?): Boolean {
        if (context == null) return false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(key, false)
    }
    fun getDefaultBooleanSharedPreference(context: Context?, key: String?): Boolean {
        if (context == null) return false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(key, true)
    }
    fun getBooleanSharedPreference(context: Context?, key: String?,result : Boolean): Boolean {
        if(context == null) return false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(key,result)
    }

    fun getIntSharedPreference(context: Context?, key: String?, defaultVal: Int): Int {
        if (context == null) return defaultVal
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getInt(key, defaultVal)
    }
    fun getIntSharedPreference(context: Context?, key: String?): Int {
        if (context == null) return 0
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getInt(key, 0)
    }

    fun getDefaultIntSharedPreference(context: Context?, key: String?): Int {
        if (context == null) return -1
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getInt(key, -1)
    }
}