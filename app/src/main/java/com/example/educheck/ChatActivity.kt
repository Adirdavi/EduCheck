package com.example.educheck

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ChatActivity"

/**
 * Activity for chat between students and teachers
 * Revised to store messages in an array within a single chat document
 */
class ChatActivity : AppCompatActivity() {

    // UI components
    private lateinit var chatTitle: TextView
    private lateinit var scrollView: NestedScrollView
    private lateinit var messagesContainer: LinearLayout
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton

    private lateinit var firestore: FirebaseFirestore
    private var chatListener: ListenerRegistration? = null

    // User details
    private var userId: String = ""
    private var userName: String = ""
    private var otherUserId: String = ""
    private var otherUserName: String = ""
    private var isTeacher: Boolean = false

    // Chat ID to identify this conversation
    private lateinit var chatId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // Initialize Firestore
        firestore = FirebaseFirestore.getInstance()

        // Get user details from intent
        userId = intent.getStringExtra("USER_ID") ?: ""
        userName = intent.getStringExtra("USER_NAME") ?: ""
        otherUserId = intent.getStringExtra("OTHER_USER_ID") ?: ""
        otherUserName = intent.getStringExtra("OTHER_USER_NAME") ?: ""
        isTeacher = intent.getBooleanExtra("IS_TEACHER", false)

        Log.d(TAG, "Chat started: User=$userName ($userId), Other=$otherUserName ($otherUserId), IsTeacher=$isTeacher")

