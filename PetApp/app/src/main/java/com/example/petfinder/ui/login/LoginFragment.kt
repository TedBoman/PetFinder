package com.example.petfinder.ui.login

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ContentValues.TAG
import android.content.Context
import android.content.DialogInterface
import android.icu.util.Calendar
import android.os.Bundle
import android.os.CountDownTimer
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.petfinder.R
import com.example.petfinder.databinding.FragmentLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Locale

interface LoginDialogListener {
  fun onClose()
}

class LoginFragment : DialogFragment(), DialogInterface.OnDismissListener {

  private lateinit var auth: FirebaseAuth

  private var listener: LoginDialogListener? = null

  fun setLoginDialogListener(listener: LoginDialogListener) {
    this.listener = listener
  }

  private lateinit var todaysDate: String
  private lateinit var etDatePicker: EditText
  private var _binding: FragmentLoginBinding? = null
  private val binding
    get() = _binding!!

  private var resetPasswordTimerTime = 60000L // 60 Sec between resets
  private var loginTries = 3 // Login tries before cool down
  private var loginTimerTime = 10000L // 10 Sec

  private var timeLeft = 0L
  private val timer =
      object : CountDownTimer(resetPasswordTimerTime, 1000) {
        override fun onTick(millisUntilFinished: Long) {
          timeLeft = millisUntilFinished
          binding.btnResetPassword.setOnClickListener {
            Toast.makeText(
                    requireContext(),
                    "Please wait ${timeLeft / 1000} seconds",
                    Toast.LENGTH_SHORT
                )
                .show()
          }
          "Wait ${millisUntilFinished / 1000} seconds".also { binding.btnResetPassword.text = it }
        }
        override fun onFinish() {
          timeLeft = 0L
          binding.btnLogin.setOnClickListener { resetPassword() }
          getActivity()?.getString(R.string.forgot_password).also {
            binding.btnResetPassword.text = it
          }
        }
      }

