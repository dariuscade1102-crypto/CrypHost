package com.cryptmc.app

import android.app.Application
import com.cryptmc.app.data.ServerRepository
import com.cryptmc.app.server.BackupManager

class CryptMcApp : Application()
{
    override fun onCreate() {
        super.onCreate()
        ServerRepository.initialize(this)
        BackupManager.initialize(this)
    }
}
