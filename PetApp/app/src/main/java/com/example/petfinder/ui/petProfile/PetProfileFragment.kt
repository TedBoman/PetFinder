package com.example.petfinder.ui.petProfile

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.example.petfinder.R
import com.example.petfinder.database.PetInfo
import com.example.petfinder.databinding.FragmentProfileBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Locale

class PetProfileFragment : DialogFragment() {
  private lateinit var auth: FirebaseAuth // Initialize Firebase Authentication

  private var _binding: FragmentProfileBinding? = null

  private lateinit var chipID: String

  private lateinit var profileDescriptor: TextView
  private lateinit var nameTextView: TextView
  private lateinit var image: ImageView
  private lateinit var birthDate: TextView
  private lateinit var gender: TextView
  private lateinit var aboutText: TextView
  private lateinit var uploaded: TextView

  private lateinit var likeBtn: ImageButton

  private lateinit var returnToProfileButton: ImageButton

  private val binding
    get() = _binding!!

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
  ): View {
    _binding = FragmentProfileBinding.inflate(inflater, container, false)
    val root: View = binding.root

    chipID = requireArguments().getString("chipID", "")
    Log.w("PetProfileFragment", "ChipID is $chipID")

    returnToProfileButton = binding.root.findViewById(R.id.btnReturnSwipe)
    profileDescriptor = binding.profileDescriptor
    nameTextView = binding.nameID
    image = binding.profilePageImage
    birthDate = binding.birthDate
    gender = binding.GenderID
    aboutText = binding.aboutText
    uploaded = binding.dateUploaded
    likeBtn = binding.btnLike

    val sharedPrefs = requireActivity().getSharedPreferences("AccountPrefs", Context.MODE_PRIVATE)
    val role = sharedPrefs.getString("role", "")

    if (role == "admin") {
      likeBtn.visibility = View.GONE
    }

    likeBtn.setOnClickListener { updateUserProfiles(chipID) }
    returnToProfileButton.setOnClickListener { returnToPrev() }

    fetchPet()

    return root
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

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setStyle(STYLE_NORMAL, R.style.FullScreenDialogStyle)

    auth = Firebase.auth
  }

  // Returns user to swipe button.
  private fun returnToPrev() {
    dismiss()
  }

  private fun updateUserProfiles(chipID: String) {
    val db = FirebaseFirestore.getInstance()
    val currentUser = auth.currentUser

    currentUser?.let { user ->
      val userId = user.uid
      val userDocRef = db.collection("users").document(userId)

      val fieldToUpdate = "matchedProfiles"
      userDocRef
          .update(fieldToUpdate, FieldValue.arrayUnion(chipID))
          .addOnSuccessListener { Log.d("SwipeFragment", "Updated $fieldToUpdate with $chipID") }
          .addOnFailureListener { e -> Log.e("SwipeFragment", "Error updating $fieldToUpdate", e) }
    }
  }

  private fun fetchPet() {
    val db = FirebaseFirestore.getInstance()

    val query = db.collection("pets").document(chipID) // Target the "pets" collection

    query
        .get()
        .addOnSuccessListener { document ->
          val pet =
              PetInfo(
                  document.id, // Ensure the fields match your Firestore data
                  document.getString("name") ?: "",
                  document.getTimestamp("date") ?: Timestamp.now(),
                  document.getString("gender") ?: "",
                  document.getString("about") ?: "",
                  document.getString("race") ?: "",
                  document.getTimestamp("uploaded"),
                  document.getString("imageUrl") ?: ""
              )
          setPetValues(pet)
        }
        .addOnFailureListener { exception ->

          // Handle query failure
          Log.e("Firestore", "Error fetching pet", exception)
        }
  }

  private fun setPetValues(pet: PetInfo) {
    Log.w(
        "ShowPetInfo",
        "${pet.name}, ${pet.birthdate}, ${pet.gender}, ${pet.about}, ${pet.species}"
    )
    nameTextView.text = pet.name
    aboutText.text = "${aboutText.text} ${pet.species}"
    birthDate.text = "${birthDate.text}: ${pet.birthdate?.let { formatDate(it) }}"
    gender.text = "${gender.text}: ${pet.gender}"
    profileDescriptor.text = pet.about

    if (pet.dateUploaded != null) {
      // Display the date
      uploaded.text = "Uploaded: ${pet.dateUploaded.let { formatDate(it) }}"
    } else {
      // Display a default message
      uploaded.text = "No date uploaded"
    }

    // Load Image (Using Glide as an example, similar for Picasso)
    Glide.with(requireContext()).load(pet.imageUrl).into(image)
  }

  private fun formatDate(timestamp: Timestamp): String {
    val date = timestamp.toDate() // Converts the Timestamp to a java.util.Date
    val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()) // Set up the date format
    return dateFormat.format(date) // Format the date
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}

