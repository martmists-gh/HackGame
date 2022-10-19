package com.martmists.server.game

import com.martmists.common.utilities.Loggable
import com.martmists.server.ServerPacketCallbacks
import com.martmists.server.database.dataholders.StoredHostDevice
import com.martmists.server.database.tables.AccountTable
import com.martmists.server.database.tables.HostTable
import com.martmists.server.database.transaction
import com.martmists.server.game.vfs.VFSDirectory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import kotlin.random.Random

object HostManager : Loggable {
    private val activeHosts = mutableMapOf<String, HostDevice>()

    init {
        debug("before runblocking")
        runBlocking {
            debug("Creating dev host")
            loadOrCreateStoredHost(
                "123.123.123.123",
                StoredHostDevice(
                    100,
                    listOf(),
                    VFSDirectory.default(),
                    "devpass1"
                )
            )

            debug("before transaction")
            transaction {
                AccountTable.insertIgnore {
                    it[AccountTable.username] = "dev"
                    it[AccountTable.password] = ServerPacketCallbacks.argon2.hash(12, 65535, 1, "dev".toCharArray())
                    it[AccountTable.homeAddress] = "123.123.123.123"
                }
            }
        }
    }

    suspend fun loadStoredHosts() {
        val map = transaction {
            HostTable.selectAll().associate {
                val host = ProtoBuf.decodeFromByteArray<StoredHostDevice>(it[HostTable.device])
                it[HostTable.address] to HostDevice(
                    it[HostTable.address],
                    listOf(),
                    listOf(),  // TODO: Load software
//                    host.software.map(SoftwareRegistry::get),
                    host.money,
                    host.files,
                    host.password
                )
            }
        }
        activeHosts.putAll(map)
    }

    fun getRandomAvailableIp(): String {
        var addr: String
        do {
            addr = "${Random.nextInt(256)}.${Random.nextInt(256)}.${Random.nextInt(256)}.${Random.nextInt(256)}"
        } while (addr in activeHosts.keys)
        return addr
    }

    fun generateRandomPass(): String {
        val characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..8).joinToString("") { characters.random().toString() }
    }

    fun getRandomUsedIp(): String {
        return activeHosts.keys.random()
    }

    /**
     * Does not guarantee registering if already taken
     */
    fun registerTempHost(host: HostDevice) {
        activeHosts.putIfAbsent(host.ip, host)
    }

    fun removeTempHost(ip: String) {
        activeHosts.remove(ip)
    }

    /**
     * Store a device in the database
     */
    suspend fun createStoredHost(ip: String, default: StoredHostDevice): HostDevice {
        transaction {
            HostTable.insertIgnore {
                it[HostTable.address] = ip
                it[HostTable.device] = ProtoBuf.encodeToByteArray(default)
            }
        }

        return HostDevice(ip, listOf(),
            listOf(),
//            default.software.map(SoftwareRegistry::get),
            default.money, default.files, default.password
        ).also {
            // Set as active host
            activeHosts[ip] = it
        }
    }

    suspend fun updateToDB(ip: String) {
        val host = activeHosts[ip]!!
        transaction {
            HostTable.update({ HostTable.address.eq(ip) }) {
                it[HostTable.device] = ProtoBuf.encodeToByteArray(
                    StoredHostDevice(
                        host.money,
                        listOf(),
                        // host.software.map(SoftwareRegistry::getId),
                        host.filesystem,
                        host.password
                    )
                )
            }
        }
    }

    /**
     * Load a host from the database
     */
    suspend fun loadOrCreateStoredHost(ip: String, default: StoredHostDevice): HostDevice {
        return activeHosts.getOrPut(ip) { createStoredHost(ip, default) }
    }

    fun getHost(ip: String): HostDevice {
        return activeHosts[ip] ?: throw Exception("$ip does not exist")
    }
}
