package com.miladtak.japo.segmentation

interface PersonSegmenter {
    suspend fun segment(frame: Any): Any
}
