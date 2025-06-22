package com.example.petfinder.functions

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.example.petfinder.R
import com.example.petfinder.database.fetchSpeciesFromDatabase
import com.google.android.material.slider.RangeSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationFragment : DialogFragment() {

    private lateinit var genderRadioGroup: RadioGroup
    private lateinit var ageRangeSlider: RangeSlider
    private lateinit var ageRangeSliderText: TextView
    private lateinit var applyButton: Button
    private lateinit var cancelButton: Button
    private lateinit var petTypeSpinner: Spinner

    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout file
        val view = inflater.inflate(R.layout.fragment_notification, container, false)

        sharedPreferences = requireActivity().getSharedPreferences("NotificationPreferences", Context.MODE_PRIVATE)

        genderRadioGroup = view.findViewById(R.id.notification_RadioGroup)
        ageRangeSlider = view.findViewById(R.id.NotificationRangeSlider)
        ageRangeSliderText = view.findViewById(R.id.rangeTextView)
        petTypeSpinner = view.findViewById(R.id.NotificationRaceSpinner)

        setupRangeSlider()
        setupSpinner()
        retrieveSavedValues()

        // Handle radio button selection
        genderRadioGroup.setOnCheckedChangeListener { group, checkedId ->
            val selectedGender = when (checkedId) {
                R.id.rb_male -> "Male"
                R.id.rb_female -> "Female"
                else -> "Both"
            }
            savePreference("selectedGender", selectedGender)
        }

        applyButton = view.findViewById(R.id.apply_button)
        // Set a click listener for the apply button
        applyButton.setOnClickListener {
            Log.d("NotificationFragment", "Apply button clicked")

            saveSelectedValues()

            // Save the selected species
            savePreference("selectedSpecies", petTypeSpinner.selectedItem.toString())

            cancelExistingNotifications()

            if (areNotificationsEnabled() && areForegroundServicePermissionsGranted()) {
                // Start the service
                GlobalScope.launch {
                    // Start the service
                    Log.d("NotificationFragment", "Starting service on new thread")
                    val serviceIntent = Intent(requireContext(), PetUpdateService::class.java)
                    requireActivity().startService(serviceIntent)
                }
                Toast.makeText(requireContext(), "We will send you a notification when we have a matching pet for you!", Toast.LENGTH_LONG).show()
                dismiss()
            } else {
                // Request notification permission (use the already registered launcher)
                requestNotificationPermission()
                requestForegroundServicePermission()
            }
        }
        cancelButton = view.findViewById(R.id.cancel_button)
        cancelButton.setOnClickListener {
            Log.d("NotificationFragment", "Cancel button clicked")
            dismiss()
        }
        return view
    }

    private fun saveSelectedValues() {
        val selectedSpecies = petTypeSpinner.selectedItemPosition
        with(sharedPreferences.edit()){
            putInt("selectedSpeciesId", selectedSpecies)
            apply()
        }

        val selectedGenderId = genderRadioGroup.checkedRadioButtonId
        with(sharedPreferences.edit()) {
            putInt("selectedGenderId", selectedGenderId)
            apply()
        }

        // Get current selected values
        val selectedRange = ageRangeSlider.values
        with(sharedPreferences.edit()) {
            putFloat("minRange", selectedRange[0])
            putFloat("maxRange", selectedRange[1])
            apply()
        }
    }

    private fun retrieveSavedValues() {
        // Retrieve saved range
        val minRange = sharedPreferences.getFloat("minRange", 0f)
        val maxRange = sharedPreferences.getFloat("maxRange", 20f)
        ageRangeSlider.values = listOf(minRange, maxRange)

        // Retrieve saved Gender
        val selectedGenderId = sharedPreferences.getInt("selectedGenderId", -1) // Default to 'Both'
        if(selectedGenderId == -1) {
            genderRadioGroup.check(R.id.rb_both)
        } else {
            genderRadioGroup.check(selectedGenderId)
        }
    }

    // setup function for the rangesliders
    private fun setupRangeSlider() {
        ageRangeSlider.valueFrom = 0f
        ageRangeSlider.valueTo = 20f
        ageRangeSlider.values = listOf(0f, 20f) // Initial values for the two thumbs
        ageRangeSlider.stepSize = 1f

        ageRangeSlider.setLabelFormatter { value ->
            if (value == ageRangeSlider.valueTo) {
                "20+"
            } else {
                value.toInt().toString() // Convert to integer
            }
        }

        ageRangeSlider.addOnChangeListener { slider, _, _ ->
            val leftValue = slider.values[0].toInt() // Convert to integer
            val rightValue = slider.values[1].toInt() // Convert to integer

            val rightLabel = if (rightValue == slider.valueTo.toInt()) "20+" else rightValue.toString()
            ageRangeSliderText.text = "Range: $leftValue - $rightLabel"

            savePreference("ageMin", leftValue)
            savePreference("ageMax", rightValue)
        }
    }

    private fun cancelExistingNotifications() {
        val notificationManager = NotificationManagerCompat.from(requireContext())

        // Replace this with the correct ID used in your service
        val notificationId = 2 // Notification ID

        notificationManager.cancel(notificationId)
    }

    // Helper function to save a preference
    private fun savePreference(key: String, value: Any) {
        val sharedPrefs = requireActivity().getSharedPreferences("NotificationPreferences", Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            when (value) {
                is String -> putString(key, value)
                is Float -> putFloat(key, value)
                is Int -> putInt(key, value)
                else -> putString(key, value.toString()) // Handle other types if needed
            }
            apply()
        }
    }

    // Helper function to check if notifications are enabled
    private fun areNotificationsEnabled(): Boolean {
        val notificationManager = NotificationManagerCompat.from(requireContext())
        return notificationManager.areNotificationsEnabled()
    }

    // Helper function to request notification permission
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Launch the permission request (using the pre-registered launcher)
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // For older Android versions, notifications are enabled by default
        }
    }

    private fun areForegroundServicePermissionsGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
            val foregroundServiceTypes = foregroundServiceTypesNeeded()
            return foregroundServiceTypes.all {
                ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // Android 9 - 13
            return ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.FOREGROUND_SERVICE
            ) == PackageManager.PERMISSION_GRANTED
        }
        // For older Android versions, assume permission is granted
        return true
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun foregroundServiceTypesNeeded(): List<String> {
        val types = mutableListOf<String>()
        types.add(android.Manifest.permission.FOREGROUND_SERVICE)
        types.add(android.Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC)
        return types
    }

    // Helper function to request foreground service permission
    private fun requestForegroundServicePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.FOREGROUND_SERVICE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Permission is not granted
                requestPermissionLauncher.launch(android.Manifest.permission.FOREGROUND_SERVICE)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialogStyle)

        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Permission granted
                if (!areNotificationsEnabled()){
                    requestNotificationPermission()
                }
                else if (!areForegroundServicePermissionsGranted()){
                    requestForegroundServicePermission()
                }
                else
                {
                    GlobalScope.launch {
                        // Start the service
                        Log.d("NotificationFragment", "Starting service on new thread")
                        val serviceIntent = Intent(requireContext(), PetUpdateService::class.java)
                        requireActivity().startService(serviceIntent)
                    }
                    Toast.makeText(requireContext(), "We will send you a notification when we have a matching pet for you!", Toast.LENGTH_LONG).show()
                    dismiss()
                }
            } else {
                // Permission denied, show a message
                Toast.makeText(
                    requireContext(),
                    "Notification and foreground permission is required",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupSpinner() {
        // Creating a new thread to run the database stuff on
        GlobalScope.launch(Dispatchers.IO) {
            val speciesList = fetchSpeciesFromDatabase()
            withContext(Dispatchers.Main) {
                val spinner: Spinner? = view?.findViewById(R.id.NotificationRaceSpinner)
                spinner?.let {
                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, speciesList)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    it.adapter = adapter

                    val selectedSpinnerPosition = sharedPreferences.getInt("selectedSpeciesId", 2) // Default to first item
                    it.setSelection(selectedSpinnerPosition)

                    it.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                            // Handle spinner item selection
                        }

                        override fun onNothingSelected(parent: AdapterView<*>) {
                            // Optional: handle no selection
                        }
                    }
                }
            }
        }
    }

    // Override the onStart method to set the dialog width and height (make it fullscreen)
    override fun onStart() {
        super.onStart()
        val dialog = dialog
        if (dialog != null) {
            val width = ViewGroup.LayoutParams.MATCH_PARENT
            val height = ViewGroup.LayoutParams.MATCH_PARENT
            dialog.window?.setLayout(width, height)
        }
    }
}