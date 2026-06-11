package com.maymobile.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maymobile.data.Category
import com.maymobile.data.Expense
import com.maymobile.data.ExpenseDraft
import com.maymobile.data.FuelLog
import com.maymobile.data.FuelLogDraft
import com.maymobile.data.MayApiClient
import com.maymobile.data.MayApiException
import com.maymobile.data.MayConnectionConfig
import com.maymobile.data.MayRepository
import com.maymobile.data.MayUser
import com.maymobile.data.SecureConfigStore
import com.maymobile.data.Summary
import com.maymobile.data.Vehicle
import kotlinx.coroutines.launch

data class MayUiState(
    val isConfigured: Boolean = false,
    val isLoading: Boolean = false,
    val baseUrl: String = "http://100.120.183.123:5050",
    val apiToken: String = "",
    val user: MayUser? = null,
    val summary: Summary? = null,
    val vehicles: List<Vehicle> = emptyList(),
    val selectedVehicle: Vehicle? = null,
    val fuelLogs: List<FuelLog> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedTab: Int = 0,
    val error: String? = null,
    val message: String? = null,
)

class MayViewModel(application: Application) : AndroidViewModel(application) {
    private val apiClient = MayApiClient()
    private val configStore = SecureConfigStore(application)
    private var config: MayConnectionConfig? = null

    var uiState by mutableStateOf(MayUiState())
        private set

    init {
        configStore.load()?.let { saved ->
            config = saved
            uiState = uiState.copy(
                isConfigured = true,
                baseUrl = saved.baseUrl,
                apiToken = saved.apiToken,
            )
            refresh()
        }
    }

    fun updateConnectionFields(baseUrl: String, apiToken: String) {
        uiState = uiState.copy(baseUrl = baseUrl, apiToken = apiToken, error = null, message = null)
    }

    fun connect(baseUrl: String, apiToken: String) {
        val cleanBaseUrl = baseUrl.trim().trimEnd('/')
        val cleanToken = apiToken.trim()
        if (cleanBaseUrl.isBlank() || cleanToken.isBlank()) {
            uiState = uiState.copy(error = "Ingresa la URL de May y el token API.")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null, message = null)
            try {
                val user = apiClient.getMe(cleanBaseUrl, cleanToken)
                val saved = MayConnectionConfig(
                    baseUrl = cleanBaseUrl,
                    apiToken = cleanToken,
                    defaultVehicleId = user.preferences.defaultVehicleId,
                )
                configStore.save(saved)
                config = saved
                loadBootstrap(existingUser = user, successMessage = "Conexion guardada.")
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, error = humanMessage(e))
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loadBootstrap(existingUser = null, successMessage = null)
        }
    }

    fun selectTab(index: Int) {
        uiState = uiState.copy(selectedTab = index)
    }

    fun selectVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            uiState = uiState.copy(selectedVehicle = vehicle, isLoading = true, error = null, message = null)
            try {
                val repo = repository() ?: return@launch
                val fuelLogs = repo.fuelLogs(vehicle.id)
                val expenses = repo.expenses(vehicle.id)
                config = config!!.copy(defaultVehicleId = vehicle.id)
                configStore.save(config!!)
                uiState = uiState.copy(isLoading = false, fuelLogs = fuelLogs, expenses = expenses)
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, error = humanMessage(e))
            }
        }
    }

    fun createFuelLog(draft: FuelLogDraft) {
        val vehicle = uiState.selectedVehicle ?: return
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null, message = null)
            try {
                repository()?.createFuelLog(vehicle.id, draft)
                loadBootstrap(existingUser = uiState.user, successMessage = "Combustible guardado.")
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, error = humanMessage(e))
            }
        }
    }

    fun deleteFuelLog(logId: Int) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null, message = null)
            try {
                repository()?.deleteFuelLog(logId)
                loadBootstrap(existingUser = uiState.user, successMessage = "Registro eliminado.")
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, error = humanMessage(e))
            }
        }
    }

    fun createExpense(draft: ExpenseDraft) {
        val vehicle = uiState.selectedVehicle ?: return
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null, message = null)
            try {
                repository()?.createExpense(vehicle.id, draft)
                loadBootstrap(existingUser = uiState.user, successMessage = "Gasto guardado.")
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, error = humanMessage(e))
            }
        }
    }

    fun deleteExpense(expenseId: Int) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null, message = null)
            try {
                repository()?.deleteExpense(expenseId)
                loadBootstrap(existingUser = uiState.user, successMessage = "Gasto eliminado.")
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, error = humanMessage(e))
            }
        }
    }

    fun clearConnection() {
        configStore.clear()
        config = null
        uiState = MayUiState(baseUrl = uiState.baseUrl)
    }

    private suspend fun loadBootstrap(existingUser: MayUser?, successMessage: String?) {
        val repo = repository()
        if (repo == null) {
            uiState = uiState.copy(isConfigured = false, isLoading = false)
            return
        }

        uiState = uiState.copy(isLoading = true, error = null, message = null)
        try {
            val user = existingUser ?: repo.me()
            val summary = repo.summary()
            val vehicles = repo.vehicles()
            val categories = repo.categories()
            val preferredId = config?.defaultVehicleId ?: user.preferences.defaultVehicleId
            val selected = vehicles.firstOrNull { it.id == uiState.selectedVehicle?.id }
                ?: vehicles.firstOrNull { it.id == preferredId }
                ?: vehicles.firstOrNull()
            val fuelLogs = selected?.let { repo.fuelLogs(it.id) }.orEmpty()
            val expenses = selected?.let { repo.expenses(it.id) }.orEmpty()

            uiState = uiState.copy(
                isConfigured = true,
                isLoading = false,
                user = user,
                summary = summary,
                vehicles = vehicles,
                selectedVehicle = selected,
                fuelLogs = fuelLogs,
                expenses = expenses,
                categories = categories,
                message = successMessage,
            )
        } catch (e: Exception) {
            uiState = uiState.copy(isLoading = false, error = humanMessage(e))
        }
    }

    private fun repository(): MayRepository? {
        val current = config ?: return null
        return MayRepository(apiClient, current)
    }

    private fun humanMessage(error: Exception): String {
        return when (error) {
            is MayApiException -> when (error.statusCode) {
                401 -> "Token invalido o revocado. Genera uno nuevo en May."
                404 -> "Recurso no encontrado en May."
                else -> error.message ?: "Error de API (${error.statusCode})."
            }
            else -> error.message ?: "No se pudo conectar con May."
        }
    }
}
