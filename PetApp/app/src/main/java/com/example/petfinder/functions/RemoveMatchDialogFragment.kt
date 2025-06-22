package com.example.petfinder.functions

import android.content.ContentValues.TAG
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.petfinder.R
import com.example.petfinder.database.Animal
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson

// import kotlinx.coroutines.unregisterTimeLoopThread

class RemoveMatchDialogFragment : DialogFragment() {

  private lateinit var backButton: ImageButton
  private lateinit var recyclerView: RecyclerView

  private lateinit var codeAdapter: MatchAdapter
  private val animals = mutableListOf<Animal>()
  private val matches = mutableListOf<String>()

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
  ): View? {
    // Inflate the layout file
    val view = inflater.inflate(R.layout.fragment_matches, container, false)

    backButton = view.findViewById(R.id.backButton)

    backButton.setOnClickListener { dismiss() }

    recyclerView = view.findViewById(R.id.rv_RemoveMatches)

    codeAdapter =
        MatchAdapter(animals, matches) { match ->
          removeMatch(match)
          // Delete the mod code from Firestore  // What??
          updateMatchCache(match.chipID)
          updateRejectCache(match.chipID)
        }
    recyclerView.adapter = codeAdapter
    recyclerView.layoutManager = LinearLayoutManager(context)
    return view
  }

  private fun loadCachedAnimals() {
    loadCachedMatches()
    loadCachedRejects()
  }

  private fun loadCachedRejects() {

    val gson = Gson()

    val rejectedMatches =
        requireActivity()
            .getSharedPreferences(getString(R.string.rejected_cache), Context.MODE_PRIVATE)
            ?: return
    val sharedRejectedData =
        rejectedMatches.getString(getString(R.string.rejected_cache_entries), "")
    Log.d("RemoveMatch - loadCachedRejects", "Found: ${sharedRejectedData}")
    if (!sharedRejectedData.isNullOrEmpty()) {

      animals.addAll(
          gson.fromJson(sharedRejectedData, Array<Animal>::class.java).asList().toMutableList()
      )
      codeAdapter.notifyDataSetChanged()
    }
  }

  private fun loadCachedMatches() {
    val gson = Gson()
    val sharedMatches =
        requireActivity()
            .getSharedPreferences(getString(R.string.match_cache), Context.MODE_PRIVATE)

    val sharedMatchData = sharedMatches.getString(getString(R.string.match_cache_entries), "")

    Log.d("RemoveMatch - loadCachedMatches", "Found: ${sharedMatchData}")
    if (!sharedMatchData.isNullOrEmpty()) {

      val sharedMatchesList =
          gson.fromJson(sharedMatchData, Array<Animal>::class.java).asList().toMutableList()
      animals.addAll(sharedMatchesList)
      sharedMatchesList.forEach { matches.add(it.chipID) }
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    animals.let { loadCachedAnimals() }
    if (animals.isNullOrEmpty()) {
      Log.d("RemoveMatchDialog - onViewCreated", "Animals is empty, sending database request")
      fetchMatches()
      // GlobalScope.launch { fetchMatches() } // Why GlobalScope on this ?
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setStyle(STYLE_NORMAL, R.style.FullScreenDialogStyle)
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

  private fun fetchMatches() {
    val userId = Firebase.auth.currentUser?.uid
    val db = Firebase.firestore
    if (userId != null) {
      db.collection("users")
          .document(userId)
          .get()
          .addOnSuccessListener { document ->
            val petsCollection = db.collection("pets")
            val matchedProfiles = document.get("matchedProfiles") as? List<String> ?: emptyList()
            val rejectedProfiles = document.get("rejectedProfiles") as? List<String> ?: emptyList()

            val allProfiles = matchedProfiles + rejectedProfiles // Combine both lists
            matches.addAll(matchedProfiles)
            // Fetch details for all profiles (matched and rejected)
            allProfiles.forEach { chipID ->
              petsCollection
                  .document(chipID)
                  .get()
                  .addOnSuccessListener { petDoc ->
                    // Add the DocumentSnapshot to the list
                    if (petDoc != null) {
                      animals.add(
                          Animal(
                              name = petDoc.getString("name") ?: "Unknown",
                              imageUrl = petDoc.getString("imageUrl") ?: "",
                              chipID = petDoc.id,
                          )
                      )
                      // Notify the adapter of the new item
                      codeAdapter.notifyItemInserted(animals.size - 1)
                    }
                  }
                  .addOnFailureListener { e ->
                    Log.e("RemoveMatchDialogFragment", "Error fetching pet details", e)
                  }
            }
          }
          .addOnFailureListener { exception -> Log.w(TAG, "Error getting user data.", exception) }
    }
  }
  private fun removeMatch(animal: Animal) {
    Log.d("RemoveMatchDialog - removeMatch", "${animal.name}, ")
    val userId =
        Firebase.auth.currentUser?.uid
            ?: run {
              Toast.makeText(
                      requireContext(),
                      "Lost connection, please restart app.",
                      Toast.LENGTH_SHORT
                  )
                  .show()
              return // Early return if user is not logged in
            }
    val db = Firebase.firestore

    // Determine if the pet is in matchedProfiles or rejectedProfiles
    val userRef = db.collection("users").document(userId)
    userRef.get().addOnSuccessListener { userDoc ->
      var matchedProfiles = userDoc.get("matchedProfiles") as? List<String> ?: emptyList()
      val rejectedProfiles = userDoc.get("rejectedProfiles") as? List<String> ?: emptyList()

      val fieldToUpdate =
          if (matchedProfiles.contains(animal.chipID)) {
            "matchedProfiles"
          } else if (rejectedProfiles.contains(animal.chipID)) {
            "rejectedProfiles"
          } else {
            ""
          }
      // Pet does not exist in user profile, remove from list and cache
      //
      if (fieldToUpdate.isNullOrEmpty()) {
        Log.d(
            "RemoveMatchDialog - removeMatch",
            "animal does not exist in user profile. Removing from cache"
        )
        animals.remove(animal)
        // if (matches.contains(animal.chipID)) {
        matches.remove(animal.chipID)
        //  updateMatchCache(animal.chipID)
        // } else {
        //  updateRejectCache(animal.chipID)
        // }
        codeAdapter.notifyDataSetChanged()
      } else { // Animal exist in user profile on server, update database and remove from list and
        // cache

        Log.d(
            "RemoveMatchDialog - removeMatch",
            "Removing ${animal.chipID} from ${fieldToUpdate} in database"
        )

        // Remove chipID from the appropriate array
        userRef
            .update(fieldToUpdate, FieldValue.arrayRemove(animal.chipID))
            .addOnSuccessListener {
              Toast.makeText(requireContext(), "Match removed successfully", Toast.LENGTH_SHORT)
                  .show()

              // Remove from the adapter's list and update UI
              matches.remove(animal.chipID)
              animals.remove(animal)

              codeAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
              Log.e(TAG, "Error removing match: ", e)
              Toast.makeText(requireContext(), "Failed to remove match", Toast.LENGTH_SHORT).show()
            }
      }
    }
  }

  private fun updateMatchCache(chipID: String) {

    val matchCache =
        requireActivity()
            .getSharedPreferences(getString(R.string.match_cache), Context.MODE_PRIVATE)
            ?: run {
              Log.d("RemoveMatchDialog", "match cache is null")
              return
            }

    val matchCacheEntries = matchCache.getString(getString(R.string.match_cache_entries), "")

    if (matchCacheEntries.isNullOrEmpty()) {
      Log.d("Account - updateMatchCache", "No saved matches")
    } else {
      val gson = Gson()
      val matchList =
          gson.fromJson(matchCacheEntries, Array<Animal>::class.java).asList().toMutableList()
      val updatedList = gson.toJson(matchList.filterNot { it.chipID == chipID })
      Log.d(
          "RemoveMatchDialog - updateMatchCache",
          "Removed: ${matchList.filterNot{it.chipID == chipID}}"
      )
      with(matchCache.edit()) {
        putString(getString(R.string.match_cache_entries), updatedList).commit()
      }
    }
  }

  private fun updateRejectCache(chipID: String) {

    val rejectedCache =
        requireActivity()
            .getSharedPreferences(getString(R.string.rejected_cache), Context.MODE_PRIVATE)
            ?: run {
              Log.d("RemoveMatchDialog", "rejected cache is null")
              return
            }

    val rejectedCacheEntries =
        rejectedCache.getString(getString(R.string.rejected_cache_entries), "")

    if (rejectedCacheEntries.isNullOrEmpty()) {
      Log.d("RemoveMatchDialog - updateRejectCache", "No saved rejects")
      return
    }

    val gson = Gson()
    val rejectList =
        gson.fromJson(rejectedCacheEntries, Array<Animal>::class.java).asList().toMutableList()

    val updatedList = gson.toJson(rejectList.filterNot { it.chipID == chipID })

    Log.d(
        "RemoveMatchDialog - updateRejectCache",
        "Removed: ${rejectList.filterNot{it.chipID == chipID}}"
    )
    with(rejectedCache.edit()) {
      putString(getString(R.string.rejected_cache_entries), updatedList).commit()
    }
  }

  class MatchAdapter(
      private val animals: MutableList<Animal>,
      private val matches: MutableList<String>,
      private val onRemove: (Animal) -> Unit
  ) : RecyclerView.Adapter<MatchAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
      val petImage: ImageView = itemView.findViewById(R.id.petImage)
      val petName: TextView = itemView.findViewById(R.id.tvPetName)
      val status: TextView = itemView.findViewById(R.id.tvStatus)
      val removeButton: Button = itemView.findViewById(R.id.removeButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
      val itemView =
          LayoutInflater.from(parent.context).inflate(R.layout.matches_item, parent, false)
      return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
      val pet = animals[position]

      holder.petName.text = pet.name

      // Got a list of matches not empty from cache, collected by swipe. Use it.
      if (!matches.isEmpty()) {
        Log.d("MatchAdapter", "Matches is not empty:")
        holder.status.text = if (pet.chipID in matches) "Match" else "Rejected"

        // No cached match list. Fetch matched profiles and update UI
      } else {
        Log.d("MatchAdapter", "Matches is empty, sending database request")
        getUserMatchedProfiles(Firebase.auth.currentUser?.uid ?: "") { userMatchedProfiles ->
          matches.addAll(userMatchedProfiles)
          holder.status.text = if (pet.chipID in userMatchedProfiles) "Match" else "Rejected"
        }
      }

      Glide.with(holder.itemView.context)
          .load(pet.imageUrl)
          .placeholder(R.drawable.ic_account_black_24px) // Optional: a placeholder image
          .error(R.drawable.image_logotype) // Optional: an error image
          .circleCrop() // makes it a circle
          .into(holder.petImage)

      holder.removeButton.setOnClickListener { onRemove(pet) }
    }

    private fun getUserMatchedProfiles(userId: String, callback: (List<String>) -> Unit) {
      FirebaseFirestore.getInstance()
          .collection("users")
          .document(userId)
          .get()
          .addOnSuccessListener { userDoc ->
            val matchedProfiles = userDoc.get("matchedProfiles") as? List<String> ?: emptyList()
            callback(matchedProfiles)
          }
          .addOnFailureListener { e ->
            Log.e(TAG, "Error getting matched profiles: ", e)
            callback(emptyList()) // Pass an empty list in case of error
          }
    }

    override fun getItemCount(): Int {
      return animals.size
    }
  }
}
