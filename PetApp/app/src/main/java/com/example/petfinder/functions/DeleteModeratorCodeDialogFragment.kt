package com.example.petfinder.functions

import android.content.ContentValues.TAG
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.petfinder.R
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class DeleteModeratorCodeDialogFragment : DialogFragment() {

    private lateinit var backButton: ImageButton

    private lateinit var codeAdapter: ModCodeAdapter
    private val modCodes = mutableListOf<DocumentSnapshot>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout file
        val view = inflater.inflate(R.layout.fragment_delete_mod_code, container, false)

        backButton = view.findViewById(R.id.backButton)

        backButton.setOnClickListener {
            dismiss()
        }

        val recyclerView: RecyclerView = view.findViewById(R.id.rv_DeleteCodes)
        codeAdapter = ModCodeAdapter(modCodes) { modCode ->
            // Delete the mod code from Firestore
            deleteModCode(modCode)
        }
        recyclerView.adapter = codeAdapter
        recyclerView.layoutManager = LinearLayoutManager(context)

        GlobalScope.launch {
            fetchModCodes()
        }

        return view
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

    private fun fetchModCodes() {
        val db = Firebase.firestore
        db.collection("modcodes")
            .get()
            .addOnSuccessListener { result ->
                modCodes.clear()
                modCodes.addAll(result.documents) // Add the DocumentSnapshot objects
                codeAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error getting mod codes.", exception)
            }
    }

    private fun deleteModCode(modCode: DocumentSnapshot) {
        val db = Firebase.firestore
        db.collection("modcodes").document(modCode.id) // Assuming codeId is the document ID
            .delete()
            .addOnSuccessListener {
                modCodes.remove(modCode)
                codeAdapter.notifyItemRemoved(modCodes.indexOf(modCode))
                Toast.makeText(context, "Mod code deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error deleting mod code.", exception)
                Toast.makeText(context, "Failed to delete mod code", Toast.LENGTH_SHORT).show()
            }
    }

    class ModCodeAdapter(
        private val modCodes: MutableList<DocumentSnapshot>,
        private val onDelete: (DocumentSnapshot) -> Unit
    ) : RecyclerView.Adapter<ModCodeAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val codeIdTextView: TextView = itemView.findViewById(R.id.tv_CodeID)
            val createdByUserIdTextView: TextView = itemView.findViewById(R.id.tv_CreatedByID)
            val createdByUserNameTextView: TextView = itemView.findViewById(R.id.tv_CreatedByUserName)
            val deleteButton: Button = itemView.findViewById(R.id.deleteButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemView = LayoutInflater.from(parent.context).inflate(R.layout.mod_code_delete, parent, false)
            return ViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val modCodeDocument = modCodes[position]

            holder.codeIdTextView.text = modCodeDocument.id // Use document ID as code ID
            holder.createdByUserIdTextView.text = modCodeDocument.getString("addedByUser")

            // Get the user's name from Firestore based on addedByUser ID
            val addedByUserId = modCodeDocument.getString("addedByUser")
            if (addedByUserId != null) {
                FirebaseFirestore.getInstance().collection("users").document(addedByUserId)
                    .get()
                    .addOnSuccessListener { userDocument ->
                        Log.d(TAG, "User name: ${userDocument.getString("firstname")} ${userDocument.getString("lastname")}")
                        val firstName = userDocument.getString("firstname")
                        val lastName = userDocument.getString("lastname")
                        val codeCreatedBy = if (firstName != null && lastName != null) {
                            "$firstName $lastName"
                        } else {
                            "Deleted User"
                        }
                        holder.createdByUserNameTextView.text = codeCreatedBy
                    }
                    .addOnFailureListener { exception ->
                        Log.w(TAG, "Error getting user name: ", exception)
                        holder.createdByUserNameTextView.text = "Deleted User"
                    }
            } else {
                holder.createdByUserNameTextView.text = "Deleted User"
            }

            holder.deleteButton.setOnClickListener {
                onDelete(modCodeDocument)
            }
        }

        override fun getItemCount(): Int {
            return modCodes.size
        }
    }

}