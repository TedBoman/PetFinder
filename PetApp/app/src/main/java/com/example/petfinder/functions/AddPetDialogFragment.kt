package com.example.petfinder.functions

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.util.Calendar
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.DialogFragment
import com.example.petfinder.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface

import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.firestore.FieldValue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

import com.example.petfinder.database.fetchSpeciesFromDatabaseToAdd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class AddPetDialogFragment : DialogFragment() {

    private lateinit var btnAddPet: Button
    private lateinit var etName: EditText
    private lateinit var etRace: Spinner
    private lateinit var etAbout: EditText
    private lateinit var etDatePicker: EditText
    private lateinit var uploadedImage: ImageView
    private lateinit var timestamp: Timestamp
    private lateinit var backButton: ImageButton

    private lateinit var genderRadioGroup: RadioGroup
    private lateinit var rbFemale: RadioButton
    private lateinit var rbMale: RadioButton

    private lateinit var btnTakeImage: Button
    private lateinit var btnUploadImage: Button

    private lateinit var todaysdate: String
    private var dateChanged = false

    private var selectedImageUri: Uri? = null
    private var cameraUri: Uri? = null

    companion object {
        private const val IMAGE_PICK_CODE = 1000
        private const val REQUEST_CODE = 200
        private val PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view =
            inflater.inflate(R.layout.fragment_addpet, container, false)

        // Initializing the items needed
        etDatePicker = view.findViewById(R.id.et_datePicker)
        btnAddPet = view.findViewById(R.id.btnAddPet)
        etName = view.findViewById(R.id.et_EditName)
        etRace = view.findViewById(R.id.AddPetRaceSpinner)
        etDatePicker = view.findViewById(R.id.et_datePicker)
        etAbout = view.findViewById(R.id.et_EditAbout)
        uploadedImage = view.findViewById(R.id.UploadedImage)


        genderRadioGroup = view.findViewById(R.id.genderRadioGroup)
        rbFemale = view.findViewById(R.id.rb_female)
        rbMale = view.findViewById(R.id.rb_male)

        btnTakeImage = view.findViewById(R.id.TakeImage)
        btnUploadImage = view.findViewById(R.id.UploadImage)

        btnAddPet

        // Restore saved state if it exists
        arguments?.let { args ->
            etName.setText(args.getString("Name"))
            etAbout.setText(args.getString("About"))
            etDatePicker.setText(args.getString("Date"))
            selectedImageUri = args.getParcelable("SelectedImageUri")
            selectedImageUri?.let { uri ->
                uploadedImage.setImageURI(uri)
            }
        }

        setupImageButton()
        setupAddPetButton()
        setupSpinner()

        timestamp = Timestamp.now()
        setDate()

        backButton = view.findViewById(R.id.backButton)

        backButton.setOnClickListener {
            dismiss()
        }

        return view
    }

    private fun hasPermissions(context: Context, vararg permissions: String): Boolean = permissions.all {
        return ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialogStyle)

        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!hasPermissions(requireContext(), *PERMISSIONS)) {
            requestPermissionLauncher.launch(PERMISSIONS)
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
    }

    private fun setupImageButton() {
        btnTakeImage.setOnClickListener {
            Log.d("Get permissions", "Getting Permissions")

            val values = ContentValues()
            values.put(MediaStore.Images.Media.TITLE, "MyPicture")
            values.put(
                MediaStore.Images.Media.DESCRIPTION,
                "Photo taken on " + System.currentTimeMillis()
            )
            cameraUri = requireContext().contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )

            //this is used to open camera and get image file
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri)
            startActivityForResult(cameraIntent, REQUEST_CODE)

            Log.d("Get permissions", "took image")
        }

        btnUploadImage.setOnClickListener {
            val pickImageIntent = Intent(
                Intent.ACTION_PICK,
                MediaStore.Images.Media.INTERNAL_CONTENT_URI
            )
            pickImageIntent.type = "image/*"
            val mimeTypes = arrayOf("image/jpeg", "image/png")
            pickImageIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            startActivityForResult(pickImageIntent, IMAGE_PICK_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_CODE) {
            Log.i("CameraCapture", cameraUri.toString())

            selectedImageUri = cameraUri
            Log.i("ImagePICK", selectedImageUri.toString())
            updateImage()
        }
        if (resultCode == Activity.RESULT_OK && requestCode == IMAGE_PICK_CODE && data != null) {

            selectedImageUri = data.data
            Log.i("ImagePICK", selectedImageUri.toString())

            updateImage()
        }
    }

    private fun updateImage() {
        selectedImageUri.let { uri ->
            uploadedImage.setImageURI(uri)
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

    private fun setDate() {
        // Initialize with current date
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month =
            calendar.get(Calendar.MONTH) + 1 // Month is 0 indexed, add 1 for correct display
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        todaysdate = String.format(Locale.UK,"%02d-%02d-%d", day, month, year)
        etDatePicker.setText(todaysdate)

        etDatePicker.setOnClickListener {
            // Date picker dialog
            val datePickerDialog = DatePickerDialog(
                requireContext(),
                { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                    val adjustedMonth =
                        selectedMonth + 1 // Month is 0 indexed, add 1 for correct display
                    val selectedDate = String.format(Locale.UK, "%02d-%02d-%d", selectedDayOfMonth, adjustedMonth, selectedYear)
                    etDatePicker.setText(selectedDate)
                    timestamp = convertDateStringToTimestamp(selectedDate)
                    dateChanged = true
                },
                year,
                month - 1,
                day
            ) // Subtract 1 for month as DatePickerDialog expects 0 indexed month
            datePickerDialog.show()
        }
    }

    private fun setupAddPetButton() {
        btnAddPet.setOnClickListener {
            GlobalScope.launch { addPetToDatabase() }
        }
    }

    private fun getSelectedGender(): String {
        return when (genderRadioGroup.checkedRadioButtonId) {
            R.id.rb_female -> "Female"
            R.id.rb_male -> "Male"
            else -> "*" // No selection or handle as needed
        }
    }

    private fun addPetToDatabase() {
        val name = etName.text.toString()
        val race = etRace.selectedItem.toString()
        val gender = getSelectedGender()

        // Basic Validation
        if ((name.isEmpty() || race.isEmpty() || gender.isEmpty()) && !dateChanged) {
            Toast.makeText(context, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        uploadImageAndSavePetData()
    }

    private fun getCompressedImageStream(context: Context, imageUri: Uri): InputStream? {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return null

            // Decode the image file into a Bitmap sized to fill the View
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = false
            var bitmap = BitmapFactory.decodeStream(inputStream, null, options) ?: return null
            inputStream.close()

            // Get the correct orientation
            val orientation = getExifOrientation(context, imageUri)
            bitmap = rotateBitmap(bitmap, orientation)

            // Compress the Bitmap as JPEG with a quality of 70%
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
            val bitmapData = outputStream.toByteArray()
            return ByteArrayInputStream(bitmapData)
        } catch (e: Exception) {
            Log.e("ImageCompression", "Error compressing image", e)
            return null
        }
    }


    private fun getExifOrientation(context: Context, imageUri: Uri): Int {
        context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
            val exif = ExifInterface(inputStream)
            return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }
        return 0
    }

    private fun rotateBitmap(bitmap: Bitmap, degree: Int): Bitmap {
        if (degree == 0) return bitmap
        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun uploadImageAndSavePetData() {
        val selectedImageUri = selectedImageUri ?: run {
            Toast.makeText(context, "Please select or take an image", Toast.LENGTH_SHORT).show()
            return
        }

        // Compress the image and get a new InputStream
        val compressedInputStream = getCompressedImageStream(requireContext(), selectedImageUri) ?: run {
            Toast.makeText(context, "Failed to compress and open image stream", Toast.LENGTH_SHORT).show()
            return
        }

        // Create image reference in Firebase storage
        val imageRef = Firebase.storage.reference.child("pet_images/${UUID.randomUUID()}.jpeg") // Changed to JPEG due to compression

        // Upload compressed image from InputStream
        val uploadTask = imageRef.putStream(compressedInputStream)

        // Chain task continuation to handle upload success/failure and get download URL
        uploadTask.continueWithTask { task ->
            if (!task.isSuccessful) {
                task.exception?.let {
                    throw it
                }
            }
            imageRef.downloadUrl
        }.addOnSuccessListener { downloadUri ->
            // Use the download URI after upload succeeds
            savePetDataToFirestore(downloadUri.toString())
        }.addOnFailureListener { e ->
            // Handle upload or URL retrieval failure
            Log.e("Firestore", "Error uploading image", e)
            Toast.makeText(context, "Image upload failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun savePetDataToFirestore(imageUrl: String?) {
        val name = etName.text.toString()
        val race = etRace.selectedItem.toString()
        val date = timestamp
        val about = etAbout.text.toString()
        val gender = getSelectedGender()

        if (!dateChanged){
            Toast.makeText(context, "Please select a date", Toast.LENGTH_SHORT).show()
            return
        }

        Log.w("AddPetDialogFragment", "Selected Date is: ${etDatePicker.text}")

        val petData: MutableMap<String, Any> = HashMap()
        petData["name"] = name
        petData["race"] = race
        petData["date"] = date
        petData["about"] = about
        petData["gender"] = gender
        petData["uploaded"] = FieldValue.serverTimestamp()

        if (imageUrl != null) {
            petData["imageUrl"] = imageUrl
        }

        Firebase.firestore.collection("pets")
            .add(petData)
            .addOnSuccessListener {
                Log.d("Firestore", "Pet added!")
                Toast.makeText(context, "Pet added successfully", Toast.LENGTH_SHORT).show()
                dismiss()
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error adding pet", e)
                Toast.makeText(context, "Failed to add pet", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupSpinner() {
        // Creating a new thread to run the database stuff on
        GlobalScope.launch(Dispatchers.IO) {
            val speciesList = fetchSpeciesFromDatabaseToAdd()
            withContext(Dispatchers.Main) {
                val spinner: Spinner? = view?.findViewById(R.id.AddPetRaceSpinner)
                spinner?.let {
                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, speciesList)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    it.adapter = adapter

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
}