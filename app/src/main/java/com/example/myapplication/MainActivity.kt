package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
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

// Cores originais claras (baseadas no flyer)
val BrownText = Color(0xFF5A3219)
val PinkAccent = Color(0xFFF25C78)
val LightPinkBackground = Color(0xFFFFE4E1)
val CardBackground = Color(0xFFFFFFFF)

// Cores extraídas do novo logotipo (Azul Marinho e Dourado)
val NavyBackground = Color(0xFF0A1F3A)     // Fundo principal escuro
val NavyCard = Color(0xFF112B4F)           // Fundo dos cartões e modais
val GoldAccent = Color(0xFFE5B951)         // Dourado do texto principal e ícones
val LightGold = Color(0xFFFDECB5)          // Dourado clarinho para detalhes e textos secundários
val WhiteText = Color(0xFFFFFFFF)

// Cores de utilidade compartilhadas
val GreenButton = Color(0xFF2E7D32)        // Verde escuro elegante
val RedButton = Color(0xFFC62828)          // Vermelho escuro elegante
val DialogBackgroundDark = Color(0xFF0F2645)   // Fundo da janela pop-up escuro

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

// Modelo de Dados do Produto no Estoque agora suporta FOTO
data class ProductItem(
    val id: String, 
    val name: String, 
    val isAvailable: Boolean, 
    val quantity: Int,
    val photoUri: String? = null
)

