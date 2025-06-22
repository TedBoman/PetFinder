package com.example.petfinder.functions

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.petfinder.R
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase


class CreateModeratorCodeDialogFragment : DialogFragment() {

    private lateinit var createButton: Button
    private lateinit var backButton: ImageButton
    private lateinit var etModCode: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout file
        val view = inflater.inflate(R.layout.fragment_create_mod_code, container, false)

        createButton = view.findViewById(R.id.CreateButton)
        backButton = view.findViewById(R.id.backButton)
        etModCode = view.findViewById(R.id.tv_ModCode)

        backButton.setOnClickListener {
            dismiss()
        }

        createButton.setOnClickListener {
            createModeratorCode()
        }

        return view
    }

    private fun createModeratorCode() {

        val modCodeData = hashMapOf(
            "addedByUser" to Firebase.auth.currentUser?.uid,
            "created" to FieldValue.serverTimestamp()
        )

        Firebase.firestore.collection("modcodes")
            .add(modCodeData)
            .addOnSuccessListener {
                Log.d("Firestore", "modcode created successfully!")
                Toast.makeText(context, "Code created successfully", Toast.LENGTH_SHORT).show()
                etModCode.text = it.id
                etModCode.visibility = View.VISIBLE
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error adding code", e)
                Toast.makeText(context, "Failed to add code", Toast.LENGTH_SHORT).show()
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

}