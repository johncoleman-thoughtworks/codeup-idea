package com.codeup.analyzer

fun analysisCacheKey(contentHash: String, catalogueHash: String, model: String, neighborsKey: String = ""): String =
    "$contentHash:$catalogueHash:$model:$neighborsKey"