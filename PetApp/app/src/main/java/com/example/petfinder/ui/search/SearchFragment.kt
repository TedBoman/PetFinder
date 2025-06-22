package com.example.petfinder.ui.search

import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.petfinder.R
import com.example.petfinder.database.Animal
import com.example.petfinder.databinding.FragmentSearchBinding
import com.example.petfinder.functions.FilterDialogFragment
import com.example.petfinder.ui.login.LoginFragment
import com.example.petfinder.ui.petProfile.PetProfileFragment
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchFragment :
    Fragment(), AnimalAdapter.AnimalClickListener, FilterDialogFragment.DialogDismissListener {

  private var _binding: FragmentSearchBinding? = null

  private lateinit var recyclerView: RecyclerView
  private lateinit var search: EditText
  private lateinit var sharedPreferences: SharedPreferences
  private lateinit var sharedFilterPrefs: SharedPreferences
  private lateinit var progressBar: ProgressBar
  private lateinit var auth: FirebaseAuth // Initialize Firebase Authentication
  private var animalCache = HashMap<String, Animal>()

  private val binding
    get() = _binding!!

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
  ): View {
    _binding = FragmentSearchBinding.inflate(inflater, container, false)
    val root: View = binding.root

    search = binding.SearchQuery
    recyclerView = binding.searchView

    progressBar = binding.progressBar

    setupTextWatcher()

    setupRecyclerView()
    setupFilterButton()

    checkLoggedInState()

    // SwipeRefreshLayout Setup
    val swipeRefreshLayout = binding.swipe
    swipeRefreshLayout.setOnRefreshListener {
      fetchAnimals() // Force a fresh fetch
      swipeRefreshLayout.isRefreshing = false // Stop the refresh indicator
    }

    return root
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    auth = Firebase.auth

    sharedFilterPrefs =
        requireContext()
            .getSharedPreferences(
                getString(R.string.filter_preferences),
                AppCompatActivity.MODE_PRIVATE
            )
    sharedPreferences =
        requireContext()
            .getSharedPreferences(getString(R.string.animal_cache), AppCompatActivity.MODE_PRIVATE)
    // sharedPreferences.edit().remove("animalCacheData").apply()
  }

  private fun checkLoggedInState() {
    val currentUser = auth.currentUser
    if (currentUser == null) {
      val loginFragment = LoginFragment()

      // Clear back stack (if needed)
      if (childFragmentManager.backStackEntryCount > 0) {
        childFragmentManager.popBackStackImmediate() // Clear all
      }

      loginFragment.show(this.childFragmentManager, "login_dialog")
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    Log.d("SearchFragment", "Loading animals")
    // Load animals from cache
    loadAnimalCache()
  }

  private var searchJob: Job? = null
  private var showNoResultsJob: Job? = null

  private fun setupTextWatcher() {
    search.addTextChangedListener(
        object : TextWatcher {
          override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
          override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            searchJob?.cancel() // Cancel any existing job
            searchJob =
                GlobalScope.launch(Dispatchers.Main) {
                  delay(500) // Debounce delay
                  if (isAdded
                  ) { // Checks that the fragment is still attached, prevents crashing issues.
                    animalCache.clear()
                    fetchAnimals(
                        requireActivity()
                            .findViewById<EditText>(R.id.SearchQuery)
                            .text
                            .toString()
                            .trim()
                    ) // Fetch animals based on search text
                  }
                }
          }
          override fun afterTextChanged(s: Editable?) {}
        }
    )
  }

  private fun setupRecyclerView() {
    recyclerView.layoutManager = GridLayoutManager(context, 2) // Assuming a grid layout
  }

  private fun mapPositionToSpecies(position: Int): String {
    return when (position) {
      0 -> "Dog"
      1 -> "Cat"
      2 -> "All"
      else -> "All" // Default or error case
    }
  }

  private fun convertDateStringToTimestamp(dateString: String): Timestamp {
    // Define the date format
    val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    // Parse the date string into a Date object
    val date = dateFormat.parse(dateString) ?: throw IllegalArgumentException("Invalid date format")

    // Create a Firestore Timestamp from the Date object
    return Timestamp(date)
  }

  private fun fetchAnimals(searchText: String = "") {
    animalCache.clear()

    val db = FirebaseFirestore.getInstance()

    val selectedSpeciesPosition = sharedFilterPrefs.getInt("selectedSpecies", -1)
    val species = mapPositionToSpecies(selectedSpeciesPosition)
    val gender = sharedFilterPrefs.getString("selectedGender", "Both")
    val ageMin = sharedFilterPrefs.getFloat("minRange", 0f)
    val ageMax = sharedFilterPrefs.getFloat("maxRange", 20f)

    val minStartDate = calculateDateRange(ageMax.toInt())
    val maxStartDate = calculateDateRange(ageMin.toInt())

    val minDate = convertDateStringToTimestamp(minStartDate)
    val maxDate = convertDateStringToTimestamp(maxStartDate)

    // Start with the base query
    var query: Query = db.collection("pets")

    // Add conditional filters
    if (species != "All") {
      query = query.whereEqualTo("race", species)
      Log.d("SearchFragment.fetchAnimals", "Adding species: $species to query")
    }
    if (gender != "Both") {
      query = query.whereEqualTo("gender", gender)
      Log.d("SearchFragment.fetchAnimals", "Adding gender: $gender to query")
    }

    // Add date range filters
    query = query.whereGreaterThan("date", minDate).whereLessThan("date", maxDate)
    Log.d("SearchFragment.fetchAnimals", "Adding date range from $minStartDate to $maxStartDate")

    // Fetching data
    query
        .get()
        .addOnSuccessListener { result ->
          val animals =
              result.documents.mapNotNull { doc ->
                try {
                  Animal(
                          chipID = doc.id,
                          name = doc.getString("name") ?: "",
                          birthdate = doc.getTimestamp("date") ?: Timestamp.now(),
                          imageUrl = doc.getString("imageUrl") ?: ""
                      )
                      .also {
                        Log.d(
                            "SearchFragment.fetchAnimals",
                            "Fetched: ${it.chipID} ${it.name} ${it.birthdate}"
                        )
                      }
                } catch (e: Exception) {
                  Log.e("SearchFragment.fetchAnimals", "Error parsing animal", e)
                  null
                }
              }
          if (searchText.isNotEmpty()) {
            processSearchResults(animals, searchText)
          } else {
            animals.forEach { animalCache[it.chipID] = it }
            saveAnimalCache()
          }

          progressBar.visibility = View.GONE

          // If the search text is empty, there's no need to show the "no results" message
          if (searchText.isNotEmpty() && animalCache.isEmpty()) {
            showNoResultsJob =
                GlobalScope.launch(Dispatchers.Main) {
                  delay(1000) // 1-second delay
                  if (isAdded) { // Check if the fragment is still attached
                    Toast.makeText(requireContext(), "No results found", Toast.LENGTH_SHORT).show()
                  }
                }
          }

          progressBar.visibility = View.GONE
        }
        .addOnFailureListener { exception ->
          Log.e("Firestore", "Error fetching animals", exception)
          progressBar.visibility = View.GONE
        }
  }

  private fun processSearchResults(animals: List<Animal>, searchText: String) {
    animals.forEach { animal ->
      if (animal.name.contains(searchText, ignoreCase = true)) {
        animalCache[animal.chipID] = animal
      }
    }
    saveAnimalCache()
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

  private fun displayAnimals() {
    recyclerView.layoutManager = GridLayoutManager(context, 2)

    // Check if cache has data
    if (animalCache.isNotEmpty()) {
      val animals = animalCache.values.toList() // Convert HashMap values to a List
      val animalAdapter = AnimalAdapter(animals)
      animalAdapter.clickListener = this
      recyclerView.adapter = animalAdapter
      progressBar.visibility = View.GONE
    }
  }

  // Makes the filter button work. it will open a box with available filter options
  private fun setupFilterButton() {
    Log.d("SearchFragment", "Filter button setup")
    // Use binding to find the filter button
    val filterButton = binding.filterButton
    filterButton.setOnClickListener {
      val filterDialog = FilterDialogFragment()
      Log.d("SearchFragment", "Filter button clicked")
      filterDialog.setDialogDismissListener(this)
      filterDialog.show(childFragmentManager, "filter_dialog")
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  override fun onAnimalClicked(chipId: String) {
    Log.w("SearchFragment", "Clicked on animal with chipID $chipId")
    showPetProfileDialog(chipId)
  }

  private fun showPetProfileDialog(chipId: String) {
    val bundle = Bundle().apply { putString("chipID", chipId) }
    val dialog = PetProfileFragment()
    dialog.arguments = bundle
    dialog.show(childFragmentManager, "pet_profile_dialog")
  }

  private fun saveAnimalCache() {
    val gson = Gson()
    val cacheJson = gson.toJson(animalCache)
    sharedPreferences.edit().putString("animalCacheData", cacheJson).apply()
    sharedPreferences
        .edit()
        .putString("animalCacheDataTime", System.currentTimeMillis().toString())
        .apply()
    displayAnimals()
  }

  private fun loadAnimalCache() {
    sharedPreferences =
        requireContext()
            .getSharedPreferences(getString(R.string.animal_cache), AppCompatActivity.MODE_PRIVATE)

    progressBar.visibility = View.VISIBLE
    progressBar.bringToFront()

    val gson = Gson()
    val cacheJson = sharedPreferences.getString("animalCacheData", null)

    val cacheTimestamp = sharedPreferences.getString("animalCacheDataTime", null)?.toLong()
    val timeElapsed = cacheTimestamp?.let { System.currentTimeMillis() - it } ?: Long.MAX_VALUE

    if (cacheTimestamp != null &&
            cacheJson != "{}" &&
            cacheJson != null &&
            timeElapsed < TimeUnit.HOURS.toMillis(24)
    ) {
      val type = object : TypeToken<HashMap<String, Animal>>() {}.type
      animalCache = gson.fromJson(cacheJson, type)
      displayAnimals()
    } else {
      // Cache is too old or doesn't exist - force a refresh
      notLoading()
    }
  }

  private fun notLoading() {
    // Cache is too old or doesn't exist - force a refresh
    animalCache.clear()
    fetchAnimals()
  }

  override fun onDialogDismissed() {
    Log.d("onDialogDismissed", "Is dismissed")
    animalCache.clear()
    progressBar.visibility = View.VISIBLE
    fetchAnimals()
  }
}

