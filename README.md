## Thought processes for solving the problem
For the solution to this problem, I put the focus on being self-contained, reliable and fast.

####Scheduling
Scheduling is done through billing cycle records in the application's database. Billing cycle records keep track of when they were created, when they are scheduled to run, and when they were fulfilled, if they have been. Upon the first `checkSchedule` call, `BillingService` finds the current billing cycle. If the due date for the cycle has passed, it is immediately fulfilled. Otherwise, `BillingService` schedules itself to fulfill the cycle when it's due.

If there are no active cycles in the database, `BillingService` creates a new billing cycle due for the start of the first of next month, inserts it to the DB and schedules itself to fulfill it. This behavior can be changed by passing a `Scheduler` to `BillingService`, which takes a time (last billing cycle date if any, Unix epoch otherwise) and returns when the next billing should occur, if any. If `scheduler` returns `null`, `BillingService` schedules itself to check for a billing cycle in 24 hours.

This approach, while contained to within the app and platform independent, also allows for delegating the scheduling to a cron job or the like, by having an external scheduled job insert billing cycle records into the application DB. It also makes it easy to schedule and run billing cycles via a rest API.

This approach is also very fault tolerant, as scheduled billing cycles will be executed even if the app is down when it is scheduled to run.

####Billing
Once `BillingService` starts processing a billing cycle, it retrieves all pending invoices before any billing. Any invoice created after that point will be billed in the next cycle. This is to prevent customers from getting billed immediately if they sign up on the 1st of a month, and to make sure that billing reliably happens at around the same time every month.

The retrieved invoices are processed in parallel. This makes sure that billing doesn't take very long if we have to wait on the payment processing for a long time, or in case of unreliable network connection. If a network exception occurs during the processing of an invoice, it is retried 3 times, waiting 0.5, 2 and 8 seconds between retries. After the 3 retries, if the payment still doesn't go through, the failure is logged and the invoice put aside for the next billing cycle. Similarly, if other unrecoverable errors like `CustomerNotFoundException` or `CurrencyMismatchException` occur, error is logged and the invoice is put aside. After all invoices are processed, billing cycle is marked fulfilled, and the next billing cycle is scheduled if any. 

## How to run
```
./docker-start.sh
```

## Libraries currently in use
* [Exposed](https://github.com/JetBrains/Exposed) - DSL for type-safe SQL
* [Javalin](https://javalin.io/) - Simple web framework (for REST)
* [kotlin-logging](https://github.com/MicroUtils/kotlin-logging) - Simple logging framework for Kotlin
* [JUnit 5](https://junit.org/junit5/) - Testing framework
* [Mockk](https://mockk.io/) - Mocking library
* [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) - Library support for Kotlin coroutines
