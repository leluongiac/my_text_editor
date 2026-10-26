package com.example.mytexteditor.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ExecutionResult(
    val output: String,
    val isError: Boolean,
    val exitCode: Int,
    val executionTimeMs: Long
)

object CodeRunner {
    // Wandbox API - Chuyên dụng cho C/C++ GCC, miễn phí và không cần API Key
    private const val API_URL = "https://wandbox.org/api/compile.json"

    suspend fun runCpp(code: String, stdin: String = ""): ExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val url = URL(API_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                connectTimeout = 10000
                readTimeout = 20000
                doOutput = true
            }

            // Payload chuẩn của Wandbox chạy GCC mới nhất
            val payload = JSONObject().apply {
                put("compiler", "gcc-head") // Phiên bản GCC mới nhất
                put("code", code)
                put("stdin", stdin)
                put("options", "c++23")     // Hỗ trợ cả C++23
            }

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()); it.flush() }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = BufferedReader(InputStreamReader(stream ?: conn.inputStream)).use { it.readText() }

            val elapsed = System.currentTimeMillis() - startTime
            val json = JSONObject(responseText)

            val status = json.optString("status", "0")
            val compilerError = json.optString("compiler_error")
            val programMessage = json.optString("program_message")
            val programError = json.optString("program_error")
            val programOutput = json.optString("program_output")

            // 1. Nếu có lỗi biên dịch (Compile Error từ GCC)
            if (compilerError.isNotEmpty() && status != "0") {
                return@withContext ExecutionResult(
                    output = compilerError,
                    isError = true,
                    exitCode = status.toIntOrNull() ?: 1,
                    executionTimeMs = elapsed
                )
            }

            // 2. Kết quả chạy bình thường
            val finalOutput = when {
                programOutput.isNotEmpty() -> programOutput
                programMessage.isNotEmpty() -> programMessage
                compilerError.isNotEmpty() -> "[Warnings]:\n$compilerError\n$programMessage"
                programError.isNotEmpty() -> "[Stderr]:\n$programError"
                else -> "[Chương trình kết thúc (không có output)]"
            }

            val isSuccess = status == "0"
            return@withContext ExecutionResult(
                output = finalOutput,
                isError = !isSuccess,
                exitCode = status.toIntOrNull() ?: 0,
                executionTimeMs = elapsed
            )

        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            ExecutionResult("Lỗi kết nối: ${e.localizedMessage ?: "Timeout"}", true, -1, elapsed)
        }
    }
}