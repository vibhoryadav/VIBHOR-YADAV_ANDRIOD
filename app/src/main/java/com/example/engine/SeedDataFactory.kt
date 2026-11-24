package com.example.engine

import com.example.domain.AccountCategory
import com.example.domain.AccountStatus
import com.example.domain.ApiKeyRecord
import com.example.domain.Customer
import com.example.domain.CustomerStatus
import com.example.domain.LedgerAccount
import com.example.domain.Money
import com.example.domain.NormalBalance
import com.example.domain.SimulationScenario
import com.example.domain.User
import com.example.domain.WebhookEndpoint
import com.example.domain.WebhookEventType
import com.example.domain.Workspace
import com.example.domain.WorkspaceMember
import com.example.domain.WorkspaceRole
import kotlinx.coroutines.runBlocking

object SeedDataFactory {
    fun seed(engine: LedgerLabEngine) {
        val now = System.currentTimeMillis()
        val day = 24L * 60L * 60L * 1000L

        // 1. Users across RBAC roles
        val owner = User(
            id = "usr_demo_owner",
            name = "Aarav Mehta",
            email = "aarav@ledgerlab.dev",
            passwordHash = CryptoUtils.hashPassword("LedgerLab@2026"),
            createdAt = now - 30 * day
        )
        val admin = User(
            id = "usr_demo_admin",
            name = "Priya Sharma",
            email = "priya.admin@ledgerlab.dev",
            passwordHash = CryptoUtils.hashPassword("LedgerLab@2026"),
            createdAt = now - 25 * day
        )
        val developer = User(
            id = "usr_demo_dev",
            name = "Rohan Kulkarni",
            email = "rohan.dev@ledgerlab.dev",
            passwordHash = CryptoUtils.hashPassword("LedgerLab@2026"),
            createdAt = now - 20 * day
        )
        val viewer = User(
            id = "usr_demo_viewer",
            name = "Neha Kapoor",
            email = "neha.audit@ledgerlab.dev",
            passwordHash = CryptoUtils.hashPassword("LedgerLab@2026"),
            createdAt = now - 15 * day
        )
        listOf(owner, admin, developer, viewer).forEach { engine.seedUser(it) }

        // 2. Two Workspaces to demonstrate workspace isolation
        val wsSandbox = Workspace(
            id = "ws_sandbox",
            name = "LedgerLab Sandbox (INR)",
            environmentLabel = "DEMO ENVIRONMENT",
            createdAt = now - 30 * day
        )
        val wsEscrow = Workspace(
            id = "ws_escrow",
            name = "Marketplace Escrow Sim",
            environmentLabel = "DEMO ENVIRONMENT",
            createdAt = now - 14 * day
        )
        engine.seedWorkspace(wsSandbox)
        engine.seedWorkspace(wsEscrow)

        // Memberships (note: viewer is NOT in ws_escrow to showcase strict workspace isolation)
        listOf(
            WorkspaceMember(wsSandbox.id, owner.id, owner.name, owner.email, WorkspaceRole.OWNER, now - 30 * day),
            WorkspaceMember(wsSandbox.id, admin.id, admin.name, admin.email, WorkspaceRole.ADMIN, now - 25 * day),
            WorkspaceMember(wsSandbox.id, developer.id, developer.name, developer.email, WorkspaceRole.DEVELOPER, now - 20 * day),
            WorkspaceMember(wsSandbox.id, viewer.id, viewer.name, viewer.email, WorkspaceRole.VIEWER, now - 15 * day),
            WorkspaceMember(wsEscrow.id, owner.id, owner.name, owner.email, WorkspaceRole.OWNER, now - 14 * day),
            WorkspaceMember(wsEscrow.id, admin.id, admin.name, admin.email, WorkspaceRole.ADMIN, now - 14 * day)
        ).forEach { engine.seedMember(it) }

        // 3. System Clearing Accounts
        engine.seedAccount(
            LedgerAccount(
                id = "acc_clearing_ws_sandbox",
                workspaceId = wsSandbox.id,
                customerId = null,
                accountNumber = "SYS-CLR-SBOX",
                name = "Simulated External Clearing Account",
                category = AccountCategory.ASSET,
                normalBalance = NormalBalance.DEBIT,
                status = AccountStatus.ACTIVE,
                isSystemAccount = true,
                createdAt = now - 30 * day
            )
        )
        engine.seedAccount(
            LedgerAccount(
                id = "acc_clearing_ws_escrow",
                workspaceId = wsEscrow.id,
                customerId = null,
                accountNumber = "SYS-CLR-ESCR",
                name = "Simulated External Clearing Account",
                category = AccountCategory.ASSET,
                normalBalance = NormalBalance.DEBIT,
                status = AccountStatus.ACTIVE,
                isSystemAccount = true,
                createdAt = now - 14 * day
            )
        )

        // 4. Synthetic Customers (Sandbox)
        val c1 = Customer(
            id = "cus_ananya",
            workspaceId = wsSandbox.id,
            fullName = "Ananya Verma",
            email = "ananya.verma@example.in",
            referenceCode = "CUST-1001",
            segment = "Merchant Treasury",
            status = CustomerStatus.ACTIVE,
            riskScore = 12,
            createdAt = now - 20 * day,
            updatedAt = now - 2 * day
        )
        val c2 = Customer(
            id = "cus_vikram",
            workspaceId = wsSandbox.id,
            fullName = "Vikramaditya Rao",
            email = "vikram.rao@example.in",
            referenceCode = "CUST-1002",
            segment = "SMB Payouts",
            status = CustomerStatus.ACTIVE,
            riskScore = 24,
            createdAt = now - 18 * day,
            updatedAt = now - 1 * day
        )
        val c3 = Customer(
            id = "cus_kabir",
            workspaceId = wsSandbox.id,
            fullName = "Kabir Malhotra",
            email = "kabir.m@example.in",
            referenceCode = "CUST-1003",
            segment = "Retail Wallet",
            status = CustomerStatus.ACTIVE,
            riskScore = 68,
            createdAt = now - 10 * day,
            updatedAt = now - 1 * day
        )
        val c4 = Customer(
            id = "cus_meera",
            workspaceId = wsSandbox.id,
            fullName = "Meera Iyer",
            email = "meera.iyer@example.in",
            referenceCode = "CUST-1004",
            segment = "Creator Payouts",
            status = CustomerStatus.ACTIVE,
            riskScore = 8,
            createdAt = now - 6 * day,
            updatedAt = now - 1 * day
        )
        val cEscrow = Customer(
            id = "cus_escrow_buyer",
            workspaceId = wsEscrow.id,
            fullName = "Zeta Logistics Pvt Ltd",
            email = "treasury@zetalogistics.example",
            referenceCode = "CUST-2001",
            segment = "Escrow Buyer",
            status = CustomerStatus.ACTIVE,
            riskScore = 5,
            createdAt = now - 12 * day,
            updatedAt = now - 1 * day
        )
        listOf(c1, c2, c3, c4, cEscrow).forEach { engine.seedCustomer(it) }

        // 5. Virtual Wallet Accounts
        val accAnanya = LedgerAccount(
            id = "acc_ananya_main",
            workspaceId = wsSandbox.id,
            customerId = c1.id,
            accountNumber = "LL4091827364",
            name = "Ananya Merchant Operating INR",
            category = AccountCategory.LIABILITY,
            normalBalance = NormalBalance.CREDIT,
            status = AccountStatus.ACTIVE,
            createdAt = now - 19 * day
        )
        val accVikram = LedgerAccount(
            id = "acc_vikram_main",
            workspaceId = wsSandbox.id,
            customerId = c2.id,
            accountNumber = "LL5182930475",
            name = "Vikram SMB Settlement Wallet",
            category = AccountCategory.LIABILITY,
            normalBalance = NormalBalance.CREDIT,
            status = AccountStatus.ACTIVE,
            createdAt = now - 17 * day
        )
        val accKabir = LedgerAccount(
            id = "acc_kabir_main",
            workspaceId = wsSandbox.id,
            customerId = c3.id,
            accountNumber = "LL6293048156",
            name = "Kabir Retail Wallet",
            category = AccountCategory.LIABILITY,
            normalBalance = NormalBalance.CREDIT,
            status = AccountStatus.ACTIVE,
            createdAt = now - 9 * day
        )
        val accMeera = LedgerAccount(
            id = "acc_meera_new",
            workspaceId = wsSandbox.id,
            customerId = c4.id,
            accountNumber = "LL7304958261",
            name = "Meera Creator Reserve",
            category = AccountCategory.LIABILITY,
            normalBalance = NormalBalance.CREDIT,
            status = AccountStatus.ACTIVE,
            createdAt = now - 4 * 60 * 60 * 1000L // < 24h old to trigger NEW_RECIPIENT_ACCOUNT rule naturally
        )
        val accEscrow = LedgerAccount(
            id = "acc_escrow_main",
            workspaceId = wsEscrow.id,
            customerId = cEscrow.id,
            accountNumber = "LL8492017463",
            name = "Zeta Escrow Pool INR",
            category = AccountCategory.LIABILITY,
            normalBalance = NormalBalance.CREDIT,
            status = AccountStatus.ACTIVE,
            createdAt = now - 12 * day
        )
        listOf(accAnanya, accVikram, accKabir, accMeera, accEscrow).forEach { engine.seedAccount(it) }

        // 6. Webhook Endpoints & API Keys
        engine.seedWebhookEndpoint(
            WebhookEndpoint(
                id = "whe_sandbox_primary",
                workspaceId = wsSandbox.id,
                url = "https://webhooks.sandbox.local/ledger-events",
                description = "Primary Payment Lifecycle Listener (Simulated)",
                subscribedEvents = WebhookEventType.entries.toSet(),
                signingSecretHash = CryptoUtils.sha256Hex("whsec_sandbox_demo_key_9182"),
                signingSecretPreview = "whsec_sand••••9182",
                enabled = true,
                createdAt = now - 20 * day
            ),
            rawSecret = "whsec_sandbox_demo_key_9182"
        )
        engine.seedWebhookEndpoint(
            WebhookEndpoint(
                id = "whe_sandbox_retry",
                workspaceId = wsSandbox.id,
                url = "https://fail-consumer.sandbox.local/events",
                description = "Simulated Degraded Consumer (Tests Retry Queue)",
                subscribedEvents = setOf(WebhookEventType.TRANSACTION_FAILED, WebhookEventType.RISK_ALERT_CREATED),
                signingSecretHash = CryptoUtils.sha256Hex("whsec_retry_demo_key_4410"),
                signingSecretPreview = "whsec_retr••••4410",
                enabled = true,
                createdAt = now - 10 * day
            ),
            rawSecret = "whsec_retry_demo_key_4410"
        )

        val demoRawKey = "ll_test_demo_portfolio_9a8b7c6d5e"
        engine.seedApiKey(
            ApiKeyRecord(
                id = "key_seed_primary",
                workspaceId = wsSandbox.id,
                name = "Sandbox Developer Key",
                keyPrefix = demoRawKey.take(14),
                keyHashSha256 = CryptoUtils.sha256Hex(demoRawKey),
                scopes = listOf("read:ledger", "write:transfers", "read:risk", "write:reconciliation"),
                createdByUserId = owner.id,
                createdAt = now - 14 * day,
                lastUsedAt = now - 3600_000L
            )
        )

        // 7. Execute real double-entry transactions through the engine so every seeded balance & journal is 100% genuine!
        runBlocking {
            // Deposits
            engine.deposit(wsSandbox.id, owner.id, accAnanya.id, 12_500_000L, "DEP-OPEN-01", "Initial treasury funding (₹1,25,000.00)", "seed_dep_1", now - 5 * day)
            engine.deposit(wsSandbox.id, owner.id, accVikram.id, 8_400_000L, "DEP-OPEN-02", "SMB settlement float (₹84,000.00)", "seed_dep_2", now - 4 * day)
            engine.deposit(wsSandbox.id, owner.id, accKabir.id, 4_200_000L, "DEP-OPEN-03", "Retail wallet top-up (₹42,000.00)", "seed_dep_3", now - 3 * day)
            engine.deposit(wsSandbox.id, owner.id, accMeera.id, 1_500_000L, "DEP-OPEN-04", "Creator reserve seed (₹15,000.00)", "seed_dep_4", now - 2 * day)
            engine.deposit(wsEscrow.id, owner.id, accEscrow.id, 25_000_000L, "ESC-FUND-01", "Escrow buyer funding (₹2,50,000.00)", "seed_esc_1", now - 3 * day)

            // Succeeded transfers
            engine.transfer(wsSandbox.id, developer.id, accAnanya.id, accVikram.id, 1_850_000L, "INV-2026-881", "Vendor invoice settlement", "seed_trf_1", nowOverrideMs = now - 3 * day)
            val txToReverse = engine.transfer(wsSandbox.id, developer.id, accVikram.id, accKabir.id, 450_000L, "TRF-ERR-402", "Duplicate payout batch #402", "seed_trf_2", nowOverrideMs = now - 2 * day)
            engine.transfer(wsSandbox.id, developer.id, accAnanya.id, accMeera.id, 625_000L, "PAY-CR-109", "Creator milestone #1 payout", "seed_trf_3", nowOverrideMs = now - 1 * day)

            // Reversal example (generates opposing journal)
            engine.reverseTransaction(wsSandbox.id, admin.id, txToReverse.id, "Duplicate payout batch #402 corrected via opposing reversal journal")

            // Withdrawal
            engine.withdraw(wsSandbox.id, owner.id, accVikram.id, 950_000L, "WDR-BANK-19", "Simulated NEFT payout to linked bank", "seed_wdr_1", nowOverrideMs = now - 18 * 3600_000L)

            // Failed transaction (insufficient funds — 0 postings)
            engine.transfer(wsSandbox.id, developer.id, accMeera.id, accAnanya.id, 9_900_000L, "TRF-NSF-07", "Attempted overdraft transfer", "seed_fail_1", nowOverrideMs = now - 12 * 3600_000L)

            // High-value transfer held for Risk Review (REQUIRES_REVIEW — 0 postings until approved)
            engine.transfer(wsSandbox.id, developer.id, accAnanya.id, accMeera.id, 6_500_000L, "TRF-HV-901", "High-value payout to newly onboarded creator wallet", "seed_rev_1", nowOverrideMs = now - 4 * 3600_000L)

            // Pending processing simulation
            engine.simulateScenario(wsSandbox.id, developer.id, SimulationScenario.PENDING_PROCESSING, accKabir.id, accVikram.id, 320_000L)

            // Run initial reconciliation verifying zero mismatches
            engine.runReconciliation(wsSandbox.id, admin.id, "Scheduled End-of-Day Ledger vs Cache Check")
        }
    }
}
