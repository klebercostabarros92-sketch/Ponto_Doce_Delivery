package com.example.myapplication.model

data class StoreStatus(
    val is_open: Boolean,
    val closed_message: String? = null,
    val store_name: String? = null,
    val instagram_url: String? = null
)

data class StoreStatusUpdateResponse(
    val success: Boolean,
    val is_open: Boolean
)
