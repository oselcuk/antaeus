package io.pleo.antaeus.models

import org.joda.time.DateTime

data class BillingCycle (
        val scheduledDate: DateTime,
        val status: InvoiceStatus
)