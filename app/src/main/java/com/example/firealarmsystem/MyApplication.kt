package com.example.firealarmsystem

import android.app.Application
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Logger

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseDatabase.getInstance().setLogLevel(Logger.Level.DEBUG)
    }
}