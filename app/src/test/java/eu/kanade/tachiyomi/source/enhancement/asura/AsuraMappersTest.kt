package eu.kanade.tachiyomi.source.enhancement.asura

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * MIKO — unit tests for the Asura Scans comment mapping, over a trimmed-down copy of a real
 * `GET /series/{id}/comments` payload. No network involved.
 */
class AsuraMappersTest {

    /** Same configuration as the app-wide `Json` from `AppModule`. */
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val commentsPayload = """
        {
          "data": [
            {
              "id": 770997,
              "content": "This shit is **peak**.\nRead ||the last page|| again @someone",
              "created_at": "2025-04-25T04:49:18Z",
              "upvotes": 331,
              "downvotes": 12,
              "gif_url": "https://cdn.asurascans.com/gifs/peak.webp",
              "media_urls": [
                "https://cdn.asurascans.com/media/one.webp",
                "https://cdn.asurascans.com/media/one.webp",
                ""
              ],
              "is_edited": true,
              "is_pinned": true,
              "reply_count": 2,
              "replies": [
                {
                  "id": 770998,
                  "content": "agreed",
                  "created_at": "2025-04-25T04:53:53.97559Z",
                  "upvotes": 4,
                  "downvotes": 0,
                  "gif_url": null,
                  "media_urls": null,
                  "is_edited": false,
                  "is_pinned": false,
                  "reply_count": 0,
                  "user": {
                    "id": 12,
                    "username": "ModGuy",
                    "profile_picture_url": "",
                    "role": "moderator",
                    "is_premium": false
                  },
                  "user_vote": null
                },
                {
                  "id": 770999,
                  "content": "nah",
                  "created_at": "not a date",
                  "upvotes": 0,
                  "downvotes": 1,
                  "gif_url": null,
                  "media_urls": null,
                  "is_edited": false,
                  "is_pinned": false,
                  "reply_count": 0,
                  "user": null,
                  "user_vote": null
                }
              ],
              "user": {
                "id": 492628,
                "username": "JXASSiN",
                "profile_picture_url": "https://cdn.asurascans.com/asura-images/profiles/492628.5fd3d0.webp",
                "role": "user",
                "is_premium": true,
                "is_beta_user": false,
                "comment_rank": 0
              },
              "user_vote": null
            }
          ],
          "meta": { "has_more": true, "total": 562 },
          "success": true
        }
    """.trimIndent()

    private fun parse(payload: String) = json.decodeFromString(AsuraCommentsPageDto.serializer(), payload)

    @Test
    fun `page meta becomes hasNextPage and total`() {
        val page = parse(commentsPayload).toPage()

        assertTrue(page.hasNextPage)
        assertEquals(562, page.total)
        assertEquals(1, page.comments.size)
    }

    @Test
    fun `top level comment is mapped field by field`() {
        val comment = parse(commentsPayload).toPage().comments.single()

        assertEquals("770997", comment.id)
        assertEquals("JXASSiN", comment.author)
        assertEquals("This shit is **peak**.\nRead ||the last page|| again @someone", comment.text)
        assertEquals(Instant.parse("2025-04-25T04:49:18Z").toEpochMilli(), comment.date)
        assertEquals(
            "https://cdn.asurascans.com/asura-images/profiles/492628.5fd3d0.webp",
            comment.avatarUrl,
        )
        assertEquals(331, comment.likes)
        assertEquals(12, comment.dislikes)
        assertTrue(comment.isEdited)
        assertTrue(comment.isPinned)
        assertEquals(2, comment.replyCount)
        assertEquals(2, comment.replies.size)
    }

    @Test
    fun `media urls and the gif are merged de-duplicated and stripped of blanks`() {
        val comment = parse(commentsPayload).toPage().comments.single()

        assertEquals(
            listOf(
                "https://cdn.asurascans.com/media/one.webp",
                "https://cdn.asurascans.com/gifs/peak.webp",
            ),
            comment.imageUrls,
        )
    }

    @Test
    fun `a premium user with an ordinary role gets the premium badge`() {
        val comment = parse(commentsPayload).toPage().comments.single()

        assertEquals("premium", comment.authorBadge)
    }

    @Test
    fun `a staff role becomes the badge and an empty avatar becomes null`() {
        val reply = parse(commentsPayload).toPage().comments.single().replies.first()

        assertEquals("770998", reply.id)
        assertEquals("ModGuy", reply.author)
        assertEquals("moderator", reply.authorBadge)
        assertNull(reply.avatarUrl)
        assertTrue(reply.imageUrls.isEmpty())
        assertTrue(reply.replies.isEmpty())
    }

    @Test
    fun `a fractional timestamp is parsed to millis`() {
        val reply = parse(commentsPayload).toPage().comments.single().replies.first()

        assertEquals(Instant.parse("2025-04-25T04:53:53.97559Z").toEpochMilli(), reply.date)
        assertTrue(reply.date > 0L)
    }

    @Test
    fun `an unparseable timestamp and a missing user degrade instead of throwing`() {
        val reply = parse(commentsPayload).toPage().comments.single().replies.last()

        assertEquals(0L, reply.date)
        assertEquals("?", reply.author)
        assertNull(reply.authorBadge)
        assertNull(reply.avatarUrl)
    }

    @Test
    fun `an empty page reports no next page and no total`() {
        val page = parse("""{"data":[],"meta":{"has_more":false},"success":true}""").toPage()

        assertTrue(page.comments.isEmpty())
        assertFalse(page.hasNextPage)
        assertNull(page.total)
    }

    @Test
    fun `a page without data or meta degrades to an empty page`() {
        val page = parse("""{"success":true}""").toPage()

        assertTrue(page.comments.isEmpty())
        assertFalse(page.hasNextPage)
        assertNull(page.total)
    }
}
