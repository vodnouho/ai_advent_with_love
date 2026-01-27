package com.fasterxml.jackson.module.kotlin

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

object JacksonModule {
    fun createMapper(): ObjectMapper = ObjectMapper().registerKotlinModule()
}
