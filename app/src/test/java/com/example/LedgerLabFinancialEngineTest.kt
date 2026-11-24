package com.example

import com.example.domain.Money
import com.example.domain.PostingSide
import com.example.domain.TransactionStatus
import com.example.engine.CryptoUtils
import com.example.engine.LedgerLabEngine
import com.example.engine.LedgerLabException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies all 14 core financial integrity, concurrency, idempotency,
 * immutability, RBAC, and cryptographic webhook invariants.
 */
class LedgerLabFinancialEngineTest {

    private val ws = "ws_sandbox"
    private val owner = "usr_demo_owner"
    private val admin = "usr_demo_admin"
    private val viewer = "usr_demo_viewer"
    private val accAnanya = "acc_ananya_main"
    private val accVikram = "acc_vikram_main"

    @Test
    fun `1 valid deposit generates balanced postings`() = runTest {
        val engine = LedgerLabEngine(seedOnInit = true)
        val before = engine.computeLedgerDerivedBalancePaise(accAnanya)
        val tx = engine.deposit(ws, owner, accAnanya, 25_000L, "TEST-DEP", "Test deposit", "dep_key_1")
        assertEquals(TransactionStatus.SUCCEEDED, tx.status)
        assertNotNull(tx.journalEntryId)
        val lines = engine.snapshot.value.postings.filter { it.journalEntryId == tx.journalEntryId }
        assertEquals(2, lines.size)
        val debits = lines.filter { it.side == PostingSide.DEBIT }.sumOf { it.amountPaise }
        val credits = lines.filter { it.side == PostingSide.CREDIT }.sumOf { it.amountPaise }
        assertEquals(debits, credits)
        assertEquals(before + 25_000L, engine.computeLedgerDerivedBalancePaise(accAnanya))
    }

    @Test
    fun `2 valid transfer generates balanced postings and preserves total money`() = runTest {
        val engine = LedgerLabEngine(seedOnInit = true)
        val senderBefore = engine.computeLedgerDerivedBalancePaise(accAnanya)
        val recvBefore = engine.computeLedgerDerivedBalancePaise(accVikram)
        val tx = engine.transfer(ws, owner, accAnanya, accVikram, 10_000L, "TEST-TRF", "Test transfer", "trf_key_1")
        assertEquals(TransactionStatus.SUCCEEDED, tx.status)
        val senderAfter = engine.computeLedgerDerivedBalancePaise(accAnanya)
        val recvAfter = engine.computeLedgerDerivedBalancePaise(accVikram)
        assertEquals(senderBefore - 10_000L, senderAfter)
        assertEquals(recvBefore + 10_000L, recvAfter)
        assertEquals(senderBefore + recvBefore, senderAfter + recvAfter)
    }

    @Test
    fun `3 valid withdrawal generates balanced postings`() = runTest {
        val engine = LedgerLabEngine(seedOnInit = true)
        val before = engine.computeLedgerDerivedBalancePaise(accVikram)
        val tx = engine.withdraw(ws, owner, accVikram, 5_000L, "TEST-WDR", "Test withdrawal", "wdr_key_1")
        assertEquals(TransactionStatus.SUCCEEDED, tx.status)
        val lines = engine.snapshot.value.postings.filter { it.journalEntryId == tx.journalEntryId }
        assertEquals(2, lines.size)
        assertEquals(before - 5_000L, engine.computeLedgerDerivedBalancePaise(accVikram))
    }

    @Test
    fun `4 & 5 failed transaction does not change balances or create posted journal`() = runTest {
        val engine = LedgerLabEngine(seedOnInit = true)
        val senderBefore = engine.computeLedgerDerivedBalancePaise(accVikram)
        val recvBefore = engine.computeLedgerDerivedBalancePaise(accAnanya)
        val journalsBefore = engine.snapshot.value.journals.size

        val tx = engine.transfer(
            ws, owner, accVikram, accAnanya,
            senderBefore + 1_000_000L, "FAIL-NSF", "Overdraft attempt", "fail_key_1"
        )
        assertEquals(TransactionStatus.FAILED, tx.status)
        assertNull(tx.journalEntryId)
        assertEquals(senderBefore, engine.computeLedgerDerivedBalancePaise(accVikram))
        assertEquals(recvBefore, engine.computeLedgerDerivedBalancePaise(accAnanya))
        assertEquals(journalsBefore, engine.snapshot.value.journals.size)
    }

