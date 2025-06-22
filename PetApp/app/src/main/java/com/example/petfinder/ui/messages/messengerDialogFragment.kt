package com.example.petfinder.ui.messages

import android.content.ContentValues.TAG
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.petfinder.ChatUpdateViewModel
import com.example.petfinder.ChatUpdateViewModelHolder
import com.example.petfinder.R
import com.example.petfinder.database.Message
import com.example.petfinder.database.getConversationDocId
import com.example.petfinder.database.getLatestMessageForPetFromUser
import com.example.petfinder.database.getRole
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Locale

private lateinit var chatItem: ChatItem_V2

class MessengerDialogFragment : DialogFragment() {
  private lateinit var chatUpdateViewModel: ChatUpdateViewModel

  private var profileImageUrl: String? = null
  private var profileName: String? = null
  private var lastSentMessage: String? = null // Used for spam filter.

  private lateinit var recyclerView: RecyclerView
  private lateinit var editTextMessage: EditText
  private lateinit var messageAdapter: RecyclerView.Adapter<MessageAdapter.MessageViewHolder>
  private lateinit var backButton: ImageButton
  private lateinit var firstName: String
  private lateinit var lastName: String
  private lateinit var role: String
  private lateinit var auth: FirebaseAuth
  private lateinit var db: FirebaseFirestore
  private lateinit var userId: String
  private lateinit var firebaseUser: FirebaseUser
  private lateinit var chipID: String
  // public lateinit var chatItem: ChatItem_V2

