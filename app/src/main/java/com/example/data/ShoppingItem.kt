package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shopping_items")
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val category: String, // Staples, Dairy, Snacks, Spices, etc.
    val quantity: String, // e.g. "2 packs", "1 kg"
    val isChecked: Boolean = false,
    val addedTimestamp: Long = System.currentTimeMillis()
)
