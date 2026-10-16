package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.util.Base64
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.network.RetrofitClient
import com.example.myapplication.network.ToggleStoreRequest
import com.example.myapplication.network.UpdateOrderStatusRequest
import com.example.myapplication.ui.theme.MyApplicationTheme
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream

// --- FUNÇÃO AUXILIAR DE COMPRESSÃO DE FOTOS (Inserida por você) ---
fun compressAndEncodeImage(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        val maxDimension = 800
        val width = originalBitmap.width
        val height = originalBitmap.height
        val ratio = minOf(1.0, maxDimension.toDouble() / maxOf(width, height))
        val targetWidth = (width * ratio).toInt()
        val targetHeight = (height * ratio).toInt()

        val scaledBitmap = if (ratio < 1.0) {
            Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
        } else {
            originalBitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        val byteArray = outputStream.toByteArray()
        val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
        "data:image/jpeg;base64,$base64"
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// --- FUNÇÃO DE IMPRESSÃO DE PDF VIA WI-FI ---
fun printOrderReceipt(context: Context, order: Order) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val jobName = "Pedido_PontoDoce_${order.id}"

    val printAdapter = object : PrintDocumentAdapter() {
        var pdfDocument: PrintedPdfDocument? = null

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: android.os.CancellationSignal?,
            callback: LayoutResultCallback?,
            extras: Bundle?
        ) {
            pdfDocument = PrintedPdfDocument(context, newAttributes)
            if (cancellationSignal?.isCanceled == true) {
                callback?.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(jobName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build()
            callback?.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out android.print.PageRange>?,
            destination: android.os.ParcelFileDescriptor,
            cancellationSignal: android.os.CancellationSignal?,
            callback: WriteResultCallback?
        ) {
            val page = pdfDocument?.startPage(1)
            val canvas = page?.canvas
            val paint = Paint().apply {
                textSize = 14f
                color = android.graphics.Color.BLACK
            }
            val titlePaint = Paint().apply {
                textSize = 18f
                isFakeBoldText = true
                color = android.graphics.Color.BLACK
            }

            canvas?.drawText("PONTO DOCE - RECIBO DE ENTREGA", 30f, 50f, titlePaint)
            canvas?.drawText("================================", 30f, 70f, paint)
            canvas?.drawText("Cliente: ${order.customerName}", 30f, 100f, paint)
            canvas?.drawText("Endereço: ${order.address}", 30f, 130f, paint)
            canvas?.drawText("Valor Total: R$ ${order.totalAmount}", 30f, 160f, paint)
            canvas?.drawText("Pagamento: ${order.paymentMethod}", 30f, 190f, paint)
            canvas?.drawText("================================", 30f, 220f, paint)
            canvas?.drawText("Obrigado pela preferência!", 30f, 250f, paint)

            pdfDocument?.finishPage(page)

            try {
                pdfDocument?.writeTo(FileOutputStream(destination.fileDescriptor))
            } catch (e: Exception) {
                e.printStackTrace()
            }

            pdfDocument?.close()
            pdfDocument = null
            callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
        }
    }

    printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
}

// Cores originais claras
val BrownText = Color(0xFF5A3219)
val PinkAccent = Color(0xFFF25C78)
val LightPinkBackground = Color(0xFFFFE4E1)
val CardBackground = Color(0xFFFFFFFF)

// Cores extraídas do novo logotipo (Azul Marinho e Dourado)
val NavyBackground = Color(0xFF0A1F3A)
val NavyCard = Color(0xFF112B4F)
val GoldAccent = Color(0xFFE5B951)
val LightGold = Color(0xFFFDECB5)
val WhiteText = Color(0xFFFFFFFF)

// Cores de utilidade
val GreenButton = Color(0xFF2E7D32)
val RedButton = Color(0xFFC62828)

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

// Modelos de Dados
data class Order(
    val id: String,
    val customerName: String,
    val totalAmount: String,
    val address: String,
    val paymentMethod: String
)

data class ProductItem(
    val id: String,
    val name: String,
    val description: String = "",
    val price: Double = 0.0,
    val isAvailable: Boolean,
    val quantity: Int,
    val photoUri: String? = null
)

@Composable
fun PontoDoceAdminApp() {
    val context = LocalContext.current
    var isOpen by remember { mutableStateOf(false) }
    var storeName by remember { mutableStateOf("Ponto Doce") }
    var instagramUrl by remember { mutableStateOf("@pontodoce_mj") }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Estado da Barra Inferior (Bottom Navigation)
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Pedidos, 1 = Estoque, 2 = Dashboard

    // Tema
    var isDarkTheme by remember { mutableStateOf(true) }

    // Rastreio de Horas Abertas na Sessão
    var openTimeMillis by remember { mutableLongStateOf(0L) }
    var lastOpenTimestamp by remember { mutableLongStateOf(0L) }
    var liveSessionTime by remember { mutableLongStateOf(0L) }

    // Variável para permissões de GPS
    var orderToStartDelivery by remember { mutableStateOf<String?>(null) }
    
    // Lista Limpa de Pedidos e Histórico
    var orders by remember { mutableStateOf(emptyList<Order>()) }
    var completedOrders by remember { mutableStateOf(emptyList<Order>()) }

    // Lista Limpa de Estoque
    var inventoryList by remember { mutableStateOf(emptyList<ProductItem>()) }

    // Launcher de Permissões
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
                        snackbarHostState.showSnackbar("Rota Iniciada! O cliente recebeu o link.")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Erro ao atualizar o servidor.")
                    }
                }
            }
        } else {
            coroutineScope.launch { snackbarHostState.showSnackbar("GPS é necessário para entrega.") }
        }
    }

    // Controle do Tempo de Loja Aberta
    LaunchedEffect(isOpen) {
        if (isOpen) {
            lastOpenTimestamp = System.currentTimeMillis()
            while(isOpen) {
                liveSessionTime = System.currentTimeMillis() - lastOpenTimestamp
                delay(60000) // Atualiza a tela a cada 1 minuto
            }
        } else {
            if (lastOpenTimestamp > 0) {
                openTimeMillis += (System.currentTimeMillis() - lastOpenTimestamp)
                lastOpenTimestamp = 0L
                liveSessionTime = 0L
            }
        }
    }

    // Conexões Iniciais
    LaunchedEffect(Unit) {
        try {
            val response = RetrofitClient.api.getStoreStatus()
            if (response.isSuccessful && response.body() != null) {
                val status = response.body()!!
                isOpen = status.is_open
                status.store_name?.let { storeName = it }
                status.instagram_url?.let { instagramUrl = it }
            }
        } catch (e: Exception) { e.printStackTrace() }

        try {
            val prodResponse = RetrofitClient.api.getAdminProducts()
            if (prodResponse.isSuccessful && prodResponse.body() != null) {
                inventoryList = prodResponse.body()!!
            }
        } catch (e: Exception) { e.printStackTrace() }
        
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
                    coroutineScope.launch { snackbarHostState.showSnackbar("🔔 NOVO PEDIDO RECEBIDO!") }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    val bgColor = if (isDarkTheme) NavyBackground else LightPinkBackground
    val cardColor = if (isDarkTheme) NavyCard else CardBackground
    val titleColor = if (isDarkTheme) GoldAccent else BrownText
    val subtitleColor = if (isDarkTheme) LightGold else PinkAccent

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = bgColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = cardColor) {
                NavigationBarItem(
                    icon = { Text("📦", fontSize = 24.sp) },
                    label = { Text("Pedidos", color = if(selectedTab==0) titleColor else Color.Gray) },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = bgColor)
                )
                NavigationBarItem(
                    icon = { Text("🧁", fontSize = 24.sp) },
                    label = { Text("Estoque", color = if(selectedTab==1) titleColor else Color.Gray) },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = bgColor)
                )
                NavigationBarItem(
                    icon = { Text("📊", fontSize = 24.sp) },
                    label = { Text("Relatórios", color = if(selectedTab==2) titleColor else Color.Gray) },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = bgColor)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // CABEÇALHO GERAL (Presente em todas as abas)
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
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                IconButton(
                    onClick = { isDarkTheme = !isDarkTheme },
                    modifier = Modifier.background(cardColor, RoundedCornerShape(8.dp))
                ) {
                    Text(if (isDarkTheme) "☀️" else "🌙", fontSize = 20.sp)
                }
            }

            // NAVEGAÇÃO ENTRE ABAS
            when (selectedTab) {
                0 -> PedidosTab(
                    isOpen = isOpen,
                    onToggleStore = { isChecked ->
                        isOpen = isChecked
                        coroutineScope.launch {
                            try { RetrofitClient.api.toggleStore(ToggleStoreRequest(isChecked)) } 
                            catch (e: Exception) { isOpen = !isChecked }
                        }
                    },
                    orders = orders,
                    isDarkTheme = isDarkTheme,
                    onStartDelivery = { order ->
                        orderToStartDelivery = order.id
                        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                        permissionLauncher.launch(perms)
                    },
                    onFinishDelivery = { order ->
                        val intent = Intent(context, LocationTrackingService::class.java)
                        context.stopService(intent)
                        coroutineScope.launch {
                            try {
                                RetrofitClient.api.updateOrderStatus(order.id, UpdateOrderStatusRequest("COMPLETED"))
                                orders = orders.filter { it.id != order.id }
                                completedOrders = completedOrders + order // Adiciona ao histórico!
                                snackbarHostState.showSnackbar("Entrega finalizada com sucesso!")
                            } catch (e: Exception) {}
                        }
                    },
                    onPrintOrder = { order ->
                        printOrderReceipt(context, order)
                    }
                )
                1 -> EstoqueTab(
                    inventoryList = inventoryList,
                    isDarkTheme = isDarkTheme,
                    onSave = { updatedList ->
                        inventoryList = updatedList
                        coroutineScope.launch {
                            try {
                                val request = com.example.myapplication.network.UpdateInventoryRequest(updatedList)
                                val response = RetrofitClient.api.updateInventory(request)
                                if (response.isSuccessful) snackbarHostState.showSnackbar("Estoque atualizado!")
                            } catch (e: Exception) { snackbarHostState.showSnackbar("Erro de conexão.") }
                        }
                    }
                )
                2 -> RelatoriosTab(
                    completedOrders = completedOrders,
                    isDarkTheme = isDarkTheme,
                    totalTimeOpenMillis = openTimeMillis + liveSessionTime
                )
            }
        }
    }
}

