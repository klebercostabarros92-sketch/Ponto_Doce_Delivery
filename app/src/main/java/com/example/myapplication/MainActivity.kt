package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.network.RetrofitClient
import com.example.myapplication.network.ToggleStoreRequest
import com.example.myapplication.network.UpdateOrderStatusRequest
import com.example.myapplication.ui.theme.MyApplicationTheme
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.launch
import org.json.JSONObject

// Cores baseadas na imagem da marca (Marrom, Rosa e Rosa Claro)
val BrownText = Color(0xFF5A3219)
val PinkAccent = Color(0xFFF25C78)
val LightPinkBackground = Color(0xFFFFE4E1)
val CardBackground = Color(0xFFFFFFFF)
val GreenButton = Color(0xFF4CAF50)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                PontoDoceAdminApp()
            }
        }
    }
}

// Modelo de Dados do Pedido atualizado com o método de pagamento
data class Order(
    val id: String, 
    val customerName: String, 
    val totalAmount: String, 
    val address: String, 
    val paymentMethod: String
)

// Modelo de Dados do Produto no Estoque
data class ProductItem(val id: String, val name: String, val isAvailable: Boolean, val quantity: Int)

@Composable
fun PontoDoceAdminApp() {
    val context = LocalContext.current
    var isOpen by remember { mutableStateOf(false) }
    var showInventoryDialog by remember { mutableStateOf(false) }
    var storeName by remember { mutableStateOf("Ponto Doce") }
    var instagramUrl by remember { mutableStateOf("@pontodoce_mj") }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Variável para guardar qual pedido está aguardando as permissões de GPS
    var orderToStartDelivery by remember { mutableStateOf<String?>(null) }
    
    // Lista de Pedidos agora é dinâmica para receber os eventos via WebSocket
    var orders by remember {
        mutableStateOf(
            listOf(
                Order("1", "Maria Silva", "36,00", "Rua das Flores, 123 - Centro", "PIX"),
                Order("2", "João Pedro", "12,00", "Av. Brasil, 456 - Jd. Paulista", "DINHEIRO")
            )
        )
    }

    // Launcher para pedir as permissões de GPS (e Notificações no Android 13+)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || 
                              permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        if (locationGranted) {
            orderToStartDelivery?.let { orderId ->
                // Iniciar o Serviço de GPS em background
                val intent = Intent(context, LocationTrackingService::class.java).apply {
                    putExtra("ORDER_ID", orderId)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                
                // Limpar variável
                orderToStartDelivery = null
                
                // Atualizar o status do pedido na API (Avisa o cliente pelo Zap que saiu)
                coroutineScope.launch {
                    try {
                        RetrofitClient.api.updateOrderStatus(orderId, UpdateOrderStatusRequest("DELIVERING"))
                        snackbarHostState.showSnackbar("Rota Iniciada! O cliente recebeu o link do mapa.")
                    } catch (e: Exception) {
                        e.printStackTrace()
                        snackbarHostState.showSnackbar("Erro ao atualizar o servidor.")
                    }
                }
            }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("É necessário habilitar o GPS para iniciar a entrega.")
            }
        }
    }

    // Buscar o status da loja e ouvir novos pedidos (WebSocket)
    LaunchedEffect(Unit) {
        // HTTP GET: Status Inicial
        try {
            val response = RetrofitClient.api.getStoreStatus()
            if (response.isSuccessful && response.body() != null) {
                val status = response.body()!!
                isOpen = status.is_open
                status.store_name?.let { storeName = it }
                status.instagram_url?.let { instagramUrl = it }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Conexão WebSocket para receber alertas de NOVO PEDIDO em tempo real
        try {
            val socket: Socket = IO.socket("http://192.168.15.52:8888")
            socket.connect()
            
            socket.on("admin:new_order") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    val newOrder = Order(
                        id = data.optString("id"),
                        customerName = data.optString("customer_name"),
                        totalAmount = data.optString("total_amount"),
                        address = data.optString("delivery_address"),
                        paymentMethod = data.optString("payment_method", "PIX").uppercase()
                    )
                    
                    // Adiciona o novo pedido no topo da lista
                    orders = listOf(newOrder) + orders
                    
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("🔔 NOVO PEDIDO RECEBIDO!")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // Lista pré-cadastrada de sabores baseada no flyer
    var inventoryList by remember {
        mutableStateOf(
            listOf(
                ProductItem("1", "Brigadeiro com coco", false, 0),
                ProductItem("2", "Doce de leite com morango", false, 0),
                ProductItem("3", "Maracujá com creme", false, 0),
                ProductItem("4", "Limão com chocolate", false, 0)
            )
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = LightPinkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "$storeName — Gestão",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = BrownText
            )
            Text(
                text = instagramUrl,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = PinkAccent,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Card de Status da Loja
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Loja Aberta para Vendas?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = BrownText
                    )
                    Switch(
                        checked = isOpen,
                        onCheckedChange = { isChecked ->
                            isOpen = isChecked
                            coroutineScope.launch {
                                try {
                                    val response = RetrofitClient.api.toggleStore(ToggleStoreRequest(isChecked))
                                    if (!response.isSuccessful) {
                                        isOpen = !isChecked // reverter em caso de falha
                                        snackbarHostState.showSnackbar("Erro ao alterar o status na nuvem.")
                                    }
                                } catch (e: Exception) {
                                    isOpen = !isChecked
                                    snackbarHostState.showSnackbar("Erro de conexão. Verifique a internet.")
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PinkAccent,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.LightGray
                        )
                    )
                }
            }

            // Botão para Gerenciar o Estoque (Pronta Entrega)
            Button(
                onClick = { showInventoryDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PinkAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Gerenciar Pronta Entrega",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Text(
                text = "Fila de Pedidos",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = BrownText,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Lista de Pedidos
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(orders) { order ->
                    OrderCard(
                        order = order,
                        onStartDelivery = {
                            orderToStartDelivery = order.id
                            
                            // Monta a lista de permissões necessárias para ligar o mapa
                            val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            } else {
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            }
                            permissionLauncher.launch(permissionsToRequest)
                        },
                        onFinishDelivery = {
                            // Parar o serviço de GPS em background
                            val intent = Intent(context, LocationTrackingService::class.java)
                            context.stopService(intent)
                            
                            // Avisar ao servidor que concluiu
                            coroutineScope.launch {
                                try {
                                    RetrofitClient.api.updateOrderStatus(order.id, UpdateOrderStatusRequest("COMPLETED"))
                                    // Remove o pedido da lista local
                                    orders = orders.filter { it.id != order.id }
                                    snackbarHostState.showSnackbar("Entrega finalizada com sucesso!")
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showInventoryDialog) {
        InventoryDialog(
            initialList = inventoryList,
            onDismiss = { showInventoryDialog = false },
            onSave = { updatedList ->
                inventoryList = updatedList
                showInventoryDialog = false
                
                coroutineScope.launch {
                    try {
                        val request = com.example.myapplication.network.UpdateInventoryRequest(updatedList)
                        val response = RetrofitClient.api.updateInventory(request)
                        
                        if (response.isSuccessful) {
                            snackbarHostState.showSnackbar("Estoque atualizado com sucesso!")
                        } else {
                            snackbarHostState.showSnackbar("Falha ao salvar no servidor (Código ${response.code()})")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        snackbarHostState.showSnackbar("Erro de conexão. O servidor está offline?")
                    }
                }
            }
        )
    }
}

@Composable
fun OrderCard(
    order: Order,
    onStartDelivery: () -> Unit,
    onFinishDelivery: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "${order.customerName} (R$ ${order.totalAmount})",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = BrownText
            )
            Text(
                text = order.address,
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            
            // Destaque do Método de Pagamento (Somente PIX ou Dinheiro)
            if (order.paymentMethod == "PIX") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE8F5E9)) // Verde clarinho
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💰 PIX - Confira o recibo no PicPay!",
                        color = Color(0xFF2E7D32), // Verde escuro
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            } else { // Se não é PIX, é DINHEIRO
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFF3E0)) // Laranja clarinho
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💵 Pagamento na Entrega (Dinheiro)",
                        color = Color(0xFFE65100), // Laranja escuro
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = onStartDelivery,
                    colors = ButtonDefaults.buttonColors(containerColor = PinkAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Text("Separar / Iniciar Rota", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                
                Button(
                    onClick = onFinishDelivery,
                    colors = ButtonDefaults.buttonColors(containerColor = GreenButton),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Concluir", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ... Restante do código (InventoryDialog e Previews permanecem inalterados) ...

@Composable
fun InventoryDialog(
    initialList: List<ProductItem>,
    onDismiss: () -> Unit,
    onSave: (List<ProductItem>) -> Unit
) {
    var currentList by remember { mutableStateOf(initialList) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LightPinkBackground,
        title = {
            Text(
                text = "Sabores Disponíveis",
                color = BrownText,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(currentList.size) { index ->
                    val product = currentList[index]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = product.name,
                                color = BrownText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            
                            // Se disponível, exibe controle de quantidade
                            if (product.isAvailable) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (product.quantity > 0) {
                                                val newList = currentList.toMutableList()
                                                newList[index] = product.copy(quantity = product.quantity - 1)
                                                currentList = newList
                                            }
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(PinkAccent.copy(alpha = 0.2f))
                                    ) {
                                        Text("-", fontSize = 20.sp, color = PinkAccent, fontWeight = FontWeight.Bold)
                                    }
                                    
                                    Text(
                                        text = "${product.quantity} unid.",
                                        fontSize = 16.sp,
                                        color = BrownText,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                    
                                    IconButton(
                                        onClick = {
                                            val newList = currentList.toMutableList()
                                            newList[index] = product.copy(quantity = product.quantity + 1)
                                            currentList = newList
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(PinkAccent)
                                    ) {
                                        Text("+", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Switch para Disponível/Indisponível
                        Switch(
                            checked = product.isAvailable,
                            onCheckedChange = { isChecked ->
                                val newList = currentList.toMutableList()
                                val newQuantity = if (isChecked) (if (product.quantity > 0) product.quantity else 1) else 0
                                newList[index] = product.copy(isAvailable = isChecked, quantity = newQuantity)
                                currentList = newList
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PinkAccent,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.LightGray
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(currentList) },
                colors = ButtonDefaults.buttonColors(containerColor = GreenButton),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Salvar Estoque", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = PinkAccent, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun PontoDocePreview() {
    MyApplicationTheme {
        PontoDoceAdminApp()
    }
}