package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.BillingCycle
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

    fun checkSchedule() {
        log("Checking schedule")
        val cycle = dal.fetchCurrentBillingCycle()
        if (cycle != null) {
            billCustomers(cycle)
        }
        scheduleNextBilling(cycle)
    }

    fun fetchAllStringify(): List<Any> {
        return dal.fetchBillingCycles().map { cycle -> object {
            val scheduledOn = cycle.scheduledOn.toString()
            val scheduledFor = cycle.scheduledFor.toString()
            val fulfilledOn = cycle.fulfilledOn.toString()
        } }
    }

    private fun billCustomers(cycle: BillingCycle) {
        log("Billing customers scheduled for ${cycle.scheduledFor}")
        dal.finalizeBillingCycleForDate(cycle.scheduledFor)
    }

    private fun scheduleNextBilling(currentCycle: BillingCycle?) {
        var nextDate = dal.fetchCurrentBillingCycle()?.scheduledFor
        if (nextDate == null) {
            nextDate = scheduler(currentCycle?.scheduledFor ?: DateTime(0))
            dal.createBillingCycleFor(nextDate)
        }
        log("Scheduling next billing for $nextDate")
        timer.schedule(nextDate.toDate()) {checkSchedule()}
    }

    private fun log(s: String) {
        println("BILLING_LOG-${DateTime.now().toLocalTime()}: $s")
    }
}