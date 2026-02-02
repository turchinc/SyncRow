package com.syncrow.ui.discovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.syncrow.R
import com.syncrow.ui.workout.WorkoutViewModel
import com.syncrow.ui.workout.DiscoveredDevice
import com.syncrow.ui.workout.DeviceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(viewModel: WorkoutViewModel, onBack: () -> Unit) {
    val devices by viewModel.discoveredDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val selectedRower by viewModel.selectedRowerAddress.collectAsState()
    val selectedHrm by viewModel.selectedHrmAddress.collectAsState()

    // Filter to only show relevant hardware as requested
    val filteredDevices = remember(devices) {
        devices.filter { it.type != DeviceType.UNKNOWN }
    }

    LaunchedEffect(Unit) {
        viewModel.startDiscovery()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopDiscovery()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_hardware_setup)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (isScanning) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp).padding(end = 16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(onClick = { viewModel.startDiscovery() }) {
                            Text(stringResource(R.string.btn_rescan))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            Text(
                stringResource(R.string.discovery_description),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (filteredDevices.isEmpty() && !isScanning) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.discovery_no_devices))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredDevices) { device ->
                        val isSelected = device.address == selectedRower || device.address == selectedHrm
                        DeviceItem(device, isSelected) {
                            viewModel.selectDevice(device)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceItem(device: DiscoveredDevice, isSelected: Boolean, onClick: () -> Unit) {
    val categoryColor = when (device.type) {
        DeviceType.ROWER -> Color(0xFF00C853) // Green
        DeviceType.HRM -> Color.Red
        else -> Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = if (isSelected) CardDefaults.cardColors(containerColor = categoryColor.copy(alpha = 0.1f)) else CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(containerColor = categoryColor) {
                        Text(device.type.name, color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    val displayName = if (device.name == "Unknown Device") stringResource(R.string.unknown_device) else device.name
                    Text(text = displayName, style = MaterialTheme.typography.titleMedium)
                }
                Text(text = device.address, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.cd_selected), tint = categoryColor)
            } else {
                Text(text = "${device.rssi} dBm", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
