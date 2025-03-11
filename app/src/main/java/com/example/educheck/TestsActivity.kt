package com.example.educheck

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.educheck.fragments.AvailableTestsFragment
import com.example.educheck.fragments.CompletedTestsFragment
import com.example.educheck.fragments.CreateTestFragment
import com.example.educheck.fragments.TeacherTestsFragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth

/**
 * Activity that displays tests - for both students and teachers.
 * Students can take tests, teachers can view tests.
 * Uses fragments to separate different test views.
 */
class TestsActivity : AppCompatActivity() {

    // UI component references
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var titleTextView: TextView
    private lateinit var backButton: ImageButton
    private lateinit var viewPagerContainer: View
    private lateinit var fragmentContainer: View

    // User role
    var isTeacher = false
    private var userId = ""

    companion object {
        private const val TAG = "TestsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge support
        enableEdgeToEdge()

        // Load the layout file for the screen
        setContentView(R.layout.activity_tests)

        // Initialize Firebase Auth
        val auth = FirebaseAuth.getInstance()

        // Get current user ID
        userId = auth.currentUser?.uid ?: ""
        if (userId.isEmpty()) {
            finish()
            return
        }

        // Determine if the user is a teacher
        isTeacher = intent.getBooleanExtra("IS_TEACHER", false)
        Log.d(TAG, "IS_TEACHER flag from intent: $isTeacher")

        // Set listener for system insets updates
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize UI components
        initializeUI()

        // Setup ViewPager with appropriate fragments
        setupViewPager()
    }

    /**
     * Initialize UI components
     */
    private fun initializeUI() {
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
        titleTextView = findViewById(R.id.titleTextView)
        backButton = findViewById(R.id.backButton)

        // Find the containers
        viewPagerContainer = findViewById(R.id.viewPagerContainer)
        fragmentContainer = findViewById(R.id.fragmentContainer)

        // Set the appropriate title based on user role
        titleTextView.text = if (isTeacher) "Manage Tests" else "Tests"

        // Set back button click listener
        backButton.setOnClickListener {
            onBackPressed()
        }
    }

    /**
     * Setup ViewPager with appropriate fragments
     */
    private fun setupViewPager() {
        // Create appropriate pager adapter based on user role
        val pagerAdapter = if (isTeacher) {
            TeacherTestsPagerAdapter(this)
        } else {
            StudentTestsPagerAdapter(this)
        }

        // Set adapter to ViewPager2
        viewPager.adapter = pagerAdapter

        // Connect TabLayout with ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = if (isTeacher) {
                when (position) {
                    0 -> "המבחנים שלי"
                    1 -> "יצירת מבחן חדש"
                    else -> "Tab ${position + 1}"
                }
            } else {
                when (position) {
                    0 -> "מבחנים זמינים"
                    1 -> "מבחנים שפתרתי"
                    else -> "Tab ${position + 1}"
                }
            }
        }.attach()
    }

    /**
     * Function to navigate to a specific fragment, replacing the current view
     */
    fun navigateToFragment(fragment: Fragment, addToBackStack: Boolean = true) {
        // Hide the ViewPager container and show the fragment container
        viewPagerContainer.visibility = View.GONE
        fragmentContainer.visibility = View.VISIBLE

        // Create a transaction to replace the fragment
        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)

        // Add to back stack if needed
        if (addToBackStack) {
            transaction.addToBackStack(null)
        }

        // Commit the transaction
        transaction.commit()
    }

    /**
     * Function to return to the tabs view
     */
    fun returnToTabs() {
        // Show the ViewPager container and hide the fragment container
        viewPagerContainer.visibility = View.VISIBLE
        fragmentContainer.visibility = View.GONE

        // Reset the title
        titleTextView.text = if (isTeacher) "Manage Tests" else "Tests"
    }

    /**
     * נקרא כאשר חוזרים לתצוגת הלשוניות, מרענן את הפרגמנט הנוכחי
     */
    fun returnToTabsAndRefresh() {
        // הצגת ViewPager והסתרת מיכל הפרגמנט
        viewPagerContainer.visibility = View.VISIBLE
        fragmentContainer.visibility = View.GONE

        // איפוס הכותרת
        titleTextView.text = if (isTeacher) "Manage Tests" else "Tests"

        // רענון הפרגמנט הנוכחי
        val currentFragment = supportFragmentManager.findFragmentByTag("f" + viewPager.currentItem)
        if (currentFragment is TeacherTestsFragment) {
            currentFragment.refreshData()
        }
    }

    /**
     * Override for onBackPressed to handle fragment navigation
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Check if the fragment container is visible
        if (fragmentContainer.visibility == View.VISIBLE) {
            // If we have fragments in the back stack, pop one
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()

                // If we no longer have fragments in the back stack after popping,
                // return to the tabs view
                if (supportFragmentManager.backStackEntryCount == 0) {
                    returnToTabs()
                }
            } else {
                // If no fragments in back stack but fragment container is visible,
                // return to the tabs view
                returnToTabs()
            }
        } else {
            // Normal back behavior
            super.onBackPressed()
        }
    }

    /**
     * Pager adapter for Teacher view
     */
    inner class TeacherTestsPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> TeacherTestsFragment()
                1 -> CreateTestFragment()
                else -> Fragment() // Should never happen
            }
        }
    }

    /**
     * Pager adapter for Student view
     */
    inner class StudentTestsPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> AvailableTestsFragment()
                1 -> CompletedTestsFragment()
                else -> Fragment() // Should never happen
            }
        }
    }
}