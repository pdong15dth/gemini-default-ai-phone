package com.aikeybridge.shell

import android.os.Process
import org.json.JSONObject

class ShellUserService : IShellService.Stub() {

    override fun destroy() {
        Process.killProcess(Process.myPid())
    }

    override fun exec(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(false)
                .start()
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exit = process.waitFor()
            JSONObject()
                .put("exit", exit)
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
