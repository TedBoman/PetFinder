package com.example.petfinder.functions

import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.petfinder.R
import com.example.petfinder.R.*
import com.example.petfinder.database.Animal
import com.example.petfinder.ui.search.AnimalAdapter
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import java.util.HashMap

class RemovePetDialogFragment : DialogFragment(), AnimalAdapter.AnimalClickListener {

  private lateinit var recyclerView: RecyclerView
  private lateinit var search: EditText
  private lateinit var searchButton: ImageButton
  private lateinit var sharedPreferences: SharedPreferences
  private lateinit var backButton: ImageButton

  private var animalCache = HashMap<String, Animal>()

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
  ): View? {
    val view = inflater.inflate(layout.fragment_removepet, container, false)

    recyclerView = view.findViewById(R.id.RemoveRecyclerView)
    search = view.findViewById(R.id.RemovePetSearchQuery)
    searchButton = view.findViewById(R.id.searchButton)
    backButton = view.findViewById(R.id.backButton)

    backButton.setOnClickListener { dismiss() }

    setupRecyclerView()

    return view
  }

  private fun setupRecyclerView() {
    recyclerView.layoutManager = GridLayoutManager(context, 2) // Assuming a grid layout
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    sharedPreferences =
        requireContext().getSharedPreferences("animal_cache", AppCompatActivity.MODE_PRIVATE)

    setStyle(STYLE_NORMAL, style.FullScreenDialogStyle)
  }

  override fun onStart() {
    super.onStart()

    val dialog = dialog
    if (dialog != null) {
      val width = ViewGroup.LayoutParams.MATCH_PARENT
      val height = ViewGroup.LayoutParams.MATCH_PARENT
      dialog.window?.setLayout(width, height)
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    fetchAnimals()
  }

  private fun fetchAnimals() {
    animalCache.clear()
    val db = FirebaseFirestore.getInstance()

    // Start with the base query
    var query: Query = db.collection("pets")

    // Execute search if needed
    val searchText =
        view?.findViewById<EditText>(R.id.RemovePetSearchQuery)?.text.toString().trim().lowercase()

    if (searchText.isNotEmpty()) {
      query = query.whereEqualTo("name", searchText)
    }

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
            Log.d("SearchFragment.fetchAnimals", "Searching for $searchText")
            processSearchResults(animals, searchText)
          } else {
            animals.forEach { animalCache[it.chipID] = it }
            displayAnimals()
          }
        }
        .addOnFailureListener { exception ->
          Log.e("Firestore", "Error fetching animals", exception)
        }
  }

  private fun processSearchResults(animals: List<Animal>, searchText: String) {
    animals.forEach { animal ->
      if (animal.name.contains(searchText, ignoreCase = true)) {
        animalCache[animal.chipID] = animal
      }
    }
    displayAnimals()
  }

  private fun showNoResultsDialog() {
    val builder = AlertDialog.Builder(requireContext())
    builder
        .setTitle("No Results Found")
        .setMessage("Try adjusting your search filters")
        .setPositiveButton("OK", null)
    builder.show()
  }

  private fun displayAnimals() {
    recyclerView.layoutManager = GridLayoutManager(context, 2)

    // Check if cache has data
    if (animalCache.isNotEmpty()) {
      val animals = animalCache.values.toList() // Convert HashMap values to a List
      val animalAdapter = AnimalAdapter(animals)
      animalAdapter.clickListener = this
      recyclerView.adapter = animalAdapter
    } else {
      showNoResultsDialog() // Handle the scenario where the cache is empty
    }
  }

  override fun onAnimalClicked(chipId: String) {
    // Context should be handled safely in fragments
    val context = context ?: return

    // Build the AlertDialog
    val builder = androidx.appcompat.app.AlertDialog.Builder(context)
    builder
        .setTitle("Delete Pet")
        .setMessage("Are you sure you want to permanently delete this pet record?")
        .setPositiveButton("Delete") { dialog, _ ->
          // Dismiss the dialog
          dialog.dismiss()
          // Delete the document from Firestore
          animalCache[chipId]?.let {
            Firebase.storage.getReferenceFromUrl(it.imageUrl).delete().addOnSuccessListener {
              Log.d("Firestore", "Pet image deleted successfully")
            }
          }
          Firebase.firestore
              .collection("pets")
              .document(chipId)
              .delete()
              .addOnSuccessListener {
                Log.d("Firestore", "Pet record deleted successfully")
                // Optionally notify the user with a Toast or update the UI
                Toast.makeText(context, "Pet deleted successfully.", Toast.LENGTH_SHORT).show()
                animalCache.remove(chipId)
                displayAnimals()
              }
              .addOnFailureListener { e ->
                Log.e("Firestore", "Error deleting pet record", e)
                // Optionally notify the user of the failure
                Toast.makeText(context, "Failed to delete pet.", Toast.LENGTH_SHORT).show()
              }
        }
        .setNegativeButton("Cancel") { dialog, _ ->
          // Just dismiss the dialog if the user cancels the action
          dialog.dismiss()
        }
        .show()
  }
}

