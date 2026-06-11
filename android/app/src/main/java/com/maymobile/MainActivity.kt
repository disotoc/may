package com.maymobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maymobile.data.Category
import com.maymobile.data.Expense
import com.maymobile.data.ExpenseDraft
import com.maymobile.data.FuelLog
import com.maymobile.data.FuelLogDraft
import com.maymobile.data.Vehicle
import com.maymobile.ui.MayUiState
import com.maymobile.ui.MayViewModel
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: MayViewModel = viewModel()
                    MayApp(viewModel)
                }
            }
        }
    }
}

@Composable
private fun MayApp(viewModel: MayViewModel) {
    val state = viewModel.uiState
    Scaffold { padding ->
        Surface(modifier = Modifier.padding(padding)) {
            if (!state.isConfigured && state.user == null) {
                ConnectionScreen(state = state, viewModel = viewModel)
            } else {
                DashboardScreen(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ConnectionScreen(state: MayUiState, viewModel: MayViewModel) {
    var baseUrl by remember(state.baseUrl) { mutableStateOf(state.baseUrl) }
    var token by remember(state.apiToken) { mutableStateOf(state.apiToken) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("May Android", style = MaterialTheme.typography.headlineMedium)
        Text("Conecta tu instancia self-hosted con un token API.")
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = baseUrl,
            onValueChange = {
                baseUrl = it
                viewModel.updateConnectionFields(baseUrl, token)
            },
            label = { Text("URL de May") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = token,
            onValueChange = {
                token = it
                viewModel.updateConnectionFields(baseUrl, token)
            },
            label = { Text("Token API") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { viewModel.connect(baseUrl, token) },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isLoading) "Probando..." else "Probar conexion")
        }
        StatusMessages(state)
    }
}

@Composable
private fun DashboardScreen(state: MayUiState, viewModel: MayViewModel) {
    var showFuelDialog by remember { mutableStateOf(false) }
    var showExpenseDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Header(state, viewModel)
        StatusMessages(state)
        if (state.isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        if (state.vehicles.isEmpty() && !state.isLoading) {
            EmptyState("No hay vehiculos disponibles para este token.")
        } else {
            VehicleSelector(
                vehicles = state.vehicles,
                selected = state.selectedVehicle,
                onSelect = viewModel::selectVehicle,
            )
            TabRow(selectedTabIndex = state.selectedTab) {
                Tab(selected = state.selectedTab == 0, onClick = { viewModel.selectTab(0) }, text = { Text("Combustible") })
                Tab(selected = state.selectedTab == 1, onClick = { viewModel.selectTab(1) }, text = { Text("Gastos") })
            }
            when (state.selectedTab) {
                0 -> FuelTab(state.fuelLogs, onAdd = { showFuelDialog = true }, onDelete = viewModel::deleteFuelLog)
                1 -> ExpenseTab(state.expenses, onAdd = { showExpenseDialog = true }, onDelete = viewModel::deleteExpense)
            }
        }
    }

    if (showFuelDialog) {
        FuelDialog(
            onDismiss = { showFuelDialog = false },
            onSave = {
                showFuelDialog = false
                viewModel.createFuelLog(it)
            },
        )
    }
    if (showExpenseDialog) {
        ExpenseDialog(
            categories = state.categories,
            onDismiss = { showExpenseDialog = false },
            onSave = {
                showExpenseDialog = false
                viewModel.createExpense(it)
            },
        )
    }
}

@Composable
private fun Header(state: MayUiState, viewModel: MayViewModel) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("May", style = MaterialTheme.typography.headlineSmall)
                Text(state.user?.username ?: "Conectado", style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = viewModel::refresh, enabled = !state.isLoading) { Text("Actualizar") }
            TextButton(onClick = viewModel::clearConnection) { Text("Salir") }
        }
        state.summary?.let { summary ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryChip("Vehiculos", summary.vehicleCount.toString())
                SummaryChip("Total", money(summary.totalCost, summary.currency))
            }
        }
    }
}

@Composable
private fun SummaryChip(label: String, value: String) {
    AssistChip(onClick = {}, label = { Text("$label: $value") })
}

@Composable
private fun VehicleSelector(vehicles: List<Vehicle>, selected: Vehicle?, onSelect: (Vehicle) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(vehicles, key = { it.id }) { vehicle ->
            FilterChip(
                selected = vehicle.id == selected?.id,
                onClick = { onSelect(vehicle) },
                label = { Text(vehicle.displayName()) },
            )
        }
    }
    selected?.let { vehicle ->
        Card(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(vehicle.displayName(), style = MaterialTheme.typography.titleMedium)
                Text("Odometro: ${vehicle.stats.lastOdometer ?: 0.0}")
                Text("Combustible: ${money(vehicle.stats.totalFuelCost, null)} | Gastos: ${money(vehicle.stats.totalExpenseCost, null)}")
            }
        }
    }
}

