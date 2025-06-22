package com.example.petfinder.functions

import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.util.Log

import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button

import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.slider.RangeSlider
import com.example.petfinder.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.example.petfinder.database.fetchSpeciesFromDatabase

class FilterDialogFragment : DialogFragment() {

    interface DialogDismissListener {
        fun onDialogDismissed()
    }

    private var listener: DialogDismissListener? = null

    fun setDialogDismissListener(listener: DialogDismissListener) {
        this.listener = listener
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        listener?.onDialogDismissed()
    }

    private lateinit var rangeSlider: RangeSlider
    private lateinit var rangeTextView: TextView
    private lateinit var genderRadioGroup: RadioGroup
    private lateinit var speciesSpinner: Spinner

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.filters_menu, container, false)

        // Setting up the range sliders and others for the filters
        rangeSlider = view.findViewById(R.id.rangeSlider)
        rangeTextView = view.findViewById(R.id.rangeTextView)
        genderRadioGroup = view.findViewById(R.id.genderRadioGroup)
        speciesSpinner = view.findViewById(R.id.SpeciesSpinner)
        val applyButton: Button = view.findViewById(R.id.apply_button)

        Log.d("FiltersFragment", "Creating the fragment")
        sharedPreferences = requireActivity().getSharedPreferences("FilterPrefs", Context.MODE_PRIVATE)
        // Set up functions to get the values and place them into the spinner and slider
        setupSpinner()
        setupRangeSlider()
        retrieveSavedValues()

        // Starts listening to the apply button
        applyButton.setOnClickListener {
            // Save selected values to SharedPreferences
            Log.d("FilterDialogFragment", "Clicked the apply button")
            saveSelectedValues()
            Log.d("FilterDialogFragment", "Dismissing the dialog fragment!")
            dismiss()
        }

        return view
    }

    override fun onDestroy() {
        super.onDestroy()
        saveSelectedValues()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.DialogSwoosh)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            // Set the width of the dialog to match the parent, making it extend to almost the edges of the screen
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun getSelectedGender(): String {
        return when (genderRadioGroup.checkedRadioButtonId) {
            R.id.rb_female -> "Female"
            R.id.rb_male -> "Male"
            R.id.rb_both -> "Both"
            else -> "*" // Error state
        }
    }

    private fun saveSelectedValues() {
        val selectedSpecies = speciesSpinner.selectedItemPosition
        with(sharedPreferences.edit()){
            putInt("selectedSpecies", selectedSpecies)
            apply()
        }

        val selectedGenderId = genderRadioGroup.checkedRadioButtonId
        with(sharedPreferences.edit()) {
            putInt("selectedGenderId", selectedGenderId)
            apply()
        }

        val sGender = getSelectedGender()
        sharedPreferences.edit().putString("selectedGender", sGender).apply()

        // Get current selected values
        val selectedRange = rangeSlider.values
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
        rangeSlider.values = listOf(minRange, maxRange)

        // Retrieve saved Gender
        val selectedGenderId = sharedPreferences.getInt("selectedGenderId", -1) // Default to 'Both'
        if(selectedGenderId == -1) {
            genderRadioGroup.check(R.id.rb_both)
        } else {
            genderRadioGroup.check(selectedGenderId)
        }

        val selectedSpeciesId = sharedPreferences.getInt("selectedSpecies", -1) // Default to 'All'
        if (selectedSpeciesId == -1){
            speciesSpinner.setSelection(2)
        } else {
            speciesSpinner.setSelection(selectedSpeciesId)
        }
    }

    // setup function to populate the spinners
    private fun setupSpinner() {
        // Creating a new thread to run the database stuff on
        GlobalScope.launch(Dispatchers.IO) {
            val speciesList = fetchSpeciesFromDatabase()
            withContext(Dispatchers.Main) {
                val spinner: Spinner? = view?.findViewById(R.id.SpeciesSpinner)
                spinner?.let {
                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, speciesList)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    it.adapter = adapter

                    // Retrieve and set the saved spinner position
                    val selectedSpinnerPosition = sharedPreferences.getInt("selectedSpecies", 2) // Default to first item
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


// setup function for the range sliders
    private fun setupRangeSlider() {
        rangeSlider.valueFrom = 0f
        rangeSlider.valueTo = 20f
        rangeSlider.values = listOf(0f, 20f) // Initial values for the two thumbs
        rangeSlider.stepSize = 1f

        rangeSlider.setLabelFormatter { value ->
            if (value == rangeSlider.valueTo) {
                "20+"
            } else {
                value.toInt().toString() // Convert to integer
            }
        }

        rangeSlider.addOnChangeListener { slider, _, _ ->
            val leftValue = slider.values[0].toInt() // Convert to integer
            val rightValue = slider.values[1].toInt() // Convert to integer

            val rightLabel = if (rightValue == slider.valueTo.toInt()) "20+" else rightValue.toString()
            rangeTextView.text = "Range: $leftValue - $rightLabel"
        }
    }
}