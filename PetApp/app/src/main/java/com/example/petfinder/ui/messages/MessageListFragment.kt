package com.example.petfinder.ui.messages

// import com.example.petfinder.database.AnimalCache
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.petfinder.App
import com.example.petfinder.ChatUpdateViewModel
import com.example.petfinder.ChatUpdateViewModelHolder
import com.example.petfinder.NotificationViewModelHolder_V2
import com.example.petfinder.NotificationViewModel_V2
import com.example.petfinder.R
import com.example.petfinder.database.Animal
import com.example.petfinder.database.Message
import com.example.petfinder.database.getRole
import com.example.petfinder.databinding.FragmentMessageBinding
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.gson.Gson
import java.text.SimpleDateFormat
import kotlin.collections.mutableListOf
import kotlin.collections.sortByDescending
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

data class ChatItem(
    val imageUrl: String,
    val petName: String,
    val userName: String,
    var latestMessage: String,
    val docId: String? = null,
    var lastupdated: Timestamp? = null
)

data class ChatItem_V2(
    val animal: Animal, // All information about animal from swipe
    val docId: String,
    var userName: String,
    val userID: String,
    var messages: MutableList<Message>, // List of all sent messages yet in this chat
    var lastupdated: Timestamp, // Pull new messages newer than this timestamp
    var created: Timestamp? = null
)

data class UserItem(val userId: String, val firstName: String, val lastName: String)

class ChatAdapter_V2(
    private val items: MutableList<ChatItem_V2>,
    private val isAdmin: Boolean,
    private val onItemClick: (ChatItem_V2) -> Unit
) : RecyclerView.Adapter<ChatAdapter_V2.ChatViewHolder>() {
  class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val chatImage: ImageView = view.findViewById(R.id.ChatImage)
    val petName: TextView = view.findViewById(R.id.PetName)
    val latestMessage: TextView = view.findViewById(R.id.LatestMessage)
  }

  public fun sortItems() {
    // items.sortWith(nullsLast(compareBy { it.lastupdated }))
    items.sortByDescending { it.lastupdated }
    // val firstElementSize = items.first().messages.size
    // if (firstElementSize > 1) {
    //  items.reverse()
    // }
    notifyDataSetChanged()
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
    // Log.d("messageFragment", "onCreateViewHolder - Opening")
    val view = LayoutInflater.from(parent.context).inflate(R.layout.message_log, parent, false)
    return ChatViewHolder(view)
  }

  override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
    // Log.d("messageFragment", "onBindViewHolder - Opening")
    val item = items[position]
    Log.d("ChatAdapter_V2 - onBindViewHolder", "userName = ${item.userName}")
    // Bind data to the views
    holder.petName.text =
        if (isAdmin) {
          "${item.animal.name} | ${item.userName}"
        } else {
          item.animal.name
        }

    var latestMessage = item.messages.last().message
    holder.latestMessage.text =
        latestMessage.take(30).let {
          if (latestMessage.length >= 30) "${it.trim()}..."
          else it // Check the original latestMessage's length
        }

    // Load image into chatImage using Glide
    Glide.with(holder.itemView.context)
        .load(item.animal.imageUrl)
        .placeholder(R.drawable.ic_account_black_24px) // Optional: a placeholder image
        .error(R.drawable.image_logotype) // Optional: an error image
        .circleCrop() // makes it a circle
        .into(holder.chatImage)

    holder.itemView.setOnClickListener { onItemClick(item) }
  }
  override fun getItemCount() = items.size
}

interface MessageDialogResultListener {
  fun onMessageDialogResult(result: String?)
}

class MessageFragment : Fragment(), MessageDialogResultListener {
  private var isCurrentFragment = false
  private var _binding: FragmentMessageBinding? = null
  private val binding
    get() = _binding!!
  private lateinit var auth: FirebaseAuth

  private lateinit var sharedPrefs: SharedPreferences
  private lateinit var role: String

  private val notificationViewModel_V2: NotificationViewModel_V2 by lazy {
    NotificationViewModelHolder_V2.retreiveViewModel_V2()
  }

  private val chatUpdateViewModel: ChatUpdateViewModel by lazy {
    ChatUpdateViewModelHolder.retreiveViewModel()
  }

  private lateinit var chatItems_V2: MutableList<ChatItem_V2>

  private lateinit var adapter_V2: ChatAdapter_V2
  private lateinit var animalCache: MutableList<Animal>
  private lateinit var admin_userCache: MutableList<UserItem>
  private lateinit var messageCachePaths: MutableList<String>
  private lateinit var conversationListeners: MutableMap<String, ListenerRegistration>
  private lateinit var name: String
  private var isAdmin: Boolean = false
  private var timestampPlaceholder = Timestamp(SimpleDateFormat("dd-MM-yyyy").parse("01-01-1970")!!)

  private lateinit var userId: String
  private lateinit var db: FirebaseFirestore

  private var currentMessageDialogDocId: String? = null

