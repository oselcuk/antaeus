package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.BillingCycle
import io.pleo.antaeus.models.InvoiceStatus
import org.joda.time.DateTime
import java.util.*
import kotlin.concurrent.schedule

// Takes the date/time of the last billing and returns the next
//  date/time a billing cycle should be scheduled
typealias Scheduler = (DateTime) -> DateTime

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val dal: AntaeusDal,
    private val scheduler: Scheduler = { date ->
        val normDate = if (date.isBeforeNow) DateTime.now() else date
        normDate
                .plusMonths(1)
                .withDayOfMonth(1)
                .withTimeAtStartOfDay()
    }
) {
    private val timer: Timer = Timer()

    private fun scheduleNextBilling() {
        var nextCycle = dal.fetchLatestBillingCycle()
        if (nextCycle?.status != InvoiceStatus.PENDING) {
            val nextDate = scheduler(
                    nextCycle?.scheduledDate ?: DateTime(0)
            )
            nextCycle = dal.createBillingCycle(nextDate)
        }
        timer.schedule(nextCycle.scheduledDate.toDate()) {checkSchedule()}
    }

    private fun billCustomers(billingCycle: BillingCycle) {
        println("Billing customers on ${billingCycle.scheduledDate}")
        dal.finalizeBillingCycleOnDate(billingCycle.scheduledDate)
    }

    fun checkSchedule() {
        println("Checking schedule on ${DateTime.now()}")
        val nextCycle = dal.fetchLatestBillingCycle()
        if (nextCycle?.status == InvoiceStatus.PENDING
                && nextCycle.scheduledDate.isBeforeNow) {
            billCustomers(nextCycle)
        }
        scheduleNextBilling()
    }
}