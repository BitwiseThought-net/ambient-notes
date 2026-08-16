package com.ambientnotes.app.targets

import com.ambientnotes.app.recognition.RecognitionResult
import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateEngineTest {

    private val sample = RecognitionResult(
        matched = true,
        title = "Song \"Title\" <special>",
        artist = "Artist & Co",
        album = "Album",
        releaseDate = "2022-01-01",
        confidence = 0.87f,
        providerName = "acrcloud",
        externalIds = mapOf("spotify" to "abc123"),
        recognizedAtEpochMs = 1_700_000_000_000L,
    )

    @Test
    fun `renders plain text template without escaping`() {
        val out = TemplateEngine.render("Now playing {{title}} by {{artist}}", sample, PayloadFormat.PLAIN_TEXT)
        assertEquals("Now playing Song \"Title\" <special> by Artist & Co", out)
    }

    @Test
    fun `escapes quotes for json payloads`() {
        val out = TemplateEngine.render("""{"text": "{{title}}"}""", sample, PayloadFormat.JSON)
        assertEquals("""{"text": "Song \"Title\" <special>"}""", out)
    }

    @Test
    fun `escapes angle brackets and ampersands for xml payloads`() {
        val out = TemplateEngine.render("<title>{{title}}</title><artist>{{artist}}</artist>", sample, PayloadFormat.XML)
        assertEquals(
            "<title>Song &quot;Title&quot; &lt;special&gt;</title><artist>Artist &amp; Co</artist>",
            out,
        )
    }

    @Test
    fun `resolves nested externalIds placeholders`() {
        val out = TemplateEngine.render("spotify:{{externalIds.spotify}}", sample, PayloadFormat.PLAIN_TEXT)
        assertEquals("spotify:abc123", out)
    }

    @Test
    fun `unknown externalIds key resolves to empty string`() {
        val out = TemplateEngine.render("[{{externalIds.does_not_exist}}]", sample, PayloadFormat.PLAIN_TEXT)
        assertEquals("[]", out)
    }

    @Test
    fun `unknown top level placeholder resolves to empty string`() {
        val out = TemplateEngine.render("[{{notAField}}]", sample, PayloadFormat.PLAIN_TEXT)
        assertEquals("[]", out)
    }

    @Test
    fun `confidence and provider placeholders render`() {
        val out = TemplateEngine.render("{{confidence}}/{{provider}}", sample, PayloadFormat.PLAIN_TEXT)
        assertEquals("0.87/acrcloud", out)
    }

    @Test
    fun `template with no placeholders is returned unchanged`() {
        val out = TemplateEngine.render("static text", sample, PayloadFormat.JSON)
        assertEquals("static text", out)
    }
}
