package com.caloriecompanion.shared

object AppInfo {
    /** The version of this source tree. A release image reports its tag instead (`CC_VERSION`). */
    const val VERSION = "0.10.0"

    /** Major API version. Clients refuse to talk to a server with a different one (NF-5). */
    const val API_VERSION = 1
}
