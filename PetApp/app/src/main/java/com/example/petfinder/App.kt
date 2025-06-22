package com.example.petfinder

import android.app.Activity
import android.app.Application
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tapadoo.alerter.Alerter

class App : Application() {
  companion object {
    lateinit var instance: App
      private set
  }

  lateinit var notificationViewModel: NotificationViewModel_V2
  var isMessageFragmentVisible = false // To track MessageFragment visibility

  override fun onCreate() {
    super.onCreate()
    instance = this

    // Start observing the ViewModel in the Application class
    NotificationViewModelHolder_V2.retreiveViewModel_V2().showNotification.observeForever {
        (title, message) ->
      val currentActivity = getCurrentActivity()
      if (currentActivity != null && !isMessageFragmentVisible && !Alerter.isShowing) {
        Alerter.create(currentActivity)
            .setTitle(title)
            .setText(message)
            .setIcon(R.drawable.ic_chat_black_24px)
            .setBackgroundResource(R.color.blue_700)
            .setOnClickListener {
              Alerter.hide()
              val bottomNavigationView =
                  currentActivity.findViewById<BottomNavigationView>(R.id.nav_view)
              // Find the menu item corresponding to the message tab
              val messageMenuItem = bottomNavigationView.menu.findItem(R.id.navigation_message)
              // Simulate a click on the menu item
              if (messageMenuItem != null) {
                bottomNavigationView.selectedItemId = messageMenuItem.itemId
                isMessageFragmentVisible = true
              }
            }
            .show()
      }
    }
  }

  // Function to get the current activity of the app
  private fun getCurrentActivity(): Activity? {
    val activityThread = Class.forName("android.app.ActivityThread")
    val currentActivityThreadMethod = activityThread.getMethod("currentActivityThread")
    val currentActivityThread = currentActivityThreadMethod.invoke(null)

    val activitiesField = activityThread.getDeclaredField("mActivities")
    activitiesField.isAccessible = true
    val activities = activitiesField.get(currentActivityThread) as Map<*, *>

    for (activityRecord in activities.values) {
      val activityRecordClass = activityRecord!!::class.java
      if (activityRecordClass.name == "android.app.ActivityThread\$ActivityClientRecord") {
        val pausedField = activityRecordClass.getDeclaredField("paused")
        pausedField.isAccessible = true
        if (!pausedField.getBoolean(activityRecord)) {
          val activityField = activityRecordClass.getDeclaredField("activity")
          activityField.isAccessible = true
          return activityField.get(activityRecord) as Activity
        }
      }
    }

    return null
  }
}
