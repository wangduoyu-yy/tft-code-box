package com.wangye.tftbox

import android.app.Application
import com.wangye.tftbox.data.AppDatabase
import com.wangye.tftbox.data.LineupRepository

class TftApp : Application() {

    val repository: LineupRepository by lazy {
        LineupRepository(AppDatabase.get(this).lineupDao())
    }
}
