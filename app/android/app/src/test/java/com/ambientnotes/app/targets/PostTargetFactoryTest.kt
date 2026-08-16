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
import org.junit.Assert.assertTrue
import org.junit.Test

class PostTargetFactoryTest {

    private fun configFor(type: TargetType) = PostTargetConfig(
        id = "id", type = type, displayName = "d", bodyTemplate = "t",
    )

    @Test fun `builds WebhookTarget`() = assertTrue(PostTargetFactory.build(configFor(TargetType.WEBHOOK)) is WebhookTarget)
    @Test fun `builds MastodonTarget`() = assertTrue(PostTargetFactory.build(configFor(TargetType.MASTODON)) is MastodonTarget)
    @Test fun `builds BlueskyTarget`() = assertTrue(PostTargetFactory.build(configFor(TargetType.BLUESKY)) is BlueskyTarget)
    @Test fun `builds TwitterXTarget`() = assertTrue(PostTargetFactory.build(configFor(TargetType.TWITTER_X)) is TwitterXTarget)
    @Test fun `builds ThreadsTarget`() = assertTrue(PostTargetFactory.build(configFor(TargetType.THREADS)) is ThreadsTarget)
    @Test fun `builds RedditTarget`() = assertTrue(PostTargetFactory.build(configFor(TargetType.REDDIT)) is RedditTarget)
    @Test fun `builds LinkedInTarget`() = assertTrue(PostTargetFactory.build(configFor(TargetType.LINKEDIN)) is LinkedInTarget)
    @Test fun `builds TumblrTarget`() {
        val config = configFor(TargetType.TUMBLR).copy(settings = mapOf("tumblrBlogIdentifier" to "x.tumblr.com"))
        assertTrue(PostTargetFactory.build(config) is TumblrTarget)
    }
    @Test fun `builds FacebookTarget`() {
        val config = configFor(TargetType.FACEBOOK).copy(settings = mapOf("facebookPageId" to "12345"))
        assertTrue(PostTargetFactory.build(config) is FacebookTarget)
    }
}
