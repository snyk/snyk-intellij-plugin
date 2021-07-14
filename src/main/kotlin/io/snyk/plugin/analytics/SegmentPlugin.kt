package io.snyk.plugin.analytics

import com.segment.analytics.Analytics
import com.segment.analytics.messages.AliasMessage
import com.segment.analytics.messages.GroupMessage
import com.segment.analytics.messages.IdentifyMessage
import com.segment.analytics.messages.TrackMessage
import ly.iterative.itly.Event
import ly.iterative.itly.Logger
import ly.iterative.itly.Plugin
import ly.iterative.itly.PluginLoadOptions
import ly.iterative.itly.Properties

/**
 * Custom implementation of SegmentPlugin to solve issue with `Anonymous ID` and `User ID`.
 *
 * See [SegmentPlugin.kt](https://github.com/amplitude/itly-sdk-jvm/blob/master/packages/plugin-segment/src/jvmMain/kotlin/ly.iterative.itly.segment/SegmentPlugin.kt)
 */
class SegmentPlugin(
    private val writeKey: String,
    private val anonymousId: String
) : Plugin(ID) {
    companion object {
        const val ID = "segment"
        private const val LOG_TAG = "[plugin-$ID]"
    }

    private lateinit var logger: Logger
    private lateinit var segment: Analytics

    val client: Analytics
        get() = this.segment

    override fun load(options: PluginLoadOptions) {
        logger = options.logger
        val builder = Analytics.builder(writeKey)
        segment = builder.build()
    }

    override fun alias(userId: String, previousId: String?) {
        logger.info("$LOG_TAG alias(userId=$userId, previousId=$previousId)")
        segment.enqueue(
            AliasMessage.builder(previousId)
                .userId(userId)
        )
    }

    override fun identify(userId: String?, properties: Properties?) {
        logger.info("$LOG_TAG identify(userId=$userId, properties=${properties?.properties})")
        val traits = HashMap(properties?.properties)
        val message = IdentifyMessage.builder().userId(userId)
        if (anonymousId.isNotEmpty()) {
            message.anonymousId(anonymousId)
        }
        if (traits.isNotEmpty()) {
            message.traits(traits)
        }
        segment.enqueue(message)
    }

    override fun group(userId: String?, groupId: String, properties: Properties?) {
        logger.info("$LOG_TAG group(userId=$userId, groupId=$groupId, properties=${properties?.properties})")
        val traits = HashMap(properties?.properties)
        val message = GroupMessage.builder(groupId).userId(userId)
        if (anonymousId.isNotEmpty()) {
            message.anonymousId(anonymousId)
        }
        if (traits.isNotEmpty()) {
            message.traits(traits)
        }
        segment.enqueue(message)
    }

    override fun track(userId: String?, event: Event) {
        logger.info("$LOG_TAG track(userId=$userId, event=${event.name}, properties=${event.properties})")
        val properties = HashMap(event.properties)
        val message = TrackMessage.builder(event.name).userId(userId)
        if (anonymousId.isNotEmpty()) {
            message.anonymousId(anonymousId)
        }
        if (properties.isNotEmpty()) {
            message.properties(properties)
        }
        segment.enqueue(message)
    }

    override fun shutdown() {
        logger.info("$LOG_TAG shutdown")
        segment.shutdown()
    }
}
