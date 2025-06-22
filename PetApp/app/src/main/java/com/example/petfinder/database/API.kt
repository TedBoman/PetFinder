package com.example.petfinder.database

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private lateinit var auth: FirebaseAuth

// data class AnimalCache(val chipID: String, val animal: Animal)

public fun getRole(callback: (String) -> Unit) {
  val firebaseUser = Firebase.auth.currentUser
  if (firebaseUser == null) {
    Log.w("getRole", "role is empty!")
    callback("User") // Or handle not logged in appropriately
  }
  var returnMessage: String

  firebaseUser ?: return

  Firebase.firestore
      .collection("users")
      .document(firebaseUser.uid)
      .get()
      .addOnSuccessListener { documentSnapshot ->
        val role = documentSnapshot.getString("role")
        if (role != null) {
          returnMessage =
              if (role.lowercase() == "admin") {
                "Admin"
              } else {
                "User" // Or a different default role
              }
          Log.d("getRole", "role = $role")
          callback(returnMessage)
        }
      }
      .addOnFailureListener { exception ->
        Log.e("Firestore", "Error getting user role", exception)
        returnMessage = "User" // Or handle the error as needed
        callback(returnMessage)
      }
}

suspend fun getRole(): Boolean {
  val firebaseUser = Firebase.auth.currentUser ?: return false // Return false if no user
  val userId = firebaseUser.uid
  var returnVal = false

  try {
    val documentSnapshot =
        withContext(Dispatchers.IO) { // Switch to IO dispatcher for Firestore query
          Firebase.firestore.collection("users").document(userId).get().await()
        }

    val role = documentSnapshot.getString("role")
    returnVal = role?.equals("admin", ignoreCase = true) ?: false
  } catch (exception: Exception) {
    Log.e("Firestore", "Error getting user role", exception)
  }

  return returnVal
}

fun fetchSpeciesFromDatabase(): List<String> {
  // returns all the species
  val animals = listOf("Dog", "Cat", "All")
  return animals
}

fun fetchSpeciesFromDatabaseToAdd(): List<String> {
  // returns all the species
  val animals = listOf("Dog", "Cat")
  return animals
}

fun separateUserIdAndPetId(combinedId: String): Pair<String?, String?> {
  val parts = combinedId.split("_")
  return when (parts.size) {
    2 -> Pair(parts[0], parts[1]) // Success: Valid format
    else -> Pair(null, null) // Error: Invalid format
  }
}

fun getConversationDocId(chipID: String, userId: String, callback: (String?) -> Unit) {
  val db = Firebase.firestore
  db.collection("conversations")
      .whereEqualTo("chipID", chipID)
      .whereEqualTo("userId", userId)
      .get()
      .addOnSuccessListener { querySnapshot ->
        if (!querySnapshot.isEmpty) {
          val docId = querySnapshot.documents[0].id
          callback(docId)
        } else {
          callback(null) // No conversation found
        }
      }
      .addOnFailureListener { exception ->
        Log.e("sendMessage", "Error getting conversation document ID: ", exception)
        callback(null)
      }
}

