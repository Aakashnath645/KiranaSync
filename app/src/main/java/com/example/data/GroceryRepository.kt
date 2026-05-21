package com.example.data

import kotlinx.coroutines.flow.Flow

class GroceryRepository(private val groceryDao: GroceryDao) {
    val allItems: Flow<List<GroceryItem>> = groceryDao.getAllItems()
    val allShoppingItems: Flow<List<ShoppingItem>> = groceryDao.getAllShoppingItems()
    val userPreferenceFlow: Flow<UserPreference?> = groceryDao.getUserPreferenceFlow()

    suspend fun insertItem(item: GroceryItem) {
        groceryDao.insertItem(item)
    }

    suspend fun deleteItem(item: GroceryItem) {
        groceryDao.deleteItem(item)
    }

    suspend fun deleteItemById(id: Int) {
        groceryDao.deleteItemById(id)
    }

    // User Preference operations
    suspend fun getUserPreference(): UserPreference? {
        return groceryDao.getUserPreference()
    }

    suspend fun saveUserPreference(preference: UserPreference) {
        groceryDao.insertUserPreference(preference)
    }

    // Shopping List operations
    suspend fun insertShoppingItem(shoppingItem: ShoppingItem) {
        groceryDao.insertShoppingItem(shoppingItem)
    }

    suspend fun deleteShoppingItem(shoppingItem: ShoppingItem) {
        groceryDao.deleteShoppingItem(shoppingItem)
    }

    suspend fun updateShoppingItemChecked(id: Int, isChecked: Boolean) {
        groceryDao.updateShoppingItemChecked(id, isChecked)
    }

    suspend fun deleteCheckedShoppingItems() {
        groceryDao.deleteCheckedShoppingItems()
    }

    suspend fun clearShoppingList() {
        groceryDao.clearShoppingList()
    }
}