  override fun onAttach(context: Context) {
    Log.d("messageFragment", "onAttach - Opening")
    super.onAttach(context)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d("messageFragment", "onCreate - Opening")
  }

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
  ): View {

    _binding = FragmentMessageBinding.inflate(inflater, container, false)

    val root: View = binding.root
    isCurrentFragment = true

    return root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    // Log.d("messageFragment", "onViewCreated - Opening")
    // chatUpdateViewModel.chatUpdate.observe(viewLifecycleOwner, this)

    conversationListeners = mutableMapOf<String, ListenerRegistration>()
    auth = Firebase.auth
    db = FirebaseFirestore.getInstance()
    userId = auth.currentUser?.uid.toString()

    chatItems_V2 = mutableListOf()
    animalCache = mutableListOf()
    messageCachePaths = mutableListOf() // Used by admin to keep track of cache
    name = ""
    role = ""

    var progressBar = binding.progressBar
    progressBar.visibility = View.VISIBLE

    // As admin
    //  * loadAccountPrefs
    //  * load messageCachePaths
    //    * if none ->
    //      * load database conversation documents
    //      * Update animalCacheEntriesPath
    //
    //  * load animalCache
    //    * if animalCache empty ->
    //      * Filter chipID duplicates from animalCacheEntriesPath to a new list
    //      * Load each animal from database into animalCache
    //  * load userCache
    //    * if userCache empty ->
    //      * Filter userID duplicates from animalCacheEntriesPath to a new list
    //      * Load each user from database into userCache
    //  * load message entries by path into chatItems list
    //    * if none ->
    //      * Load conversations from database
    //      * Store each in chatItems list
    //
    //  * onSaveInstanceState ->
    //    * save animalCacheEntriesPath
    //    * save userCache
    //    * save animalCache

    Log.d("MessageFragment - onViewCreated", "Loading account prefs")
    loadAccountPrefs()
    Log.d("MessageFragment - onViewCreated", "Finished loading account prefs")

    // adapter = ChatAdapter(chatItems) { chatItem -> enterConversation(chatItem) }
    // binding.searchView.adapter = adapter
    //

    binding.searchView.layoutManager = LinearLayoutManager(context)

    adapter_V2 =
        ChatAdapter_V2(chatItems_V2, isAdmin) { chatItem_V2 -> enterConversation_V2(chatItem_V2) }
    binding.searchView.adapter = adapter_V2

    if (!isAdmin) {
      Log.d("MessageFragment - onViewCreated", "Loading animals")

      loadCachedAnimals() { animalCacheResult ->
        if (!animalCacheResult) {
          user_loadPetProfilesFromDatabase()
        }
      }

      Log.d("MessageFragment - onViewCreated", "Loading cached conversations")
      let { user_loadCachedConversations() }
      Log.d("MessageFragment - onViewCreated", "Finished loading cached conversations")
      Log.d("MessageFragment - loadAnimals", "Generating empty conversationg for new matches")
      generateEmptyConversationForAllNewMatches()
      Log.d(
          "MessageFragment - loadAnimals",
          "Finished generating empty conversationg for new matches"
      )
    } else {
      admin_userCache = mutableListOf()
      messageCachePaths = mutableListOf()
      loadMessageCachePaths() { pathResult ->
        if (pathResult == false) {
          admin_loadAllFromDatabase()
        } else {
          admin_loadCachedConversations() { cachedConvResult ->
            if (cachedConvResult == false) {
              admin_loadAllFromDatabase()
            } else {
              loadCachedAnimals() { animalCacheResult ->
                if (animalCacheResult == false) {
                  messageCachePaths.forEach { loadPetProfileFromDatabase(it.substringBefore("_")) }
                }
                admin_loadCachedUsers() { userCacheResult ->
                  if (!userCacheResult)
                      messageCachePaths.forEach {
                        admin_loadUserInfoFromDatabase(it.substringAfter("_"))
                      }
                }
              }
            }
          }
        }
      }
    }
  }
  // Does not update newly created conversations :(
  private fun admin_listenNewConversations() {
    val lastestCreationDate =
        if (chatItems_V2.size > 0) {
          chatItems_V2.first().lastupdated
        } else {
          timestampPlaceholder
        }
    val query = db.collection("conversations").whereGreaterThan("created", lastestCreationDate)

    conversationListeners["headListener"] =
        query.addSnapshotListener(MetadataChanges.EXCLUDE) { snapshots, error ->
          if (error != null) {
            Log.e("MessageFragment", "Error listening for messages", error)
            conversationListeners["headlistener"]?.remove()
            admin_listenNewConversations()
            return@addSnapshotListener
          } else if (snapshots != null && !snapshots.isEmpty) {
            val lastSnapshot = snapshots.last()
            snapshots.distinct().forEach { snapshot ->
              val docId = snapshot.id
              val created = snapshot.getTimestamp("created")
              val existing = chatItems_V2.find { it.docId == docId }
              if (existing == null) {

                loadConversationFromDatabase(docId)
                Log.d("MessageFragment - admin_listenNewConversations", "new docId! ${docId}")
                val docUserId = snapshot.getString("userId")
                // var lastupdated = snapshot.documents[0].getTimestamp("lastupdated")
                var lastupdated = timestampPlaceholder
                val chipID = snapshot.getString("petId")

                if (docUserId != null && created != null && chipID != null) {
                  val messages: MutableList<Message> = mutableListOf()
                  var existingPet = animalCache.find { it.chipID == chipID }
                  var existingUser = admin_userCache.find { it.userId == docUserId }
                  if (existingPet == null) {
                    loadPetProfileFromDatabase(chipID) {
                      if (existingUser == null) {
                        Log.d(
                            "MessageFragment - admin_listenNewConversations",
                            "NEW pet ${chipID}  and NEW user ${docUserId}"
                        )
                        admin_loadUserInfoFromDatabase(docUserId) {
                          admin_createEmptyChatItem(chipID, docUserId)
                        }
                      } else {
                        Log.d(
                            "MessageFragment - admin_listenNewConversations",
                            "NEW pet ${chipID}  with EXISTING user ${docUserId}"
                        )
                        admin_createEmptyChatItem(chipID, docUserId)
                      }
                    }
                  } else {
                    if (existingUser == null) {
                      Log.d(
                          "MessageFragment - admin_listenNewConversations",
                          "EXISTING pet ${chipID}  but NEW user ${userId}"
                      )
                      admin_loadUserInfoFromDatabase(docUserId) {
                        admin_createEmptyChatItem(chipID, docUserId)
                      }
                    } else {
                      Log.d(
                          "MessageFragment - admin_listenNewConversations",
                          "EXISTING pet ${chipID}  AND EXISTING user ${userId}"
                      )
                      admin_createEmptyChatItem(chipID, userId)
                    }
                  }
                }
              } else {
                Log.d(
                    "MessageFragment - admin_listenNewConversations",
                    "docId exists ${docId} ${existing}"
                )
              }
            }
          }
        }
  }

  private fun admin_createEmptyChatItem(chipID: String, convUserId: String) {
    val user = admin_userCache.find { it.userId == userId }
    val animal = animalCache.find { it.chipID == chipID }
    if (animal == null || user == null) {
      return
    }
    val docId = chipID + "_" + userId
    val messages: MutableList<Message> = mutableListOf()

    Log.d(
        "MessageFragment - admin_createEmptyChatItem",
        "Adding chatItem for ${user.firstName} ${user.lastName} - ${animal.name}\n With docId ${docId}"
    )

    messages.add(
        Message(
            sender = animal.chipID,
            date = timestampPlaceholder,
            message = "Hi I'm ${animal.name}! 😊"
        )
    )
    val chatItem =
        ChatItem_V2(
            animal = animal,
            docId = docId,
            lastupdated = timestampPlaceholder,
            messages = messages,
            userName = "${user.firstName} ${user.lastName}",
            userID = convUserId
        )
    addChatItem(chatItem)
    listenUpdateMessage(chatItem)
    adapter_V2.notifyDataSetChanged()
  }

  private fun admin_loadAllFromDatabase(callback: ((Boolean) -> Unit)? = null) {
    admin_getAllConversationsFromDatabase() { result ->
      if (result) {
        messageCachePaths.forEach { docId ->
          loadPetProfileFromDatabase(docId.substringBefore("_")) {
            admin_loadUserInfoFromDatabase(docId.substringAfter("_")) { userRes ->
              if (userRes) {
                loadConversationFromDatabase(docId)
              }
            }
          }
          Log.d("MessageFragment - onCreateView", "loading conversations from database")
        }
      }
    }
    callback?.invoke(true)
  }

  fun loadConversationFromDatabase(docId: String, callback: ((Boolean) -> Unit)? = null) {

    Log.d("MessageFragment - loadConversationFromDatabase", "Trying to load animal")
    val animal =
        animalCache.find { it.chipID == docId.substringBefore("_") }
            ?: run {
              callback?.invoke(false)
              return
            }
    Log.d("MessageFragment - loadConversationFromDatabase", "Trying to load user")
    val user =
        admin_userCache.find { it.userId == docId.substringAfter("_") }
            ?: run {
              callback?.invoke(false)
              return
            }
    Log.d("MessageFragment - loadConversationFromDatabase", "Success for animal and user")
    val messages: MutableList<Message> = mutableListOf()
    messages.add(
        Message(
            sender = animal.chipID,
            date = timestampPlaceholder,
            message = "Hi I'm ${animal.name}! 😊"
        )
    )
    addChatItem(
        ChatItem_V2(
            animal = animal,
            docId = docId,
            lastupdated = timestampPlaceholder,
            messages = messages,
            userName = "${user.firstName} ${user.lastName}",
            userID = docId.substringAfter("_"),
            created = timestampPlaceholder,
        )
    )

    Log.d(
        "MessageFragment - loadConversationFromDatabase",
        "Adding chatItem for ${user.firstName} ${user.lastName} - ${animal.name}"
    )

    callback?.invoke(true)
  }

  fun loadMessageCachePaths(callback: ((Boolean) -> Unit)? = null) {
    val sharedMessages =
        requireActivity()
            .getSharedPreferences(
                getActivity()?.getString(R.string.message_cache) ?: "match_cache",
                Context.MODE_PRIVATE
            )
            ?: return

    var sharedMessagePaths =
        sharedMessages.getString(
            getActivity()?.getString(R.string.message_cache_entries_paths)
                ?: "message_cache_entries_path",
            ""
        )

    if (sharedMessagePaths.isNullOrEmpty()) {
      callback?.invoke(false)
      Log.d("MessageFragment - loadMessageCachePaths", "No saved messageCachePaths")
      return
    } else {
      val gson = Gson()
      messageCachePaths =
          gson.fromJson(sharedMessagePaths, Array<String>::class.java).asList().toMutableList()
      Log.d("MessageFragment - loadMessageCachePaths", "Loaded ${messageCachePaths.size} paths")
      callback?.invoke(true)
      return
    }
  }

  fun hideProgressBar() {
    Log.d("hideProgressBar", "Hiding")
    var progressBar = binding.progressBar
    progressBar.visibility = View.INVISIBLE
  }

  fun addChatItem(chatItem: ChatItem_V2) {
    Log.d("MessageFragment - addChatItem", "Adding ${chatItem.animal.name} - ${chatItem.userName}")
    val existingEntry = chatItems_V2.find { it.docId == chatItem.docId }

    if (existingEntry == null) {
      conversationListeners[chatItem.docId]?.remove()
      chatItems_V2.add(chatItem)
      adapter_V2.notifyDataSetChanged()
      listenUpdateMessage(chatItem)
    } else {
      existingEntry.messages.addAll(
          chatItem.messages.filter { !existingEntry.messages.contains(it) }
      )
      conversationListeners[existingEntry.docId]?.remove()
      adapter_V2.notifyDataSetChanged()
    }
    adapter_V2.sortItems()
    if (isAdmin) {
      if (chatItems_V2.size > 0 &&
              messageCachePaths.size > 0 &&
              chatItems_V2.size == messageCachePaths.size
      ) {
        hideProgressBar()
        // admin_listenNewConversations()
      }
    }
  }

  fun loadPetProfileFromDatabase(chipID: String, callback: ((Boolean) -> Unit)? = null) {
    db.collection("pets")
        .document(chipID)
        .get()
        .addOnSuccessListener { petDoc ->
          val animal =
              Animal(
                  chipID = petDoc.id,
                  name = petDoc.getString("name") ?: "Unknown",
                  birthdate = petDoc.getTimestamp("date") ?: Timestamp.now(),
                  imageUrl = petDoc.getString("imageUrl") ?: ""
              )

          animalCache.add(animal)
          callback?.invoke(true)
          Log.d(
              "MessageFragment - loadPetprofileFromDatabase",
              "Loading:  ${animal.name}: ${animal.chipID}"
          )
        }
        .addOnFailureListener { e ->
          Log.e("MessageFragment loadPetProfileFromDataBase", "Error:", e)
          callback?.invoke(false)
        }
    callback?.invoke(true)
  }

  fun admin_getAllConversationsFromDatabase(callback: ((Boolean) -> Unit)? = null) {
    if (!isAdmin) {
      Log.w(
          "MessageFragment - getAllConversationsIdsFromDatabase",
          "User has entered wrong function, returning"
      )
      return
    }
    Log.d("MessageFragment - admin_getAllConversationsFromDatabase", "Entering")
    db.collection("conversations").get().addOnCompleteListener { task ->
      if (task.isSuccessful) {
        Log.d("admin_getAllConversationsFromDatabase", "Found ${task.result.size()} conversations")
        messageCachePaths.addAll(task.result.map { it.id.toString() })
        callback?.invoke(true)
      } else {
        Log.d("admin_getAllConversationsFromDatabase", "Failure: ${task.result} ")
        callback?.invoke(false)
      }
    }
  }

  fun admin_loadUserInfoFromDatabase(convUserId: String, callback: ((Boolean) -> Unit)? = null) {
    // if (admin_userCache.find { it.userId == userId } != null) {
    //  callback?.invoke(true)
    //  return
    // }
    db.collection("users")
        .document(convUserId)
        .get()
        .addOnSuccessListener { user ->
          var _firstName = user.getString("firstname")
          var _lastName = user.getString("lastname")
          if (_firstName.isNullOrEmpty() || _lastName.isNullOrEmpty()) {
            Log.w(
                "MessageFragment - admin_loadUsersFromDataBase",
                "Got null name $_firstName $_lastName"
            )
            callback?.invoke(false)
          } else {
            var userItem = UserItem(firstName = _firstName, lastName = _lastName, userId = user.id)
            admin_userCache.add(userItem)
            admin_CacheUserItem(userItem)
            callback?.invoke(true)
            Log.d(
                "MessageFragment - admin_loadUserInfoFromDatabase",
                "Loading:  ${userItem.firstName} ${userItem.lastName}, input: ${userId}  db: ${user.id}"
            )
          }
        }
        .addOnFailureListener { e ->
          Log.e("MessageFragment admin_loadUserInfoFromDatabase", "Error:", e)
          callback?.invoke(false)
        }
    callback?.invoke(true)
  }

  // Generates empty conversation for match and adds match to animalCache
  fun user_loadPetProfilesFromDatabase() {
    getMatchesFromDatabase { pets ->
      Log.d("MessageFragment - loadPetprofilesFromDatabase", "Matches: ${pets}")
      pets.forEach {
        db.collection("pets")
            .document(it)
            .get()
            .addOnSuccessListener { petDoc ->
              val animal =
                  Animal(
                      chipID = petDoc.id,
                      name = petDoc.getString("name") ?: "Unknown",
                      birthdate = petDoc.getTimestamp("date") ?: Timestamp.now(),
                      imageUrl = petDoc.getString("imageUrl") ?: ""
                  )
              generateEmptyConversationForMatch(animal)
              animalCache.add(animal)
            }
            .addOnFailureListener { e -> Log.e("MessageFragment loadPetProfile", "Error:", e) }
      }
    }
  }

  fun getMatchesFromDatabase(callback: (List<String>) -> Unit) {
    val userDoc = db.collection("users").document(userId)
    val matchedProfiles: MutableList<String> = mutableListOf()
    userDoc.get().addOnCompleteListener { task ->
      if (task.isSuccessful()) {
        val matches = (task.result.get("matchedProfiles") as? List<*>)?.filterIsInstance<String>()
        matchedProfiles.addAll(matches as List<String>)
        Log.d("MessageFragment - getMatchesFromDatabase", "Found ${matchedProfiles.size}")
        callback(matchedProfiles)
      } else {
        Log.d("MessageFragment - getMatchesFromDatabase", "Not successful")
      }
    }
  }

  fun sendMessage(chatItem: ChatItem_V2) {
    Log.d(
        "MessageFragment",
        "Triggering Chat Update for ${chatItem.animal.name} - ${chatItem.userName}"
    )
    chatUpdateViewModel.triggerChatUpdate(chatItem)
  }

  override fun onResume() {
    Log.d("messageFragment", "onResume - Opening")
    super.onResume()
    isCurrentFragment = true
    (activity?.application as? App)?.isMessageFragmentVisible = true
  }

  override fun onPause() {
    Log.d("messageFragment", "onPause - Opening")
    super.onPause()
    isCurrentFragment = false
    (activity?.application as? App)?.isMessageFragmentVisible = false
  }

  override fun onDetach() {
    super.onDetach()
    Log.d("messageFragment", "onDetach - Opening")
  }

  override fun onSaveInstanceState(outState: Bundle) {
    if (!isAdded) {
      return
    }
    Log.d("messageFragment", "onSaveInstanceState - Opening")
    if (!isAdmin) {
      user_saveCache()
      return
    }
    admin_saveCache()
  }

  private fun admin_saveCache() {
    if (!isAdded) {
      return
    }
    val gson = Gson()
    saveAnimalCache()

    var messageCache =
        requireActivity()
            .getSharedPreferences(
                getActivity()?.getString(R.string.message_cache),
                Context.MODE_PRIVATE
            )

    if (messageCache == null || chatItems_V2.size < 0) {
      return
    }
    messageCachePaths.clear()
    with(messageCache.edit()) {
      chatItems_V2.forEach {
        putString(it.docId, gson.toJson(it))
        messageCachePaths.add(it.docId)
        Log.d("onSaveInstanceState", "Cached chatItem_V2: ${it.docId}")
      }
      putString(
          getActivity()?.getString(R.string.message_cache_entries_paths),
          gson.toJson(messageCachePaths)
      )
      Log.d(
          "onSaveInstanceState",
          "Cached message_cache_entries_paths: ${gson.toJson(messageCachePaths)}"
      )
      apply()
    }
  }

  private fun admin_CacheAllUsers() {
    val gson = Gson()
    var userCache =
        requireActivity()
            .getSharedPreferences(
                getActivity()?.getString(R.string.user_cache),
                Context.MODE_PRIVATE
            )
    if (userCache != null && admin_userCache.size > 0) {
      with(userCache.edit()) {
        admin_userCache.forEach {
          putString(it.userId, gson.toJson(it))
          Log.d("MessageFragment - admin_saveCache", "Saved user ${it.firstName} ${it.lastName}")
        }
        apply()
      }
    }
  }

  private fun admin_CacheUserItem(userItem: UserItem) {
    if (!isAdded) {
      return
    }
    var userCache =
        requireActivity()
            .getSharedPreferences(
                getActivity()?.getString(R.string.user_cache),
                Context.MODE_PRIVATE
            )
    if (userCache != null) {
      val gson = Gson()

      Log.d(
          "MessengerDialogFragment - admin_CacheUserItem",
          "Saved user ${userItem.firstName} ${userItem.lastName}"
      )
      with(userCache.edit()) {
        putString(userItem.userId, gson.toJson(userItem))
        commit()
      }
    }
  }

  private fun user_saveCache() {
    if (!isAdded) {
      return
    }
    val gson = Gson()

    var messageCache =
        requireActivity()
            .getSharedPreferences(
                getActivity()?.getString(R.string.message_cache),
                Context.MODE_PRIVATE
            )
    if (messageCache != null) {
      chatItems_V2.forEach {
        var conversationId = it.animal.chipID + "_" + userId

        with(messageCache.edit()) {
          putString(
              getActivity()?.getString(R.string.message_cache_entries) + "$conversationId",
              gson.toJson(it)
          )
          apply()
        }
        Log.d("onSaveInstanceState", "Cached chatItem_V2: ${conversationId}")
      }
    }
    saveAnimalCache()
  }

  private fun saveAnimalCache() {
    val gson = Gson()
    val sharedAnimal =
        requireActivity()
            .getSharedPreferences(
                getActivity()?.getString(R.string.match_cache) ?: "match_cache",
                Context.MODE_PRIVATE
            )
            ?: return

    with(sharedAnimal.edit()) {
      putString(getActivity()?.getString(R.string.match_cache_entries), gson.toJson(animalCache))
      apply()
    }
    Log.d("onSaveInstanceState", "Cached ${animalCache.size} animals")
  }

  private fun generateIfNotExisting(match: Animal) {
    var conversationId = match.chipID + "_" + userId
    db.collection("conversations").document(conversationId).get().addOnCompleteListener { task ->
      var existingEntry = chatItems_V2.find { it.animal == match }
      if (!task.isSuccessful() || existingEntry == null) {
        generateEmptyConversationForMatch(match)
        Log.d("generateIfNotExisting", "${match.name} does not exist, generate new")
      } else {
        Log.d("generateIfNotExisting", "${match.name} exists, not generating new")
      }
    }
  }

  private fun generateEmptyConversationForAllNewMatches() {
    val existingMatches: MutableList<String> = mutableListOf()
    chatItems_V2.forEach { existingMatches.add(it.animal.chipID) }
    val newMatches = animalCache.filter { !existingMatches.contains(it.chipID) }
    Log.d("generateEmptyConversationForAllNewMatches", "")
    Log.d("", "Excluded existing matches")
    existingMatches.forEach { Log.d("", "${it}") }
    Log.d("", "")
    if (newMatches.size > 0) {
      Log.d("generateEmptyConversationForAllNewMatches", "Generating new empty conversations for")

      newMatches.forEach {
        Log.d("new match", "${it.name} ${it.chipID}")
        generateIfNotExisting(it)
      }

      Log.d("", "")
    } else {
      Log.d("generateEmptyConversationForAllNewMatches", "No new matches")
      // Log.d("generateEmptyConversationForAllNewMatches", "${animalCache}")
    }
    hideProgressBar()
  }

  private fun generateEmptyConversationForMatch(match: Animal) {
    Log.d("MesssageFragment: generateEmptyConversationForMatch", "${match.name}: ${match.chipID}")
    var conversationId = match.chipID + "_" + userId
    var messages: MutableList<Message> = mutableListOf()
    messages.add(
        Message(sender = match.chipID, message = "Hi I'm ${match.name}! 😊", date = Timestamp.now())
    )
    addChatItem(
        ChatItem_V2(
            animal = match,
            docId = conversationId,
            lastupdated = timestampPlaceholder,
            messages = messages,
            userName = name,
            userID = userId,
            created = timestampPlaceholder
        )
    )
  }

  private fun listenUpdateAllMessages() {
    chatItems_V2.forEach { listenUpdateMessage(it) }
  }

  private fun listenUpdateMessage(chatItem: ChatItem_V2) {
    GlobalScope.launch {
      val lastupdated = chatItem.lastupdated
      var query: Query?

      if (chatItem.messages.size <= 1) {
        Log.d(
            "listenUpdateMessage",
            "No stored messages for ${chatItem.animal.name}, query for all"
        )
        query =
            db.collection("conversations")
                .document(chatItem.docId)
                .collection("messages")
                .orderBy("date", Query.Direction.ASCENDING)
      } else {
        Log.d(
            "listenUpdateMessage",
            "${chatItem.messages.size} stored for ${chatItem.animal.name}, query since ${lastupdated.toDate()}"
        )
        query =
            db.collection("conversations")
                .document(chatItem.docId)
                .collection("messages")
                .whereGreaterThan("date", lastupdated)
                .orderBy("date", Query.Direction.ASCENDING)
      }

      var listener: ListenerRegistration? = null
      listener =
          query.addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
            if (error != null) {
              Log.e("listenUpdateMessage", "Could not listen for updates for:")
              Log.e("listenUpdateMessage", "Pet name:             ${chatItem.animal.name}")
              Log.e("listenUpdateMessage", "ChipID:               ${chatItem.animal.chipID}")
              Log.e("listenUpdateMessage", "Current message size: ${chatItem.messages.size}")
              Log.e("listenUpdateMessage", error.toString())
              Log.e("listenUpdateMessage", "Query: " + query.toString())
              Log.e("", "")
              return@addSnapshotListener
            }
            Log.d(
                "listenUpdateMessage",
                "  ${snapshot?.size()} new messages for ${chatItem.animal.name}" +
                    "- ${chatItem.userName}"
            )

            if (snapshot != null && !snapshot.isEmpty) {
              // Log.d("listenUpdateMessage", "snapshot is not null and is not empty")
              val lastTimestamp = snapshot.documents.last().getTimestamp("date")
              snapshot.documents.forEach { conversation ->
                var sender = conversation.getString("sender")
                var message = conversation.getString("message")
                var date = conversation.getTimestamp("date")
                Log.d("", "Message: ${message}")

                if (sender.isNullOrEmpty() || message == null || date == null || chatItem == null) {
                  Log.w("listenUpdateMessage", "sender  isNullOrEmpty? ${sender.isNullOrEmpty()}")
                  Log.w("listenUpdateMessage", "message is null?       ${message == null}")
                  Log.w("listenUpdateMessage", "date    is null?       ${date == null}")
                  return@forEach
                } else {

                  chatItem.lastupdated = date

                  val regex = """(?i)\b(\[G\]|Admin)\b""".toRegex()

                  if (regex.containsMatchIn(sender)) {
                    chatItem.messages.add(Message(sender = sender, message = message, date = date))
                  } else if (sender.toString() == userId.toString()) {
                    sender = "[G]$name"
                    chatItem.messages.add(Message(sender = sender, message = message, date = date))
                  } // Escape for green message in dialog
                  else if (sender.toString() == chatItem.userID.toString()) {
                    sender = chatItem.userName
                    chatItem.messages.add(Message(sender = sender, message = message, date = date))
                  } else if (role != getActivity()?.getString(R.string.account_prefs_role_admin)) {
                    sender = chatItem.animal.name
                    chatItem.messages.add(Message(sender = sender, message = message, date = date))
                  } else if (role == getActivity()?.getString(R.string.account_prefs_role_admin)) {
                    admin_loadUserInfo(sender) { fullName ->
                      chatItem.messages.add(
                          Message(
                              sender = "Admin ${fullName.first} ${fullName.second}",
                              message = message,
                              date = date
                          )
                      )
                    }
                  } else {
                    // Log.w("MessageFragment listenUpdateMessage", "Could not load userinfo for")
                    // Log.w("MessageFragment listenUpdateMessage", "userId ${sender}")
                    // Log.w("MessageFragment listenUpdateMessage", "This userId = ${userId}")
                    // Log.w(
                    //    "MessageFragment listenUpdateMessage",
                    //    "role == getActivity()?.getString(R.string.account_prefs_role_admin) :
                    // ${role == getActivity()?.getString(R.string.account_prefs_role_admin)}"
                    // )
                  }

                  // Trigger notification with the ChatItem if not currently viewing
                  if (!isCurrentFragment) {
                    Log.d("listenUpdateMessage", "is currentFragment ${isCurrentFragment}")
                    // val chatItem_V2 = chatItems_V2.find { it.docId == chatItem.docId }
                    notificationViewModel_V2.triggerNotification_V2(chatItem)
                  }

                  if (chatItem.docId == currentMessageDialogDocId) {
                    Log.d("listenUpdateMessage", "Sending Update to Dialog")
                    sendMessage(chatItem)
                  }
                }
                if (lastTimestamp == null || date == lastTimestamp) {
                  chatItems_V2.sortWith(nullsLast(compareBy { it.lastupdated }))
                  adapter_V2.sortItems()
                  chatItem.lastupdated = chatItem.messages.last().date
                  if (conversationListeners[chatItem.docId] == null) {
                    listenUpdateMessage(chatItem)
                  }
                  conversationListeners[chatItem.docId]?.remove()
                  listener?.remove()
                  return@forEach
                }
              }
            }
          }
    }
  }

  fun admin_loadUserInfo(convUserId: String, callback: (Pair<String, String>) -> Unit) {
    val userID = convUserId.toString()
    val existingUser = admin_userCache.find { it.userId == userID }
    if (existingUser != null) {
      callback(Pair(existingUser.firstName, existingUser.lastName))
    } else {
      admin_getUserNameFromDatabase(userID) { callback(it) }
    }
  }

  fun admin_getUserNameFromDatabase(convUserId: String, callback: (Pair<String, String>) -> Unit) {
    db.collection("users")
        .document(convUserId)
        .get()
        .addOnSuccessListener { user ->
          val firstName = user.getString("firstname")
          val lastName = user.getString("lastname")
          if (firstName.isNullOrEmpty() || lastName.isNullOrEmpty()) {
            Log.d(
                "MessengerDialogFragment - admin_getUserNameFromDatabase",
                "Not adding: userId ${convUserId} first or lastname empty:  ${firstName} ${lastName}"
            )
            callback(Pair("", ""))
          } else {
            var userItem = UserItem(user.id, firstName, lastName)
            admin_CacheUserItem(userItem)
            callback(Pair(userItem.firstName, userItem.lastName))
            Log.d(
                "MessengerDialogFragment - admin_getUserNameFromDatabase",
                "Adding:  ${userItem.firstName} ${userItem.lastName} to cache"
            )
          }
        }
        .addOnFailureListener { e ->
          Log.e("MessageFragment admin_loadPetprofilesFromDatabase", "Error:", e)
        }
  }

  private fun loadAccountPrefs() {
    val accountPrefs =
        requireActivity()
            .getSharedPreferences(
                getActivity()?.getString(R.string.account_prefs),
                Context.MODE_PRIVATE
            )
            ?: return
    name =
        accountPrefs
            .getString(getActivity()?.getString(R.string.account_prefs_first_name), "")
            .toString() // Returns "null" if null

    name +=
        " " +
            accountPrefs
                .getString(getActivity()?.getString(R.string.account_prefs_last_name), "")
                .toString() // Returns "null" if null

    role =
        accountPrefs.getString(getActivity()?.getString(R.string.account_prefs_role), "").toString()
    Log.d("MessageFragment - loadAccountPrefs", "Role: ${role}")
    if (role.isNullOrEmpty()) {
      Log.d("loadAccountPrefs", "role is invaild, updating role")

      getRole { _role ->
        Log.d("MessageFragment - loadAccountPrefs", "Role from API: ${role}")
        if (_role.isNullOrEmpty()) {
          role = getActivity()?.getString(R.string.account_prefs_role_user) ?: "user"
        } else {
          role = _role
        }

        accountPrefs
            .edit()
            .putString(getActivity()?.getString(R.string.account_prefs_role), role)
            .apply()
      }
    }
    if (role == getActivity()?.getString(R.string.account_prefs_role_admin)) {
      isAdmin = true
    } else {
      isAdmin = false
    }
    Log.d("MessageFragment - loadAccountPrefs", "isAdmin: ${isAdmin}")
  }

  private fun user_loadCachedConversations() {
    Log.d(
        "MessageFragment -user_loadCachedConversations",
        "Searching in cache for ${animalCache.size} instances to load"
    )
    val gson = Gson()

    animalCache.forEach {
      var conversationId = it.chipID + "_" + userId
      var messageCache =
          requireActivity()
              .getSharedPreferences(
                  getActivity()?.getString(R.string.message_cache),
                  Context.MODE_PRIVATE
              )
      var messageCacheEntry =
          messageCache.getString(
              getActivity()?.getString(R.string.message_cache_entries) + "$conversationId",
              ""
          )

      if (messageCacheEntry.isNullOrEmpty()) {
        // Log.d("onCreate", "No saved Items for ${it.name} ${it.chipID}")
      } else {
        // Log.d("onCreate", "Cached chats found for ${it.name} ${it.chipID}")
        var loadedEntry = gson.fromJson(messageCacheEntry, ChatItem_V2::class.java)
        addChatItem(loadedEntry)
      }
    }
  }

  private fun admin_loadCachedConversations(callback: ((Boolean) -> Unit)? = null) {
    Log.d(
        "MessageFragment -user_loadCachedConversations",
        "Searching in cache for ${animalCache.size} instances to load"
    )
    val gson = Gson()
    var messageCache =
        requireActivity()
            .getSharedPreferences(
                getActivity()?.getString(R.string.message_cache),
                Context.MODE_PRIVATE
            )
    if (messageCache == null) {
      callback?.invoke(false)
      return
    }

    messageCachePaths.forEach { conversationId ->
      var messageCacheEntry = messageCache.getString(conversationId, "")

      if (messageCacheEntry.isNullOrEmpty()) {
        Log.d("admin_loadCachedConversations", "No saved Items for $conversationId")
      } else {
        // Log.d("onCreate", "Cached chats found for ${it.name} ${it.chipID}")
        var loadedEntry = gson.fromJson(messageCacheEntry, ChatItem_V2::class.java)
        addChatItem(loadedEntry)
      }
    }

    if (chatItems_V2.size > 0) {
      callback?.invoke(true)
      return
    }
  }

  private fun admin_loadCachedUsers(callback: ((Boolean) -> Unit)? = null) {
    var userCache =
        requireActivity()
            .getSharedPreferences(
                getActivity()?.getString(R.string.user_cache),
                Context.MODE_PRIVATE
            )

    if (userCache == null) {
      callback?.invoke(false)
      return
    }

    val gson = Gson()

    var userIdList: MutableList<String> = mutableListOf()
    userIdList.addAll(messageCachePaths.map { it.substringAfter("_") }.distinct())
    userIdList.forEach {
      var user = userCache.getString(it, "")
      if (!user.isNullOrEmpty()) {
        admin_userCache.add(gson.fromJson(user, UserItem::class.java))
        Log.d(
            "MessageFragment - admin_loadCachedUsers",
            "Loaded ${admin_userCache.last().firstName}"
        )
      }
    }

    if (admin_userCache.isNullOrEmpty()) {
      callback?.invoke(false)
      return
    }
    callback?.invoke(true)
  }

  private fun _admin_loadCachedUsers(callback: ((Boolean) -> Unit)? = null) {
    var userCache =
        requireActivity()
            .getSharedPreferences(
                getActivity()?.getString(R.string.user_cache),
                Context.MODE_PRIVATE
            )

    if (userCache == null) {
      callback?.invoke(false)
      return
    }

    var userCacheEntries =
        userCache.getString(getActivity()?.getString(R.string.user_cache_entries), "")

    if (userCacheEntries.isNullOrEmpty()) {
      callback?.invoke(false)
      return
    }

    val gson = Gson()
    admin_userCache.addAll(
        gson.fromJson(userCacheEntries, Array<UserItem>::class.java).asList().toMutableList()
    )
    if (admin_userCache.size > 0) {
      callback?.invoke(true)
      return
    }
    callback?.invoke(false)
  }

  private fun loadCachedAnimals(callback: ((Boolean) -> Unit)? = null) {
    animalCache.clear()
    val sharedAnimals =
        requireActivity()
            .getSharedPreferences(
                getActivity()?.getString(R.string.match_cache) ?: "match_cache",
                Context.MODE_PRIVATE
            )
    if (sharedAnimals == null) {
      callback?.invoke(false)
      return
    }

    var sharedAnimalData =
        sharedAnimals.getString(
            getActivity()?.getString(R.string.match_cache_entries) ?: "match_cache_entries",
            ""
        )

    if (sharedAnimalData.isNullOrEmpty()) {
      callback?.invoke(false)
      return
    } else {
      val gson = Gson()
      val animals: MutableList<Animal> =
          gson.fromJson(sharedAnimalData, Array<Animal>::class.java).asList().toMutableList()
      animals.distinct().forEach { animalCache.add(it) }

      if (animalCache.size > 0) {
        callback?.invoke(true)
        return
      }
      callback?.invoke(false)
    }
  }

  // In-App-Notifications with https://github.com/Tapadoo/Alerter
  // Function to create a SnapshotListener for a conversation
  //  private fun createConversationListener(
  //      conversationId: String,
  //      adapter: ChatAdapter,
  //  ) {
  //    val listener =
  //        db.collection("conversations")
  //            .document(conversationId)
  //            .collection("messages")
  //            .orderBy("date", Query.Direction.DESCENDING)
  //            .limit(1)
  //            .addSnapshotListener { snapshot, error ->
  //              if (error != null) {
  //                Log.e("MessageFragment", "Error listening for messages", error)
  //                return@addSnapshotListener
  //              }
  //
  //              if (!isCurrentFragment) {
  //                if (snapshot != null) {
  //                  Log.d("MessageFragment", "New message received")
  //                  val chatItem = chatItems.find { it.docId == conversationId }
  //                  notificationViewModel.triggerNotification(
  //                      chatItem!!
  //                  ) // Trigger notification with the ChatItem
  //                }
  //              }
  //
  //              if (snapshot != null && !snapshot.isEmpty) {
  //
  //                val latestMessage = snapshot.documents[0].getString("message") ?: "No messages
  // yet"
  //                val latestTimestamp = snapshot.documents[0].getTimestamp("date")
  //                val chatItemToUpdate = chatItems.find { it.docId == conversationId }
  //                chatItemToUpdate?.let {
  //                  it.latestMessage = latestMessage
  //                  it.lastupdated = latestTimestamp
  //                }
  //                chatItems.sortByDescending { it.lastupdated }
  //                adapter.notifyDataSetChanged()
  //              }
  //            }
  //    conversationListeners[conversationId] = listener
  //  }
  //
  //  // Helper to fetch matched profiles for regular users
  //  private fun getUserMatchedProfiles(userId: String): List<String> {
  //    val userDoc =
  //        db.collection("users")
  //            .document(userId)
  //            .get()
  //            .result // Blocking call, consider making it asynchronous
  //    return userDoc?.get("matchedProfiles") as? List<String> ?: emptyList()
  //  }
  //
  //  // Helper function to fetch pets and their conversations
  //  private fun fetchPetsForConversations(
  //      conversationSnapshots: QuerySnapshot,
  //      animalConversations: MutableList<AnimalConversation>,
  //      callback: (List<AnimalConversation>) -> Unit
  //  ) {
  //    val petsCollection = FirebaseFirestore.getInstance().collection("pets")
  //
  //    var completedQueries = 0 // Counter to track completed Firestore queries
  //
  //    conversationSnapshots.forEach { conversationDoc ->
  //      val chipID = conversationDoc.getString("chipID") ?: return@forEach
  //      val userId = conversationDoc.getString("userId") ?: return@forEach
  //
  //      // Fetch pet information
  //      petsCollection.document(chipID).get().addOnSuccessListener { petDoc ->
  //        // Fetch user information (asynchronously)
  //        db.collection("users")
  //            .document(userId)
  //            .get()
  //            .addOnSuccessListener { userDoc ->
  //              val userName = userDoc.getString("firstname") + " " +
  // userDoc.getString("lastname")
  //
  //              animalConversations.add(
  //                  AnimalConversation(
  //                      chipID = chipID,
  //                      name = petDoc.getString("name") ?: "",
  //                      userName = userName,
  //                      birthdate = petDoc.getTimestamp("date") ?: Timestamp.now(),
  //                      imageUrl = petDoc.getString("imageUrl") ?: "",
  //                      docId = conversationDoc.id,
  //                      lastUpdated = conversationDoc.getTimestamp("lastupdated") ?:
  // Timestamp.now()
  //                  )
  //              )
  //
  //              completedQueries++
  //
  //              // Only call the callback when ALL queries are complete
  //              if (completedQueries == conversationSnapshots.size()) {
  //                callback(animalConversations.sortedByDescending { it.lastUpdated })
  //              }
  //            }
  //            .addOnFailureListener { e -> Log.e("MessageFragment", "Error fetching user data", e)
  // }
  //      }
  //    }
  //  }

  private fun enterConversation_V2(chatItem: ChatItem_V2) {
    // Find the pet document ID associated with this chatItem
    // Create a bundle with chipID and other information
    Log.d("messageFragment", "enterConversation_V2")
    currentMessageDialogDocId = chatItem.docId
    val gson = Gson()
    val bundle =
        Bundle().apply {
          putString(
              getActivity()?.getString(R.string.message_enter_conversation_argument),
              gson.toJson(chatItem)
          )
        }
    val dialog = MessengerDialogFragment()
    dialog.messageDialogResultListener = this

    dialog.arguments = bundle
    dialog.show(childFragmentManager, "messenger_dialog")
  }
  // Recieve event on dialog close
  override fun onMessageDialogResult(result: String?) {
    Log.d("onMessageDialogResult", "Got result: ${result}")
    currentMessageDialogDocId = null
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
    isCurrentFragment = false
  }
}
