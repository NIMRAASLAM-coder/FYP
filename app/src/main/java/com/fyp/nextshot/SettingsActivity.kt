package com.fyp.nextshot

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var switchPracticeReminders: SwitchMaterial
    private lateinit var switchAchievementAlerts: SwitchMaterial
    private lateinit var switchWeeklyReports: SwitchMaterial
    private lateinit var switchCoachingTips: SwitchMaterial
    private lateinit var btnExportData: TextView
    private lateinit var btnResetSettings: TextView
    private lateinit var btnDeleteAccount: TextView
    private lateinit var tvPrivacyPolicy: TextView
    private lateinit var tvTermsOfService: TextView
    private lateinit var tvContactSupport: TextView

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initializeViews()
        loadSettings()
        setupClickListeners()
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btn_back)

        // Notification switches
        switchPracticeReminders = findViewById(R.id.switch_practice_reminders)
        switchAchievementAlerts = findViewById(R.id.switch_achievement_alerts)
        switchWeeklyReports = findViewById(R.id.switch_weekly_reports)
        switchCoachingTips = findViewById(R.id.switch_coaching_tips)

        // Account buttons
        btnExportData = findViewById(R.id.btn_export_data)
        btnResetSettings = findViewById(R.id.btn_reset_settings)
        btnDeleteAccount = findViewById(R.id.btn_delete_account)

        // About links
        tvPrivacyPolicy = findViewById(R.id.tv_privacy_policy)
        tvTermsOfService = findViewById(R.id.tv_terms_of_service)
        tvContactSupport = findViewById(R.id.tv_contact_support)

        sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)
    }

    private fun loadSettings() {
        // Load saved notification preferences
        switchPracticeReminders.isChecked = sharedPreferences.getBoolean("practice_reminders", true)
        switchAchievementAlerts.isChecked = sharedPreferences.getBoolean("achievement_alerts", true)
        switchWeeklyReports.isChecked = sharedPreferences.getBoolean("weekly_reports", false)
        switchCoachingTips.isChecked = sharedPreferences.getBoolean("coaching_tips", true)
    }

    private fun setupClickListeners() {
        // Back button
        btnBack.setOnClickListener {
            finish()
        }

        // Notification switches
        switchPracticeReminders.setOnCheckedChangeListener { _, isChecked ->
            saveNotificationSetting("practice_reminders", isChecked)
            showToast("Practice reminders ${if (isChecked) "enabled" else "disabled"}")
        }

        switchAchievementAlerts.setOnCheckedChangeListener { _, isChecked ->
            saveNotificationSetting("achievement_alerts", isChecked)
            showToast("Achievement alerts ${if (isChecked) "enabled" else "disabled"}")
        }

        switchWeeklyReports.setOnCheckedChangeListener { _, isChecked ->
            saveNotificationSetting("weekly_reports", isChecked)
            showToast("Weekly reports ${if (isChecked) "enabled" else "disabled"}")
        }

        switchCoachingTips.setOnCheckedChangeListener { _, isChecked ->
            saveNotificationSetting("coaching_tips", isChecked)
            showToast("Coaching tips ${if (isChecked) "enabled" else "disabled"}")
        }

        // Account actions
        btnExportData.setOnClickListener {
            exportUserData()
        }

        btnResetSettings.setOnClickListener {
            showResetConfirmationDialog()
        }

        btnDeleteAccount.setOnClickListener {
            showDeleteAccountDialog()
        }

        // About section
        tvPrivacyPolicy.setOnClickListener {
            openUrl("https://nextshot.ai/privacy-policy")
        }

        tvTermsOfService.setOnClickListener {
            openUrl("https://nextshot.ai/terms-of-service")
        }

        tvContactSupport.setOnClickListener {
            contactSupport()
        }
    }

    private fun saveNotificationSetting(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(key, value).apply()
    }

    private fun exportUserData() {
        AlertDialog.Builder(this)
            .setTitle("Export My Data")
            .setMessage("Your data will be exported as a JSON file and shared via email or other apps.")
            .setPositiveButton("Export") { _, _ ->
                // TODO: Implement actual data export functionality
                showToast("Exporting data...")

                // Simulate export (you'll need to implement actual export logic)
                val exportIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "NextShot Data Export")
                    putExtra(Intent.EXTRA_TEXT, "Your NextShot data export is ready.")
                }
                startActivity(Intent.createChooser(exportIntent, "Share Data Export"))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showResetConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reset All Settings")
            .setMessage("This will reset all your preferences to default values. Your profile and session data will not be affected.")
            .setPositiveButton("Reset") { _, _ ->
                resetAllSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetAllSettings() {
        sharedPreferences.edit().clear().apply()

        // Reset switches to default
        switchPracticeReminders.isChecked = true
        switchAchievementAlerts.isChecked = true
        switchWeeklyReports.isChecked = false
        switchCoachingTips.isChecked = true

        saveNotificationSetting("practice_reminders", true)
        saveNotificationSetting("achievement_alerts", true)
        saveNotificationSetting("weekly_reports", false)
        saveNotificationSetting("coaching_tips", true)

        showToast("Settings reset to default")
    }

    private fun showDeleteAccountDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete your account? This action cannot be undone and all your data will be permanently deleted.")
            .setPositiveButton("Delete") { _, _ ->
                showFinalDeleteConfirmation()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFinalDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Final Confirmation")
            .setMessage("This is your last chance. Delete account permanently?")
            .setPositiveButton("Yes, Delete") { _, _ ->
                deleteAccount()
            }
            .setNegativeButton("No, Keep Account", null)
            .show()
    }

    private fun deleteAccount() {
        // TODO: Implement actual account deletion with backend/Firebase

        // Clear all local data
        sharedPreferences.edit().clear().apply()
        getSharedPreferences("UserSession", MODE_PRIVATE).edit().clear().apply()

        showToast("Account deleted successfully")

        // Navigate to sign in
        val intent = Intent(this, SignIn::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            showToast("Unable to open link")
        }
    }

    private fun contactSupport() {
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:support@nextshot.ai")
            putExtra(Intent.EXTRA_SUBJECT, "NextShot Support Request")
            putExtra(Intent.EXTRA_TEXT, "Hello NextShot Support Team,\n\n")
        }

        try {
            startActivity(Intent.createChooser(emailIntent, "Contact Support"))
        } catch (e: Exception) {
            showToast("No email app found")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}