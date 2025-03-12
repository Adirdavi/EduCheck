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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Activity for teachers to select a student to track their progress
 */
class StudentTrackActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var studentsAdapter: StudentsAdapter

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    private val studentsList = ArrayList<Student>()
    private var teacherId: String = ""

    companion object {
        private const val TAG = "StudentTrackActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_track)

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

        // Set up adapter with grid layout (2 columns)
        recyclerView.layoutManager = GridLayoutManager(this, 2)

        studentsAdapter = StudentsAdapter(studentsList) { student ->
            openStudentProgress(student)
        }

        recyclerView.adapter = studentsAdapter

        val backButton = findViewById<ImageButton>(R.id.backButton)
        backButton.setOnClickListener {
            onBackPressed()
        }
        // Load students list
        loadStudents()
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
                Toast.makeText(this@StudentTrackActivity,
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
     * Open progress tracking for selected student
     */
    private fun openStudentProgress(student: Student) {
        try {
            val intent = Intent(this, StudentProgressActivity::class.java).apply {
                putExtra("STUDENT_ID", student.id)
                putExtra("STUDENT_NAME", student.name)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening student progress: ${e.message}")
            Toast.makeText(this, "Error opening student progress", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Data class for Student
     */
    data class Student(val id: String, val name: String)

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
                .inflate(R.layout.item_student_grid, parent, false)
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