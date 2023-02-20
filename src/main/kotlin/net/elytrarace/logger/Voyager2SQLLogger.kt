package net.elytrarace.logger

import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import java.util.logging.Logger

class Voyager2SQLLogger(private val logger: Logger) : SqlLogger {
    override fun log(context: StatementContext, transaction: Transaction) {
        logger.finest("SQL: " + context.expandArgs(transaction))
    }
}