package com.example.educheck.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.educheck.R
import com.example.educheck.TestsActivity
import com.example.educheck.utilities.Test
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Fragment for teachers to manage their tests
 */
class TeacherTestsFragment : Fragment() {

    // List of available tests
    private val testsList = mutableListOf<Test>()

    // UI components
    private lateinit var testsRecyclerView: RecyclerView
    private lateinit var progressIndicator: ProgressBar
    private lateinit var noTestsMessage: TextView
    private lateinit var testsAdapter: TeacherTestsAdapter

    // Firebase
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var userId = ""

    companion object {
        private const val TAG = "TeacherTestsFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_teacher_tests, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Get current user ID
        userId = auth.currentUser?.uid ?: ""
        if (userId.isEmpty()) {
            Toast.makeText(requireContext(), "Error: User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // Initialize UI components
        initializeUI(view)

        // Load tests from Firebase
        loadTestsFromFirebase()
    }

    override fun onResume() {
        super.onResume()
        // Refresh the list when returning to this fragment
        if (::firestore.isInitialized && userId.isNotEmpty()) {
            loadTestsFromFirebase()
        }
    }

    /**
     * Initialize UI components
     */
    private fun initializeUI(view: View) {
        // Find views
        testsRecyclerView = view.findViewById(R.id.testsRecyclerView)
        progressIndicator = view.findViewById(R.id.progressIndicator)
        noTestsMessage = view.findViewById(R.id.noTestsMessage)

        // Set up RecyclerView
        testsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        testsAdapter = TeacherTestsAdapter()
        testsRecyclerView.adapter = testsAdapter
    }

    /**
     * רענון הנתונים - ייקרא כשחוזרים מעריכת מבחן
     */
    fun refreshData() {
        // טעינת המבחנים מחדש
        loadTestsFromFirebase()
    }

    /**
     * Load the list of tests from Firebase
     */
    private fun loadTestsFromFirebase() {
        try {
            // Show loading indicator
            progressIndicator.visibility = View.VISIBLE

            // For teachers, only show their own tests
            Log.d(TAG, "Loading teacher tests for user ID: $userId")
            firestore.collection("tests")
                .whereEqualTo("createdBy", userId)
                .get()
                .addOnSuccessListener { documents ->
                    progressIndicator.visibility = View.GONE
                    Log.d(TAG, "Found ${documents.size()} tests")

                    testsList.clear()
                    val tempList = mutableListOf<Test>()

                    for (document in documents) {
                        try {
                            val test = document.toObject(Test::class.java)
                            if (test.id.isNotEmpty()) {  // Ensure test is valid
                                tempList.add(test)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting document to test: ${e.message}")
                            // Continue to next item
                        }
                    }

                    // Sort in memory
                    testsList.addAll(tempList.sortedByDescending { it.createdAt })

                    // Update the adapter
                    testsAdapter.notifyDataSetChanged()

                    // Show message if there are no available tests
                    if (testsList.isEmpty()) {
                        noTestsMessage.visibility = View.VISIBLE
                    } else {
                        noTestsMessage.visibility = View.GONE
                    }
                }
                .addOnFailureListener { e ->
                    progressIndicator.visibility = View.GONE
                    noTestsMessage.visibility = View.VISIBLE

                    Log.e(TAG, "Error loading tests: ${e.message}")
                    Toast.makeText(requireContext(), "Error loading tests: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            progressIndicator.visibility = View.GONE
            Log.e(TAG, "General error loading tests: ${e.message}")
            Toast.makeText(requireContext(), "Error loading tests: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Edit test using CreateTestFragment instead of CreateTestActivity
     */
    private fun editTest(test: Test) {
        try {
            // מקבל את האקטיביטי המארחת
            val activity = requireActivity() as TestsActivity

            // יוצר את הפרגמנט CreateTestFragment עם פרמטרים מתאימים
            val createTestFragment = CreateTestFragment.newInstance(true, test.id)

            // מסתיר את ה-ViewPager ומציג את מיכל הפרגמנט
            activity.findViewById<View>(R.id.viewPagerContainer)?.visibility = View.GONE
            activity.findViewById<View>(R.id.fragmentContainer)?.visibility = View.VISIBLE

            // מעדכן את הכותרת ל"Edit Test"
            activity.findViewById<TextView>(R.id.titleTextView)?.text = "Edit Test"

            // מחליף את הפרגמנט הנוכחי בפרגמנט החדש
            activity.supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, createTestFragment)
                .addToBackStack("edit_test")
                .commit()

        } catch (e: Exception) {
            Log.e(TAG, "Error opening CreateTestFragment: ${e.message}")
            Toast.makeText(requireContext(), "Error opening edit screen: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Confirm and delete a test
     */
    private fun confirmAndDeleteTest(test: Test, position: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Test")
            .setMessage("Are you sure you want to delete the test \"${test.title}\"? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteTest(test, position)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Delete a test from Firebase
     */
    private fun deleteTest(test: Test, position: Int) {
        progressIndicator.visibility = View.VISIBLE

        firestore.collection("tests").document(test.id)
            .delete()
            .addOnSuccessListener {
                progressIndicator.visibility = View.GONE
                // Remove from local list
                testsList.removeAt(position)
                testsAdapter.notifyItemRemoved(position)

                // Show empty message if no tests left
                if (testsList.isEmpty()) {
                    noTestsMessage.visibility = View.VISIBLE
                }

                Toast.makeText(requireContext(), "Test deleted successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                progressIndicator.visibility = View.GONE
                Log.e(TAG, "Error deleting test: ${e.message}")
                Toast.makeText(requireContext(), "Error deleting test: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Adapter for teacher view
     */
    inner class TeacherTestsAdapter : RecyclerView.Adapter<TeacherTestsAdapter.TeacherViewHolder>() {

        inner class TeacherViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val testNumberText: TextView = itemView.findViewById(R.id.testNumberText)
            val testTitle: TextView = itemView.findViewById(R.id.testTitle)
            val testDetails: TextView = itemView.findViewById(R.id.testDetails)
            val editButton: MaterialButton = itemView.findViewById(R.id.editButton)
            val deleteButton: MaterialButton = itemView.findViewById(R.id.deleteButton)

            init {
                editButton.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        editTest(testsList[position])
                    }
                }

                deleteButton.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        confirmAndDeleteTest(testsList[position], position)
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeacherViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.test_item_teacher, parent, false)
            return TeacherViewHolder(view)
        }

        override fun onBindViewHolder(holder: TeacherViewHolder, position: Int) {
            val test = testsList[position]

            // Set test number
            holder.testNumberText.text = (position + 1).toString()

            holder.testTitle.text = test.title
            holder.testDetails.text = "${test.questions.size} questions"
        }

        override fun getItemCount() = testsList.size
    }
}