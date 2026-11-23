package com.sozler.spotify

import android.content.Context
import android.content.SharedPreferences

object Cfg {
    // anahtarlar: size(sp), offset(sn), alpha(%), locked, approx, y(px)
    fun prefs(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("cfg", Context.MODE_PRIVATE)
}
