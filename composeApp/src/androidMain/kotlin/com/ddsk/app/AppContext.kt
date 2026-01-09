package com.ddsk.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import java.lang.ref.WeakReference

lateinit var appContext: Context
    private set

object CurrentActivityHolder : Application.ActivityLifecycleCallbacks {
    private var currentRef: WeakReference<Activity>? = null

    fun currentActivity(): Activity? = currentRef?.get()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        currentRef = WeakReference(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        currentRef = WeakReference(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        currentRef = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {
        if (currentRef?.get() === activity) currentRef = null
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (currentRef?.get() === activity) currentRef = null
    }
}

class UpDogApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = this
        registerActivityLifecycleCallbacks(CurrentActivityHolder)
    }
}