    @Test
    fun `6 customer cannot overdraw an account via withdrawal`() = runTest {
        val engine = LedgerLabEngine(seedOnInit = true)
        val bal = engine.computeLedgerDerivedBalancePaise(accVikram)
        val tx = engine.withdraw(ws, owner, accVikram, bal + 100L, "OVERDRAW", "Exceeds balance", "od_key_1")
        assertEquals(TransactionStatus.FAILED, tx.status)
        assertEquals(bal, engine.computeLedgerDerivedBalancePaise(accVikram))
    }

    @Test
    fun `7 duplicate idempotency key replays same result and rejects mutated payload with 409`() = runTest {
        val engine = LedgerLabEngine(seedOnInit = true)
        val balBefore = engine.computeLedgerDerivedBalancePaise(accAnanya)
        val tx1 = engine.transfer(ws, owner, accAnanya, accVikram, 5_000L, "IDEM-1", "Original", "idem_dup_1")
        val tx2 = engine.transfer(ws, owner, accAnanya, accVikram, 5_000L, "IDEM-1", "Original", "idem_dup_1")
        assertEquals(tx1.id, tx2.id)
        assertTrue(tx2.idempotencyReplayed)
        assertEquals(balBefore - 5_000L, engine.computeLedgerDerivedBalancePaise(accAnanya))

        var caughtStatus = 0
        try {
            engine.transfer(ws, owner, accAnanya, accVikram, 6_000L, "IDEM-1", "Tampered", "idem_dup_1")
        } catch (ex: LedgerLabException) {
            caughtStatus = ex.httpStatus
        }
        assertEquals(409, caughtStatus)
    }

    @Test
    fun `8 concurrent transfers cannot overdraw an account`() = runTest {
        val engine = LedgerLabEngine(seedOnInit = true)
        val cust = engine.createCustomer(ws, owner, "Race Tester", "race@example.in", "Test")
        val source = engine.createAccount(ws, owner, cust.id, "Race Source")
        val target = engine.createAccount(ws, owner, cust.id, "Race Target")
        engine.deposit(ws, owner, source.id, 10_000L, "RACE-FUND", "₹100.00 seed", "race_dep_1")

        // Launch 5 concurrent transfers of ₹60.00 each against ₹100.00 balance; at most 1 may succeed!
        val results = (1..5).map { idx ->
            async {
                engine.transfer(ws, owner, source.id, target.id, 6_000L, "RACE-$idx", "Concurrent", "race_k_$idx")
            }
        }.awaitAll()

        val succeeded = results.count { it.status == TransactionStatus.SUCCEEDED }
        val failed = results.count { it.status == TransactionStatus.FAILED }
        assertEquals(1, succeeded)
        assertEquals(4, failed)
        assertEquals(4_000L, engine.computeLedgerDerivedBalancePaise(source.id))
        assertEquals(6_000L, engine.computeLedgerDerivedBalancePaise(target.id))
    }

    @Test
    fun `9 every posted journal in engine balances`() = runTest {
        val engine = LedgerLabEngine(seedOnInit = true)
        val snap = engine.snapshot.value
        assertTrue(snap.journals.isNotEmpty())
        snap.journals.forEach { journal ->
            val lines = snap.postings.filter { it.journalEntryId == journal.id }
            assertTrue("Journal ${journal.id} has < 2 lines", lines.size >= 2)
            val debits = lines.filter { it.side == PostingSide.DEBIT }.sumOf { it.amountPaise }
            val credits = lines.filter { it.side == PostingSide.CREDIT }.sumOf { it.amountPaise }
            assertEquals("Journal ${journal.id} unbalanced", debits, credits)
        }
    }

