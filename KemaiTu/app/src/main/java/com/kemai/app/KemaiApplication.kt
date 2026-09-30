package com.kemai.app

import android.app.Application
import com.kemai.app.data.AppRepository

class KemaiApplication : Application() {

    lateinit var repo: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = AppRepository(this)
        repo.load()
    }
}