// ---------------------------------------------------------
// ABA 1: PEDIDOS
// ---------------------------------------------------------
@Composable
fun PedidosTab(
    isOpen: Boolean,
    onToggleStore: (Boolean) -> Unit,
    orders: List<Order>,
    isDarkTheme: Boolean,
    onStartDelivery: (Order) -> Unit,
    onFinishDelivery: (Order) -> Unit,
    onPrintOrder: (Order) -> Unit
) {
    val cardColor = if (isDarkTheme) NavyCard else CardBackground
    val textColor = if (isDarkTheme) WhiteText else BrownText
    val titleColor = if (isDarkTheme) GoldAccent else BrownText

    Column {
        Card(
            colors = CardDefaults.cardColors(containerColor = cardColor),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Loja Aberta para Vendas?", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = textColor)
                Switch(
                    checked = isOpen,
                    onCheckedChange = onToggleStore,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White, checkedTrackColor = GreenButton,
                        uncheckedThumbColor = Color.Gray, uncheckedTrackColor = Color.DarkGray
                    )
                )
            }
        }

        Text("Fila de Pedidos", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = titleColor, modifier = Modifier.padding(bottom = 12.dp))

        if (orders.isEmpty()) {
            Text("Nenhum pedido na fila. Aguardando clientes...", color = Color.Gray, modifier = Modifier.padding(top = 20.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(orders) { order ->
                    OrderCard(order, isDarkTheme, { onStartDelivery(order) }, { onFinishDelivery(order) }, { onPrintOrder(order) })
                }
            }
        }
    }
}

