package com.ambientnotes.app.targets

import com.ambientnotes.app.targets.impl.BlueskyTarget
import com.ambientnotes.app.targets.impl.FacebookTarget
import com.ambientnotes.app.targets.impl.LinkedInTarget
import com.ambientnotes.app.targets.impl.MastodonTarget
import com.ambientnotes.app.targets.impl.RedditTarget
import com.ambientnotes.app.targets.impl.ThreadsTarget
import com.ambientnotes.app.targets.impl.TumblrTarget
import com.ambientnotes.app.targets.impl.TwitterXTarget
import com.ambientnotes.app.targets.impl.WebhookTarget

/** Builds the right [PostTarget] implementation for a [PostTargetConfig].
 * Add a new [TargetType] + branch here to support a new destination. */
object PostTargetFactory {
    fun build(config: PostTargetConfig): PostTarget = when (config.type) {
        TargetType.WEBHOOK -> WebhookTarget(config)
        TargetType.MASTODON -> MastodonTarget(config)
        TargetType.BLUESKY -> BlueskyTarget(config)
        TargetType.TWITTER_X -> TwitterXTarget(config)
        TargetType.THREADS -> ThreadsTarget(config)
        TargetType.FACEBOOK -> FacebookTarget(config)
        TargetType.REDDIT -> RedditTarget(config)
        TargetType.LINKEDIN -> LinkedInTarget(config)
        TargetType.TUMBLR -> TumblrTarget(config)
    }
}
