package com.example.myapplication.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // Substitua pela URL do seu servidor backend quando estiver online
    // Apontando para o IP real do servidor na rede local (Porta 3333 original)
    private const val BASE_URL = "http://192.168.15.52:3333/"

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: PontoDoceApi by lazy {
        retrofit.create(PontoDoceApi::class.java)
    }
}
