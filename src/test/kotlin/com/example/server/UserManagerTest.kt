package com.example.server

import com.example.models.User
import org.junit.jupiter.api.Assertions.*
import kotlin.test.BeforeTest
import kotlin.test.Test

class UserManagerTest {
    @BeforeTest
    fun setup() {
        // Reset to initial state
        UserManager.apply {
            clearUsers()
            addUsers(
                listOf(
                    User("shachar", "1234"),
                    User("shachar2", "1234"),
                    User("shachar3", "1234"),
                    User("shachar4", "1234")
                )
            )
        }
    }

    @Test
    fun `test check user`() {
        assertTrue(UserManager.doesUserExist("shachar"))
        assertFalse(UserManager.doesUserExist("shachar5"))
    }

    @Test
    fun `test add user`() {
        UserManager.addUser("testuser", "password")
        assertTrue(UserManager.doesUserExist("testuser"))
    }

    @Test
    fun `test remove user`() {
        UserManager.removeUser("shachar")
        assertFalse(UserManager.doesUserExist("shachar"))
    }

    @Test
    fun `test credentials`() {
        assertTrue(UserManager.areCredentialsValid("shachar", "1234"))
        assertFalse(UserManager.areCredentialsValid("shachar", "wrong"))
    }

    @Test
    fun `test clear users`() {
        UserManager.clearUsers()
        assertFalse(UserManager.doesUserExist("shachar"))
    }
}