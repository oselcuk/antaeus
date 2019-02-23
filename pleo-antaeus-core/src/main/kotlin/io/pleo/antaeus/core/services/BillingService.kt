package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.BillingCycle
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.joda.time.DateTime
import java.util.*
import kotlin.concurrent.schedule


// Takes the date/time of the last billing and returns the next
//  date/time a billing cycle should be scheduled
typealias Scheduler = (DateTime) -> DateTime?

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val dal: AntaeusDal,
    private val scheduler: Scheduler = { date ->
        val normDate = if (date.isBeforeNow) DateTime.now() else date
        normDate
                .plusMonths(1)
                .withDayOfMonth(1)
                .withTimeAtStartOfDay()
    },
    private val networkExceptionRetries: List<Float> = listOf(0.5f, 2f, 8f)
) {
    private val timer = Timer()
    private val logger = KotlinLogging.logger {}

    fun checkSchedule() {
        logger.info { "Checking schedule" }
        val cycle = dal.fetchCurrentBillingCycle()
        if (cycle != null) {
            billCustomers(cycle)
        } else {
            scheduleNextBilling(cycle)
        }
    }

    // The "unused" members of the anonymous type are actually used for jsonifying the billing cycles
    @Suppress("unused")
    fun fetchAllTimestamp(): List<Any> {
        return dal.fetchBillingCycles().map { cycle -> object {
            val scheduledOn = cycle.scheduledOn.millis / 1_000
            val scheduledFor = cycle.scheduledFor.millis / 1_000
            val fulfilledOn = if (cycle.fulfilledOn == null) 0 else cycle.fulfilledOn!!.millis / 1_000
        } }
    }

    private fun billCustomers(cycle: BillingCycle) {
        logger.info { "Billing customers scheduled for ${cycle.scheduledFor}" }
        val start = DateTime.now().millis
        val pendingInvoices = dal.fetchPendingInvoices()
        logger.info { "Found ${pendingInvoices.size} pending invoices" }

        val failures = runBlocking {
            coroutineScope {
                pendingInvoices
                        .map { async { billCustomer(it) } }
                        .sumBy { res -> if (res.await()) 1 else 0 }
            }
        }

        val duration = DateTime.now().millis - start
        logger.info { "Done billing with $failures failures in ${duration/1_000f} seconds" }
        dal.finalizeBillingCycleForDate(cycle.scheduledFor)
        scheduleNextBilling(cycle)
    }

    private suspend fun billCustomer(invoice: Invoice): Boolean {
        var success = false
        val numRetries = networkExceptionRetries.size
        for (retryIndex in 0..numRetries) {
            try {
                success = paymentProvider.charge(invoice)
                break
            }
            catch (e: CustomerNotFoundException) {
                logger.warn(e) {"while processing $invoice"}
                break
            }
            catch (e: CurrencyMismatchException) {
                logger.warn(e) {"while processing $invoice"}
                break
            }
            catch (e: NetworkException) {
                if (retryIndex < numRetries) {
                    val waitSeconds = networkExceptionRetries[retryIndex]
                    logger.debug(e) {"while processing $invoice\nWaiting $waitSeconds seconds before retrying..."}
                    delay((waitSeconds * 1_000).toLong())
                } else {
                    logger.warn(e) { "while processing $invoice" }
                }
            }
        }
        if (success) {
            dal.setInvoiceStatus(invoice, InvoiceStatus.PAID)
        }
        return success
    }

    private fun scheduleNextBilling(currentCycle: BillingCycle?) {
        var nextDate = dal.fetchCurrentBillingCycle()?.scheduledFor
        if (nextDate == null) {
            nextDate = scheduler(currentCycle?.scheduledFor ?: DateTime(0))
            if (nextDate != null) {
                dal.createBillingCycleFor(nextDate)
            }
        }
        if (nextDate == null) {
            nextDate = DateTime.now().plusDays(1)
        } else {
            logger.info{ "Scheduling next billing for $nextDate" }
        }
        timer.schedule(nextDate!!.toDate()) {checkSchedule()}
    }
}