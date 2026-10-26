package com.example.mytexteditor.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
    private const val API_URL = "https://emkc.org/api/v2/piston/execute"

    suspend fun runCpp(code: String, stdin: String = ""): ExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val url = URL(API_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10000
                readTimeout = 15000
                doOutput = true
            }

            // DÙNG version: "*" ĐỂ PISTON TỰ ĐỘNG CHỌN BẢN GCC MỚI NHẤT
            val payload = JSONObject().apply {
                put("language", "c++")
                put("version", "*")
                put("stdin", stdin)
                val fileObj = JSONObject().apply {
                    put("name", "main.cpp")
                    put("content", code)
                }
                put("files", JSONArray().put(fileObj))
            }

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()); it.flush() }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = BufferedReader(InputStreamReader(stream ?: conn.inputStream)).use { it.readText() }

            val elapsed = System.currentTimeMillis() - startTime
            val json = JSONObject(responseText)

            // 1. Bắt thông báo lỗi từ server nếu có
            if (json.has("message")) {
                return@withContext ExecutionResult(
                    output = "Server Message: " + json.getString("message"),
                    isError = true,
                    exitCode = responseCode,
                    executionTimeMs = elapsed
                )
            }

            // 2. Bắt lỗi biên dịch (Compile Error - GCC)
            if (json.has("compile")) {
                val compileObj = json.getJSONObject("compile")
                val compileCode = compileObj.optInt("code", 0)
                if (compileCode != 0) {
                    val stderr = compileObj.optString("stderr").ifEmpty { compileObj.optString("output") }
                    return@withContext ExecutionResult(
                        output = stderr.ifEmpty { "Lỗi biên dịch (Compile Error)" },
                        isError = true,
                        exitCode = compileCode,
                        executionTimeMs = elapsed
                    )
                }
            }

            // 3. Kết quả chạy (Runtime)
            if (json.has("run")) {
                val runObj = json.getJSONObject("run")
                val runCode = runObj.optInt("code", 0)
                val stdout = runObj.optString("stdout")
                val stderr = runObj.optString("stderr")
                val output = runObj.optString("output")

                val finalOutput = when {
                    output.isNotEmpty() -> output
                    stderr.isNotEmpty() -> "$stdout\n[Stderr]:\n$stderr"
                    stdout.isNotEmpty() -> stdout
                    else -> "[Chương trình kết thúc thành công (không có output)]"
                }

                return@withContext ExecutionResult(
                    output = finalOutput,
                    isError = runCode != 0,
                    exitCode = runCode,
                    executionTimeMs = elapsed
                )
            }

            ExecutionResult("Phản hồi không xác định: $responseText", true, responseCode, elapsed)
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            ExecutionResult("Lỗi mạng: ${e.localizedMessage ?: "Timeout"}", true, -1, elapsed)
        }
    }
}