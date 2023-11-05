package com.sesac.lbsmapexample.common

import android.app.Application

class DefaultApplication: Application() {
    init {
        instance = this
    }
    companion object {
        private var instance: DefaultApplication? = null
        fun applicationContext() : DefaultApplication {
            return instance as DefaultApplication
        }
    }
}