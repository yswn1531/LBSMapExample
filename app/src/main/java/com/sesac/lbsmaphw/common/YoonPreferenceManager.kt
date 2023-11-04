package com.sesac.lbsmaphw.common

import android.content.SharedPreferences
import androidx.preference.PreferenceManager

private const val IS_LOCATION = "is_location"

class YoonPreferenceManager {

    companion object {
        private lateinit var manager: YoonPreferenceManager
        private lateinit var sp: SharedPreferences
        private lateinit var spEditor: SharedPreferences.Editor


        fun getInstance(): YoonPreferenceManager {
            if (this::manager.isInitialized) {
                return manager
            } else {
                sp = PreferenceManager.getDefaultSharedPreferences(
                    DefaultApplication.applicationContext()
                )
                spEditor = sp.edit()
                manager = YoonPreferenceManager()
            }
            return manager
        }
    }

    /**
     * 본 앱의 퍼미션 체크 여부
     */
    var isPermission : Boolean
        get() = sp.getBoolean(IS_LOCATION, false)
        set(permissionCheck) {
            with(spEditor){
                putBoolean(IS_LOCATION, permissionCheck).apply()
            }
        }
}