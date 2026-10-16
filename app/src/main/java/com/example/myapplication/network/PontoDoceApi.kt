package com.example.myapplication.network

import com.example.myapplication.model.StoreStatus
import com.example.myapplication.model.StoreStatusUpdateResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.DELETE
import retrofit2.http.Path

// Requisição para atualizar o status da loja
data class ToggleStoreRequest(val is_open: Boolean)

// Requisição para atualizar o status de um pedido
data class UpdateOrderStatusRequest(val status: String)

// Requisição para atualizar o estoque de pronta entrega
data class UpdateInventoryRequest(val items: List<com.example.myapplication.ProductItem>)

interface PontoDoceApi {

    @GET("/store/status")
    suspend fun getStoreStatus(): Response<StoreStatus>

    @POST("/admin/store/toggle")
    suspend fun toggleStore(@Body request: ToggleStoreRequest): Response<StoreStatusUpdateResponse>

    @GET("/admin/products")
    suspend fun getAdminProducts(): Response<List<com.example.myapplication.ProductItem>>

    @PATCH("/admin/orders/{id}/status")
    suspend fun updateOrderStatus(
        @Path("id") orderId: String,
        @Body request: UpdateOrderStatusRequest
    ): Response<Any>
    
    @POST("/admin/inventory/update")
    suspend fun updateInventory(@Body request: UpdateInventoryRequest): Response<Any>

    @DELETE("/admin/products/{id}")
    suspend fun deleteProduct(@Path("id") id: String): Response<Any>
}
