package io.pleo.antaeus.models

import org.joda.time.DateTime

data class BillingCycle (
        val scheduledOn: DateTime,
        val scheduledFor: DateTime,
        val fulfilledOn: DateTime?
)