@Composable
fun PontoDoceAdminApp() {
    val context = LocalContext.current
    var isOpen by remember { mutableStateOf(false) }
    var showInventoryDialog by remember { mutableStateOf(false) }
    var storeName by remember { mutableStateOf("Ponto Doce") }
    var instagramUrl by remember { mutableStateOf("@pontodoce_mj") }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Toggle de Tema (O usuário agora controla se quer Claro ou Escuro (Premium))
    var isDarkTheme by remember { mutableStateOf(true) } // Padrão: Tema Premium Marinho/Dourado

    // Variável para guardar qual pedido está aguardando as permissões de GPS
    var orderToStartDelivery by remember { mutableStateOf<String?>(null) }
    
    // Lista de Pedidos
    var orders by remember {
        mutableStateOf(
            listOf(
                Order("1", "Maria Silva", "36,00", "Rua das Flores, 123 - Centro", "PIX"),
                Order("2", "João Pedro", "12,00", "Av. Brasil, 456 - Jd. Paulista", "DINHEIRO")
            )
        )
    }

    // Lista de Produtos do Estoque
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

    // Launcher para pedir as permissões de GPS
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || 
                              permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        if (locationGranted) {
            orderToStartDelivery?.let { orderId ->
                val intent = Intent(context, LocationTrackingService::class.java).apply {
                    putExtra("ORDER_ID", orderId)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                
                orderToStartDelivery = null
                
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

        // Buscar a lista real de produtos cadastrados no servidor
        try {
            val prodResponse = RetrofitClient.api.getAdminProducts()
            if (prodResponse.isSuccessful && prodResponse.body() != null) {
                val fetched = prodResponse.body()!!
                if (fetched.isNotEmpty()) {
                    inventoryList = fetched
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        try {
            val socket: Socket = IO.socket("http://192.168.15.52:3333")
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

    // Definindo as cores com base no toggle de tema manual
    val bgColor = if (isDarkTheme) NavyBackground else LightPinkBackground
    val cardColor = if (isDarkTheme) NavyCard else CardBackground
    val titleColor = if (isDarkTheme) GoldAccent else BrownText
    val subtitleColor = if (isDarkTheme) LightGold else PinkAccent
    val textColor = if (isDarkTheme) WhiteText else BrownText
    val accentColor = if (isDarkTheme) GoldAccent else PinkAccent

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = bgColor,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Cabeçalho com o botão de Alternar Tema
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "$storeName — Gestão",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor
                    )
                    Text(
                        text = instagramUrl,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = subtitleColor,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }
                
                // Botão Mágico: Alternar Tema
                IconButton(
                    onClick = { isDarkTheme = !isDarkTheme },
                    modifier = Modifier.background(cardColor, RoundedCornerShape(8.dp))
                ) {
                    Text(if (isDarkTheme) "☀️" else "🌙", fontSize = 20.sp)
                }
            }

            // Card de Status da Loja
            Card(
                colors = CardDefaults.cardColors(containerColor = cardColor),
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
                        color = textColor
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
                            checkedTrackColor = GreenButton,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
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
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Gerenciar Pronta Entrega",
                    color = if (isDarkTheme) NavyBackground else Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Text(
                text = "Fila de Pedidos",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Lista de Pedidos
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(orders) { order ->
                    OrderCard(
                        order = order,
                        isDarkTheme = isDarkTheme,
                        onStartDelivery = {
                            orderToStartDelivery = order.id
                            val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                            permissionLauncher.launch(permissionsToRequest)
                        },
                        onFinishDelivery = {
                            val intent = Intent(context, LocationTrackingService::class.java)
                            context.stopService(intent)
                            coroutineScope.launch {
                                try {
                                    RetrofitClient.api.updateOrderStatus(order.id, UpdateOrderStatusRequest("COMPLETED"))
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
            isDarkTheme = isDarkTheme,
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
    isDarkTheme: Boolean,
    onStartDelivery: () -> Unit,
    onFinishDelivery: () -> Unit
) {
    val cardColor = if (isDarkTheme) NavyCard else CardBackground
    val textColor = if (isDarkTheme) WhiteText else BrownText
    val subtitleColor = if (isDarkTheme) LightGold else Color.Gray
    val accentColor = if (isDarkTheme) GoldAccent else PinkAccent
    val accentTextColor = if (isDarkTheme) NavyBackground else Color.White

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
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
                color = textColor
            )
            Text(
                text = order.address,
                fontSize = 14.sp,
                color = subtitleColor,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            
            if (order.paymentMethod == "PIX") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE8F5E9))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💰 PIX - Confira o recibo no PicPay!",
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFF3E0))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💵 Pagamento na Entrega (Dinheiro)",
                        color = Color(0xFFE65100),
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
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Text("Separar / Iniciar Rota", color = accentTextColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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

@Composable
fun InventoryDialog(
    initialList: List<ProductItem>,
    isDarkTheme: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<ProductItem>) -> Unit
) {
    var currentList by remember { mutableStateOf(initialList) }
    var showAddItemDialog by remember { mutableStateOf(false) }

    val bgColor = if (isDarkTheme) DialogBackgroundDark else LightPinkBackground
    val titleColor = if (isDarkTheme) GoldAccent else BrownText
    val textColor = if (isDarkTheme) WhiteText else BrownText
    val subtitleColor = if (isDarkTheme) LightGold else BrownText
    val cardColor = if (isDarkTheme) NavyCard else PinkAccent.copy(alpha = 0.2f)
    val accentColor = if (isDarkTheme) GoldAccent else PinkAccent
    val accentTextColor = if (isDarkTheme) NavyBackground else Color.White
    val dismissColor = if (isDarkTheme) RedButton else PinkAccent

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = bgColor,
        title = {
            Text(
                text = "Gerenciar Estoque",
                color = titleColor,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column {
                Button(
                    onClick = { showAddItemDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("+ Novo Produto", color = accentTextColor, fontWeight = FontWeight.Bold)
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.heightIn(max = 300.dp)
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
                                    color = textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                
                                // Indicador de foto anexada
                                if (product.photoUri != null) {
                                    Text(
                                        text = "📷 Foto Anexada",
                                        color = GreenButton,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                
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
                                                .background(cardColor)
                                        ) {
                                            Text("-", fontSize = 20.sp, color = accentColor, fontWeight = FontWeight.Bold)
                                        }
                                        
                                        Text(
                                            text = "${product.quantity} unid.",
                                            fontSize = 16.sp,
                                            color = subtitleColor,
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
                                                .background(accentColor)
                                        ) {
                                            Text("+", fontSize = 20.sp, color = accentTextColor, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

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
                                    checkedTrackColor = GreenButton,
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.DarkGray
                                )
                            )
                        }
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
                Text("Cancelar", color = dismissColor, fontWeight = FontWeight.Bold)
            }
        }
    )

    // Dialog secundário para adicionar NOVO ITEM
    if (showAddItemDialog) {
        var newName by remember { mutableStateOf("") }
        var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
        
        // Inicializa o seletor de fotos da galeria do celular
        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            selectedImageUri = uri
        }

        val inputBg = if (isDarkTheme) NavyCard else Color.White

        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            containerColor = inputBg,
            title = { Text("Cadastrar Novo Produto", color = titleColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nome do Produto / Sabor", color = subtitleColor) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            focusedLabelColor = accentColor,
                            unfocusedBorderColor = Color.Gray,
                            unfocusedTextColor = textColor,
                            focusedTextColor = textColor
                        )
                    )
                    
                    // Botão para abrir a galeria
                    Button(
                        onClick = { 
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) 
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedImageUri != null) GreenButton else Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (selectedImageUri != null) "📸 Foto Selecionada ✓" else "📷 Anexar Foto da Galeria",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            val newItem = ProductItem(
                                id = "new_${System.currentTimeMillis()}",
                                name = newName,
                                isAvailable = false,
                                quantity = 0,
                                photoUri = selectedImageUri?.toString() // Salva o endereço da foto
                            )
                            currentList = currentList + newItem
                            showAddItemDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("Adicionar à Lista", color = accentTextColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemDialog = false }) {
                    Text("Cancelar", color = dismissColor, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PontoDocePreview() {
    MyApplicationTheme {
        PontoDoceAdminApp()
    }
}