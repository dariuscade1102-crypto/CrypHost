package com.cryptmc.app

import android.app.Application
import com.cryptmc.app.data.ServerRepository

class CryptMcApp : Application()
{
    override fun onCreate() {
        super.onCreate()
        ServerRepository.initialize(this)
    }
}