@Composable
private fun FuelTab(logs: List<FuelLog>, onAdd: () -> Unit, onDelete: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Button(onClick = onAdd, modifier = Modifier.padding(16.dp).fillMaxWidth()) { Text("Agregar combustible") }
        if (logs.isEmpty()) {
            EmptyState("Sin registros de combustible.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(logs, key = { it.id }) { log ->
                    FuelRow(log, onDelete)
                }
            }
        }
    }
}

@Composable
private fun ExpenseTab(expenses: List<Expense>, onAdd: () -> Unit, onDelete: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Button(onClick = onAdd, modifier = Modifier.padding(16.dp).fillMaxWidth()) { Text("Agregar gasto") }
        if (expenses.isEmpty()) {
            EmptyState("Sin gastos registrados.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(expenses, key = { it.id }) { expense ->
                    ExpenseRow(expense, onDelete)
                }
            }
        }
    }
}

@Composable
private fun FuelRow(log: FuelLog, onDelete: (Int) -> Unit) {
    Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(log.date, style = MaterialTheme.typography.titleSmall)
                Text("Odometro ${log.odometer} | Volumen ${log.volume ?: 0.0}")
                Text("Costo ${money(log.totalCost ?: 0.0, null)} | ${log.station.orEmpty()}")
            }
            TextButton(onClick = { onDelete(log.id) }) { Text("Eliminar") }
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, onDelete: (Int) -> Unit) {
    Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(expense.description, style = MaterialTheme.typography.titleSmall)
                Text("${expense.date} | ${expense.category}")
                Text("${money(expense.cost, null)} | ${expense.vendor.orEmpty()}")
            }
            TextButton(onClick = { onDelete(expense.id) }) { Text("Eliminar") }
        }
    }
}

@Composable
private fun FuelDialog(onDismiss: () -> Unit, onSave: (FuelLogDraft) -> Unit) {
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var odometer by remember { mutableStateOf("") }
    var volume by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }
    var fullTank by remember { mutableStateOf(true) }
    var station by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo combustible") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormText(date, { date = it }, "Fecha YYYY-MM-DD")
                NumberText(odometer, { odometer = it }, "Odometro")
                NumberText(volume, { volume = it }, "Volumen")
                NumberText(price, { price = it }, "Precio por unidad")
                NumberText(total, { total = it }, "Total")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = fullTank, onCheckedChange = { fullTank = it })
                    Text("Tanque lleno")
                }
                FormText(station, { station = it }, "Estacion")
                FormText(notes, { notes = it }, "Notas")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val odo = odometer.toDoubleOrNull()
                if (date.isBlank() || odo == null) {
                    error = "Fecha y odometro son obligatorios."
                } else {
                    onSave(
                        FuelLogDraft(
                            date = date,
                            odometer = odo,
                            volume = optionalDouble(volume),
                            pricePerUnit = optionalDouble(price),
                            totalCost = optionalDouble(total),
                            isFullTank = fullTank,
                            station = station.trim().ifBlank { null },
                            notes = notes.trim().ifBlank { null },
                        )
                    )
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ExpenseDialog(categories: List<Category>, onDismiss: () -> Unit, onSave: (ExpenseDraft) -> Unit) {
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var category by remember(categories) { mutableStateOf(categories.firstOrNull()?.id ?: "maintenance") }
    var description by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var odometer by remember { mutableStateOf("") }
    var vendor by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo gasto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormText(date, { date = it }, "Fecha YYYY-MM-DD")
                if (categories.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories, key = { it.id }) { item ->
                            FilterChip(selected = category == item.id, onClick = { category = item.id }, label = { Text(item.name) })
                        }
                    }
                } else {
                    FormText(category, { category = it }, "Categoria")
                }
                FormText(description, { description = it }, "Descripcion")
                NumberText(cost, { cost = it }, "Costo")
                NumberText(odometer, { odometer = it }, "Odometro")
                FormText(vendor, { vendor = it }, "Proveedor")
                FormText(notes, { notes = it }, "Notas")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedCost = cost.toDoubleOrNull()
                if (date.isBlank() || category.isBlank() || description.isBlank() || parsedCost == null) {
                    error = "Fecha, categoria, descripcion y costo son obligatorios."
                } else {
                    onSave(
                        ExpenseDraft(
                            date = date,
                            category = category,
                            description = description.trim(),
                            cost = parsedCost,
                            odometer = optionalDouble(odometer),
                            vendor = vendor.trim().ifBlank { null },
                            notes = notes.trim().ifBlank { null },
                        )
                    )
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun FormText(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun NumberText(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EmptyState(text: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text)
    }
}

@Composable
private fun StatusMessages(state: MayUiState) {
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp)) }
}

private fun Vehicle.displayName(): String {
    val details = listOfNotNull(make, model).joinToString(" ").ifBlank { vehicleType }
    return "$name ($details)"
}

private fun money(value: Double, currency: String?): String {
    val prefix = currency?.let { "$it " }.orEmpty()
    return prefix + String.format("%.2f", value)
}

private fun optionalDouble(value: String): Double? = value.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
