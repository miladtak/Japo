package com.miladtak.japo.logging

data class ErrorLog(val time:Long,val component:String,val message:String,val stackTrace:String?=null)