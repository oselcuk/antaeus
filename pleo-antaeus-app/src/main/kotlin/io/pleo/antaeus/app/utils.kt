
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.joda.time.DateTime
import java.math.BigDecimal
import kotlin.random.Random

// This will create all schemas and setup initial data
internal fun setupInitialData(dal: AntaeusDal) {
    val customers = (1..100).mapNotNull {
        dal.createCustomer(
            currency = Currency.values()[Random.nextInt(0, Currency.values().size)]
        )
    }

    customers.forEach { customer ->
        (1..10).forEach {
            dal.createInvoice(
                amount = Money(
                    value = BigDecimal(Random.nextDouble(10.0, 500.0)),
                    currency = customer.currency
                ),
                customer = customer,
                status = if (it == 1) InvoiceStatus.PENDING else InvoiceStatus.PAID
            )
        }
    }

    // Set up a billing cycle for 15 seconds from now, for testing
    println("Setting dummy billing date on ${DateTime.now()}")
    val date = DateTime.now()
    dal.createBillingCycleFor(date)
    dal.createBillingCycleFor(date.plusSeconds(15))
    dal.createBillingCycleFor(date.plusSeconds(30))
    dal.createBillingCycleFor(date.plusSeconds(60))
}

// This is the mocked instance of the payment provider
internal fun getPaymentProvider(allowExceptions: Boolean = false): PaymentProvider {
    return object : PaymentProvider {
        override fun charge(invoice: Invoice): Boolean {
            return if (allowExceptions) {
                val res = Random.nextInt(8)
                when (res) {
                    0 -> throw CustomerNotFoundException(invoice.customerId)
                    1 -> throw CurrencyMismatchException(invoice.id, invoice.customerId)
                    2,3 -> throw NetworkException()
                    else -> res % 2 == 0
                }
            } else {
                Random.nextBoolean()
            }
        }
    }
}