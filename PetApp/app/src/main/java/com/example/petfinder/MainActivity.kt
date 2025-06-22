package com.example.petfinder

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.example.petfinder.database.messageUpdate
import com.example.petfinder.databinding.ActivityMainBinding
import com.example.petfinder.ui.login.LoginFragment
import com.example.petfinder.ui.messages.ChatItem
import com.example.petfinder.ui.messages.ChatItem_V2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding
  private lateinit var auth: FirebaseAuth // Initialize Firebase Authentication
  private val notificationViewModel: NotificationViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (!isOnline()) {
      showNoConnectionDialog()
    }

    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    val navView: BottomNavigationView = binding.navView

    val navController = findNavController(R.id.nav_host_fragment_activity_main)

    // Firebase Authentication
    auth = Firebase.auth

    // Check if the user is not logged in and navigate to LoginFragment
    if (checkLoggedInState()) {
      val loginFragment = LoginFragment()
      loginFragment.show(supportFragmentManager, "login_dialog")
    }

    // Passing each menu ID as a set of Ids because each
    // menu should be considered as top level destinations.
    val appBarConfiguration: AppBarConfiguration =
        AppBarConfiguration(
            setOf(
                R.id.navigation_home,
                R.id.navigation_swipe,
                R.id.navigation_search,
                R.id.navigation_message,
                R.id.navigation_account
            )
        )

    navView.setupWithNavController(navController)
  }

  private fun checkLoggedInState(): Boolean {
    val currentUser = auth.currentUser
    return if (currentUser == null) {
      true // logged in
    } else {
      false // not logged in
    }
  }

  // If the user is offline it will show a dialog option.
  // The user can choose to retry the internet or exit the app
  private fun showNoConnectionDialog() {
    val dialogBuilder = AlertDialog.Builder(this)
    dialogBuilder
        .setMessage(
            "You are not connected to the internet. Please check your connection and try again."
        )
        .setCancelable(false)
        .setPositiveButton("Retry") { _, _ ->
          // Retry to check internet connection
          if (!isOnline()) {
            showNoConnectionDialog()
          }
        }
        .setNegativeButton("Close app") { _, _ ->
          // Close the app
          finish()
        }

    val alert = dialogBuilder.create()
    alert.setTitle("No Internet Connection")
    alert.show()
  }

  // Checks if the user is online before allowing access to the app
  private fun isOnline(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork
    val capabilities = connectivityManager.getNetworkCapabilities(network)
    return capabilities?.let {
      it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
          it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
          it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
        ?: false
  }
}

class NotificationViewModel : ViewModel() {
  // Change _showNotification to MutableLiveData
  private val _showNotification = MutableLiveData<Pair<String, String>>()
  val showNotification: LiveData<Pair<String, String>> = _showNotification

  // Make navigateToChat MutableLiveData
  private val _navigateToChat = MutableLiveData<ChatItem>()
  val navigateToChat: LiveData<ChatItem> = _navigateToChat

  fun triggerNotification(chatItem: ChatItem) {
    Log.d("NotificationViewModel", "Triggering notification for ${chatItem.petName}")
    _navigateToChat.value = chatItem // Set the ChatItem for navigation
    _showNotification.value = Pair(chatItem.petName, chatItem.latestMessage)
  }
}

object NotificationViewModelHolder : ViewModelStoreOwner {

  override val viewModelStore: ViewModelStore
    get() = ViewModelStore()

  private val viewModel: NotificationViewModel by lazy {
    ViewModelProvider(this).get(NotificationViewModel::class.java)
  }

  fun retreiveViewModel(): NotificationViewModel {
    return viewModel
  }
}

class NotificationViewModel_V2 : ViewModel() {
  // Change _showNotification to MutableLiveData
  private val _showNotification = MutableLiveData<Pair<String, String>>()
  val showNotification: LiveData<Pair<String, String>> = _showNotification

  // Make navigateToChat MutableLiveData
  private val _navigateToChat = MutableLiveData<ChatItem_V2>()
  val navigateToChat: LiveData<ChatItem_V2> = _navigateToChat

  fun triggerNotification_V2(chatItem_V2: ChatItem_V2) {
    Log.d("NotificationViewModel", "Triggering notification for ${chatItem_V2.animal.name}")
    _navigateToChat.value = chatItem_V2 // Set the ChatItem for navigation
    _showNotification.value = Pair(chatItem_V2.animal.name, chatItem_V2.messages.last().message)
  }
}

object NotificationViewModelHolder_V2 : ViewModelStoreOwner {

  override val viewModelStore: ViewModelStore
    get() = ViewModelStore()

  private val viewModel_V2: NotificationViewModel_V2 by lazy {
    ViewModelProvider(this).get(NotificationViewModel_V2::class.java)
  }

  fun retreiveViewModel_V2(): NotificationViewModel_V2 {
    return viewModel_V2
  }
}

class ChatUpdateViewModel : ViewModel() {
  interface ChatUpdateEventListener {
    fun onNotificationReceived(update: messageUpdate)
  }

  private val _chatUpdate = MutableLiveData<messageUpdate>()
  val chatUpdate: LiveData<messageUpdate> = _chatUpdate

  fun triggerChatUpdate(chatItem: ChatItem_V2) {
    Log.d("NotificationViewModel", "Triggering chatUpdate for ${chatItem.animal.name}")
    // _chatUpdate.value = messageUpdate(chatItem.docId, chatItem.messages.last())
    _chatUpdate.postValue(messageUpdate(chatItem.docId, chatItem.messages.last()))
  }
}

object ChatUpdateViewModelHolder : ViewModelStoreOwner {

  override val viewModelStore: ViewModelStore
    get() = ViewModelStore()

  private val viewModel: ChatUpdateViewModel by lazy {
    ViewModelProvider(this).get(ChatUpdateViewModel::class.java)
  }

  fun retreiveViewModel(): ChatUpdateViewModel {
    return viewModel
  }
}
