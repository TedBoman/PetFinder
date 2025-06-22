# PetApp

PetApp is an Android application designed to help users find and connect with pets available for adoption. It offers a "Tinder-like" swiping interface for Browse pet profiles, along with features for matching, messaging, and managing user and pet profiles. The application also includes a moderator role for managing the platform's content.

## Features

* **User Authentication:** Secure user registration and login functionality using Firebase Authentication.
* **Pet Swiping:** A familiar swipe-right to like and swipe-left to dislike interface for Browse pet profiles.
* **Matching:** When a user and a pet they've liked also "like" them back, a match is created, enabling communication.
* **Real-time Messaging:** Users can chat with the owners or shelters of their matched pets in real-time.
* **Search and Filtering:** Advanced search capabilities with filters for species, gender, and age to help users find the perfect pet.
* **Pet and User Profiles:** Detailed profiles for both pets and users, including images, descriptions, and other relevant information.
* **Moderation Tools:** A dedicated moderator role with privileges to manage and review content on the app.
* **Push Notifications:** Users receive notifications for new matches and messages.

## Tech Stack

* **Kotlin:** The primary programming language for the application.
* **Android SDK:** The native Android development kit.
* **Firebase:**
    * **Authentication:** For user management and authentication.
    * **Cloud Firestore:** As the primary database for storing user, pet, and message data.
    * **Cloud Storage:** For storing user and pet profile images.
* **Android Jetpack:**
    * **Navigation Component:** For handling in-app navigation.
    * **View Binding:** To easily write code that interacts with views.
    * **RecyclerView:** For displaying lists of data, such as search results and messages.
* **Glide:** For image loading and caching.
* **Gson:** For serializing and deserializing Java objects to and from JSON.

## Setup and Installation

1.  **Clone the repository:**
    ```bash
    git clone [https://github.com/your-repository/PetApp.git](https://github.com/your-repository/PetApp.git)
    ```
2.  **Open in Android Studio:** Open the cloned repository in Android Studio.
3.  **Firebase Configuration:**
    * This project uses Firebase for its backend services. You will need to create a new Firebase project and add your own `google-services.json` file to the `app` directory.
    * For more information on how to set up Firebase for an Android project, please refer to the [official Firebase documentation](https://firebase.google.com/docs/android/setup).
4.  **Build and Run:** Build and run the application on an Android emulator or a physical device.

## Firebase Configuration

To get the application to run with your own Firebase backend, you need to add your `google-services.json` file to the `app` directory. The file contains the necessary configuration details for your Firebase project. You can download this file from the Firebase console after creating your project.

## Contributors
[William Bodlund](https://github.com/Bowi1337)
[Linus Nyzelius](https://github.com/Nyzelius)
[TedBoman](https://github.com/TedBoman)

## License

This project is licensed under the **Happy paws - Custom Proprietary License**.

1.  You are granted a non-exclusive, non-transferable, revocable license to view the source code for personal or educational purposes only.
2.  You may not modify, distribute, sublicense, or sell the source code or any part of it.
3.  The source code is provided "as is" without any warranty of any kind, express or implied.
4.  The author of this code is not liable for any damages or losses arising from the use or inability to use this code.

For more details, see the `LICENSE.md` file.
