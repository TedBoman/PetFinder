package com.example.petfinder.ui.account

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.example.petfinder.R
import com.example.petfinder.database.getRole
import com.example.petfinder.databinding.FragmentAccountBinding
import com.example.petfinder.functions.NotificationFragment
import com.example.petfinder.functions.RemoveMatchDialogFragment
import com.example.petfinder.ui.login.LoginDialogListener
import com.example.petfinder.ui.login.LoginFragment
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class AccountFragment : Fragment(), LoginDialogListener {

  private lateinit var auth: FirebaseAuth // Initialize Firebase Authentication
  private var _binding: FragmentAccountBinding? = null
  private val binding
    get() = _binding!!

  private var isModerator: Boolean = false

  private lateinit var accountPrefs: SharedPreferences
  private lateinit var firstName: String
  private lateinit var lastName: String
  private lateinit var role: String

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
  ): View {
    _binding = FragmentAccountBinding.inflate(inflater, container, false)
    val root: View = binding.root

    // Reference to settings button.
    val btnResetPassword: Button = binding.root.findViewById(R.id.btnResetPassword)

    // Set up the settings menu

    val btnLogOut: Button = binding.root.findViewById(R.id.btnLogOut)

    val btnRemoveMatch: Button = binding.root.findViewById(R.id.btnMatches)

    val btnDeleteAccount: Button = binding.root.findViewById(R.id.btnDeleteAccount)

    val btnNotification: Button = binding.root.findViewById(R.id.btnNotifications)

    val btnClearCache: Button = binding.root.findViewById(R.id.btnClearCache)

    // Moderator button setup
    val moderatorButton = binding.root.findViewById<Button>(R.id.btnModerator)

    btnResetPassword.setOnClickListener {
      val emailAddress = auth.currentUser?.email
      if (emailAddress == null)
          Toast.makeText(
                  requireContext(),
                  getActivity()?.getString(R.string.null_email_toast),
                  Toast.LENGTH_LONG
              )
              .show()
      else {
        // Create and show the confirmation dialog
        AlertDialog.Builder(requireContext())
            .setTitle("Reset Password")
            .setMessage("Are you sure you want to reset your password?")
            .setPositiveButton("Reset") { _, _ ->
              // Send the password reset email
              Firebase.auth.sendPasswordResetEmail(emailAddress).addOnCompleteListener {
                if (it.isSuccessful) {
                  Toast.makeText(
                          requireContext(),
                          getActivity()?.getString(R.string.password_reset_toast_success),
                          Toast.LENGTH_LONG
                      )
                      .show()
                } else {
                  Toast.makeText(
                          requireContext(),
                          getActivity()?.getString(R.string.password_reset_toast_failure),
                          Toast.LENGTH_LONG
                      )
                      .show()
                }
              }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
              dialog.dismiss() // Dismiss the dialog if user cancels
            }
            .show()
      }
    }
    // Set up the logout button, confirmation window to prevent accidental logging out.
    btnLogOut.setOnClickListener {
      // Create and show the confirmation dialog
      AlertDialog.Builder(requireContext())
          .setTitle("Log Out")
          .setMessage("Are you sure you want to log out?")
          .setPositiveButton("Log out") { _, _ ->
            // Proceed with the log out
            logOut()
          }
          .setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss() // Dismiss the dialog if user cancels
          }
          .show()
    }
    btnRemoveMatch.setOnClickListener {
      getRole { role ->
        if (role == "Admin") {
          Toast.makeText(requireContext(), "Admin cannot remove matches", Toast.LENGTH_SHORT).show()
        } else {
          val notificationFragment = RemoveMatchDialogFragment()
          notificationFragment.show(this.childFragmentManager, "remove_match_dialog")
        }
      }
    }

    btnNotification.setOnClickListener {
      val notificationFragment = NotificationFragment()
      notificationFragment.show(this.childFragmentManager, "notification_dialog")
    }
    btnDeleteAccount.setOnClickListener { deleteUser() }

    btnClearCache.setOnClickListener {
      clearAllPreferences()

      Toast.makeText(this.requireContext(), "Cache cleared!", Toast.LENGTH_SHORT).show()
      getName()
      checkMod()
    }

    moderatorButton.setOnClickListener { navigateToModerator() }

    return root
  }

  private fun navigateToModerator() {
    view?.let {
      val navController = Navigation.findNavController(it)
      navController.navigate(R.id.navigation_moderator)
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    auth = Firebase.auth // Initialize Firebase Auth here if needed
    firstName = ""
    lastName = ""
    role = ""
    getRole { _role -> role = _role }
    getName()
    GlobalScope.launch { checkMod() }

    accountPrefs =
        requireContext().getSharedPreferences(getActivity()?.getString(R.string.account_prefs), 0)

    checkLoggedInState()
  }

  override fun onResume() {
    super.onResume()
    auth = Firebase.auth
  }

  private var userProfileName: String? = null

  private fun displayUserName(role: Boolean) {
    val textViewProfileInfo = binding.root.findViewById<TextView>(R.id.text_profileInfo)
    val firebaseUser = Firebase.auth.currentUser
    if (firebaseUser == null) {
      Log.w("UserProfile", "User not logged in, cannot display name")
      textViewProfileInfo.text = getActivity()?.getString(R.string.not_logged_in)
      return
    }
    if (userProfileName != null) {
      // Use the stored profile name
      if (role) {
        val name = "Welcome Moderator $userProfileName!"
        textViewProfileInfo.text = name
      }
      textViewProfileInfo.text = userProfileName
    } else {
      Log.d("UserProfile", "Name: $firstName $lastName Moderator: $role")
      val name = "Welcome $firstName $lastName!"
      textViewProfileInfo.text = name
    }
  }

  private fun getName() {
    val sharedPrefs =
        requireActivity()
            .getSharedPreferences(
                getActivity()?.getString(R.string.account_prefs),
                Context.MODE_PRIVATE
            )

    firstName =
        sharedPrefs
            .getString(getActivity()?.getString(R.string.account_prefs_first_name), "")
            .toString() // Returns "null" if null

    lastName =
        sharedPrefs
            .getString(getActivity()?.getString(R.string.account_prefs_last_name), "")
            .toString() // Returns "null" if null

    if (firstName == "" || lastName == "") {
      val firebaseUser = Firebase.auth.currentUser
      firebaseUser ?: return
      Firebase.firestore
          .collection("users")
          .document(firebaseUser.uid)
          .get()
          .addOnSuccessListener { document ->
            firstName = document.getString("firstname").toString()
            lastName = document.getString("lastname").toString()
            saveSharedPref(
                getActivity()?.getString(R.string.account_prefs_first_name) ?: "",
                firstName
            )
            saveSharedPref(
                getActivity()?.getString(R.string.account_prefs_last_name) ?: "",
                lastName
            )
          }
          .addOnFailureListener { e -> Log.e("UserProfile", "Error fetching user name", e) }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  private fun checkLoggedInState() {
    Log.d("AccountFragment", "Checking logged in")
    val currentUser = auth.currentUser
    if (currentUser == null) {
      val loginFragment = LoginFragment()
      val accountFragment = this

      loginFragment.setLoginDialogListener(accountFragment) // Set the listener

      if (childFragmentManager.backStackEntryCount > 0) {
        childFragmentManager.popBackStackImmediate() // Clear all
      }
      loginFragment.show(this.childFragmentManager, "login_dialog")
    }
  }

  override fun onClose() {
    GlobalScope.launch {
      checkMod()
      Log.d("Admin", "Testing ADMIN")
    }
  }

  private fun checkMod() {
    getRole { _role ->
      role = _role.lowercase()
      if (isAdded) { // Check if fragment is still attached
        Log.w("API GetRole", "Role was $role")
        if (role == (getActivity()?.getString(R.string.account_prefs_role_admin)) ?: "admin") {
          Log.d("Moderator", "You are an admin")
          binding.btnModerator.isVisible = true
          isModerator = true
          displayUserName(true)
          saveSharedPref(
              getActivity()?.getString(R.string.account_prefs_role) ?: "",
              getActivity()?.getString(R.string.account_prefs_role_admin) ?: ""
          )
        } else {
          Log.d("Moderator", "You are not an admin")
          saveSharedPref(
              getActivity()?.getString(R.string.account_prefs_role) ?: "",
              getActivity()?.getString(R.string.account_prefs_role_user) ?: ""
          )
          displayUserName(false)
        }
      }
    }
  }

  // Helper function to save a preference
  private fun saveSharedPref(key: String, value: Any) {
    if (key.isNullOrEmpty()) {
      Log.w("AccountFragment", "saveSharedPref - key.isNullOrEmpty")
      return
    }
    val sharedPrefs =
        requireActivity()
            .getSharedPreferences(
                getActivity()?.getString(R.string.account_prefs) ?: "AccountPrefs",
                Context.MODE_PRIVATE
            )
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

  private fun clearAllPreferences() {
    val sharedPrefs =
        requireActivity()
            .getSharedPreferences(
                getActivity()?.getString(R.string.account_prefs) ?: "AccountPrefs",
                Context.MODE_PRIVATE
            )

    val sharedMatches =
        requireActivity()
            .getSharedPreferences(
                getActivity()?.getString(R.string.match_cache) ?: "match_cache",
                Context.MODE_PRIVATE
            )

    val sharedRejects =
        requireActivity()
            .getSharedPreferences(
                getActivity()?.getString(R.string.rejected_cache) ?: "rejected_cache",
                Context.MODE_PRIVATE
            )

    val messageCache =
        requireActivity()
            .getSharedPreferences(
                getActivity()?.getString(R.string.message_cache),
                Context.MODE_PRIVATE
            )
    val sharedFilterPrefs =
        requireActivity()
            .getSharedPreferences(getString(R.string.filter_preferences), Context.MODE_PRIVATE)

    val animalCache =
        requireContext()
            .getSharedPreferences(getString(R.string.animal_cache), Context.MODE_PRIVATE)

    val messageCachePaths =
        requireActivity()
            .getSharedPreferences(
                getActivity()?.getString(R.string.message_cache_entries_paths),
                Context.MODE_PRIVATE
            )

    if (role == (getActivity()?.getString(R.string.account_prefs_role_admin)) ?: "admin") {
      Log.d("AccountFragment - clearAllPreferences", "Clearing as admin")
      // admin_userCache
      var userCache =
          requireActivity()
              .getSharedPreferences(
                  getActivity()?.getString(R.string.user_cache),
                  Context.MODE_PRIVATE
              )

      userCache.edit().clear().commit()

      // messageCachePaths, cleared with message_cache?

    }

    messageCachePaths.edit().clear().commit()

    sharedMatches.edit().clear().commit()

    sharedRejects.edit().clear().commit()

    messageCache.edit().clear().commit()

    sharedPrefs.edit().clear().commit()

    sharedFilterPrefs.edit().clear().commit()

    animalCache.edit().clear().commit()

    // with(sharedPrefs.edit()) {
    //  putString(getActivity()?.getString(R.string.account_prefs_role) ?: "role", "")
    //  putString(getActivity()?.getString(R.string.account_prefs_first_name) ?: "firstname", "")
    //  putString(getActivity()?.getString(R.string.account_prefs_last_name) ?: "lastname", "")
    //  commit()
    // }

    // with(sharedMatches.edit()) {
    //  putString(getActivity()?.getString(R.string.match_cache_entries) ?: "match_cache_entries",
    // "")
    //  commit()
    // }

    // with(sharedRejects.edit()) {
    //  putString(
    //      getActivity()?.getString(R.string.rejected_cache_entries) ?: "rejected_cache_entries",
    //      ""
    //  )
    //  commit()
    // }

    // with(messageCache.edit()) {
    //  putString(
    //      getActivity()?.getString(R.string.message_cache_entries) ?: "message_cache_entries",
    //      ""
    //  )
    //  commit()
    // }
  }

  private fun logOut() {
    // Firebase Authentication sign out
    // saveSharedPref(getActivity()?.getString(R.string.account_prefs_role) ?: "role", "")
    // saveSharedPref(getActivity()?.getString(R.string.account_prefs_first_name) ?: "firstname",
    // "")
    // saveSharedPref(getActivity()?.getString(R.string.account_prefs_last_name) ?: "lastname", "")
    let { clearAllPreferences() }
    auth.signOut()
    checkLoggedInState()
  }

  private fun deleteUser() {
    val firebaseUser = auth.currentUser ?: return // Return if no user is logged in

    // Build the AlertDialog
    val builder = androidx.appcompat.app.AlertDialog.Builder(this.requireContext())
    builder
        .setTitle("Delete Account")
        .setMessage("Are you sure you want to permanently delete your account?")
        .setPositiveButton("Delete") { dialog, _ ->
          Firebase.firestore
              .collection("users")
              .document(firebaseUser.uid)
              .delete()
              .addOnSuccessListener { Log.d("Firestore", "User data deleted from Firestore") }
              .addOnFailureListener { e -> Log.e("Firestore", "Error deleting user data", e) }
          firebaseUser.delete().addOnCompleteListener { task ->
            if (task.isSuccessful) {
              Log.d("Firestore", "Account deleted successfully")
              // saveSharedPref(getActivity()?.getString(R.string.account_prefs_role) ?: "role", "")
              clearAllPreferences()
              checkLoggedInState()
              Toast.makeText(
                      this.requireContext(),
                      "Account deleted successfully",
                      Toast.LENGTH_SHORT
                  )
                  .show()
            } else {
              Log.e("Firestore", "Error deleting account", task.exception)
              Toast.makeText(this.requireContext(), "Error deleting account.", Toast.LENGTH_SHORT)
                  .show()
            }
          }
          dialog.dismiss()
        }
        .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
        .show()
  }
}
