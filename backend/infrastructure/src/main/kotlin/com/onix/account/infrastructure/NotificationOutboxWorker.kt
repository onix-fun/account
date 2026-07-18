package com.onix.account.infrastructure

import org.slf4j.LoggerFactory
import com.onix.account.domain.NotificationOutboxItem
import com.onix.account.domain.OwnerRef
import com.onix.account.usecases.BlockRepository
import com.onix.account.usecases.NotificationOutboxProcessor
import com.onix.account.usecases.NotificationUseCases
import com.onix.account.usecases.SocialUseCases

class NotificationOutboxWorker(
    private val outboxRepo: NotificationOutboxProcessor,
    private val socialUseCases: SocialUseCases,
    private val blockRepo: BlockRepository,
    private val notificationUseCases: NotificationUseCases,
    private val sseManager: SseManager
) {
    private val log = LoggerFactory.getLogger(javaClass)
    @Volatile private var thread: Thread? = null

    fun start() {
        if (thread?.isAlive == true) return
        thread = Thread({
            while (!Thread.currentThread().isInterrupted) {
                runCatching { processBatch() }.onFailure { log.error("Notification outbox worker failed", it) }
                try {
                    Thread.sleep(1_000)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }, "notification-outbox-worker").apply { isDaemon = true; start() }
    }

    fun stop() {
        thread?.interrupt()
        thread = null
    }

    fun processBatch(): Int = outboxRepo.processPending(limit = 25, handler = ::fanOut)

    private fun fanOut(item: NotificationOutboxItem) {
        val actor = OwnerRef(item.event.actorType, item.event.actorId)
        var page = 1
        val limit = 250
        while (true) {
            val (followers, total) = socialUseCases.getFollowers(actor, page, limit)
            followers
                .asSequence()
                .map { it.subscriberId }
                .filterNot { recipientId -> blockRepo.isBlockedEither(actor, OwnerRef.user(recipientId)) }
                .forEach { recipientId ->
                    val notification = notificationUseCases.createActivityNotification(item.event, recipientId)
                    if (notification != null) sseManager.push(recipientId.toString(), notification)
                }

            if (page * limit >= total || followers.isEmpty()) return
            page += 1
        }
    }
}
