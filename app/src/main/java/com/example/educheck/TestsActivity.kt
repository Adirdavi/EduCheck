package com.example.educheck

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
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
 * Activity that displays tests for both students and teachers.
 * Provides different views and interactions based on user role.
 */
class TestsActivity : AppCompatActivity() {

    // UI component references
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var backButton: ImageButton
    private lateinit var viewPagerContainer: View
    private lateinit var fragmentContainer: View

    // User role and identification
    var isTeacher = false
    private var userId = ""

    companion object {
        private const val TAG = "TestsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable full-screen edge-to-edge display
        enableEdgeToEdge()

        // Set the layout for the activity
        setContentView(R.layout.activity_tests)

        // Initialize Firebase Authentication
        val auth = FirebaseAuth.getInstance()

        // Retrieve current user ID
        userId = auth.currentUser?.uid ?: ""
        if (userId.isEmpty()) {
            // Finish activity if no user is logged in
            finish()
            return
        }

        // Check if the user is a teacher from the intent
        isTeacher = intent.getBooleanExtra("IS_TEACHER", false)
        Log.d(TAG, "User is teacher: $isTeacher")

        // Set up system bars insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize user interface components
        initializeUI()

        // Configure ViewPager with appropriate fragments
        setupViewPager()
    }

    /**
     * Initialize all UI components and set up listeners
     */
    private fun initializeUI() {
        // Find views by their IDs
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
        backButton = findViewById(R.id.backButton)

        // Find container views
        viewPagerContainer = findViewById(R.id.viewPagerContainer)
        fragmentContainer = findViewById(R.id.fragmentContainer)

        // Set up back button to navigate to appropriate menu
        backButton.setOnClickListener {
            navigateToAppropriateMenu()
        }
    }

    /**
     * Navigate to the correct menu based on user role
     */
    private fun navigateToAppropriateMenu() {
        // Choose the correct menu activity based on user role
        val intent = if (isTeacher) {
            Intent(this, TeacherMenuActivity::class.java)
        } else {
            Intent(this, StudentMenuActivity::class.java)
        }

        // Clear the activity stack and start the menu activity
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    /**
     * Set up ViewPager with fragments based on user role
     */
    private fun setupViewPager() {
        // Create the appropriate pager adapter
        val pagerAdapter = if (isTeacher) {
            TeacherTestsPagerAdapter(this)
        } else {
            StudentTestsPagerAdapter(this)
        }

        // Set the adapter for ViewPager2
        viewPager.adapter = pagerAdapter

        // Connect TabLayout with ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = if (isTeacher) {
                when (position) {
                    0 -> "My Tests"
                    1 -> "Create New Test"
                    else -> "Tab ${position + 1}"
                }
            } else {
                when (position) {
                    0 -> "Available Tests"
                    1 -> "Completed Tests"
                    else -> "Tab ${position + 1}"
                }
            }
        }.attach()
    }

    /**
     * Navigate to a specific fragment, replacing the current view
     */
    fun navigateToFragment(fragment: Fragment, addToBackStack: Boolean = true) {
        // Hide ViewPager and show fragment container
        viewPagerContainer.visibility = View.GONE
        fragmentContainer.visibility = View.VISIBLE

        // Begin fragment transaction
        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)

        // Add to back stack if required
        if (addToBackStack) {
            transaction.addToBackStack(null)
        }

        // Commit the transaction
        transaction.commit()
    }

    /**
     * Return to the tabs view
     */
    fun returnToTabs() {
        // Show ViewPager and hide fragment container
        viewPagerContainer.visibility = View.VISIBLE
        fragmentContainer.visibility = View.GONE
    }

    /**
     * חזרה לתצוגת הטאבים ורענון הפרגמנט הנוכחי
     * עם דגש על רענון רשימת המבחנים
     */
    fun returnToTabsAndRefresh() {
        // הצג את ViewPager והסתר את מיכל הפרגמנט
        viewPagerContainer.visibility = View.VISIBLE
        fragmentContainer.visibility = View.GONE

        // אם זה מורה, ודא שהטאב הראשון (My Tests) נבחר
        if (isTeacher) {
            viewPager.currentItem = 0
        }

        // רענן את הפרגמנט הנוכחי אם זה TeacherTestsFragment
        val currentFragment = supportFragmentManager.findFragmentByTag("f0")
        if (currentFragment is TeacherTestsFragment) {
            currentFragment.refreshData()
        } else {
            // נסה למצוא את הפרגמנט בדרך אחרת
            val teacherTestsFragment = supportFragmentManager.fragments.find { it is TeacherTestsFragment }
            if (teacherTestsFragment is TeacherTestsFragment) {
                teacherTestsFragment.refreshData()
            }
        }
    }

    /**
     * Handle back button press
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // בדוק אם מסך העריכה מוצג
        if (fragmentContainer.visibility == View.VISIBLE && supportFragmentManager.backStackEntryCount > 0) {
            // במקום לחזור לאקטיביטי הקודמת, חזור לרשימת המבחנים
            returnToTabsAndRefresh()

            // ודא שהטאב הראשון (My Tests) נבחר
            if (isTeacher) {
                viewPager.currentItem = 0  // קבע את האינדקס ל-0 - טאב My Tests
            }

            // נקה את כל מחסנית ה-fragments
            supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)

            return
        } else if (fragmentContainer.visibility == View.VISIBLE) {
            // אם מיכל הפרגמנט גלוי אבל אין פרגמנטים במחסנית, חזור לטאבים
            returnToTabs()
            return
        }

        // התנהגות רגילה של כפתור חזור
        super.onBackPressed()
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