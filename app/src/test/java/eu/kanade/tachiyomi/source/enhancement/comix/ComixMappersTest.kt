package eu.kanade.tachiyomi.source.enhancement.comix

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * MIKO — comix.to thread payload → [eu.kanade.domain.source.enhancement.SourceComment], over a
 * trimmed copy of the real `GET /threads/985/comments?sort=best` answer for Solo Leveling. No network.
 */
class ComixMappersTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val payload = """
        {
          "status": "ok",
          "result": {
            "thread": {
              "id": 985, "objectType": "manga", "pageIdentifier": "manga32026",
              "pageUrl": "/title/emqg8-solo-leveling", "pageTitle": "Solo Leveling",
              "commentCount": 401, "mainCommentCount": 145, "isClosed": false, "userClosed": false, "userAbilities": []
            },
            "count": 3,
            "items": [
              {
                "id": 242, "threadId": 985, "parentId": 0, "status": "visible",
                "user": {"id": 1874, "hashId": "e0xm", "username": "JudgeCheese", "displayName": "JudgeCheese",
                         "avatar": "/images/avatars/1/1874.webp?t=1784043812", "isMod": true, "url": "/u/e0xm"},
                "contentHtml": "This site has the potential to fill in the void from Comick",
                "media": null, "likeCount": 155, "dislikeCount": 0, "replyCount": 6,
                "isPinned": true, "isEdited": false, "createdAtFormatted": "9mos ago",
                "replies": [
                  {
                    "id": 282, "threadId": 985, "parentId": 242, "status": "visible",
                    "user": {"id": 2147, "username": "arsonist", "displayName": "NotanArsonist", "avatar": null, "isMod": false},
                    "contentHtml": "This is one of my first times reading manga any recommendations.",
                    "media": null, "likeCount": 23, "dislikeCount": 0, "replyCount": 10,
                    "isPinned": false, "isEdited": false, "createdAtFormatted": "9mos ago",
                    "replies": [
                      {
                        "id": 146721, "threadId": 985, "parentId": 282, "status": "visible",
                        "user": {"id": 68700, "username": "mrs_mystique", "displayName": "mrs_mystique", "avatar": null, "isMod": false},
                        "contentHtml": "You might wanna try Omniscient readers viewpoint, it&#039;s peak",
                        "media": null, "likeCount": 81, "dislikeCount": 8, "replyCount": 4,
                        "isPinned": false, "isEdited": false, "createdAtFormatted": "7mos ago",
                        "replies": [], "shownReplies": 0, "cursor": ""
                      }
                    ],
                    "shownReplies": 1, "cursor": "0:353262:41"
                  }
                ],
                "userAbilities": [], "userReaction": null, "actions": [], "shownReplies": 1, "cursor": "0:282:23"
              },
              {
                "id": 447718, "threadId": 985, "parentId": 0, "status": "visible",
                "user": {"id": 56278, "hashId": "7dzj2", "username": "just_sayin", "displayName": "just_sayin 🪭",
                         "avatar": "/images/avatars/56/56278.webp?t=1780766652", "isMod": false, "url": "/u/7dzj2"},
                "contentHtml": "10/10 <br/>\nNo need to try to persuade you. <br/>\n <br/>\n<img src=\"https://i.postimg.cc/wj6b6tVr/886abd21a105e2811595e7e7ac042a8f.jpg\" alt=\"\" class=\"rich-img\"/>",
                "media": null, "likeCount": 67, "dislikeCount": 16, "replyCount": 1,
                "isPinned": false, "isEdited": false, "createdAtFormatted": "6mos ago",
                "replies": [], "shownReplies": 0, "cursor": ""
              },
              {
                "id": 5173757, "threadId": 985, "parentId": 0, "status": "visible",
                "user": {"id": 523946, "username": "MARAHMAN", "displayName": "The Fool", "avatar": null, "isMod": false},
                "contentHtml": "One of my first Manhwa&#039;s",
                "media": {"filename": "2a8c.webp", "url": "/images/cmm/2a/2a8c.webp", "isSpoiler": false},
                "likeCount": 18, "dislikeCount": 1, "replyCount": 2,
                "isPinned": false, "isEdited": true, "createdAtFormatted": "1mo ago",
                "replies": [], "shownReplies": 0, "cursor": ""
              }
            ],
            "cursor": "0:4691716:5"
          }
        }
    """.trimIndent()

    private val now = 1_786_990_000_000L

    private fun parse() = json.decodeFromString(ComixThreadResponseDto.serializer(), payload)

    @Test
    fun `envelope, thread and cursor are read`() {
        val result = parse().result!!
        assertEquals(985L, result.thread!!.id)
        assertEquals(145, result.thread!!.mainCommentCount)
        assertEquals("manga32026", result.thread!!.pageIdentifier)
        assertEquals(3, result.items!!.size)
        assertEquals("0:4691716:5", result.cursor)
    }

    @Test
    fun `top level comment is mapped field by field and its reply tree is flattened`() {
        val dto = parse().result!!.items!![0]
        val comment = dto.toSourceComment(now)

        assertEquals("242", comment.id)
        assertEquals("JudgeCheese", comment.author)
        assertEquals("https://comix.to/images/avatars/1/1874.webp?t=1784043812", comment.avatarUrl)
        assertEquals("mod", comment.authorBadge)
        assertEquals("This site has the potential to fill in the void from Comick", comment.text)
        assertEquals(155, comment.likes)
        assertEquals(0, comment.dislikes)
        assertTrue(comment.isPinned)
        assertFalse(comment.isEdited)
        assertTrue(comment.date in (now - 300L * 24 * 3600 * 1000)..(now - 240L * 24 * 3600 * 1000))
        // 6 direct replies on the site, 2 shipped inline (one nested under the other) → flat list of 2.
        assertEquals(6, comment.replyCount)
        assertEquals(listOf("282", "146721"), comment.replies.map { it.id })
        assertEquals("This is one of my first times reading manga any recommendations.", comment.replies[0].text)
        // The nested reply is prefixed with the username it answers so the thread stays readable.
        assertEquals("@arsonist You might wanna try Omniscient readers viewpoint, it's peak", comment.replies[1].text)
        assertEquals(0, comment.replies[1].replyCount)
        assertTrue(comment.replies[1].replies.isEmpty())
        assertEquals("NotanArsonist", comment.replies[0].author)
        assertNull(comment.replies[0].avatarUrl)
        assertNull(comment.replies[0].authorBadge)
    }

    @Test
    fun `rich images embedded in the html and media attachments both become image urls`() {
        val items = parse().result!!.items!!
        val withRichImage = items[1].toSourceComment(now)
        assertEquals("just_sayin 🪭", withRichImage.author)
        assertEquals("10/10\nNo need to try to persuade you.", withRichImage.text)
        assertEquals(listOf("https://i.postimg.cc/wj6b6tVr/886abd21a105e2811595e7e7ac042a8f.jpg"), withRichImage.imageUrls)
        assertEquals(67, withRichImage.likes)
        assertEquals(16, withRichImage.dislikes)
        assertEquals(1, withRichImage.replyCount)

        val withMedia = items[2].toSourceComment(now)
        assertEquals("One of my first Manhwa's", withMedia.text)
        assertEquals(listOf("https://comix.to/images/cmm/2a/2a8c.webp"), withMedia.imageUrls)
        assertTrue(withMedia.isEdited)
        assertEquals("The Fool", withMedia.author)
    }

    @Test
    fun `fetched replies are flattened the same way`() {
        val replies = parse().result!!.items!![0].replies!!.toFlatReplies(now)
        assertEquals(listOf("282", "146721"), replies.map { it.id })
        assertTrue(replies[1].text.startsWith("@arsonist "))
    }

    @Test
    fun `a lookup answer without items still parses`() {
        val lookup = """{"status":"ok","result":{"thread":{"id":777,"pageIdentifier":"manga32026_chap200_vol0"},
            "announcement":"<p>rules</p>","reportOptions":{"spam":"Spam"}}}"""
        val result = json.decodeFromString(ComixThreadResponseDto.serializer(), lookup).result!!
        assertEquals(777L, result.thread!!.id)
        assertNull(result.items)
        assertNull(result.cursor)
    }
}
