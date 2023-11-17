package io.github.smaugfm.monobudget.common.lifecycle

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smaugfm.monobudget.common.account.BankAccountService
import io.github.smaugfm.monobudget.common.account.TransferBetweenAccountsDetector
import io.github.smaugfm.monobudget.common.model.financial.StatementItem
import io.github.smaugfm.monobudget.common.telegram.TelegramMessageSender
import io.github.smaugfm.monobudget.common.transaction.TransactionFactory
import io.github.smaugfm.monobudget.common.transaction.TransactionMessageFormatter
import io.github.smaugfm.monobudget.common.util.pp

private val log = KotlinLogging.logger {}

abstract class StatementItemProcessor<TTransaction, TNewTransaction>(
    private val statementItem: StatementItem,
    private val transactionFactory: TransactionFactory<TTransaction, TNewTransaction>,
    private val bankAccounts: BankAccountService,
    private val transferDetector: TransferBetweenAccountsDetector<TTransaction>,
    private val messageFormatter: TransactionMessageFormatter<TTransaction>,
    private val telegramMessageSender: TelegramMessageSender
) {

    suspend fun process() {
        logStatement()
        processStatement()
    }

    private suspend fun processStatement() {
        val maybeTransfer =
            transferDetector.checkTransfer()

        val transaction = transactionFactory.create(maybeTransfer)
        val message = messageFormatter.format(transaction)

        telegramMessageSender.send(statementItem.accountId, message)
    }

    private suspend fun logStatement() {
        val alias = bankAccounts.getAccountAlias(statementItem.accountId)
        with(statementItem) {
            log.info {
                "Incoming transaction from $alias's account.\n" +
                    if (log.isTraceEnabled()) {
                        this.pp()
                    } else {
                        "\tAmount: ${amount}\n" +
                            "\tDescription: $description" +
                            "\tMemo: $comment"
                    }
            }
        }
    }
}
