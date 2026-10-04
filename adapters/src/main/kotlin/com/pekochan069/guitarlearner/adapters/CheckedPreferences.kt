package com.pekochan069.guitarlearner.adapters

import android.annotation.SuppressLint
import android.content.SharedPreferences

// The KTX edit helper returns Unit and cannot acknowledge a checked durable write.
@SuppressLint("UseKtx")
internal fun SharedPreferences.commitString(key: String, source: String?): Boolean {
    val editor = edit()
    if (source == null) editor.remove(key) else editor.putString(key, source)
    return editor.commit()
}
