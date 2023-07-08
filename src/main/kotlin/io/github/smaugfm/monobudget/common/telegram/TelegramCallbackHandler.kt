package io.github.smaugfm.monobudget.common.telegram

import com.elbekd.bot.model.ChatId
import com.elbekd.bot.types.CallbackQuery
import com.elbekd.bot.types.InlineKeyboardMarkup
import com.elbekd.bot.types.Message
import com.elbekd.bot.types.ParseMode
import io.github.smaugfm.monobudget.common.category.CategoryService
import io.github.smaugfm.monobudget.common.model.callback.ActionCallbackType
import io.github.smaugfm.monobudget.common.model.callback.ActionCallbackType.ChooseCategory
import io.github.smaugfm.monobudget.common.model.callback.CallbackType
import io.github.smaugfm.monobudget.common.model.callback.TransactionUpdateType
import io.github.smaugfm.monobudget.common.model.callback.TransactionUpdateType.MakePayee
import io.github.smaugfm.monobudget.common.model.callback.TransactionUpdateType.Unapprove
import io.github.smaugfm.monobudget.common.model.callback.TransactionUpdateType.Uncategorize
import io.github.smaugfm.monobudget.common.model.callback.TransactionUpdateType.UpdateCategory
import io.github.smaugfm.monobudget.common.model.settings.MultipleAccountSettings
import io.github.smaugfm.monobudget.common.transaction.TransactionMessageFormatter
import io.github.smaugfm.monobudget.common.transaction.TransactionMessageFormatter.Companion.extractPayee
import io.github.smaugfm.monobudget.common.transaction.TransactionMessageFormatter.Companion.extractTransactionId
import io.github.smaugfm.monobudget.common.util.isEven
import io.github.smaugfm.monobudget.common.util.isOdd
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val log = KotlinLogging.logger {}

abstract class TelegramCallbackHandler<TTransaction> : KoinComponent {
    protected val categoryService: CategoryService by inject()
    private val telegram: TelegramApi by inject()
    private val formatter: TransactionMessageFormatter<TTransaction> by inject()
    private val monoSettings: MultipleAccountSettings by inject()
    private val telegramChatIds = monoSettings.telegramChatIds

    suspend fun handle(callbackQuery: CallbackQuery) {
        try {
            if (callbackQuery.from.id !in telegramChatIds) {
                log.warn { "Received Telegram callbackQuery from unknown chatId: ${callbackQuery.from.id}" }
                return
            }

            val (callbackType, message) =
                parseCallbackQuery(callbackQuery) ?: return

            log.debug { "Parsed callback query of callbackType: $callbackType" }

            when (callbackType) {
                is ActionCallbackType ->
                    handleAction(callbackType, message)

                is TransactionUpdateType ->
                    handleUpdate(callbackType, message)
            }
        } finally {
            try {
                telegram.answerCallbackQuery(callbackQuery.id)
            } catch (e: Throwable) {
                log.error(e) {
                    "Error answering callback queryId=${callbackQuery.id}"
                }
            }
        }
    }

    private suspend fun handleAction(callbackType: ActionCallbackType, message: Message) {
        when (callbackType) {
            is ChooseCategory ->
                telegram.editKeyboard(
                    ChatId.IntegerId(message.chat.id),
                    message.messageId,
                    categoriesInlineKeyboard(
                        categoryService.categoryIdToNameList()
                    )
                )
        }
    }

    private fun categoriesInlineKeyboard(
        categoryIdToNameList: List<Pair<String, String>>
    ): InlineKeyboardMarkup {
        val buttons = categoryIdToNameList
            .map { (id, name) -> UpdateCategory.button(id, name) }
        val rows = buttons
            .zipWithNext()
            .map { it.toList() }
            .filterIndexed { i, _ -> i.isEven() }
            .toMutableList()
        if (buttons.size.isOdd()) {
            rows.add(listOf(buttons.last()))
        }

        return InlineKeyboardMarkup(rows.toList())
    }

    private suspend fun handleUpdate(callbackType: TransactionUpdateType, message: Message) {
        val updatedTransaction = updateTransaction(callbackType)
        val updatedText = updateHTMLStatementMessage(updatedTransaction, message)

        val updatedMarkup =
            formatter.getReplyKeyboard(updatedTransaction)

        if (stripHTMLTagsFromMessage(updatedText) != message.text ||
            updatedMarkup != message.replyMarkup
        ) {
            with(message) {
                telegram.editMessage(
                    ChatId.IntegerId(chat.id),
                    messageId,
                    updatedText,
                    ParseMode.Html,
                    updatedMarkup
                )
            }
        }
    }

    protected abstract suspend fun updateTransaction(callbackType: TransactionUpdateType): TTransaction
    protected abstract suspend fun updateHTMLStatementMessage(
        updatedTransaction: TTransaction,
        oldMessage: Message
    ): String

    private fun parseCallbackQuery(callbackQuery: CallbackQuery): TransactionUpdateCallbackQueryWrapper? {
        val data = callbackQueryData(callbackQuery)
        val message = callbackQueryMessage(callbackQuery)

        if (data == null || message == null) {
            return null
        }

        val callbackType = deserializeCallbackType(data, message)
        if (callbackType == null) {
            log.error { "Unknown callbackType. Skipping this callback query: $callbackQuery" }
            return null
        }

        return TransactionUpdateCallbackQueryWrapper(callbackType, message)
    }

    private fun callbackQueryMessage(callbackQuery: CallbackQuery): Message? =
        callbackQuery.message ?: log.warn { "Received Telegram callbackQuery with empty message" }
            .let { return null }

    private fun callbackQueryData(callbackQuery: CallbackQuery): String? =
        callbackQuery.data.takeUnless { it.isNullOrBlank() }
            ?: log.warn { "Received Telegram callbackQuery with empty data.\n$callbackQuery" }
                .let { return null }

    private fun stripHTMLTagsFromMessage(messageText: String): String {
        val replaceHtml = Regex("<.*?>")
        return replaceHtml.replace(messageText, "")
    }

    private data class TransactionUpdateCallbackQueryWrapper(
        val updateType: CallbackType,
        val message: Message
    )

    companion object {
        private fun deserializeCallbackType(callbackData: String, message: Message): CallbackType? {
            val cls =
                CallbackType.classFromCallbackData(callbackData)

            val payee = extractPayee(message) ?: return null
            val transactionId = extractTransactionId(message)

            return when (cls) {
                Uncategorize::class ->
                    Uncategorize(transactionId)

                Unapprove::class ->
                    Unapprove(transactionId)

                MakePayee::class ->
                    MakePayee(transactionId, payee)

                ChooseCategory::class ->
                    ChooseCategory(transactionId)

                UpdateCategory::class ->
                    UpdateCategory(
                        transactionId,
                        UpdateCategory
                            .extractCategoryIdFromCallbackData(callbackData)
                    )

                else -> throw IllegalArgumentException(
                    "Unknown class CallbackType: ${cls?.simpleName}"
                )
            }
        }
    }
}
