package com.miladtak.japo.matting

interface MattingProcessor {
    suspend fun refine(alpha: Any): Any
}
