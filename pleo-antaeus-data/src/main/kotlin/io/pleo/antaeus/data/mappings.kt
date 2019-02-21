/*
    Defines mappings between database rows and Kotlin objects.
    To be used by `AntaeusDal`.
 */

package io.pleo.antaeus.data

import io.pleo.antaeus.models.*
import org.jetbrains.exposed.sql.ResultRow

fun ResultRow.toInvoice(): Invoice = Invoice(
    id = this[InvoiceTable.id],
    amount = Money(
        value = this[InvoiceTable.value],
        currency = Currency.valueOf(this[InvoiceTable.currency])
    ),
    status = InvoiceStatus.valueOf(this[InvoiceTable.status]),
    customerId = this[InvoiceTable.customerId]
)

fun ResultRow.toCustomer(): Customer = Customer(
    id = this[CustomerTable.id],
    currency = Currency.valueOf(this[CustomerTable.currency])
)

fun ResultRow.toBillingCycle(): BillingCycle = BillingCycle(
    scheduledDate = this[BillingCycleTable.scheduledDate],
    status = InvoiceStatus.valueOf(this[BillingCycleTable.status])
)
