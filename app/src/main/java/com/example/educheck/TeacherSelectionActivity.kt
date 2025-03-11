package com.example.educheck

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

/**
 * Activity for student to select a teacher to chat with
 */
class TeacherSelectionActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var teachersAdapter: TeachersAdapter

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    private val teachersList = ArrayList<Teacher>()
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
        teachersAdapter = TeachersAdapter(teachersList) { teacher ->
            openChatWithTeacher(teacher)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = teachersAdapter

        // Get student name
        getStudentName()

        // Load teachers list
        loadTeachers()
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
                        val subject = teacherSnapshot.child("subject").getValue(String::class.java) ?: ""

                        val teacherName = "$firstName $lastName"
                        teachersList.add(Teacher(teacherId, teacherName, subject))
                    }
                }

                // Update UI
                updateUI()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error loading teachers: ${error.message}")
                Toast.makeText(this@TeacherSelectionActivity,
                    "Error loading teachers list", Toast.LENGTH_SHORT).show()
            }
        })
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
        private val onItemClick: (Teacher) -> Unit
    ) : RecyclerView.Adapter<TeachersAdapter.TeacherViewHolder>() {

        inner class TeacherViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cardView: CardView = itemView.findViewById(R.id.teacherCard)
            val nameTextView: TextView = itemView.findViewById(R.id.teacherName)
            val subjectTextView: TextView = itemView.findViewById(R.id.teacherSubject)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeacherViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_teacher, parent, false)
            return TeacherViewHolder(view)
        }

        override fun onBindViewHolder(holder: TeacherViewHolder, position: Int) {
            val teacher = teachers[position]

            holder.nameTextView.text = teacher.name

            // Show subject if available
            if (teacher.subject.isNotEmpty()) {
                holder.subjectTextView.text = teacher.subject
                holder.subjectTextView.visibility = View.VISIBLE
            } else {
                holder.subjectTextView.visibility = View.GONE
            }

            // Set click listener
            holder.cardView.setOnClickListener {
                onItemClick(teacher)
            }
        }

        override fun getItemCount() = teachers.size
    }
}