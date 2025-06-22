package com.example.petfinder.ui.swipeMode

// import com.example.petfinder.database.Animal
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.petfinder.R
import com.example.petfinder.database.Animal
import com.example.petfinder.databinding.FragmentSwipeBinding
import com.example.petfinder.ui.petProfile.PetProfileFragment
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import java.util.Calendar
import kotlin.math.abs
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SwipeFragment : Fragment() {

  private lateinit var auth: FirebaseAuth // Initialize Firebase Authentication
  // private var filteredCache = HashMap<String, Animal>()
  private lateinit var filteredCache: MutableList<Animal>
  private lateinit var matchCache: MutableList<Animal>
  private lateinit var rejectedCache: MutableList<Animal>
  private lateinit var swipePrefs: SharedPreferences
  private lateinit var matchPrefs: SharedPreferences
  private lateinit var rejectPrefs: SharedPreferences
  private var _binding: FragmentSwipeBinding? = null

  private var disabled = false

  // This property is only valid between onCreateView and onDestroyView.
  private val binding
    get() = _binding!!

  // Variables for swipe functionality.
  // Its used so the image can be temporarily moved around in the tab in both y-x axis. Delta is the
  // change while initial keeps start position.
  private var initialX = 0f
  private var initialY = 0f
  private var deltaX = 0f
  private var deltaY = 0f
  private val swipeSensitivity = 20

  private var isViewAlive = true // Flag to track view lifecycle

  private fun checkModeratorStatus(): Boolean {
    val sharedPrefs = requireActivity().getSharedPreferences("AccountPrefs", Context.MODE_PRIVATE)
    val role = sharedPrefs.getString("role", "") // Assuming "role" is stored as a string

    return role == "admin" // Assuming "admin" is the role assigned to moderators
  }

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
  ): View {
    _binding = FragmentSwipeBinding.inflate(inflater, container, false)
    val root: View = binding.root
    auth = Firebase.auth

    return root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    filteredCache = mutableListOf()
    matchCache = mutableListOf()
    rejectedCache = mutableListOf()
    filteredCache.clear()
    matchCache.clear()
    rejectedCache.clear()
    isViewAlive = true

    setupToolbar()

    val rightSwipeButton: ImageButton = binding.root.findViewById(R.id.btnRightSwipe)
    val leftSwipeButton: ImageButton = binding.root.findViewById(R.id.btnLeftSwipe)

    // Button listeners.
    rightSwipeButton.setOnClickListener { swipeAction(true) }
    leftSwipeButton.setOnClickListener { swipeAction(false) }

    // The actual function for moving the image.
    movableImage()

    swipePrefs =
        requireActivity()
            .getSharedPreferences(
                getActivity()?.getString(R.string.swipe_cache),
                Context.MODE_PRIVATE
            )

    matchPrefs =
        requireActivity()
            .getSharedPreferences(
                getActivity()?.getString(R.string.match_cache),
                Context.MODE_PRIVATE
            )

    rejectPrefs =
        requireActivity()
            .getSharedPreferences(
                getActivity()?.getString(R.string.rejected_cache),
                Context.MODE_PRIVATE
            )

    // Why is this a global scope?
    GlobalScope.launch {
      while (!checkLoggedInState()) {
        delay(200)
      }
      if (isViewAlive) { // Check if the view is still alive
        fetchProfiles()
      }
    }
    fetchProfiles()

    if (checkModeratorStatus()) {
      disableSwipeMode()
    }
  }

  override fun onPause() {
    // chatUpdateViewModel.chatUpdate.removeObservers(viewLifecycleOwner)
    Log.d("swipe", "onPause - opening")
    super.onPause()
    saveMatch()
    saveReject()
    matchCache.clear()
    rejectedCache.clear()
    filteredCache.clear()
    Log.d("swipe", "onPause - finished")
  }

  override fun onDetach() {
    super.onDetach()
    Log.d("swipe", "onDetach - opening")
  }

  private fun checkLoggedInState(): Boolean {
    return auth.currentUser != null
  }

  // Disables swipe mode for moderators
  private fun disableSwipeMode() {
    val profileImageView: ImageView = binding.profilePicContainer
    profileImageView.setOnTouchListener { _, _ -> true } // Disables touch events
    profileImageView.setOnClickListener {}
    disabled = true

    val rightSwipeButton: ImageButton = binding.root.findViewById(R.id.btnRightSwipe)
    val leftSwipeButton: ImageButton = binding.root.findViewById(R.id.btnLeftSwipe)
    rightSwipeButton.isEnabled = false
    leftSwipeButton.isEnabled = false
    binding.openProfileButton.text = "Swipe mode is disabled for admins."
    binding.profilePicContainer.visibility = View.INVISIBLE
    binding.btnRightSwipe.visibility = View.INVISIBLE
    binding.btnLeftSwipe.visibility = View.INVISIBLE
  }

  private fun setupToolbar() {
    val toolbarImage: ImageView = binding.customTopbar.circularImage
    val toolbarTitle: TextView = binding.customTopbar.customTopbarText

    toolbarImage.setImageResource(R.drawable.image_logotype)
    toolbarTitle.text = getActivity()?.getString(R.string.happy_paws)
  }

  // Now handles both right and left swipe.
  private fun swipeAction(matched: Boolean) {
    val currentAnimal = filteredCache.firstOrNull()
    if (currentAnimal != null) {

      if (matched) {
        matchCache.add(currentAnimal)
      } else {
        rejectedCache.add(currentAnimal)
      }
    } else {
      Log.d("swipeAction", "matched = ${matched}, currentAnimal = ${currentAnimal}")
    }
    currentAnimal?.let {
      val chipID = it.chipID
      Log.d("matched boolean is: ", matched.toString())
      updateUserProfiles(chipID, matched)
      moveImage(if (matched) 1200f else -1200f, 0f, binding.profilePicContainer)
      removeCurrentAnimalFromCache()
      if (isViewAlive) { // Check if the view is still alive
        displayProfile()
      }
      resetImagePosition()
    }
  }

  // Removes the swiped on animal from the cache.
  private fun removeCurrentAnimalFromCache() {
    val currentAnimalKey = filteredCache.first().chipID
    currentAnimalKey.let { filteredCache.remove(filteredCache[0]) }
  }

  // Displays the profile from the cache.
  private fun displayProfile() {
    if (disabled) return

    if (matchCache.isNotEmpty()) {
      binding.profilePicContainer.visibility = View.VISIBLE
      val currentAnimal = filteredCache.firstOrNull()
      currentAnimal?.let { it ->
        val chipID = it.chipID
        binding.openProfileButton.setOnClickListener {
          // Show the pet profile dialog
          showPetProfileDialog(chipID)
        }
        val age = it.birthdate?.let { calculateAge(it) }
        val displayName = "${it.name}, $age"
        binding.openProfileButton.text = displayName
        Glide.with(requireContext()).load(it.imageUrl).into(binding.profilePicContainer)
      }
          ?: run {}
    } else {
      // If there are no more animals in the cache, clear the display
      binding.openProfileButton.text = "No profiles found right now."
      binding.profilePicContainer.visibility = View.INVISIBLE
    }
  }

  // Adds either the matched or rejected petID to the users respective list.
  private fun updateUserProfiles(petId: String, matched: Boolean) {
    val db = FirebaseFirestore.getInstance()
    val currentUser = auth.currentUser

    currentUser?.let { user ->
      val userId = user.uid
      val userDocRef = db.collection("users").document(userId)

      val fieldToUpdate = if (matched) "matchedProfiles" else "rejectedProfiles"
      userDocRef
          .update(fieldToUpdate, FieldValue.arrayUnion(petId))
          .addOnSuccessListener { Log.d("SwipeFragment", "Updated $fieldToUpdate with $petId") }
          .addOnFailureListener { e -> Log.e("SwipeFragment", "Error updating $fieldToUpdate", e) }
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  // Makes the image movable.
  private fun movableImage() {
    var touchStartTime = 0L // Time when the touch starts
    val profileImageView: ImageView = binding.profilePicContainer
    val matchTextGreen: TextView = binding.matchTextGreen
    val matchTextRed: TextView = binding.matchTextRed

    profileImageView.setOnClickListener {
      if (matchCache.isNotEmpty()) {
        showPetProfileDialog(filteredCache.first().chipID)
      }
    }

    profileImageView.setOnTouchListener { _, event ->
      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          initialX = event.x
          initialY = event.y
          deltaX = 0f
          deltaY = 0f
          touchStartTime = System.currentTimeMillis() // Store touch start time
        }
        MotionEvent.ACTION_MOVE -> {
          deltaX = event.x - initialX
          deltaY = event.y - initialY
          moveImage(deltaX, deltaY, profileImageView)

          tiltImage(profileImageView.translationX, profileImageView)
          // Check if the swipe motion is halfway to the swipe limit
          if (abs(profileImageView.translationX) > swipeSensitivity / 2) {
            val matched = profileImageView.translationX > 0
            if (matched) {
              matchTextGreen.visibility = View.VISIBLE
              matchTextRed.visibility = View.GONE
            } else {
              matchTextRed.visibility = View.VISIBLE
              matchTextGreen.visibility = View.GONE
            }
          } else {
            // Reset outline and hide matchText if below threshold
            profileImageView.setBackgroundResource(0)
            matchTextGreen.visibility = View.GONE
            matchTextRed.visibility = View.GONE
          }
        }
        MotionEvent.ACTION_UP -> {
          val touchDuration = System.currentTimeMillis() - touchStartTime
          if (touchDuration < 200 && deltaX < swipeSensitivity && deltaY < swipeSensitivity
          ) { // Adjust threshold as needed
            // This is a click (short touch duration and minimal movement)
            profileImageView.performClick()
          } else {
            // This is a drag (longer duration or more movement)
            if (deltaX <= -swipeSensitivity) {
              swipeAction(false)
            } else if (deltaX >= swipeSensitivity) {
              swipeAction(true)
            } else {
              resetImagePosition()
            }
          }
          initialX = 0f
          initialY = 0f
          deltaX = 0f
          deltaY = 0f
        }
      }
      true
    }
  }

  // Applies tilt effect based on the translationX value
  private fun tiltImage(translationX: Float, imageView: ImageView) {
    val maxTiltAngle = 20f // Maximum tilt angle in degrees
    val screenWidth = resources.displayMetrics.widthPixels
    val tiltAngle =
        (translationX / screenWidth) * maxTiltAngle * 2 // Multiply by 2 for a more noticeable tilt
    imageView.rotation = tiltAngle
  }

  // Handles the moving of the image by tracking the change in x and y values. .translation shifts
  // the images position by the delta.
  private fun moveImage(deltaX: Float, deltaY: Float, imageView: ImageView) {
    imageView.translationX += deltaX
    imageView.translationY += deltaY
  }

  private fun resetImagePosition() {
    val imageView: ImageView = binding.profilePicContainer
    val matchTextGreen: TextView = binding.matchTextGreen
    val matchTextRed: TextView = binding.matchTextRed
    imageView
        .animate()
        .translationX(0f)
        .translationY(0f)
        .setInterpolator(AccelerateInterpolator())
        .setDuration(300) // Set a duration for the animation
        .start()
    imageView.rotation = 0f
    matchTextGreen.visibility = View.INVISIBLE
    matchTextRed.visibility = View.INVISIBLE
  }

  private fun calculateAge(birthdate: Timestamp): String {
    val calendar = Calendar.getInstance()
    val currentDate = calendar.timeInMillis
    val birthDateMillis = birthdate.toDate().time
    val diffMillis = currentDate - birthDateMillis

    val millisecondsInYear = 1000 * 60 * 60 * 24 * 365.25
    val years = (diffMillis / millisecondsInYear).toInt()
    val days = ((diffMillis % millisecondsInYear) / (1000 * 60 * 60 * 24)).toInt()

    return if (years < 1) {
      "$days days old"
    } else {
      "       $years years"
    }
  }

  private fun saveAnimal() {
    if (isAdded) {
      val gson = Gson()
      val filteredJson = gson.toJson(filteredCache)

      swipePrefs
          .edit()
          .putString(getActivity()?.getString(R.string.swipe_cache_filtered_entries), filteredJson)
          .apply()
      swipePrefs
          .edit()
          .putString(
              getActivity()?.getString(R.string.swipe_cache_time),
              System.currentTimeMillis().toString()
          )
          .apply()

      if (isViewAlive) { // Check if the view is still alive
        displayProfile()
      }
    }
  }

  private fun saveMatch() {
    if (matchCache.isNullOrEmpty()) {
      Log.d("swipe", "save match - is empty, returning")
      return
    }
    Log.d("swipe", "save match")
    matchCache.forEach { Log.d("", "${it.name}: ${it.chipID}") }
    val gson = Gson()

    val sharedMatches =
        requireActivity()
            .getSharedPreferences(
                getActivity()?.getString(R.string.match_cache),
                Context.MODE_PRIVATE
            )
            ?: return
    val sharedMatchData =
        sharedMatches.getString(getActivity()?.getString(R.string.match_cache_entries), "")
    // Log.d("swipe", "sharedMatchData: ${sharedMatchData}")

    if (!sharedMatchData.isNullOrEmpty()) {
      matchCache.addAll(
          gson.fromJson(sharedMatchData, Array<Animal>::class.java)
              .asList()
              .toMutableList()
              .filter { !matchCache.contains(it) }
      )
    }
    val matchJson = gson.toJson(matchCache)

    with(matchPrefs.edit()) {
      putString(getActivity()?.getString(R.string.match_cache_entries), matchJson).commit()
    }
    Log.d("swipe", "save match finished")
  }

  private fun saveReject() {
    val gson = Gson()
    if (rejectedCache.isNullOrEmpty()) {
      Log.d("swipe", "save reject - is empty, returning")
      return
    }
    Log.d("swipe", "save reject")
    rejectedCache.forEach { Log.d("", "${it.name}: ${it.chipID}") }

    val sharedRejects =
        requireActivity()
            .getSharedPreferences(
                getActivity()?.getString(R.string.rejected_cache),
                Context.MODE_PRIVATE
            )
            ?: return
    val sharedRejectedData =
        sharedRejects.getString(getActivity()?.getString(R.string.rejected_cache_entries), "")

    // Log.d("swipe", "sharedRejectedData: ${sharedRejectedData}")

    if (!sharedRejectedData.isNullOrEmpty()) {

      rejectedCache.addAll(
          gson.fromJson(sharedRejectedData, Array<Animal>::class.java)
              .asList()
              .toMutableList()
              .filter { !rejectedCache.contains(it) }
      )
    }
    val rejectedJson = gson.toJson(rejectedCache)

    with(rejectPrefs.edit()) {
      putString(getActivity()?.getString(R.string.rejected_cache_entries), rejectedJson).commit()
    }
    Log.d("swipe", "save reject finished")
  }

  // Fetches all animals from database not found in rejectedProfile.
  private fun fetchProfiles() {
    filteredCache.clear()
    val db = FirebaseFirestore.getInstance()
    auth = FirebaseAuth.getInstance() // Ensure auth is initialized
    val currentUser = auth.currentUser

    currentUser?.let { user ->
      val userId = user.uid
      val userDocRef = db.collection("users").document(userId)

      userDocRef
          .get()
          .addOnSuccessListener { document ->
            if (document != null) {
              val rejectedProfiles =
                  (document.get("rejectedProfiles") as? List<*>)?.filterIsInstance<String>()
                      ?: emptyList()
              val matchedProfiles =
                  (document.get("matchedProfiles") as? List<*>)?.filterIsInstance<String>()
                      ?: emptyList()
              val excludedProfiles = rejectedProfiles + matchedProfiles
              Log.d("SwipeFragment.fetchAnimals", "Excluded Profiles: $excludedProfiles")

              // Fetch all animals
              db.collection("pets")
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
                          } catch (e: Exception) {
                            Log.e("SwipeFragment.fetchAnimals", "Error parsing animal", e)
                            null
                          }
                        }

                    // Filter out rejected profiles locally
                    val filteredAnimals = animals.filter { !excludedProfiles.contains(it.chipID) }
                    filteredAnimals.forEach {
                      filteredCache.add(it)
                      Log.d("Swipe", "Filtered Animal:")
                      Log.d("", "${it.name}:  ${it.chipID}")
                    }

                    // Filter for matched profiles (used in messages) and account?
                    animals.filter { matchedProfiles.contains(it.chipID) }.forEach {
                      matchCache.add(it)
                      Log.d("Swipe", "Matched Animal:")
                      Log.d("", "name:      ${it.name}")
                      Log.d("", "chipID:    ${it.chipID}")
                      Log.d("", "birthdata: ${it.birthdate}")
                      Log.d("", "imageUrl:  ${it.imageUrl}")
                    }

                    // // Filter for rejected profiles (used in account if cache exists)
                    animals.filter { rejectedProfiles.contains(it.chipID) }.forEach {
                      rejectedCache.add(it)
                      Log.d("Swipe", "Rejected Animal:")
                      Log.d("", "name:      ${it.name}")
                      Log.d("", "chipID:    ${it.chipID}")
                      Log.d("", "birthdata: ${it.birthdate}")
                      Log.d("", "imageUrl:  ${it.imageUrl}")
                    }

                    if (isViewAlive) { // Check if the view is still alive
                      displayProfile()
                    }
                  }
                  .addOnFailureListener { exception ->
                    Log.e("SwipeFragment.fetchAnimals", "Error fetching animals", exception)
                    if (isViewAlive) { // Check if the view is still alive
                      displayProfile()
                    }
                  }
            } else {
              Log.d("SwipeFragment", "No such document")
              if (isViewAlive) { // Check if the view is still alive
                displayProfile()
              }
            }
          }
          .addOnFailureListener { exception ->
            Log.d("SwipeFragment", "get failed with ", exception)
            if (isViewAlive) { // Check if the view is still alive
              displayProfile()
            }
          }
    }
  }

  private fun showPetProfileDialog(chipId: String) {
    val bundle = Bundle().apply { putString("chipID", chipId) }
    val dialog = PetProfileFragment()
    dialog.arguments = bundle
    dialog.show(childFragmentManager, "pet_profile_dialog")
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
    isViewAlive = false // Mark view as destroyed
  }
}