@Composable
fun OrderCard(
    order: Order,
    isDarkTheme: Boolean,
    onStartDelivery: () -> Unit,
    onFinishDelivery: () -> Unit,
    onPrintOrder: () -> Unit
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${order.customerName} (R$ ${order.totalAmount})", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
                // BOTÃO DE IMPRIMIR PDF
                IconButton(
                    onClick = onPrintOrder,
                    modifier = Modifier.size(32.dp).background(Color.LightGray.copy(alpha=0.3f), RoundedCornerShape(8.dp))
                ) {
                    Text("🖨️", fontSize = 16.sp)
                }
            }
            Text(order.address, fontSize = 14.sp, color = subtitleColor, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
            
            if (order.paymentMethod == "PIX") {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE8F5E9)).padding(12.dp)) {
                    Text("💰 PIX - Confira o recibo no PicPay!", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFF3E0)).padding(12.dp)) {
                    Text("💵 Pagamento na Entrega (Dinheiro)", color = Color(0xFFE65100), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = onStartDelivery, colors = ButtonDefaults.buttonColors(containerColor = accentColor), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Iniciar Rota", color = accentTextColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Button(onClick = onFinishDelivery, colors = ButtonDefaults.buttonColors(containerColor = GreenButton), shape = RoundedCornerShape(8.dp)) {
                    Text("Concluir", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------------------------------------------------------
// ABA 2: ESTOQUE E PRODUTOS (Substituiu o Modal Dialog)
// ---------------------------------------------------------
@Composable
fun EstoqueTab(
    inventoryList: List<ProductItem>,
    isDarkTheme: Boolean,
    onSave: (List<ProductItem>) -> Unit
) {
    var currentList by remember { mutableStateOf(inventoryList) }
    var showAddItemDialog by remember { mutableStateOf(false) }

    val textColor = if (isDarkTheme) WhiteText else BrownText
    val subtitleColor = if (isDarkTheme) LightGold else BrownText
    val cardColor = if (isDarkTheme) NavyCard else PinkAccent.copy(alpha = 0.2f)
    val accentColor = if (isDarkTheme) GoldAccent else PinkAccent
    val accentTextColor = if (isDarkTheme) NavyBackground else Color.White

    // Atualiza a lista caso chegue da API
    LaunchedEffect(inventoryList) {
        if (currentList.isEmpty() && inventoryList.isNotEmpty()) {
            currentList = inventoryList
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
            modifier = Modifier.weight(1f)
        ) {
            items(currentList.size) { index ->
                val product = currentList[index]
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${product.name} (R$ ${String.format("%.2f", product.price).replace('.', ',')})", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (product.description.isNotBlank()) {
                            Text(product.description, color = subtitleColor, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (product.photoUri != null) {
                            Text("📷 Foto Anexada", color = GreenButton, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                        }
                        
                        if (product.isAvailable) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                IconButton(
                                    onClick = {
                                        if (product.quantity > 0) {
                                            val newQty = product.quantity - 1
                                            val isAvail = newQty > 0
                                            val newList = currentList.toMutableList()
                                            newList[index] = product.copy(quantity = newQty, isAvailable = isAvail)
                                            currentList = newList
                                        }
                                    },
                                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(cardColor)
                                ) { Text("-", fontSize = 20.sp, color = accentColor, fontWeight = FontWeight.Bold) }
                                
                                Text("${product.quantity} unid.", fontSize = 16.sp, color = subtitleColor, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 12.dp))
                                
                                IconButton(
                                    onClick = {
                                        val newList = currentList.toMutableList()
                                        newList[index] = product.copy(quantity = product.quantity + 1)
                                        currentList = newList
                                    },
                                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(accentColor)
                                ) { Text("+", fontSize = 20.sp, color = accentTextColor, fontWeight = FontWeight.Bold) }
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
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = GreenButton)
                    )
                }
            }
        }

        Button(
            onClick = { onSave(currentList) },
            colors = ButtonDefaults.buttonColors(containerColor = GreenButton),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Salvar Estoque na Nuvem", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
        }
    }

    if (showAddItemDialog) {
        var newName by remember { mutableStateOf("") }
        var newDesc by remember { mutableStateOf("") }
        var newPrice by remember { mutableStateOf("") }
        var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
        val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> selectedImageUri = uri }

        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            containerColor = if (isDarkTheme) NavyCard else Color.White,
            title = { Text("Cadastrar Produto", color = if(isDarkTheme) GoldAccent else BrownText, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Sabor", color = subtitleColor) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    item { OutlinedTextField(value = newDesc, onValueChange = { newDesc = it }, label = { Text("Descrição", color = subtitleColor) }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                    item { OutlinedTextField(value = newPrice, onValueChange = { newPrice = it }, label = { Text("Preço", color = subtitleColor) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()) }
                    item {
                        Button(
                            onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (selectedImageUri != null) GreenButton else Color.Gray),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)
                        ) { Text(if (selectedImageUri != null) "📸 Foto Selecionada ✓" else "📷 Anexar Foto da Galeria", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        val priceParsed = newPrice.replace(",", ".").toDoubleOrNull() ?: 0.0
                        val newItem = ProductItem("new_${System.currentTimeMillis()}", newName, newDesc, priceParsed, false, 0, selectedImageUri?.toString())
                        currentList = currentList + newItem
                        showAddItemDialog = false
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = accentColor)) { Text("Adicionar", color = accentTextColor) }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemDialog = false }) { Text("Cancelar", color = RedButton) }
            }
        )
    }
}

