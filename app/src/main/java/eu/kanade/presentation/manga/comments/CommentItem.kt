package eu.kanade.presentation.manga.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.domain.source.enhancement.SourceComment
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Locale

/**
 * MIKO — one comment, plus its replies when expanded.
 *
 * Only top-level comments ([depth] `0`) can expand: every site Miko supports keeps replies one level
 * deep, so a reply row never grows another toggle.
 */
@Composable
fun CommentItem(
    comment: SourceComment,
    depth: Int,
    expanded: Boolean,
    loadingReplies: Boolean,
    onToggleReplies: (SourceComment) -> Unit,
    onOpenImage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.padding.medium,
                    end = MaterialTheme.padding.medium,
                    top = MaterialTheme.padding.small,
                    bottom = MaterialTheme.padding.extraSmall,
                ),
        ) {
            CommentAvatar(url = comment.avatarUrl)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = MaterialTheme.padding.small),
            ) {
                CommentHeader(comment)

                CommentText(
                    text = comment.text,
                    modifier = Modifier.padding(top = 2.dp),
                )

                if (comment.imageUrls.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                    ) {
                        comment.imageUrls.forEach { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = stringResource(MKMR.strings.comments_open_image),
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.CenterStart,
                                modifier = Modifier
                                    .heightIn(max = IMAGE_MAX_HEIGHT)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onOpenImage(url) },
                            )
                        }
                    }
                }

                CommentFooter(
                    comment = comment,
                    depth = depth,
                    expanded = expanded,
                    loadingReplies = loadingReplies,
                    onToggleReplies = onToggleReplies,
                )
            }
        }

        if (expanded && depth == 0 && comment.replies.isNotEmpty()) {
            // Subtle rail marking the reply block, drawn instead of laid out so the nesting needs no
            // intrinsic measurement (AsyncImage and lazy content do not answer those reliably).
            val railColor = MaterialTheme.colorScheme.outlineVariant
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = REPLY_RAIL_INSET)
                    .drawBehind {
                        drawLine(
                            color = railColor,
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 1.dp.toPx(),
                        )
                    },
            ) {
                comment.replies.forEach { reply ->
                    CommentItem(
                        comment = reply,
                        depth = depth + 1,
                        expanded = false,
                        loadingReplies = false,
                        onToggleReplies = onToggleReplies,
                        onOpenImage = onOpenImage,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentAvatar(url: String?) {
    val shownUrl = url?.takeIf { it.isNotBlank() }
    if (shownUrl == null) {
        Icon(
            imageVector = Icons.Outlined.AccountCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(AVATAR_SIZE),
        )
    } else {
        AsyncImage(
            model = shownUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(AVATAR_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

@Composable
private fun CommentHeader(comment: SourceComment) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        Text(
            text = comment.author,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        comment.authorBadge?.takeIf { it.isNotBlank() }?.let { badge ->
            CommentPill(text = badge.replaceFirstChar { it.titlecase(Locale.getDefault()) })
        }
        if (comment.isPinned) {
            CommentPill(text = stringResource(MKMR.strings.comments_pinned))
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        val relativeDate = remember(comment.date) {
            if (comment.date <= 0L) {
                ""
            } else {
                android.text.format.DateUtils
                    .getRelativeTimeSpanString(comment.date, System.currentTimeMillis(), 0L)
                    .toString()
            }
        }
        if (relativeDate.isNotEmpty()) {
            Text(
                text = relativeDate,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (comment.isEdited) {
            Text(
                text = stringResource(MKMR.strings.comments_edited),
                style = MaterialTheme.typography.labelSmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CommentFooter(
    comment: SourceComment,
    depth: Int,
    expanded: Boolean,
    loadingReplies: Boolean,
    onToggleReplies: (SourceComment) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        val likes = comment.likes
        if (likes != null) {
            Icon(
                imageVector = Icons.Outlined.ThumbUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = likes.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val dislikes = comment.dislikes
        if (dislikes != null && dislikes > 0) {
            Icon(
                imageVector = Icons.Outlined.ThumbDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = MaterialTheme.padding.small)
                    .size(14.dp),
            )
            Text(
                text = dislikes.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (comment.replyCount > 0 && depth == 0) {
            TextButton(onClick = { onToggleReplies(comment) }) {
                if (loadingReplies) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = if (expanded) {
                            stringResource(MKMR.strings.comments_hide_replies)
                        } else {
                            stringResource(MKMR.strings.comments_show_replies, comment.replyCount)
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

private val AVATAR_SIZE = 32.dp
private val IMAGE_MAX_HEIGHT = 240.dp

/** Start inset of the reply rail: avatar + its gap, so replies line up under the author's name. */
private val REPLY_RAIL_INSET = 28.dp
