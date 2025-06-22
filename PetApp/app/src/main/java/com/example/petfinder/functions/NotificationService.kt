package com.example.petfinder.functions

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.petfinder.MainActivity
import com.example.petfinder.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class Pet(
    var name: String = "",
    var race: String = "",
    var gender: String = "",
    var date: Timestamp = Timestamp.now() // Default to current time
)

data class UserPreferences(val gender: String, val ageMin: Int, val ageMax: Int, val species: String)

class PetUpdateService : Service() {

    companion object {
        private const val CHANNEL_ID = "pet_updates_channel"
        private const val NOTIFICATION_ID = 1
        private const val FOREGROUND_NOTIFICATION_ID = 2
    }

    private var listenerRegistration: ListenerRegistration? = null

    private fun setupPetListener() {
        Log.d("Setup Pet Listener", "Setting up pet listener")

        val db = FirebaseFirestore.getInstance()
        val petCollection = db.collection("pets")

        val userPrefs = getUserPreferences()
        val ageMin = userPrefs.ageMin
        val ageMax = userPrefs.ageMax
        val gender = userPrefs.gender
        val species = userPrefs.species

        val maxDate = convertDateStringToTimestamp(calculateDateRange(ageMin))
        val minDate = convertDateStringToTimestamp(calculateDateRange(ageMax))

        var query: Query = petCollection
            .whereGreaterThanOrEqualTo("date", minDate)
            .whereLessThanOrEqualTo("date", maxDate)
            .whereGreaterThan("uploaded", Timestamp.now())

        if (gender != "Both") {
            query = query.whereEqualTo("gender", gender)
        }

        if (species != "All") {
            query = query.whereEqualTo("race", species)
        }

        listenerRegistration = query.addSnapshotListener { snapshots, e ->
            Log.d(TAG, "Current snapshots: ${snapshots?.size()}")

            if (e != null) {
                Log.w(TAG, "Listen failed.", e)
                return@addSnapshotListener
            }

            for (doc in snapshots!!.documentChanges) {
                val pet = doc.document.toObject(Pet::class.java)
                if (matchesPreferences(pet, userPrefs)) {
                    showNotification(this, "New pet matches your preferences!", "Check out ${pet.name}!")
                }
            }
        }

        val intent = Intent("com.example.petfinder.ACTION_SERVICE_STARTED")
        sendBroadcast(intent)
    }

    private fun calculateDateRange(yearsAgo: Int): String {
        val calendar = Calendar.getInstance()

        // Calculate 'yearsAgo' date
        calendar.add(Calendar.YEAR, -yearsAgo)
        val startDate = calendar.time

        // Format into "DD-MM-YYYY" format
        val dateFormatter = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val startDateFormatted = dateFormatter.format(startDate)

        return startDateFormatted
    }

    private fun convertDateStringToTimestamp(dateString: String): Timestamp {
        // Define the date format
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

        // Parse the date string into a Date object
        val date = dateFormat.parse(dateString) ?: throw IllegalArgumentException("Invalid date format")

        // Create a Firestore Timestamp from the Date object
        return Timestamp(date)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        setupPetListener()

        Log.d("Notification Service", "Starting service")

        // Create a notification for the foreground service
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    private fun createNotification(): Notification {
        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        val notificationSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Pet Notifications", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Channel for Pet Finder Notifications"
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.image_logotype)
            .setContentTitle("Pet Finder")
            .setContentText("Checking for new pets")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSound(notificationSoundUri)
            .build()

        return notification
    }

    private fun showNotification(context: Context, title: String, content: String) {
        val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java) as NotificationManager

        Log.d("Show Notification", "Showing notification")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Pet Service", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Channel for Pet Finder Notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.image_logotype)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notification)
    }

    private fun matchesPreferences(pet: Pet, prefs: UserPreferences): Boolean {
        Log.d("Matches Preferences", "Checking if pet matches preferences")

        val matchesSpecies = (prefs.species == "All") || (pet.race == prefs.species)
        val matchesGender = (prefs.gender == "Both") || (pet.gender == prefs.gender)

        return matchesSpecies && matchesGender
    }

    private fun getUserPreferences(): UserPreferences {
        val prefs = getSharedPreferences("NotificationPreferences", Context.MODE_PRIVATE)
        val gender = prefs.getString("selectedGender", "Both") ?: "Both"
        val ageMin = prefs.getInt("ageMin", 0)  // default values
        val ageMax = prefs.getInt("ageMax", 20)
        val species = prefs.getString("selectedSpecies", "All") ?: "All"
        return UserPreferences(gender, ageMin, ageMax, species)
    }

    override fun onDestroy() {
        listenerRegistration?.remove() // Clean up listener when service is destroyed
        super.onDestroy()
    }

    // Implement onBind according to your needs, typically returning null for a started service
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
