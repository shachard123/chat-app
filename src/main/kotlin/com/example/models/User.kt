package com.example.models

import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

data class User(
    @BsonId val _id: ObjectId,  // MongoDB ID
    val username: String,
    var password: String
)