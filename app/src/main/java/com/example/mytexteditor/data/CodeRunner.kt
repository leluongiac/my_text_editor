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
                connectTimeout = 8000
                readTimeout = 12000
                doOutput = true
            }

            // Build payload JSON chuẩn Piston
            val payload = JSONObject().apply {
                put("language", "c++")
                put("version", "10.2.0")
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
            val responseText = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            val elapsed = System.currentTimeMillis() - startTime
            val json = JSONObject(responseText)

            // Kiểm tra Compile Error trước
            if (json.has("compile")) {
                val compileObj = json.getJSONObject("compile")
                val compileCode = compileObj.optInt("code", 0)
                if (compileCode != 0) {
                    val stderr = compileObj.optString("stderr").ifEmpty { compileObj.optString("output") }
                    return@withContext ExecutionResult(
                        output = stderr,
                        isError = true,
                        exitCode = compileCode,
                        executionTimeMs = elapsed
                    )
                }
            }

            // Kết quả chạy runtime
            if (json.has("run")) {
                val runObj = json.getJSONObject("run")
                val runCode = runObj.optInt("code", 0)
                val stdout = runObj.optString("stdout")
                val stderr = runObj.optString("stderr")
                val output = if (stderr.isNotEmpty()) "$stdout\n[Stderr]:\n$stderr" else stdout

                return@withContext ExecutionResult(
                    output = output.ifEmpty { "[Chương trình kết thúc nhưng không có output]" },
                    isError = runCode != 0,
                    exitCode = runCode,
                    executionTimeMs = elapsed
                )
            }

            ExecutionResult("Lỗi phản hồi từ máy chủ", true, -1, elapsed)
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            ExecutionResult("Lỗi kết nối: ${e.localizedMessage ?: "Timeout"}", true, -1, elapsed)
        }
    }
}