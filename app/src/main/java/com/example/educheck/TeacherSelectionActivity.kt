package com.example.educheck

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.educheck.utilities.Teacher
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Activity for student to select a teacher to chat with
 */
class TeacherSelectionActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var teachersAdapter: TeachersAdapter

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var firestore: FirebaseFirestore

    private val teachersList = ArrayList<Teacher>()
    private val unreadMessagesMap = HashMap<String, Int>()
    private var studentId: String = ""
    private var studentName: String = ""

    companion object {
        private const val TAG = "TeacherSelection"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_selection)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Get student details
        studentId = auth.currentUser?.uid ?: ""
        if (studentId.isEmpty()) {
            Toast.makeText(this, "Login required", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize UI
        recyclerView = findViewById(R.id.teachersRecyclerView)
        emptyView = findViewById(R.id.emptyView)

        // Set up adapter
        teachersAdapter = TeachersAdapter(teachersList, unreadMessagesMap) { teacher ->
            openChatWithTeacher(teacher)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = teachersAdapter

        val backButton = findViewById<ImageButton>(R.id.backButton)
        backButton.setOnClickListener {
            // פעולת חזרה
            onBackPressed()
        }

        // Get student name
        getStudentName()

        // Load teachers list
        loadTeachers()
    }

    override fun onResume() {
        super.onResume()
        // Check for unread messages when returning to this screen
        checkForUnreadMessages()
    }

    /**
     * Get student name from Firebase
     */
    private fun getStudentName() {
        val userRef = database.getReference("users").child(studentId)
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val firstName = snapshot.child("firstName").getValue(String::class.java) ?: ""
                    val lastName = snapshot.child("lastName").getValue(String::class.java) ?: ""
                    studentName = "$firstName $lastName"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error getting student name: ${error.message}")
            }
        })
    }

    /**
     * Load list of teachers from Firebase
     */
    private fun loadTeachers() {
        val usersRef = database.getReference("users")

        usersRef.orderByChild("role").equalTo("teacher").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                teachersList.clear()

                if (snapshot.exists()) {
                    for (teacherSnapshot in snapshot.children) {
                        val teacherId = teacherSnapshot.key ?: continue
                        val firstName = teacherSnapshot.child("firstName").getValue(String::class.java) ?: ""
                        val lastName = teacherSnapshot.child("lastName").getValue(String::class.java) ?: ""

                        val teacherName = "$firstName $lastName"
                        teachersList.add(Teacher(teacherId, teacherName, ""))
                    }
                }

                // Check for unread messages
                checkForUnreadMessages()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error loading teachers: ${error.message}")
                Toast.makeText(this@TeacherSelectionActivity,
                    "Error loading teachers list", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * Check for unread messages from each teacher
     */
    private fun checkForUnreadMessages() {
        try {
            unreadMessagesMap.clear()

            firestore.collection("chats")
                .whereArrayContains("participants", studentId)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.isEmpty) {
                        for (document in snapshot.documents) {
                            try {
                                @Suppress("UNCHECKED_CAST")
                                val messages = document.get("messages") as? ArrayList<HashMap<String, Any>> ?: ArrayList()

                                // Group unread messages by sender
                                for (message in messages) {
                                    val receiverId = message["receiverId"] as? String ?: ""
                                    val senderId = message["senderId"] as? String ?: ""
                                    val isRead = message["isRead"] as? Boolean ?: true

                                    // If the message was sent to the student and is unread
                                    if (receiverId == studentId && !isRead) {
                                        // Increment unread count for this teacher
                                        val currentCount = unreadMessagesMap[senderId] ?: 0
                                        unreadMessagesMap[senderId] = currentCount + 1
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing chat document: ${e.message}")
                            }
                        }
                    }

                    // Update UI
                    updateUI()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error checking for unread messages: ${e.message}")
                    updateUI()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkForUnreadMessages: ${e.message}")
            updateUI()
        }
    }

    /**
     * Update UI based on loaded data
     */
    private fun updateUI() {
        if (teachersList.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE

            // Update list
            teachersAdapter.notifyDataSetChanged()
        }
    }

    /**
     * Open chat with selected teacher
     */
    private fun openChatWithTeacher(teacher: Teacher) {
        try {
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("USER_ID", studentId)
                putExtra("USER_NAME", studentName)
                putExtra("OTHER_USER_ID", teacher.id)
                putExtra("OTHER_USER_NAME", teacher.name)
                putExtra("IS_TEACHER", false)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening chat: ${e.message}")
            Toast.makeText(this, "Error opening chat", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Adapter for the teachers RecyclerView
     */
    inner class TeachersAdapter(
        private val teachers: List<Teacher>,
        private val unreadMessagesMap: HashMap<String, Int>,
        private val onItemClick: (Teacher) -> Unit
    ) : RecyclerView.Adapter<TeachersAdapter.TeacherViewHolder>() {

        inner class TeacherViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cardView: CardView = itemView.findViewById(R.id.teacherCard)
            val nameTextView: TextView = itemView.findViewById(R.id.teacherName)
            val unreadBadge: TextView = itemView.findViewById(R.id.unreadBadge)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeacherViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_teacher, parent, false)
            return TeacherViewHolder(view)
        }

        override fun onBindViewHolder(holder: TeacherViewHolder, position: Int) {
            val teacher = teachers[position]

            holder.nameTextView.text = teacher.name

            // Show unread message badge if there are unread messages
            val unreadCount = unreadMessagesMap[teacher.id] ?: 0
            if (unreadCount > 0) {
                holder.unreadBadge.visibility = View.VISIBLE
                holder.unreadBadge.text = if (unreadCount > 9) "9+" else unreadCount.toString()
            } else {
                holder.unreadBadge.visibility = View.GONE
            }

            // Set click listener
            holder.cardView.setOnClickListener {
                onItemClick(teacher)
            }
        }

        override fun getItemCount() = teachers.size
    }
}