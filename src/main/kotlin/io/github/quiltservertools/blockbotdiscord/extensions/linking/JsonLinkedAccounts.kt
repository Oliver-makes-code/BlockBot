package io.github.quiltservertools.blockbotdiscord.extensions.linking

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.User
import io.github.quiltservertools.blockbotdiscord.BlockBotDiscord
import io.github.quiltservertools.blockbotdiscord.logInfo
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import net.minecraft.server.network.ServerPlayerEntity
import java.io.File
import java.util.*

@OptIn(ExperimentalSerializationApi::class)
class JsonLinkedAccounts : LinkedAccountData {
    private val json = Json {
        prettyPrint = true
    }
    private val file = File("linked.json")

    private val linked: MutableMap<Snowflake, MutableSet<UUID>> = mutableMapOf()
    private val linkedIds: MutableMap<UUID, Snowflake> = mutableMapOf()

    override fun get(id: UUID): Snowflake? = linkedIds[id]

    override fun get(id: Snowflake): Set<UUID>? = linked[id]

    override fun add(id: Snowflake, uuid: UUID) {
        addAccounts(id, uuid)

        save()
    }

    private fun addAccounts(id: Snowflake, uuid: UUID) {
        if (linked.contains(id)) {
            linked[id]!!.add(uuid)
        } else {
            linked[id] = mutableSetOf(uuid)
        }

        linkedIds[uuid] = id
    }

    override fun load() {
        if (linked.isNotEmpty()) {
            save()
            linked.clear()
            linkedIds.clear()
        }

        if (file.exists()) {
            val accounts = json.decodeFromString<List<LinkedAccount>>(file.readText())
            for (account in accounts) {
                for (uuid in account.uuids) {
                    addAccounts(account.snowflake, uuid)
                }
            }

            logInfo("Loaded linked accounts")
        }
    }

    override fun save() {
        file.writeText(json.encodeToString(linked.map { LinkedAccount(it.value, it.key) }))
    }
}

suspend fun ServerPlayerEntity.getLinkedAccount(): User? {
    val id = BlockBotDiscord.linkedAccounts.get(this.uuid)
    if (id != null) {
        return BlockBotDiscord.bot.getKoin().get<Kord>().getUser(id)
    }

    return null
}

@Serializable
private data class LinkedAccount(val uuids: Set<@Serializable(with = UUIDSerializer::class) UUID>, val snowflake: Snowflake)

private object UUIDSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }
}
