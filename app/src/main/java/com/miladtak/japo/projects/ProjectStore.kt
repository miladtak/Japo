package com.miladtak.japo.projects

interface ProjectStore { fun save(id:String,json:String); fun load(id:String):String? }