  private lateinit var convId: String
  // private var listenerRegistration: ListenerRegistration? = null
  private lateinit var adapter: MessageAdapter
  private var screenHeight: Int? = null
  private var keypadHeight: Int? = null
  private var chatItemJson: String? = null
  var messageDialogResultListener: MessageDialogResultListener? = null

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
  ): View? {
    val view = inflater.inflate(R.layout.chatlog, container, false)

    editTextMessage = view.findViewById(R.id.editTextMessage)

    backButton = view.findViewById(R.id.btnBackButton)

    backButton.setOnClickListener { dismiss() }

    // chipID = arguments?.getString("chipID") ?: ""
    chatItemJson = arguments?.getString(getString(R.string.message_enter_conversation_argument))

    val mainLayout = view?.findViewById<ConstraintLayout>(R.id.constraintLayout)
    mainLayout?.viewTreeObserver?.addOnGlobalLayoutListener {
      val rect = Rect()
      mainLayout.getWindowVisibleDisplayFrame(rect)
      screenHeight = mainLayout.rootView.height // height retrieval
      keypadHeight = mainLayout.rootView.height - rect.bottom // keyboard height retrieval

      // Initialize RecyclerView adapter
    }
    return view
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

    chatUpdateViewModel = ChatUpdateViewModelHolder.retreiveViewModel()

    super.onViewCreated(view, savedInstanceState)
    Log.d("messageDialog", "onViewCreated - Opening")

    auth = Firebase.auth

    db = FirebaseFirestore.getInstance()
    userId = FirebaseAuth.getInstance().currentUser?.uid.toString()
    firstName = ""
    lastName = ""
    role = ""

    loadAccountPrefs()

    recyclerView = view.findViewById(R.id.messageRecyclerView)
    messageAdapter = MessageAdapter()
    recyclerView.adapter = messageAdapter
    recyclerView.layoutManager = LinearLayoutManager(requireContext())

    // Load messages based on the chipID
    loadFromChatItemJson()

    // Get arguments (petName and imageUrl)
    profileImageUrl = chatItem.animal.imageUrl
    profileName = chatItem.animal.name
    convId = chatItem.docId

    val profileImage = view.findViewById<ImageView>(R.id.profileImage)
    val profileNameTextView = view.findViewById<TextView>(R.id.profileName)

    val sendMessageButton: ImageButton = view.findViewById(R.id.sendMessage)
    // Set profile image and name to views
    sendMessageButton.setOnClickListener {
      messageFilterCheck()
    } // Pass chipID when calling sendMessage

    profileNameTextView.text = profileName

    Glide.with(requireContext())
        .load(profileImageUrl)
        .placeholder(R.drawable.default_profile_image)
        .error(R.drawable.default_profile_image)
        .circleCrop()
        .into(profileImage)
    Log.d("messageDialog OnviewCreated", "updateView instantScroll")
    (messageAdapter as? MessageAdapter)?.updateView(instantScroll = true)

    // getLastMessageSent(chipID)
    val keypadHeight = keypadHeight
    val screenHeight = screenHeight

    if (keypadHeight != null && screenHeight != null)
        if (keypadHeight > screenHeight * 0.15
        ) { // If keyboard is visible (adjust threshold as needed)
          val adapter = (messageAdapter as? MessageAdapter)
          if (adapter != null) {
            // val messages = adapter.messages
            // if (messages.isNotEmpty()) {
            // Smooth scroll to the last message
            recyclerView.smoothScrollToPosition(chatItem.messages.size - 1)
          }
        }

    // gets triggered
    chatUpdateViewModel.chatUpdate.observe(
        viewLifecycleOwner,
    ) { (docId, messageItem) ->
      Log.d("messageDialog", "chatUpdate observe")
      Log.d("", "docId:            ${docId}")
      Log.d("", "messageItem:      ${messageItem}")
      Log.d("", "")
      insertMessageFromChatUpdate(docId, messageItem)
    }
  }

  override fun onResume() {
    Log.d("messageDialog", "onResume - opening")
    super.onResume()
  }

  override fun onPause() {
    Log.d("messageDialog", "onPause - opening")
    // chatUpdateViewModel.chatUpdate.removeObservers(viewLifecycleOwner)
    super.onPause()
  }

  override fun onDetach() {
    super.onDetach()
    Log.d("messageDialog", "onDetach - opening")
    // chatUpdateViewModel.chatUpdate.removeObservers(this)
    dismissWithResult(null)
  }

  fun dismissWithResult(result: String?) {
    if (!isAdded) {
      return
    }
    dismiss()
    Log.d("dismissWithResult", "Sending result: ${result}")
    messageDialogResultListener?.onMessageDialogResult(result)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setStyle(STYLE_NORMAL, R.style.FullScreenDialogStyle)
    Log.d("messageDialog", "onViewCreated - Opening")
  }

  override fun onStart() {
    super.onStart()
    Log.d("messageDialog", "onStart - opening")
    val dialog = dialog
    if (dialog != null) {
      val width = ViewGroup.LayoutParams.MATCH_PARENT
      val height = ViewGroup.LayoutParams.MATCH_PARENT
      dialog.window?.setLayout(width, height)
    }
  }

  private fun loadAccountPrefs() {
    val accountPrefs =
        requireActivity()
            .getSharedPreferences(getString(R.string.account_prefs), Context.MODE_PRIVATE)
            ?: return
    firstName = accountPrefs.getString(getString(R.string.account_prefs_first_name), "") ?: "Me"

    lastName =
        accountPrefs
            .getString(getString(R.string.account_prefs_last_name), "")
            .toString() // Returns "null" if null

    role = accountPrefs.getString(getString(R.string.account_prefs_role), "").toString()

    if (role.isNullOrEmpty()) {
      Log.d("loadAccountPrefs", "role is invaild, updating role")

      getRole { _role ->
        if (_role.isNullOrEmpty()) {
          role = getString(R.string.account_prefs_role_user)
        } else {
          role = _role
        }
        accountPrefs.edit().putString(getString(R.string.account_prefs_role), role).apply()
      }
    }
  }

  private fun getLastMessageSent(chipID: String?) {
    if (chipID != null) {
      getConversationDocId(chipID, userId) { docID ->
        if (docID != null) {
          getLatestMessageForPetFromUser(docID) { latestMessage ->
            if (latestMessage != null) {
              lastSentMessage = latestMessage
            }
          }
        }
      }
    }
  }

  private fun insertMessageFromChatUpdate(docId: String, message: Message) {
    if (chatItem.docId != docId || chatItem.messages.last() == message) {
      return
    }
    (messageAdapter as? MessageAdapter)?.addMessage(message)
    // chatItem.messages.add(message)
  }

  private fun loadFromChatItemJson() {
    val gson = Gson()

    chatItem = gson.fromJson(chatItemJson, ChatItem_V2::class.java)
    chatItem.messages.sortBy { it.date }
    chatItem.messages = chatItem.messages.distinct() as MutableList
    if (role != getString(R.string.account_prefs_role_admin)) {
      (messageAdapter as? MessageAdapter)?.setInstructionMessage()
    }
    Log.d("messageDialog", "chatItem loaded")
    chipID = chatItem.animal.chipID // TODO remove this backwards compatability

    (messageAdapter as? MessageAdapter)?.updateView(instantScroll = true)

    Log.d("", "Pet Name:        ${chatItem.animal.name}")
    // Log.d("", "Latest message:  ${chatItem.lastupdated.?toDate() ?: "none"} }")
    Log.d("", "Message docId:   ${chatItem.docId}")
    Log.d("", "This users name: ${chatItem.userName}")
    Log.d("", "${chatItem.messages.size} messages")
    // Log.d("Messages", "")
    // chatItem.messages.forEach { msg ->
    //  Log.d("", "${msg.date.toDate()}")
    //  Log.d("", "${msg.sender}: ${msg.message}")
    // }
    Log.d("", "")
  }

  private fun loadCachedMessagesFromChipID() {
    val gson = Gson()
    val messageCache =
        requireActivity()
            .getSharedPreferences(getString(R.string.message_cache), Context.MODE_PRIVATE)
    val messageCacheEntries = messageCache.getString(getString(R.string.message_cache_entries), "")

    if (messageCacheEntries.isNullOrEmpty()) {
      Log.d("messageDialog", "No saved messages for ${chipID}")
    } else {
      Log.d("messageDialog", "messageCache loaded")
      var loadedChatItem_V2 =
          gson.fromJson(messageCacheEntries, Array<ChatItem_V2>::class.java)
              .asList()
              .toMutableList() // .filter { chipID.contains(it.animal.chipID)}
              .firstOrNull { chipID.contains(it.animal.chipID) }
      if (loadedChatItem_V2 == null) {
        Log.d("messageDialog", "messageCache does not contain ${chipID}")
        // loadMessagesFromDatabase()
        return
      }
      chatItem = loadedChatItem_V2
      Log.d("messageDialog", "chatItem loaded")
      Log.d("", "${chatItem.toString()}")

      // profileImageUrl = loadedChatItem_V2.animal.imageUrl
      // profileName = loadedChatItem_V2.animal.name
      // convId = loadedChatItem_V2.docId
      // if (convId.isNullOrEmpty()) {
      //  convId = chipID + "_" + loadedChatItem_V2.userID
      //  if (convId.length < (28 * 2 + 1)) // length of documentID
      //  {
      //    Log.w("messageDialog", "loadCachedMessagesFromChipID, invalid convId!") // TODO handle
      //    Log.w("", "${convId}")
      //  }
      // }
      // (messageAdapter as? MessageAdapter)?.addMessages(loadedChatItem_V2.messages)
      // loadedChatItem_V2.messages.forEach {
      //  Log.d("", "Sender:   ${it.sender}")
      //  Log.d("", "Message:  ${it.message}")
      //  Log.d("", "Date:     ${it.date.toDate()}")
      //  Log.d("", "")
      // }
    }
  }

  // Function to load messages from Firestore based on chipID
  //  private fun loadMessagesFromDatabase() {
  //    if (role == "admin") {
  //      db.collection("conversations")
  //      .document(convId) // No query needed
  //      .get()
  //      .addOnSuccessListener { document ->
  //        if (document.exists()) {
  //          // Fetch all messages for the conversation (no sender filtering)
  //          document
  //          .reference
  //          .collection("messages")
  //          .orderBy("date")
  //          .get()
  //          .addOnSuccessListener { querySnapshot ->
  //            val instructionMessage =
  //            Message(
  //              sender = "PetFinder",
  //              message =
  //              "Welcome to the PetFinder admin chat.\n" +
  //              "1. Respond promptly and professionally to inquiries about pets.\n" +
  //              "2. Gather information about the potential adopter (lifestyle, experience,
  // etc.).\n" +
  //              "3. Highlight the pet's personality, needs, and history.\n" +
  //              "4. Encourage meaningful conversations about pet compatibility.\n" +
  //              "5. Guide the conversation towards responsible adoption decisions.",
  //              date = Timestamp.now()
  //            )
  //
  //            (messageAdapter as? MessageAdapter)?.add(listOf(instructionMessage))
  //
  //            val messages =
  //            querySnapshot.documents.mapNotNull { document ->
  //              document.toObject(Message::class.java)
  //            }
  //            (messageAdapter as? MessageAdapter)?.addMessages(messages)
  //          }
  //          .addOnFailureListener { exception ->
  //            Log.w(TAG, "Error getting messages.", exception)
  //          }
  //        } else {
  //          Log.w("messengerDialogFragment", "No messages found")
  //        }
  //      }
  //      .addOnFailureListener {
  //        // Handle error fetching the document
  //        Log.e("messengerDialogFragment", "Failed to fetch messages")
  //      }
  //      val messagesCollection =
  //      db.collection("conversations").document(convId).collection("messages")
  //
  //      listenerRegistration =
  //      messagesCollection.orderBy("date").addSnapshotListener { querySnapshot, error ->
  //        Log.d("Admin message listener", "New message received")
  //        if (error != null) {
  //          Log.w(TAG, "Error listening for new messages", error)
  //          return@addSnapshotListener // Exit early on error
  //        }
  //
  //        if (querySnapshot != null) {
  //          // Get new messages from the snapshot, ensuring they're not already in the adapter
  //          val newMessages =
  //          querySnapshot.documentChanges
  //          .filter { it.type == DocumentChange.Type.ADDED }
  //          .mapNotNull { it.document.toObject(Message::class.java) }
  //
  //          // Update the adapter with the new messages
  //          (messageAdapter as? MessageAdapter)?.addMessages(newMessages)
  //        }
  //      }
  //    } else {
  //      // Determine the conversation ID based on user role
  //      val conversationQuery: Query =
  //      db.collection("conversations")
  //      .whereEqualTo("chipID", chipID)
  //      .whereEqualTo("userId", userId)
  //      Log.d("messageDialog conversationQuery", "")
  //      Log.d("", "chipID: ${chipID}")
  //      Log.d("", "userId: ${userId}")
  //      Log.d("", "")
  //
  //      conversationQuery
  //      .get()
  //      .addOnSuccessListener {
  //
  //        // Get the conversation document ID
  //        val conversationId = "${chipID}_$userId"
  //
  //        val messagesCollection =
  //        db.collection("conversations").document(conversationId).collection("messages")
  //
  //        messagesCollection
  //        .orderBy("date")
  //        .get()
  //        .addOnSuccessListener { querySnapshot ->
  //          val instructionMessage =
  //          Message(
  //            sender = "PetFinder",
  //            message =
  //            "Read this before you start talking to the animal shelter.\n" +
  //            "1. Being polite and respectful will increase your chances of talking to an
  // employee.\n" +
  //            "2. Write about yourself so that we can make sure this pet if the right fit for
  // you.\n" +
  //            "3. Ask about the pet! Asking about the pet will make us aware that you are
  // interested in this animal.\n" +
  //            "4. Keep this chat about this pet. Your can start other conversations about other
  // pets in the previous page.\n" +
  //            "5. When starting a conversation about a pet, you may not be the first or the
  // fastest one to respond. This is fine since the employees of the animal shelter will keep
  // talking to people to find the best fit for this specific animal. Fast responses are NOT an
  // advantage, quality is.",
  //            date = Timestamp.now()
  //          )
  //
  //          (messageAdapter as? MessageAdapter)?.setChatItem_V2(listOf(instructionMessage))
  //
  //          if (!querySnapshot.isEmpty) {
  //            val messages =
  //            querySnapshot.documents.mapNotNull { document ->
  //              document.toObject(Message::class.java)
  //            }
  //            (messageAdapter as? MessageAdapter)?.addMessages(messages)
  //          }
  //        }
  //        .addOnFailureListener { exception ->
  //          Log.w(TAG, "Error getting messages.", exception)
  //        }
  //
  //        listenerRegistration =
  //        messagesCollection.orderBy("date").addSnapshotListener { querySnapshot, error ->
  //          Log.d("User message listener", "New message received")
  //          if (error != null) {
  //            Log.w(TAG, "Error listening for new messages", error)
  //            return@addSnapshotListener // Exit early on error
  //          }
  //
  //          if (querySnapshot != null) {
  //            // Get new messages from the snapshot, ensuring they're not already in the
  //            // adapter
  //            val newMessages =
  //            querySnapshot.documentChanges
  //            .filter { it.type == DocumentChange.Type.ADDED }
  //            .mapNotNull { it.document.toObject(Message::class.java) }
  //
  //            // Update the adapter with the new messages
  //            (messageAdapter as? MessageAdapter)?.addMessages(newMessages)
  //          }
  //        }
  //      }
  //      .addOnFailureListener { e -> Log.e("messageDialog", "Error getting messages", e) }
  //    }
  //  }

  // Saves the sent message to the database.
  private fun saveMessageToDatabase(chipID: String, message: String) {
    val conversationDocRef =
        if (role == "admin") {
          // Admin: Find the existing conversation
          db.collection("conversations").document(convId).get().continueWithTask { task ->
            if (task.isSuccessful) {
              // Use the existing conversation ID
              Tasks.forResult(task.result.reference)
            } else {
              // No user conversation found for this pet, handle this case
              Log.d(TAG, "No user conversation found for pet $chipID")
              Tasks.forResult(null) // Return null to indicate no conversation
            }
          }
        } else {
          // User: Create the conversation if it doesn't exist
          val conversationDocRef = db.collection("conversations").document("${chatItem.docId}")
          conversationDocRef.get().continueWithTask { task ->
            if (task.isSuccessful && task.result.exists()) {
              // Conversation exists
              Tasks.forResult(task.result.reference)
            } else {
              // Conversation doesn't exist, create it
              val conversationData =
                  hashMapOf(
                      "petId" to chatItem.animal.chipID,
                      "userId" to userId,
                      "created" to FieldValue.serverTimestamp(),
                      "lastupdated" to FieldValue.serverTimestamp()
                  )
              conversationDocRef.set(conversationData)

              // listenerRegistration?.remove()
              // val messagesCollection = conversationDocRef.collection("messages")
              //    messagesCollection.orderBy("date").addSnapshotListener {
              //        querySnapshot,
              //        error ->
              //      Log.d("User message listener", "New message received")
              //      if (error != null) {
              //        Log.w(TAG, "Error listening for new messages", error)
              //        return@addSnapshotListener // Exit early on error
              //      }

              //      if (querySnapshot != null) {
              //        // Get new messages from the snapshot, ensuring they're not already in
              //        // the adapter
              //        val newMessages =
              //            querySnapshot.documentChanges
              //                .filter { it.type == DocumentChange.Type.ADDED }
              //                .mapNotNull { it.document.toObject(Message::class.java) }

              //        // Update the adapter with the new messages
              //        (messageAdapter as? MessageAdapter)?.addMessages(newMessages)
              //      }
              //    }
              Tasks.forResult(conversationDocRef)
            }
          }
        }

    // Add the message (only if a conversation reference was found or created)
    conversationDocRef
        .addOnCompleteListener { task ->
          if (task.isSuccessful && task.result != null) {
            val conversationRef = task.result
            if (conversationRef != null) {
              addMessageToConversation(conversationRef, userId, message)
            }
          } else {
            // Handle the case where no conversation was found or created
            Log.e(TAG, "Error finding or creating conversation: ", task.exception)
          }
        }
        .addOnFailureListener { exception -> Log.e(TAG, "Error fetching user role: ", exception) }
  }

  private fun addMessageToConversation(
      conversationDocRef: DocumentReference,
      senderId: String,
      messageText: String
  ) {
    val messageData =
        hashMapOf(
            "sender" to senderId,
            "message" to messageText,
            "date" to FieldValue.serverTimestamp() // Use server timestamp for accuracy
        )

    conversationDocRef.update("lastupdated", FieldValue.serverTimestamp())

    conversationDocRef
        .collection("messages")
        .add(messageData)
        .addOnSuccessListener { Log.d(TAG, "Message added to conversation") }
        .addOnFailureListener { e -> Log.w(TAG, "Error adding message", e) }
  }

  /*
  Crude list of words, and sentences, that will be filtered from any chat. Simple attempt att keeping
  chat friendlier. It also removes some common domain extensions. It should somewhat decrease the
  possibility of sending odd links in chat that could contain malicious content.
  */
  companion object {
    val filteredWords =
        listOf(
            // English offensive words
            "whore",
            "faggot",
            "fucktard",
            "kill yourself",
            "cunt",
            "bitch",
            "dick",
            "pussy",
            "asshole",
            "chink",
            "spic",
            "kike",
            "wop",
            "coon",
            "retard",
            "idiot",
            "bastard",
            "shithead",
            "motherfucker",
            "cock",
            "douche",
            "dumbass",
            "jackass",
            "prick",
            "twat",
            "wanker",
            "dyke",
            "skank",
            "bollocks",
            "arse",
            "wog",
            "pikey",
            "git",
            "muppet",

            // Swedish offensive words
            "slampa",
            "hora",
            "fjolla",
            "ta livet av dig",
            "jävla",
            "helvete",
            "knulla",
            "kuk",
            "fitta",
            "idiot",
            "dum i huvudet",
            "skitstövel",
            "bög",
            "hora",
            "blatte",
            "tattare",
            "mongo",
            "cp",
            "homo",
            "pisshuvud",
            "slyna",
            "kuksugare",
            "röv",
            "rövskalle",
            "fitthuvud",

            // URLs and common spam elements
            ".com",
            ".net",
            ".org",
            ".se",
            ".gov",
            ".edu",
            ".tv"
        )
  }
  // Helper function to spamFilterCheck(), simply checks if words in the message are also on the
  // banned messages list. Crude censor tool....
  private fun isMessageValid(message: String): Boolean {
    val urlPattern = "\\b(?:https?|ftp)?://\\S+\\b".toRegex()

    // Check for URLs
    if (urlPattern.containsMatchIn(message)) {
      Toast.makeText(this.requireContext(), "No links allowed in messages.", Toast.LENGTH_SHORT)
          .show()
      return false // Message contains a URL
    }

    for (bannedWord in filteredWords) {
      if (message.contains(bannedWord, ignoreCase = true)) {
        Toast.makeText(
                this.requireContext(),
                "Message contained a banned word. Be kind.",
                Toast.LENGTH_SHORT
            )
            .show()
        return false // Message contains a word on the list of banned words
      }
    }
    return true // Message is valid
  }

  private fun getLastMessageTime(): Long? {
    return chatItem.lastupdated?.toDate()?.time
    // val messages = (messageAdapter as? MessageAdapter)?.messages
    // return messages?.lastOrNull()?.date?.toDate()?.time
  }

  // Function that makes sure message is valid and prevents spam.
  private fun messageFilterCheck() {
    val message = editTextMessage.text.toString()
    Log.d("messageFilterCheck", "message: ${message}")
    // val lastMessageTime = getLastMessageTime()
    val lastMessageTime =
        (chatItem.messages.lastOrNull { it.sender == userId })?.date?.toDate()?.time
    Log.d("messageFilterCheck", "last message time: ${lastMessageTime}")
    Log.d("messageFilterCheck", "last message: ${lastSentMessage}")
    val lastMessageSentByUser = lastSentMessage
    val timeLimit = 1500

    // Prevents sending certain words from a list of banned slurs and words.
    // Meant to at least somewhat clean up the user atmosphere from bad actors.
    if (!isMessageValid(message)) {
      return
    }

    // Can't send the same message twice in a row.
    if (lastMessageSentByUser != null) {
      if (message.trim() == lastMessageSentByUser.trim()) {
        Toast.makeText(
                this.requireContext(),
                "You cannot send the same message twice in a row.",
                Toast.LENGTH_SHORT
            )
            .show()
        return
      }
    }

    if (lastMessageTime != null) {
      if ((lastMessageTime - System.currentTimeMillis()) > timeLimit) {
        Toast.makeText(this.requireContext(), "Slow down.", Toast.LENGTH_SHORT).show()
        return
      }
    }
    // Prevents low-quality spam of messages under 2 characters.
    else if (message.length < 2) {
      Toast.makeText(this.requireContext(), "Minimum 2 letters per message.", Toast.LENGTH_SHORT)
          .show()
      return
    }
    sendMessage(chipID, message)
  }

  private fun sendMessage(chipID: String, message: String) { // Add chipID as a parameter
    if (isMessageValid(message)) {
      lastSentMessage = message
      saveMessageToDatabase(chipID, message)
      editTextMessage.text.clear()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    // listenerRegistration?.remove()
    // listenerRegistration = null
  }

  // Define the adapter directly within the MessengerDialogFragment class
  private class MessageAdapter : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private var recyclerView: RecyclerView? = null
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
      super.onAttachedToRecyclerView(recyclerView)
      this.recyclerView = recyclerView
    }

    // Clear the RecyclerView reference when the adapter is detached
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
      Log.d("MessageAdapter", "onDetachedFromRecyclerView - opening")
      super.onDetachedFromRecyclerView(recyclerView)
      this.recyclerView = null
    }

    fun updateViewRange(
        start: Int,
        count: Int,
        smoothScroll: Boolean? = false,
        instantScroll: Boolean? = false
    ) {
      Log.d(
          "updateViewRange",
          "start: $start\tcount: $count\tsmoothScroll: ${smoothScroll == true}\tinstantScroll: ${instantScroll == true}"
      )
      Log.d("messageDialog updateViewRange", "notifyItemRangeInserted(${start}, ${count}")
      notifyItemRangeInserted(start, count) // Only notify about the added items

      if (smoothScroll == true) {
        smoothScrollBottom()
        Log.d("messageDialog updateViewRange", "smoothScroll")
      } else if (instantScroll == true) {
        Log.d("messageDialog updateViewRange", "instandScroll")
        instantScrollBottom()
      }
    }

    // Refreshes view from all messages and scrolls to bottom
    fun updateView(smoothScroll: Boolean? = false, instantScroll: Boolean? = false) {
      Log.d("messageDialog updateView", "notifyDataSetChanged")
      notifyDataSetChanged()

      if (smoothScroll == true) {
        Log.d("messageDialog updateView", "smoothScroll")
        smoothScrollBottom()
      } else if (instantScroll == true) {
        Log.d("messageDialog updateView", "instantScroll")
        instantScrollBottom()
      }
    }

    fun smoothScrollBottom() {
      if (chatItem.messages.isNotEmpty()) {
        // notifyDataSetChanged()
        recyclerView?.smoothScrollToPosition(chatItem.messages.size - 1)
      }
    }

    fun instantScrollBottom() {
      if (chatItem.messages.isNotEmpty()) {
        recyclerView?.scrollToPosition(chatItem.messages.size - 1)
      }
    }

    fun setInstructionMessage() {
      val insertTime = chatItem.messages.first().date
      val instructionMessage =
          Message(
              sender = "PetFinder",
              message =
                  "Welcome to the PetFinder admin chat.\n" +
                      "1. Respond promptly and professionally to inquiries about pets.\n" +
                      "2. Gather information about the potential adopter (lifestyle, experience, etc.).\n" +
                      "3. Highlight the pet's personality, needs, and history.\n" +
                      "4. Encourage meaningful conversations about pet compatibility.\n" +
                      "5. Guide the conversation towards responsible adoption decisions.",
              date = insertTime
          )
      // addMessage(instructionMessage)
      val newList: MutableList<Message> = mutableListOf()
      newList.add(instructionMessage)
      chatItem.messages =
          (newList +
              chatItem.messages.filter {
                !it.message.contains("Hi I'm ${chatItem.animal.name}! 😊")
              }) as
              MutableList
      // if (chatItem.messages.size > 0) {
      //  chatItem.messages[0] = instructionMessage
      // } else {
      //  chatItem.messages.reverse()
      //  addMessage(instructionMessage)
      //  chatItem.messages.reverse()
      // }
    }
    // Adds message and updates view range
    fun addMessage(newMessage: Message) {
      val previousSize = chatItem.messages.size // Store the original size for notification

      if (previousSize > 1 && chatItem.messages.first().date < newMessage.date) {
        chatItem.messages.sortedBy { it.date }
      }

      chatItem.messages.add(newMessage)
      updateViewRange(
          previousSize,
          previousSize + 1,
          smoothScroll = true
      ) // Only notify about the added items
    }
    // Adds a list of message and updates view range
    fun addMessages(newMessages: List<Message>) {
      val previousSize = chatItem.messages.size // Store the original size for notification
      chatItem.messages.addAll(newMessages)
      updateViewRange(
          previousSize,
          newMessages.size,
          smoothScroll = true
      ) // Only notify about the added items
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
      val view = LayoutInflater.from(parent.context).inflate(R.layout.message_item, parent, false)
      // Log.d("MessageAdapter", "onCreateViewHolder - opening")
      return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
      // Log.d("MessageAdapter", "onBindViewHolder - opening")
      val message = chatItem.messages[position]

      // Get current user's ID
      // val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
      // val currentUserId = Firebase.auth.currentUser?.uid.toString()
      // Log.d("MessengerDialogFragment - onBindViewHolder", "currentUserId = ${currentUserId}")

      // Determine if the message was sent by the current user
      val isCurrentUser =
          if (message.sender.length >= 3 && message.sender.subSequence(0, 3) == "[G]") {
            true
          } else {
            false
          }
      // Log.d("MessengerDialogFragment - onBindViewHolder", "message.sender: ${message.sender}")
      // Log.d("MessengerDialogFragment - onBindViewHolder", "isCUrrentUser = ${isCurrentUser}")
      holder.bind(message, isCurrentUser) // Pass isCurrentUse
    }

    override fun getItemCount(): Int {
      return chatItem.messages.size
    }

    // ViewHolder class for holding views of each message item
    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
      private val messageTextView: TextView = itemView.findViewById(R.id.textMessage)
      private val senderTextView: TextView = itemView.findViewById(R.id.senderTextView)
      private val timestampTextView: TextView = itemView.findViewById(R.id.textTimestamp)

      fun bind(message: Message, isCurrentUser: Boolean) {
        messageTextView.text = message.message
        senderTextView.text =
            if (isCurrentUser) {
              message.sender.subSequence(3, message.sender.length)
            } else if (message.sender == chatItem.animal.chipID) {
              chatItem.animal.name
            } else {
              message.sender
            }

        // Format timestamp, when sending a new message the date is set locally since its not yet
        // stored in the database.
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        // Log.d("messageDialog bind", "timestamp = ${message.date}")
        // Log.d("messageDialog bind", "date = ${message.date.toDate()}")
        val formattedDate = dateFormat.format(message.date.toDate())
        timestampTextView.text = formattedDate

        // Set message color based on sender or receiver (using item view instead of
        // messageContainer)
        val layoutParams = itemView.layoutParams as RecyclerView.LayoutParams // Cast to
        // RecyclerView.LayoutParams
        if (isCurrentUser) {
          // Set the sender color
          itemView.setBackgroundResource(R.drawable.sent_message_background)
        } else {
          // Set the receiver color
          itemView.setBackgroundResource(R.drawable.received_message_background)
        }
        itemView.layoutParams = layoutParams
      }
    }
  }
}
