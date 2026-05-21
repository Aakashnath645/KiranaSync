package com.example

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.GroceryDatabase
import com.example.data.GroceryItem
import com.example.data.GroceryRepository
import com.example.data.UserPreference
import com.example.data.ShoppingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class GroceryViewModel(private val application: Application) : AndroidViewModel(application) {

    private val repository: GroceryRepository
    val allItems: StateFlow<List<GroceryItem>>
    val allShoppingItems: StateFlow<List<ShoppingItem>>
    val userPreference: StateFlow<UserPreference?>

    init {
        val database = GroceryDatabase.getDatabase(application)
        repository = GroceryRepository(database.groceryDao)
        
        allItems = repository.allItems.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allShoppingItems = repository.allShoppingItems.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        userPreference = repository.userPreferenceFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

        // Populate a default preference record if none exists
        viewModelScope.launch {
            if (repository.getUserPreference() == null) {
                repository.saveUserPreference(UserPreference())
            }
        }
    }

    data class DummyProduct(val name: String, val category: String, val quantity: String)

    val barcodeMap = mapOf(
        "8901262010015" to DummyProduct("Amul Butter", "Dairy", "100g"),
        "8901058002475" to DummyProduct("Maggi 70g", "Snacks", "70g"),
        "8901725181221" to DummyProduct("Aashirvaad Atta 5kg", "Staples", "5kg"),
        "8901491101836" to DummyProduct("Tata Salt 1kg", "Staples", "1kg"),
        "8901030753067" to DummyProduct("Surf Excel Powder 1kg", "Staples", "1kg")
    )

    // Scanner state
    private val _scannedProduct = MutableStateFlow<DummyProduct?>(null)
    val scannedProduct = _scannedProduct.asStateFlow()

    private val _scannedBarcode = MutableStateFlow<String?>(null)
    val scannedBarcode = _scannedBarcode.asStateFlow()

    // Add / Edit form states
    val formName = MutableStateFlow("")
    val formQuantity = MutableStateFlow("")
    val formCategory = MutableStateFlow("Staples")
    val formExpiryDays = MutableStateFlow("7") // Default 7 days expiry

    fun onBarcodeScanned(barcode: String) {
        _scannedBarcode.value = barcode
        val dummy = barcodeMap[barcode]
        if (dummy != null) {
            _scannedProduct.value = dummy
            formName.value = dummy.name
            formCategory.value = dummy.category
            formQuantity.value = dummy.quantity
        } else {
            _scannedProduct.value = DummyProduct("New Item", "Other", "1 unit")
            formName.value = "EAN - $barcode"
            formCategory.value = "Other"
            formQuantity.value = "1 unit"
        }
    }

    fun resetForm() {
        formName.value = ""
        formQuantity.value = ""
        formCategory.value = "Staples"
        formExpiryDays.value = "7"
        _scannedBarcode.value = null
        _scannedProduct.value = null
    }

    fun setDirectForm(name: String, cat: String, qty: String) {
        formName.value = name
        formCategory.value = cat
        formQuantity.value = qty
    }

    fun saveItem() {
        val nameVal = formName.value.trim()
        val qtyVal = formQuantity.value.trim()
        val catVal = formCategory.value
        val days = formExpiryDays.value.toIntOrNull() ?: 7

        if (nameVal.isEmpty()) return

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, days)
        val expiryTs = calendar.timeInMillis

        viewModelScope.launch {
            repository.insertItem(
                GroceryItem(
                    name = nameVal,
                    quantity = if (qtyVal.isEmpty()) "1 unit" else qtyVal,
                    category = catVal,
                    expiryTimestamp = expiryTs,
                    barcode = _scannedBarcode.value
                )
            )
            resetForm()
        }
    }

    fun addGroceryItem(name: String, category: String, qty: String, expiryDays: Int, barcode: String? = null) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, expiryDays)
        viewModelScope.launch {
            repository.insertItem(
                GroceryItem(
                    name = name.trim(),
                    category = category,
                    quantity = if (qty.trim().isEmpty()) "1" else qty.trim(),
                    expiryTimestamp = calendar.timeInMillis,
                    barcode = barcode
                )
            )
        }
    }

    fun deleteItem(item: GroceryItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }

    // Settings & User Preference Helpers
    fun saveUserPreference(username: String, currency: String, weightUnit: String, volumeUnit: String, threshold: Int, categoryOrder: String) {
        viewModelScope.launch {
            val updatedPref = UserPreference(
                id = 1,
                username = username,
                defaultCurrency = currency,
                preferredWeightUnit = weightUnit,
                preferredVolumeUnit = volumeUnit,
                reminderThresholdDays = threshold,
                categoryOrderJson = categoryOrder
            )
            repository.saveUserPreference(updatedPref)
            scheduleExpiryCheckAlarm(application, threshold)
        }
    }

    fun scheduleExpiryCheckAlarm(context: Context, thresholdDays: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ExpiryAlarmReceiver::class.java)
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            4001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Schedule to check periodically or trigger immediately for demonstration and test
        val triggersIn = SystemClock.elapsedRealtime() + 3000 // Test check in 3 seconds!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggersIn,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggersIn,
                pendingIntent
            )
        }
    }

    // Shopping List Helpers
    fun addShoppingItem(name: String, category: String, qty: String) {
        if (name.trim().isEmpty()) return
        viewModelScope.launch {
            repository.insertShoppingItem(
                ShoppingItem(
                    name = name.trim(),
                    category = category,
                    quantity = if (qty.trim().isEmpty()) "1 unit" else qty.trim()
                )
            )
        }
    }

    fun toggleShoppingItemChecked(item: ShoppingItem) {
        viewModelScope.launch {
            repository.updateShoppingItemChecked(item.id, !item.isChecked)
        }
    }

    fun deleteShoppingItem(item: ShoppingItem) {
        viewModelScope.launch {
            repository.deleteShoppingItem(item)
        }
    }

    fun deleteCheckedShoppingItems() {
        viewModelScope.launch {
            repository.deleteCheckedShoppingItems()
        }
    }

    fun clearShoppingList() {
        viewModelScope.launch {
            repository.clearShoppingList()
        }
    }

    fun transferLowStockToShoppingList(item: GroceryItem) {
        viewModelScope.launch {
            repository.insertShoppingItem(
                ShoppingItem(
                    name = item.name,
                    category = item.category,
                    quantity = item.quantity
                )
            )
        }
    }
}
