package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_preferences")
data class UserPreference(
    @PrimaryKey val id: Int = 1, // Store a single row of settings
    val username: String = "Aakash Nath",
    val defaultCurrency: String = "INR (₹)",
    val preferredWeightUnit: String = "kg", // "g" vs "kg"
    val preferredVolumeUnit: String = "L", // "ml" vs "L"
    val reminderThresholdDays: Int = 3, // Alert items expiring in <= 3 days
    val categoryOrderJson: String = "Staples,Dairy,Snacks,Spices,Other" // Custom display order
)
