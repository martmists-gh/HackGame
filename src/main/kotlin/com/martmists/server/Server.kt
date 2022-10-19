package com.martmists.server

import com.martmists.commandparser.dispatch.Dispatcher
import com.martmists.commandparser.dsl.build
import com.martmists.common.loadConfig
import com.martmists.server.commands.ServerCommandContext
import com.martmists.server.commands.ServerCommands
import com.martmists.server.network.ServerConnectionManager
import kotlinx.coroutines.runBlocking

object Server {
    val dispatcher = Dispatcher<ServerCommandContext>()

    val config by lazy {
        loadConfig<ServerConfig>("server.yaml", "/configs/server.yaml")
    }

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        ServerPacketCallbacks.initialize()
        ServerCommands.register(dispatcher)
        ServerConnectionManager.run()
    }
}
