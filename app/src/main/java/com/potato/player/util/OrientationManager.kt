package com.potato.player.util

import android.app.Activity

// ponytail: one file, one function, no class
fun lockOrientation(activity: Activity?, orientation: Int) {
    activity?.requestedOrientation = orientation
}
