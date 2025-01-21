package com.example.utils

fun getInput(string: String): String {
    print(string)
    return readlnOrNull() ?: ""
}