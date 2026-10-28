package com.aikeybridge.shell

import android.content.Context
import android.os.Process
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject

class ShellUserService() : IShellService.Stub() {

    @Suppress("unused")
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : this()

    private val io = Executors.newCachedThreadPool()

    override fun destroy() {
        io.shutdownNow()
        Process.killProcess(Process.myPid())
    }

    override fun exec(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(false)
                .start()
            runCatching { process.outputStream.close() }
            val outFuture = io.submit<String> {
                process.inputStream.bufferedReader().use { it.readText() }
            }
            val errFuture = io.submit<String> {
                process.errorStream.bufferedReader().use { it.readText() }
            }
            val finished = process.waitFor(8, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return JSONObject()
                    .put("exit", -1)
                    .put("out", runCatching { outFuture.get(200, TimeUnit.MILLISECONDS) }.getOrDefault(""))
                    .put("err", "Lệnh quá thời gian (8s).")
                    .toString()
            }
            val stdout = runCatching { outFuture.get(1, TimeUnit.SECONDS) }.getOrDefault("")
            val stderr = runCatching { errFuture.get(1, TimeUnit.SECONDS) }.getOrDefault("")
            JSONObject()
                .put("exit", process.exitValue())
                .put("out", stdout)
                .put("err", stderr)
                .toString()
        } catch (throwable: Throwable) {
            JSONObject()
                .put("exit", -1)
                .put("out", "")
                .put("err", throwable.toString())
                .toString()
        }
    }
}
