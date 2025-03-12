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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Activity for teacher to select a student to chat with
 */
class StudentSelectionActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var studentsAdapter: StudentsAdapter

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    private val studentsList = ArrayList<Student>()
    private var teacherId: String = ""
    private var teacherName: String = ""

    companion object {
        private const val TAG = "StudentSelection"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_selection)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Get teacher details
        teacherId = auth.currentUser?.uid ?: ""
        if (teacherId.isEmpty()) {
            Toast.makeText(this, "Login required", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize UI
        recyclerView = findViewById(R.id.studentsRecyclerView)
        emptyView = findViewById(R.id.emptyView)

        // Set up adapter
        studentsAdapter = StudentsAdapter(studentsList) { student ->
            openChatWithStudent(student)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = studentsAdapter


        val backButton = findViewById<ImageButton>(R.id.backButton)
        backButton.setOnClickListener {
            onBackPressed()
        }

        // Get teacher name
        getTeacherName()

        // Load students list
        loadStudents()
    }

    /**
     * Get teacher name from Firebase
     */
    private fun getTeacherName() {
        val userRef = database.getReference("users").child(teacherId)
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val firstName = snapshot.child("firstName").getValue(String::class.java) ?: ""
                    val lastName = snapshot.child("lastName").getValue(String::class.java) ?: ""
                    teacherName = "$firstName $lastName"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error getting teacher name: ${error.message}")
            }
        })
    }

    /**
     * Load list of students from Firebase
     */
    private fun loadStudents() {
        val usersRef = database.getReference("users")

        usersRef.orderByChild("role").equalTo("student").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                studentsList.clear()

                if (snapshot.exists()) {
                    for (studentSnapshot in snapshot.children) {
                        val studentId = studentSnapshot.key ?: continue
                        val firstName = studentSnapshot.child("firstName").getValue(String::class.java) ?: ""
                        val lastName = studentSnapshot.child("lastName").getValue(String::class.java) ?: ""

                        val studentName = "$firstName $lastName"
                        studentsList.add(Student(studentId, studentName))
                    }
                }

                // Update UI
                updateUI()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error loading students: ${error.message}")
                Toast.makeText(this@StudentSelectionActivity,
                    "Error loading students list", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * Update UI based on loaded data
     */
    private fun updateUI() {
        if (studentsList.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE

            // Update list
            studentsAdapter.notifyDataSetChanged()
        }
    }

    /**
     * Open chat with selected student
     */
    private fun openChatWithStudent(student: Student) {
        try {
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("USER_ID", teacherId)
                putExtra("USER_NAME", teacherName)
                putExtra("OTHER_USER_ID", student.id)
                putExtra("OTHER_USER_NAME", student.name)
                putExtra("IS_TEACHER", true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening chat: ${e.message}")
            Toast.makeText(this, "Error opening chat", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Adapter for the students RecyclerView
     */
    inner class StudentsAdapter(
        private val students: List<Student>,
        private val onItemClick: (Student) -> Unit
    ) : RecyclerView.Adapter<StudentsAdapter.StudentViewHolder>() {

        inner class StudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cardView: CardView = itemView.findViewById(R.id.studentCard)
            val nameTextView: TextView = itemView.findViewById(R.id.studentName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_student, parent, false)
            return StudentViewHolder(view)
        }

        override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
            val student = students[position]

            holder.nameTextView.text = student.name

            // Set click listener
            holder.cardView.setOnClickListener {
                onItemClick(student)
            }
        }

        override fun getItemCount() = students.size
    }
}

/**
 * Data model representing a student
 */
data class Student(
    val id: String = "",
    val name: String = ""
)