  private var loginTriesLeft = loginTries
  private var loginTimeLeft = 0L
  private val loginTimer =
      object : CountDownTimer(loginTimerTime, 1000) {
        override fun onTick(millisUntilFinished: Long) {
          loginTimeLeft = millisUntilFinished
          binding.btnLogin.setOnClickListener {
            Toast.makeText(
                    requireContext(),
                    "Please wait ${loginTimeLeft / 1000} seconds",
                    Toast.LENGTH_SHORT
                )
                .show()
          }

          "Wait ${millisUntilFinished / 1000} seconds".also { binding.btnLogin.text = it }
        }
        override fun onFinish() {
          loginTriesLeft = loginTries
          binding.btnLogin.setOnClickListener { login() }
          getActivity()?.getString(R.string.button_login_text).also { binding.btnLogin.text = it }
        }
      }
  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
  ): View {
    _binding = FragmentLoginBinding.inflate(inflater, container, false)
    val root: View = binding.root

    val sharedPref = activity?.getSharedPreferences("login", Context.MODE_PRIVATE)

    val editor = sharedPref?.edit()
    editor?.putString("key", "value")
    editor?.apply()

    // Set click listeners for buttons
    binding.btnLogin.setOnClickListener { login() }
    binding.btnSwitchToCreate.setOnClickListener { switchToCreate() }
    binding.btnResetPassword.setOnClickListener { resetPassword() }
    binding.tvReadPolicy.setOnClickListener { privacyPolicyToggle() }
    binding.passwordHelpIcon.setOnClickListener { passwordInfoToggle() }
    binding.btnSwitchToLogin.setOnClickListener { switchToLogIn() }
    binding.btnCreateAccount.setOnClickListener { createAccountWithEmailAndPassword() }
    etDatePicker = binding.etDatePicker

    setDate()
    // Button to toggle hiding or showing password.
    showPasswordToggle()

    return root
  }

  private fun switchToCreate() {
    binding.LogInScreen.visibility = View.GONE
    binding.CreateAccountScreen.visibility = View.VISIBLE
  }

  private fun switchToLogIn() {
    binding.CreateAccountScreen.visibility = View.GONE
    binding.LogInScreen.visibility = View.VISIBLE
  }

  private fun isValidEmail(emailAddress: String): Boolean {
    return android.util.Patterns.EMAIL_ADDRESS.matcher(emailAddress).matches()
  }
  private fun resetPassword() {
    val emailAddress = binding.etEmail.text.toString().trim()
    val validEmail = isValidEmail(emailAddress)
    if (validEmail) {
      if (timeLeft == 0L) {
        // Start the timer
        timer.start()
        com.google.firebase.Firebase.auth.sendPasswordResetEmail(emailAddress)
            .addOnCompleteListener {
              if (it.isSuccessful) {
                val toastText = getActivity()?.getString(R.string.password_reset_toast_success)
                Toast.makeText(requireContext(), toastText, Toast.LENGTH_LONG).show()
              }
            }
      }
    } else {
      val toastText = getActivity()?.getString(R.string.invalid_email)
      Toast.makeText(requireContext(), toastText, Toast.LENGTH_LONG).show()
    }
  }

  override fun onStart() {
    super.onStart()
    val dialog = dialog
    if (dialog != null) {
      val width = ViewGroup.LayoutParams.MATCH_PARENT
      val height = ViewGroup.LayoutParams.MATCH_PARENT
      dialog.window?.setLayout(width, height)
    }

    requireDialog().setOnKeyListener { _, keyCode, event ->
      if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
        // Handle back button press here
        val builder = androidx.appcompat.app.AlertDialog.Builder(this.requireContext())
        builder
            .setTitle("Exit")
            .setMessage("Are you sure you want to exit the app?")
            .setPositiveButton("Exit") { dialog, _ ->
              dialog.dismiss()
              activity?.finish()
            }
            .setNegativeButton("Stay") { dialog, _ -> dialog.dismiss() }
            .show()
        true // Indicate that we've consumed the event
      } else {
        false // Let the event propagate normally
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setStyle(STYLE_NORMAL, R.style.FullScreenDialogStyle)

    // Initialize Firebase Auth
    auth = Firebase.auth
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  private fun setDate() {
    // Initialize with current date
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1 // Month is 0 indexed, add 1 for correct display
    val day = calendar.get(Calendar.DAY_OF_MONTH)

    todaysDate = String.format(Locale.UK, "%02d-%02d-%d", day, month, year)
    etDatePicker.setText(todaysDate)

    etDatePicker.setOnClickListener {
      // Date picker dialog
      val datePickerDialog =
          DatePickerDialog(
              requireContext(),
              { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                val adjustedMonth =
                    selectedMonth + 1 // Month is 0 indexed, add 1 for correct display
                val selectedDate =
                    String.format(
                        Locale.UK,
                        "%02d-%02d-%d",
                        selectedDayOfMonth,
                        adjustedMonth,
                        selectedYear
                    )
                etDatePicker.setText(selectedDate)
              },
              year,
              month - 1,
              day
          ) // Subtract 1 for month as DatePickerDialog expects 0 indexed month
      datePickerDialog.show()
    }
  }

  private fun login() {
    if (checkLoginInfo()) {
      loginWithEmailAndPassword() // Use Firebase
    }
  }

  private fun showPasswordToggle() {
    // Buttons.
    val showPasswordCheckbox: CheckBox = binding.checkboxShowPassword
    val etPassword: EditText = binding.etPassword

    showPasswordCheckbox.setOnCheckedChangeListener { _, isChecked ->
      if (isChecked) {
        // Show password
        etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
      } else {
        // Hide password
        etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
      }
    }
  }

  private fun loginWithEmailAndPassword() {
    val email = binding.etEmail.text.toString().trim()
    val password = binding.etPassword.text.toString().trim()

    auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(requireActivity()) { task
      ->
      if (task.isSuccessful) {
        Log.d("FirebaseLogin", "signInWithEmail:success")

        val toast = Toast.makeText(requireContext(), "Login successful!", Toast.LENGTH_LONG)
        toast.show()
        dismiss()
        dismiss()
      } else {
        Log.w("FirebaseLogin", "signInWithEmail:failure", task.exception)
        val toast = Toast.makeText(requireContext(), "Authentication failed.", Toast.LENGTH_LONG)
        toast.show()
        if (--loginTriesLeft <= 0) {
          loginTimer.start()
        }
      }
    }
  }

  private fun convertDateFormat(dateString: String): String {
    // Define the current format of the date string
    val currentFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    // Parse the date string into a Date object
    val date = currentFormat.parse(dateString)

    // Define the new desired format
    val newFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Format the date into the new format and return it
    return newFormat.format(date ?: return "")
  }

  private fun createAccountWithEmailAndPassword() {
    val email = binding.etCreateEmail.text.toString().trim()
    val password = binding.etCreatePassword.text.toString().trim()
    val firstname = binding.etCreateFirstName.text.toString().trim()
    val lastname = binding.etCreateLastName.text.toString().trim()
    val birthdate = convertDateFormat(binding.etDatePicker.text.toString())
    val modCode = binding.etModCode.text.toString().trim()

    if (checkCreateInfo()) {
      if (!isValidPassword(password)) {
        val toast =
            Toast.makeText(
                requireContext(),
                "Password does not meet requirements.",
                Toast.LENGTH_LONG
            )
        toast.show()
      } else {
        if (modCode.isNotEmpty()) {
          validateModCode(modCode) { isValid ->
            if (isValid) {
              createUserAndSaveData(email, password, firstname, lastname, birthdate, true)
            } else {
              showInvalidModCodeAlert()
            }
          }
        } else {
          createUserAndSaveData(email, password, firstname, lastname, birthdate, false)
        }
      }
    }
  }

  private fun validateModCode(modCode: String, callback: (Boolean) -> Unit) {
    val db = Firebase.firestore
    db.collection("modcodes")
        .document(modCode)
        .get()
        .addOnSuccessListener { document ->
          callback(
              document.exists()
          ) // Call the callback with true if the code exists, false otherwise
        }
        .addOnFailureListener { exception ->
          Log.e(TAG, "Error validating mod code:", exception)
          callback(false) // Assume invalid if there's an error
        }
  }

  private fun createUserAndSaveData(
      email: String,
      password: String,
      firstname: String,
      lastname: String,
      birthdate: String,
      isAdmin: Boolean
  ) {
    auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(requireActivity()) {
        task ->
      if (task.isSuccessful) {
        Log.d("FirebaseCreate", "createUserWithEmail:success")
        val user = auth.currentUser

        // Store additional user data in Firestore
        saveUserDataToFirestore(user?.uid, firstname, lastname, birthdate, isAdmin)

        val toast = Toast.makeText(requireContext(), "Account created!", Toast.LENGTH_LONG)
        toast.show()
        dismiss()
      } else {
        Log.w("FirebaseCreate", "createUserWithEmail:failure", task.exception)
        val toast =
            Toast.makeText(
                requireContext(),
                "Account creation failed. Please try again",
                Toast.LENGTH_LONG
            )
        toast.show()
      }
    }
  }

  private fun showInvalidModCodeAlert() {
    AlertDialog.Builder(requireContext())
        .setTitle("Invalid Moderator Code")
        .setMessage(
            "The moderator code you entered is not valid. Please try again or create an account without a code."
        )
        .setPositiveButton("OK", null)
        .show()
  }

  private fun saveUserDataToFirestore(
      userId: String?,
      firstname: String,
      lastname: String,
      birthdate: String,
      isAdmin: Boolean
  ) {
    val db = Firebase.firestore
    val user =
        if (isAdmin) {
          saveRole(
              getActivity()?.getString(R.string.account_prefs_role) ?: "role",
              getActivity()?.getString(R.string.account_prefs_role_admin) ?: "admin"
          )
          hashMapOf(
              (getActivity()?.getString(R.string.account_prefs_first_name)
                  ?: "firstname") to firstname,
              (getActivity()?.getString(R.string.account_prefs_last_name)
                  ?: "lastname") to lastname,
              (getActivity()?.getString(R.string.account_prefs_birthdate)
                  ?: "birthdate") to birthdate,
              (getActivity()?.getString(R.string.account_prefs_role)
                  ?: "role") to
                  (getActivity()?.getString(R.string.account_prefs_role_admin) ?: "admin")
          )
        } else {
          saveRole(
              getActivity()?.getString(R.string.account_prefs_role) ?: "role",
              getActivity()?.getString(R.string.account_prefs_role_user) ?: "user"
          )
          hashMapOf(
              (getActivity()?.getString(R.string.account_prefs_first_name)
                  ?: "firstname") to firstname,
              (getActivity()?.getString(R.string.account_prefs_last_name)
                  ?: "lastname") to lastname,
              (getActivity()?.getString(R.string.account_prefs_birthdate)
                  ?: "birthdate") to birthdate,
              (getActivity()?.getString(R.string.account_prefs_role)
                  ?: "role") to
                  (getActivity()?.getString(R.string.account_prefs_role_user) ?: "user")
          )
        }

    userId?.let { uid ->
      val userDocRef = db.collection("users").document(uid)
      userDocRef
          .set(user)
          .addOnSuccessListener {
            Log.d("Firestore", "User data saved!")

            // Create "inbox" subcollection
            userDocRef
                .collection("inbox")
                .document("empty")
                .set(hashMapOf<String, String>())
                .addOnSuccessListener { Log.d("Firestore", "Inbox subcollection created") }
                .addOnFailureListener { e -> Log.w("Firestore", "Error creating inbox", e) }

            // Create "sent" subcollection
            userDocRef
                .collection("sent")
                .document("empty")
                .set(hashMapOf<String, String>())
                .addOnSuccessListener { Log.d("Firestore", "Sent subcollection created") }
                .addOnFailureListener { e -> Log.w("Firestore", "Error creating sent", e) }
          }
          .addOnFailureListener { e -> Log.w("Firestore", "Error saving user data", e) }
    }
  }

  private fun checkCreateInfo(): Boolean {
    val email = binding.etCreateEmail.text.toString().trim()
    val password = binding.etCreatePassword.text.toString().trim()
    val confirmPassword = binding.etConfirmPassword.text.toString().trim()
    val firstname = binding.etCreateFirstName.text.toString().trim()
    val lastname = binding.etCreateLastName.text.toString().trim()
    val date = binding.etDatePicker.text.toString().trim()

    if (date == todaysDate)
        Toast.makeText(requireContext(), "Invalid birthday, try again.", Toast.LENGTH_LONG).show()

    // Check to see no field is empty and that the date isn't today's date to make sure it has been
    // changed.
    return email.isNotEmpty() &&
        password.isNotEmpty() &&
        confirmPassword == password &&
        firstname.isNotEmpty() &&
        lastname.isNotEmpty() &&
        date != todaysDate
  }

  private fun checkLoginInfo(): Boolean {
    val email = binding.etEmail.text.toString()
    val password = binding.etPassword.text.toString()

    return email.isNotEmpty() && password.isNotEmpty()
  }

  // At least 9 characters password and must contain 1 number, must have a special character and
  // must have lower and upper case.
  private fun isValidPassword(password: String): Boolean {
    if (password.length < 9) return false
    if (!password.any { it.isDigit() }) return false
    if (!password.any { !it.isLetterOrDigit() }) return false
    if (!password.any { it.isUpperCase() }) return false
    if (!password.any { it.isLowerCase() }) return false
    return true
  }

  private fun privacyPolicyToggle() {
    val privacyPolicyContent = getActivity()?.getString(R.string.passwordInfoText)

    val builder = AlertDialog.Builder(requireContext())
    builder
        .setTitle("Privacy Policy")
        .setMessage(privacyPolicyContent)
        .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        .show()
  }

  private fun passwordInfoToggle() {
    val passwordInfoContent = getActivity()?.getString(R.string.passwordInfoText)

    val builder = AlertDialog.Builder(requireContext())
    builder
        .setTitle("Password requirements")
        .setMessage(passwordInfoContent)
        .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        .show()
  }

  // Helper function to save a preference
  private fun saveRole(key: String, value: Any) {
    val sharedPrefs = requireActivity().getSharedPreferences("AccountPrefs", Context.MODE_PRIVATE)
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

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    listener?.onClose() // Call the updated interface method
  }
}
