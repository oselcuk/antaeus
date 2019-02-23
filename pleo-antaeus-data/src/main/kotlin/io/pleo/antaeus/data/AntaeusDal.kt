/*
    Implements the data access layer (DAL).
    This file implements the database queries used to fetch and insert rows in our database tables.

    See the `mappings` module for the conversions between database rows and Kotlin objects.
 */

package io.pleo.antaeus.data

import io.pleo.antaeus.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

class AntaeusDal(private val db: Database) {
    fun fetchInvoice(id: Int): Invoice? {
        // transaction(db) runs the internal query as a new database transaction.
        return transaction(db) {
            // Returns the first invoice with matching id.
            InvoiceTable
                .select { InvoiceTable.id.eq(id) }
                .firstOrNull()
                ?.toInvoice()
        }
    }

    fun fetchInvoices(): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                .selectAll()
                .map { it.toInvoice() }
        }
    }

    fun fetchPendingInvoices(): List<Invoice> {
        val pendingString = InvoiceStatus.PENDING.toString()
        return transaction(db) {
            InvoiceTable
                    .select { InvoiceTable.status.eq(pendingString) }
                    .map { it.toInvoice() }
        }
    }

    fun createInvoice(amount: Money, customer: Customer, status: InvoiceStatus = InvoiceStatus.PENDING): Invoice? {
        val id = transaction(db) {
            // Insert the invoice and returns its new id.
            InvoiceTable
                .insert {
                    it[this.value] = amount.value
                    it[this.currency] = amount.currency.toString()
                    it[this.status] = status.toString()
                    it[this.customerId] = customer.id
                } get InvoiceTable.id
        }

        return fetchInvoice(id!!)
    }

    fun setInvoiceStatus(invoice: Invoice, status: InvoiceStatus) {
        transaction(db) {
            InvoiceTable.update ({InvoiceTable.id.eq(invoice.id)}) {
                it[InvoiceTable.status] = status.toString()
            }
        }
    }

    fun fetchCustomer(id: Int): Customer? {
        return transaction(db) {
            CustomerTable
                .select { CustomerTable.id.eq(id) }
                .firstOrNull()
                ?.toCustomer()
        }
    }

    fun fetchCustomers(): List<Customer> {
        return transaction(db) {
            CustomerTable
                .selectAll()
                .map { it.toCustomer() }
        }
    }

    fun createCustomer(currency: Currency): Customer? {
        val id = transaction(db) {
            // Insert the customer and return its new id.
            CustomerTable.insert {
                it[this.currency] = currency.toString()
            } get CustomerTable.id
        }

        return fetchCustomer(id!!)
    }

    fun fetchBillingCycles(): List<BillingCycle> {
        return transaction(db) {
            BillingCycleTable
                    .selectAll()
                    .map {it.toBillingCycle() }
        }
    }

    // Returns the latest billing cycle before now if it is unfulfilled
    // otherwise returns the earliest billing cycle after now if it is unfulfilled
    // otherwise returns null
    fun fetchCurrentBillingCycle(): BillingCycle? {
        val now = DateTime.now()
        return transaction(db) {
            var cycle = BillingCycleTable
                    .select {BillingCycleTable.scheduledFor.less(now)}
                    .orderBy(BillingCycleTable.scheduledFor to false)
                    .firstOrNull()
                    ?.toBillingCycle()
            if (cycle == null || cycle.fulfilledOn != null) {
                cycle = BillingCycleTable
                        .select {BillingCycleTable.scheduledFor.greater(now)}
                        .orderBy(BillingCycleTable.scheduledFor to true)
                        .firstOrNull()
                        ?.toBillingCycle()
            }
            if (cycle?.fulfilledOn != null) {
                cycle = null
            }
            cycle
        }
    }

    fun createBillingCycleFor(date: DateTime): BillingCycle {
        val now = DateTime.now()
        transaction(db) {
            BillingCycleTable.insert {
                it[this.scheduledOn] = now
                it[this.scheduledFor] = date
                it[this.fulfilledOn] = null
            }
        }
        return BillingCycle(now, date, null)
    }

    fun finalizeBillingCycleForDate(date: DateTime) {
        val now = DateTime.now()
        transaction(db) {
            BillingCycleTable.update(
                    {BillingCycleTable.scheduledFor.eq(date)}) {
                it[BillingCycleTable.fulfilledOn] = now
            }
        }
    }
}