        if (userId.isEmpty() || otherUserId.isEmpty()) {
            Toast.makeText(this, "פרטי משתמש חסרים", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Create a chat ID (ensure the same ID is used regardless of who initiates)
        chatId = if (userId < otherUserId) {
            "$userId-$otherUserId"
        } else {
            "$otherUserId-$userId"
        }

        // Initialize UI
        initUI()

        // Initialize chat document and then load messages
        initChatDocument()
    }

    override fun onDestroy() {
        super.onDestroy()
        chatListener?.remove()
    }

    private fun initUI() {
        // Initialize views using the IDs from your layout
        chatTitle = findViewById(R.id.chatTitle)
        scrollView = findViewById(R.id.scrollView)
        messagesContainer = findViewById(R.id.messagesContainer)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)

        // Set chat title to other user's name
        chatTitle.text = otherUserName

        // Set up send button
        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
                messageInput.text.clear()
            }
        }
    }

    private fun initChatDocument() {
        val chatRef = firestore.collection("chats").document(chatId)

        chatRef.get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    // Create new chat document if it doesn't exist
                    val chatData = hashMapOf(
                        "participants" to listOf(userId, otherUserId),
                        "participantNames" to mapOf(userId to userName, otherUserId to otherUserName),
                        "lastUpdated" to System.currentTimeMillis(),
                        "messages" to ArrayList<HashMap<String, Any>>()
                    )

                    chatRef.set(chatData)
                        .addOnSuccessListener {
                            Log.d(TAG, "Created new chat document")
                            loadMessages()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error creating chat document", e)
                            Toast.makeText(this, "שגיאה ביצירת צ'אט", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    // Document already exists
                    Log.d(TAG, "Chat document already exists")
                    loadMessages()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking chat document", e)
                Toast.makeText(this, "שגיאה בטעינת צ'אט", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadMessages() {
        // Clear existing messages
        messagesContainer.removeAllViews()

        // Remove previous listener if exists
        chatListener?.remove()

        // Create a reference to the chat document
        val chatRef = firestore.collection("chats").document(chatId)

        // Listen for changes to the chat document
        chatListener = chatRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to chat updates", error)
                return@addSnapshotListener
            }

            // Clear existing messages from view
            messagesContainer.removeAllViews()

            if (snapshot != null && snapshot.exists()) {
                try {
                    // Get the messages array from the document
                    @Suppress("UNCHECKED_CAST")
                    val messages = snapshot.get("messages") as? ArrayList<HashMap<String, Any>> ?: ArrayList()

                    Log.d(TAG, "Found ${messages.size} messages in chat")

                    // Process each message
                    for (messageData in messages) {
                        try {
                            displayMessage(messageData)

                            // Mark as read if I am the receiver
                            val senderId = messageData["senderId"] as? String ?: ""
                            val receiverId = messageData["receiverId"] as? String ?: ""
                            val isRead = messageData["isRead"] as? Boolean ?: false

                            if (receiverId == userId && senderId == otherUserId && !isRead) {
                                markMessagesAsRead()
                                break  // Only need to update once
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing message", e)
                        }
                    }

                    // Scroll to bottom
                    scrollView.post {
                        scrollView.fullScroll(View.FOCUS_DOWN)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing snapshot", e)
                }
            }
        }
    }

    private fun displayMessage(messageData: HashMap<String, Any>) {
        try {
            val senderId = messageData["senderId"] as? String ?: ""
            val senderName = messageData["senderName"] as? String ?: ""
            val text = messageData["text"] as? String ?: ""
            val timestamp = messageData["timestamp"] as? Long ?: System.currentTimeMillis()


            // Determine which layout to use based on who sent the message and user role
            val isFromMe = senderId == userId

            val layoutId = if (isFromMe) {
                // Message I sent
                if (isTeacher) {
                    R.layout.item_message_teacher  // I'm a teacher sending message
                } else {
                    R.layout.item_message_student  // I'm a student sending message
                }
            } else {
                // Message I received
                if (isTeacher) {
                    R.layout.item_message_student  // I'm a teacher receiving from student
                } else {
                    R.layout.item_message_teacher  // I'm a student receiving from teacher
                }
            }

            Log.d(TAG, "Display message: $text, sender=$senderId, layoutId=$layoutId, isFromMe=$isFromMe")

            // Inflate message view
            val messageView = LayoutInflater.from(this).inflate(layoutId, messagesContainer, false)

            // Set message content
            val messageText = messageView.findViewById<TextView>(R.id.messageText)
            val messageTime = messageView.findViewById<TextView>(R.id.messageTime)
            val messageSender = messageView.findViewById<TextView>(R.id.messageSender)

            messageText.text = text
            messageSender.text = senderName

            // Format message time
            val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            messageTime.text = dateFormat.format(timestamp)

            // Add to container
            messagesContainer.addView(messageView)
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying message", e)
        }
    }

    private fun markMessagesAsRead() {
        val chatRef = firestore.collection("chats").document(chatId)

        chatRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val messages = document.get("messages") as? ArrayList<HashMap<String, Any>> ?: ArrayList()

                    // Update messages where I am the receiver
                    val updatedMessages = messages.map { message ->
                        val senderId = message["senderId"] as? String ?: ""
                        val receiverId = message["receiverId"] as? String ?: ""
                        val isRead = message["isRead"] as? Boolean ?: false

                        if (receiverId == userId && senderId == otherUserId && !isRead) {
                            val updatedMessage = HashMap(message)
                            updatedMessage["isRead"] = true
                            updatedMessage
                        } else {
                            message
                        }
                    }

                    // Update Firestore
                    chatRef.update("messages", updatedMessages)
                        .addOnSuccessListener {
                            Log.d(TAG, "Messages marked as read")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error marking messages as read", e)
                        }

                } catch (e: Exception) {
                    Log.e(TAG, "Error processing messages for read status", e)
                }
            }
        }
    }

    private fun sendMessage(messageText: String) {
        try {
            // Create timestamp
            val timestamp = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val dateTime = dateFormat.format(Date(timestamp))

            // Create message object
            val message = hashMapOf(
                "senderId" to userId,
                "senderName" to userName,
                "receiverId" to otherUserId,
                "receiverName" to otherUserName,
                "chatId" to chatId,
                "isTeacher" to isTeacher,
                "text" to messageText,
                "timestamp" to timestamp,
                "dateTime" to dateTime,
                "isRead" to false
            )

            Log.d(TAG, "Sending message: $messageText, isTeacher=$isTeacher")

            // Add message to the messages array in Firestore
            val chatRef = firestore.collection("chats").document(chatId)

            // Update the chat document
            chatRef.update(
                mapOf(
                    "messages" to FieldValue.arrayUnion(message),
                    "lastUpdated" to timestamp
                )
            ).addOnSuccessListener {
                Log.d(TAG, "Message sent successfully")
            }.addOnFailureListener { e ->
                Log.e(TAG, "Error sending message", e)

                // If update fails, try to create the document if it doesn't exist
                val chatData = hashMapOf(
                    "participants" to listOf(userId, otherUserId),
                    "participantNames" to mapOf(userId to userName, otherUserId to otherUserName),
                    "lastUpdated" to timestamp,
                    "messages" to listOf(message)
                )

                chatRef.set(chatData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Created new chat with first message")
                    }
                    .addOnFailureListener { setError ->
                        Log.e(TAG, "Error creating chat document", setError)
                        Toast.makeText(this, "שגיאה בשליחת הודעה", Toast.LENGTH_SHORT).show()
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendMessage", e)
            Toast.makeText(this, "שגיאה בשליחת הודעה", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}