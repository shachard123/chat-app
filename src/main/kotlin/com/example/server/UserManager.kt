package com.example.server

import com.example.models.User
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import org.bson.Document
import org.bson.types.ObjectId

object UserManager {
    private val client = MongoClients.create("mongodb://localhost:27017")
    private val database: MongoDatabase = client.getDatabase("chatapp")
    private val usersCollection: MongoCollection<Document> = database.getCollection("users")

    private val connectedUsers = mutableSetOf<ObjectId>()

    fun addUser(username: String, password: String) {
        if (!doesUserExist(username)) {
            val user = Document("_id", ObjectId())
                .append("username", username)
                .append("password", password)
            usersCollection.insertOne(user)
        }
    }

    fun removeUser(userId: ObjectId) {
        usersCollection.deleteOne(Document("_id", userId))
        connectedUsers.remove(userId)
    }

    fun areCredentialsValid(username: String, attemptedPassword: String): Boolean {
        val user = usersCollection.find(Document("username", username)).first()
        return user?.getString("password") == attemptedPassword
    }

    fun doesUserExist(username: String): Boolean {
        return usersCollection.find(Document("username", username)).first() != null
    }

    fun clearUsers() {
        usersCollection.deleteMany(Document())
        connectedUsers.clear()
    }

    fun addUsers(newUsers: List<User>) {
        val documents = newUsers.map {
            Document("_id", it._id)
                .append("username", it.username)
                .append("password", it.password)
        }
        usersCollection.insertMany(documents)
    }

    fun isUserConnected(userId: ObjectId): Boolean {
        return connectedUsers.contains(userId)
    }

    fun markUserAsConnected(userId: ObjectId) {
        connectedUsers.add(userId)
    }

    fun markUserAsDisconnected(userId: ObjectId) {
        connectedUsers.remove(userId)
    }

    fun getUserByUsername(username: String): User? {
        val doc = usersCollection.find(Document("username", username)).first() ?: return null
        return User(
            _id = doc.getObjectId("_id"),
            username = doc.getString("username"),
            password = doc.getString("password")
        )
    }

    fun getUserById(userId: ObjectId): User? {
        val doc = usersCollection.find(Document("_id", userId)).first() ?: return null
        return User(
            _id = doc.getObjectId("_id"),
            username = doc.getString("username"),
            password = doc.getString("password")
        )
    }
}
