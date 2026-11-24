package com.example.engine

import com.example.domain.LedgerAccount
import com.example.domain.Money
import com.example.domain.RiskAction
import com.example.domain.RiskEvaluation
import com.example.domain.RiskRuleConfig
import com.example.domain.RiskRuleMatch
import com.example.domain.RiskSeverity
import com.example.domain.Transaction
import com.example.domain.TransactionStatus
import com.example.domain.TransactionType

/**
 * Deterministic, explainable 7-rule Risk & Fraud Detection Engine.
 * Uses only transaction behaviour and account activity metrics (never sensitive personal attributes).
 */
class RiskEngine {
    val defaultRules: List<RiskRuleConfig> = listOf(
        RiskRuleConfig(
            ruleCode = "HIGH_VALUE_TX",
            title = "High-Value Transaction",
            description = "Triggers when a single transaction is >= ₹50,000.00.",
            weight = 35
        ),
        RiskRuleConfig(
            ruleCode = "EXCESSIVE_FREQUENCY",
            title = "Excessive Transaction Frequency",
            description = "Triggers when >= 4 transactions occur from the sender account in 10 minutes.",
            weight = 25
        ),
        RiskRuleConfig(
            ruleCode = "MULTIPLE_FAILURES",
            title = "Multiple Recent Failures",
            description = "Triggers when >= 2 failed transactions were attempted recently on the account.",
            weight = 20
        ),
        RiskRuleConfig(
            ruleCode = "NEW_RECIPIENT_ACCOUNT",
            title = "Newly Created Recipient Account",
            description = "Triggers when the recipient virtual account is less than 24 hours old.",
            weight = 18
        ),
        RiskRuleConfig(
            ruleCode = "UNUSUAL_TX_SIZE",
            title = "Unusual Transaction Size",
            description = "Triggers when transaction exceeds 3x the account's historical average.",
            weight = 22
        ),
        RiskRuleConfig(
            ruleCode = "MULTI_NEW_RECIPIENTS",
            title = "Repeated Transfers to Multiple Recipients",
            description = "Triggers when sender dispatches to >= 3 distinct recipients within 24 hours.",
            weight = 20
        ),
        RiskRuleConfig(
            ruleCode = "VELOCITY_LIMIT_EXCEEDED",
            title = "24h Velocity Limit Exceeded",
            description = "Triggers when cumulative 24h outgoing volume reaches >= ₹1,50,000.00.",
            weight = 30
        )
    )