    @Test
    fun `10 reversals generate opposing postings and restore balances`() = runTest {
        val engine = LedgerLabEngine(seedOnInit = true)
        val senderBefore = engine.computeLedgerDerivedBalancePaise(accAnanya)
        val recvBefore = engine.computeLedgerDerivedBalancePaise(accVikram)
        val tx = engine.transfer(ws, owner, accAnanya, accVikram, 20_000L, "REV-TEST", "Will reverse", "rev_k_1")
        assertEquals(senderBefore - 20_000L, engine.computeLedgerDerivedBalancePaise(accAnanya))

        val reversal = engine.reverseTransaction(ws, admin, tx.id, "Test reversal")
        assertEquals(TransactionStatus.SUCCEEDED, reversal.status)
        assertEquals(senderBefore, engine.computeLedgerDerivedBalancePaise(accAnanya))
        assertEquals(recvBefore, engine.computeLedgerDerivedBalancePaise(accVikram))
    }

    @Test
    fun `11 posted ledger records cannot be edited`() {
        val engine = LedgerLabEngine(seedOnInit = true)
        val firstJournalId = engine.snapshot.value.journals.first().id
        var status = 0
        try {
            engine.attemptModifyPostedJournal(firstJournalId)
        } catch (ex: LedgerLabException) {
            status = ex.httpStatus
        }
        assertEquals(403, status)
    }

    @Test
    fun `12 unauthorized users cannot access another workspace or mutate as Viewer`() = runTest {
        val engine = LedgerLabEngine(seedOnInit = true)
        // Viewer is NOT a member of ws_escrow
        var crossWsStatus = 0
        try {
            engine.createCustomer("ws_escrow", viewer, "Intruder", "intruder@example.in", "Test")
        } catch (ex: LedgerLabException) {
            crossWsStatus = ex.httpStatus
        }
        assertEquals(403, crossWsStatus)

        // Viewer in ws_sandbox cannot mutate financials
        var rbacStatus = 0
        try {
            engine.transfer(ws, viewer, accAnanya, accVikram, 1_000L, "RBAC", "Should fail", "rbac_1")
        } catch (ex: LedgerLabException) {
            rbacStatus = ex.httpStatus
        }
        assertEquals(403, rbacStatus)
    }

    @Test
    fun `13 API key revocation prevents further access`() = runTest {
        val engine = LedgerLabEngine(seedOnInit = true)
        val created = engine.createApiKey(ws, owner, "Test Key", listOf("read:ledger"))
        val authOk = engine.authenticateApiKey(created.plaintextKeyOnce, ws)
        assertEquals(created.record.id, authOk.id)

        engine.revokeApiKey(ws, owner, created.record.id)
        var status = 0
        try {
            engine.authenticateApiKey(created.plaintextKeyOnce, ws)
        } catch (ex: LedgerLabException) {
            status = ex.httpStatus
        }
        assertEquals(401, status)
    }

    @Test
    fun `14 invalid webhook signatures fail verification`() {
        val secret = "whsec_test_secret_123"
        val payload = """{"event":"transaction.succeeded","amountPaise":10000}"""
        val sigHeader = CryptoUtils.signWebhookPayload(secret, 1732400000L, payload)
        assertTrue(CryptoUtils.verifyWebhookSignature(secret, sigHeader, payload))
        assertFalse(CryptoUtils.verifyWebhookSignature("wrong_secret", sigHeader, payload))
        assertFalse(CryptoUtils.verifyWebhookSignature(secret, sigHeader, """{"event":"tampered"}"""))
    }

    @Test
    fun `money parser handles INR integer paise without floating point`() {
        assertEquals(10000L, Money.parseInrToPaise("100.00").getOrThrow())
        assertEquals(150050L, Money.parseInrToPaise("1500.50").getOrThrow())
        assertEquals("₹1,50,000.50", Money.formatInr(15_000_050L))
        assertTrue(Money.parseInrToPaise("10.123").isFailure)
        assertTrue(Money.parseInrToPaise("-50.00").isFailure)
    }
}
