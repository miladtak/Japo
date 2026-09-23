package com.miladtak.japo.tracking

interface PersonTracker {
    fun reset()
    fun update(frame: Any): List<Int>
}