    fun evaluate(
        workspaceId: String,
        transactionId: String,
        type: TransactionType,
        amountPaise: Long,
        senderAccount: LedgerAccount?,
        receiverAccount: LedgerAccount?,
        recentTransactions: List<Transaction>,
        enabledRuleCodes: Set<String>,
        nowMs: Long
    ): RiskEvaluation {
        val matches = mutableListOf<RiskRuleMatch>()

        // Rule 1: High-value transaction >= ₹50,000 (5,000,000 paise)
        if ("HIGH_VALUE_TX" in enabledRuleCodes && amountPaise >= 5_000_000L) {
            matches += RiskRuleMatch(
                ruleCode = "HIGH_VALUE_TX",
                title = "High-Value Transaction",
                scoreContribution = if (amountPaise >= 15_000_000L) 45 else 35,
                reason = "Transaction amount ${Money.formatInr(amountPaise)} exceeds high-value threshold ₹50,000.00."
            )
        }

        val senderId = senderAccount?.id
        val tenMinAgo = nowMs - 10 * 60 * 1000L
        val oneDayAgo = nowMs - 24 * 60 * 60 * 1000L

        if (senderId != null && !senderAccount.isSystemAccount) {
            val senderRecent10m = recentTransactions.filter {
                it.senderAccountId == senderId && it.createdAt >= tenMinAgo
            }
            if ("EXCESSIVE_FREQUENCY" in enabledRuleCodes && senderRecent10m.size >= 4) {
                matches += RiskRuleMatch(
                    ruleCode = "EXCESSIVE_FREQUENCY",
                    title = "Excessive Transaction Frequency",
                    scoreContribution = 25,
                    reason = "${senderRecent10m.size} transactions originated from ${senderAccount.accountNumber} within 10 minutes."
                )
            }

            val recentFailures = recentTransactions.count {
                it.senderAccountId == senderId &&
                    it.status == TransactionStatus.FAILED &&
                    it.createdAt >= oneDayAgo
            }
            if ("MULTIPLE_FAILURES" in enabledRuleCodes && recentFailures >= 2) {
                matches += RiskRuleMatch(
                    ruleCode = "MULTIPLE_FAILURES",
                    title = "Multiple Recent Failures",
                    scoreContribution = 20,
                    reason = "$recentFailures failed attempts recorded on ${senderAccount.accountNumber} in the last 24h."
                )
            }

            val historicalSucceeded = recentTransactions.filter {
                it.senderAccountId == senderId && it.status == TransactionStatus.SUCCEEDED
            }
            if ("UNUSUAL_TX_SIZE" in enabledRuleCodes && historicalSucceeded.size >= 2) {
                val avgPaise = historicalSucceeded.sumOf { it.amountPaise } / historicalSucceeded.size
                if (avgPaise > 0L && amountPaise >= avgPaise * 3L) {
                    matches += RiskRuleMatch(
                        ruleCode = "UNUSUAL_TX_SIZE",
                        title = "Unusual Transaction Size",
                        scoreContribution = 22,
                        reason = "Amount ${Money.formatInr(amountPaise)} is >= 3x historical average (${Money.formatInr(avgPaise)})."
                    )
                }
            }

            val distinctRecipients24h = recentTransactions
                .filter {
                    it.senderAccountId == senderId &&
                        it.type == TransactionType.TRANSFER &&
                        it.createdAt >= oneDayAgo &&
                        it.receiverAccountId != null
                }
                .mapNotNull { it.receiverAccountId }
                .toSet()
            if ("MULTI_NEW_RECIPIENTS" in enabledRuleCodes && distinctRecipients24h.size >= 3) {
                matches += RiskRuleMatch(
                    ruleCode = "MULTI_NEW_RECIPIENTS",
                    title = "Repeated Transfers to Multiple Recipients",
                    scoreContribution = 20,
                    reason = "Account sent transfers to ${distinctRecipients24h.size} distinct recipients in 24h."
                )
            }

            val volume24h = recentTransactions
                .filter {
                    it.senderAccountId == senderId &&
                        it.status != TransactionStatus.FAILED &&
                        it.createdAt >= oneDayAgo
                }
                .sumOf { it.amountPaise } + amountPaise
            if ("VELOCITY_LIMIT_EXCEEDED" in enabledRuleCodes && volume24h >= 15_000_000L) {
                matches += RiskRuleMatch(
                    ruleCode = "VELOCITY_LIMIT_EXCEEDED",
                    title = "24h Velocity Limit Exceeded",
                    scoreContribution = 30,
                    reason = "Projected 24h outgoing volume ${Money.formatInr(volume24h)} reaches velocity cap ₹1,50,000.00."
                )
            }
        }

        // Rule 4: Newly created recipient account (< 24h old)
        if ("NEW_RECIPIENT_ACCOUNT" in enabledRuleCodes &&
            receiverAccount != null &&
            !receiverAccount.isSystemAccount &&
            type == TransactionType.TRANSFER &&
            (nowMs - receiverAccount.createdAt) < 24 * 60 * 60 * 1000L
        ) {
            matches += RiskRuleMatch(
                ruleCode = "NEW_RECIPIENT_ACCOUNT",
                title = "Newly Created Recipient Account",
                scoreContribution = 18,
                reason = "Recipient account ${receiverAccount.accountNumber} was created less than 24 hours ago."
            )
        }

        val totalScore = matches.sumOf { it.scoreContribution }.coerceIn(0, 100)
        val severity = when {
            totalScore >= 80 -> RiskSeverity.CRITICAL
            totalScore >= 60 -> RiskSeverity.HIGH
            totalScore >= 30 -> RiskSeverity.MEDIUM
            else -> RiskSeverity.LOW
        }
        val recommendedAction = when (severity) {
            RiskSeverity.CRITICAL -> RiskAction.HOLD_FOR_REVIEW
            RiskSeverity.HIGH -> RiskAction.HOLD_FOR_REVIEW
            RiskSeverity.MEDIUM -> RiskAction.FLAG_MONITOR
            RiskSeverity.LOW -> RiskAction.ALLOW
        }

        return RiskEvaluation(
            id = CryptoUtils.generatePrefixedId("rsk"),
            workspaceId = workspaceId,
            transactionId = transactionId,
            customerId = senderAccount?.customerId ?: receiverAccount?.customerId,
            score = totalScore,
            severity = severity,
            recommendedAction = recommendedAction,
            rulesTriggered = matches,
            evaluatedAt = nowMs
        )
    }
}
