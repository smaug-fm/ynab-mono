package io.github.smaugfm.monobudget.ynab

import io.github.smaugfm.monobudget.common.account.BankAccountService
import io.github.smaugfm.monobudget.common.account.TransferBetweenAccountsDetector
import io.github.smaugfm.monobudget.common.lifecycle.StatementItemProcessor
import io.github.smaugfm.monobudget.common.lifecycle.StatementProcessingScopeComponent
import io.github.smaugfm.monobudget.common.model.financial.StatementItem
import io.github.smaugfm.monobudget.common.telegram.TelegramMessageSender
import io.github.smaugfm.monobudget.common.transaction.TransactionFactory
import io.github.smaugfm.monobudget.common.transaction.TransactionMessageFormatter
import io.github.smaugfm.monobudget.ynab.model.YnabSaveTransaction
import io.github.smaugfm.monobudget.ynab.model.YnabTransactionDetail
import org.koin.core.annotation.Scope
import org.koin.core.annotation.Scoped

@Scoped
@Scope(StatementProcessingScopeComponent::class)
class YnabStatementItemProcessor(
    statementItem: StatementItem,
    transactionFactory: TransactionFactory<YnabTransactionDetail, YnabSaveTransaction>,
    bankAccounts: BankAccountService,
    transferDetector: TransferBetweenAccountsDetector<YnabTransactionDetail>,
    messageFormatter: TransactionMessageFormatter<YnabTransactionDetail>,
    telegramMessageSender: TelegramMessageSender
) : StatementItemProcessor<YnabTransactionDetail, YnabSaveTransaction>(
    statementItem,
    transactionFactory,
    bankAccounts,
    transferDetector,
    messageFormatter,
    telegramMessageSender
)