fun getLatestMessageForPet(
    chipID: String,
    userId: String? = null,
    docId: String? = null,
    _role: String? = null,
    _call: Boolean? = true,
    callback: (String?) -> Unit
) {
  val db = Firebase.firestore
  var role = _role

  auth = Firebase.auth
  val currentUser = auth.currentUser

  currentUser?.let {
    // role = sharedPrefs.getString("role", "").toString()
    if (_role.isNullOrEmpty()) {
      Log.d("getTEST2", "role = $role")
      if (_call != null && !_call) {
        return
      }
      getRole { _role ->
        role = _role
        Log.d("getTEST2", "role = $role")
        Log.d("getTEST3", "_role = $_role")
        getLatestMessageForPet(chipID, userId, docId, _role, false) { ret -> callback(ret) }
      }
    }

    if (role.isNullOrEmpty()) {
      return
    }
    Log.d("getLatestMessageForPet", "role = $role")
    currentUser?.let {
      // role = sharedPrefs.getString("role", "").toString()
      getRole { role ->
        Log.d("getLatestMessageForPet", "Role = $role")
        val conversationQuery =
            if (role == "Admin") {
              val (chipID, userId) = separateUserIdAndPetId(docId!!)
              // Admin: Query all conversations for the pet
              db.collection("conversations")
                  .whereEqualTo("chipID", chipID)
                  .whereEqualTo("userId", userId)
            } else {
              // User: Query for the specific conversation with the user and pet
              // val conversationId = "${chipID}_$currentUserId" // Old leftovers. Ignore.
              db.collection("conversations")
                  .whereEqualTo("chipID", chipID)
                  .whereEqualTo("userId", userId)
            }

        conversationQuery
            .get()
            .addOnSuccessListener { conversationSnapshots ->
              if (conversationSnapshots.isEmpty) {
                callback(null) // No conversations found
                return@addOnSuccessListener
              }

              var latestMessage: String? = null
              var latestTimestamp: Timestamp? = null

              for (conversation in conversationSnapshots) {
                val messagesCollection = conversation.reference.collection("messages")
                messagesCollection
                    .orderBy("date", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { messageSnapshots ->
                      if (!messageSnapshots.isEmpty) {
                        val currentMessage = messageSnapshots.documents[0].getString("message")
                        val currentTimestamp = messageSnapshots.documents[0].getTimestamp("date")
                        if (latestTimestamp == null || currentTimestamp!! > latestTimestamp!!) {
                          latestMessage = currentMessage
                          latestTimestamp = currentTimestamp
                        }
                      }
                      // Check if this was the last conversation to process
                      if (conversation == conversationSnapshots.documents.last() &&
                              messageSnapshots.documents.isNotEmpty()
                      ) {
                        callback(latestMessage)
                      }
                    }
                    .addOnFailureListener { exception ->
                      Log.e("SwipeFragment", "Error fetching latest message: ", exception)
                    }
              }
            }
            .addOnFailureListener { e -> Log.e("messageDialog", "Error getting messages", e) }
      }
    }
  }
}

fun getLatestMessageForPetFromUser(docId: String? = null, callback: (String?) -> Unit) {
  val db = Firebase.firestore
  val auth = Firebase.auth
  val currentUser = auth.currentUser

  currentUser?.let {

    // Ensure docId is not null before proceeding with the query
    if (docId != null) {
      db.collection("conversations")
          .document(docId)
          .collection("messages")
          .orderBy("date", Query.Direction.DESCENDING)
          .limit(1)
          .get()
          .addOnSuccessListener { messageSnapshots ->
            if (!messageSnapshots.isEmpty) {
              val latestMessage = messageSnapshots.documents[0].getString("message")
              callback(latestMessage)
            } else {
              callback(null) // No messages found in this conversation
            }
          }
          .addOnFailureListener { exception ->
            Log.e("getLatestMessageForPet", "Error fetching latest message: ", exception)
            callback(null)
          }
    } else {
      callback(null) // No docId provided
    }
  }
      ?: run {
        callback(null) // No current user found
      }
}

data class Animal(
    val chipID: String,
    val name: String,
    val birthdate: Timestamp? = null,
    val imageUrl: String
)

data class AnimalConversation(
    val chipID: String,
    val name: String,
    val userName: String,
    val birthdate: Timestamp? = null,
    val imageUrl: String,
    val docId: String = "",
    val lastUpdated: Timestamp? = null
)

data class AnimalToRemoveMatch(
    val chipID: String,
    val name: String,
    val imageUrl: String,
)

data class PetInfo(
    val chipID: String,
    val name: String,
    val birthdate: Timestamp? = null,
    val gender: String,
    val about: String,
    val species: String,
    val dateUploaded: Timestamp? = null,
    val imageUrl: String
)

data class Message(
    var sender: String = "",
    var message: String = "",
    val date: Timestamp = Timestamp.now()
)

data class messageUpdate(val docId: String, val messageItem: Message)