// ---------------------------------------------------------
// ABA 3: DASHBOARD E RELATÓRIOS
// ---------------------------------------------------------
@Composable
fun RelatoriosTab(
    completedOrders: List<Order>,
    isDarkTheme: Boolean,
    totalTimeOpenMillis: Long
) {
    val cardColor = if (isDarkTheme) NavyCard else CardBackground
    val textColor = if (isDarkTheme) WhiteText else BrownText
    val titleColor = if (isDarkTheme) GoldAccent else BrownText
    
    // Cáculo de Faturamento Local
    val totalFaturamento = completedOrders.sumOf { 
        it.totalAmount.replace("R$", "").replace(" ", "").replace(",", ".").toDoubleOrNull() ?: 0.0 
    }

    // Cálculo de Horas Abertas
    val hours = totalTimeOpenMillis / 3600000
    val minutes = (totalTimeOpenMillis % 3600000) / 60000
    val openTimeStr = if (hours > 0) "${hours}h ${minutes}m" else "${minutes} min"

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text("Visão Geral de Hoje", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = titleColor)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = cardColor), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Faturamento", color = Color.Gray, fontSize = 14.sp)
                        Text("R$ ${String.format("%.2f", totalFaturamento)}", color = GreenButton, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = cardColor), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Vendas Concluídas", color = Color.Gray, fontSize = 14.sp)
                        Text("${completedOrders.size}", color = textColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cardColor), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("⏳ Tempo de Loja Aberta (Sessão)", color = Color.Gray, fontSize = 14.sp)
                    Text(openTimeStr, color = textColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Monitoramento ao vivo.", color = GreenButton, fontSize = 12.sp)
                }
            }
        }
        item {
            Text("Histórico de Entregas", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = titleColor, modifier = Modifier.padding(top = 16.dp))
        }
        
        if (completedOrders.isEmpty()) {
            item {
                Text("Nenhuma venda concluída nesta sessão ainda.", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
            }
        } else {
            items(completedOrders) { order ->
                Card(colors = CardDefaults.cardColors(containerColor = cardColor), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("${order.customerName} - R$ ${order.totalAmount}", color = textColor, fontWeight = FontWeight.Bold)
                        Text("Pago via: ${order.paymentMethod}", color = Color.Gray, fontSize = 12.sp)
                        Text("Entregue em: ${order.address}", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
