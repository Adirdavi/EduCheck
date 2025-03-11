package com.example.educheck.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.educheck.R
import com.example.educheck.TakeTestActivity
import com.example.educheck.utilities.Test
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

/**
 * Fragment that displays available tests for students
 */
class AvailableTestsFragment : Fragment() {
    // List of available tests
    private val testsList = mutableListOf<Test>()

    // UI components
    private lateinit var testsRecyclerView: RecyclerView
    private lateinit var progressIndicator: ProgressBar
    private lateinit var noTestsMessage: TextView
    private lateinit var testsAdapter: TestsAdapter

    // Firebase
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    companion object {
        private const val TAG = "AvailableTestsFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_available_tests, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Initialize UI components
        initializeUI(view)

        // Load tests from Firebase
        loadTestsFromFirebase()
    }

    override fun onResume() {
        super.onResume()
        // Refresh the list when returning to this fragment
        if (::firestore.isInitialized) {
            loadTestsFromFirebase()
        }
    }

    /**
     * Initialize UI components
     */
    private fun initializeUI(view: View) {
        try {
            testsRecyclerView = view.findViewById(R.id.testsRecyclerView)
            progressIndicator = view.findViewById(R.id.progressIndicator)
            noTestsMessage = view.findViewById(R.id.noTestsMessage)

            // Hide the "No tests" message initially
            noTestsMessage.visibility = View.GONE

            // Set up the recycler view
            testsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            testsAdapter = TestsAdapter()
            testsRecyclerView.adapter = testsAdapter

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing UI: ${e.message}")
            Toast.makeText(requireContext(), "Error initializing UI: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Load the list of tests from Firebase
     */
    private fun loadTestsFromFirebase() {
        try {
            // Show loading indicator
            progressIndicator.visibility = View.VISIBLE

            // For students, show all tests
            Log.d(TAG, "Loading all tests for student")
            firestore.collection("tests")
                .orderBy("createdAt", Query.Direction.DESCENDING)
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

                    testsList.addAll(tempList)

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
     * Open screen to take the test
     */
    private fun openTestActivity(test: Test) {
        try {
            val intent = Intent(requireActivity(), TakeTestActivity::class.java).apply {
                putExtra("TEST_ID", test.id)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening test screen: ${e.message}")
            Toast.makeText(requireContext(), "Error opening test: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Adapter for student view
     */
    inner class TestsAdapter : RecyclerView.Adapter<TestsAdapter.TestViewHolder>() {

        inner class TestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val testNumberText: TextView = itemView.findViewById(R.id.testNumberText)
            val testTitle: TextView = itemView.findViewById(R.id.testTitle)
            val testDetails: TextView = itemView.findViewById(R.id.testDetails)
            val startTestButton: MaterialButton = itemView.findViewById(R.id.startTestButton)

            init {
                startTestButton.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        openTestActivity(testsList[position])
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TestViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.test_item, parent, false)
            return TestViewHolder(view)
        }

        override fun onBindViewHolder(holder: TestViewHolder, position: Int) {
            val test = testsList[position]

            // Set test number
            holder.testNumberText.text = (position + 1).toString()

            holder.testTitle.text = test.title
            holder.testDetails.text = "${test.questions.size} questions"
        }

        override fun getItemCount() = testsList.size
    }
}