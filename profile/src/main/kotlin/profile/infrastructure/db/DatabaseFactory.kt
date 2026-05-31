package profile.infrastructure.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import profile.infrastructure.config.PostgresConfig
import javax.sql.DataSource

object DatabaseFactory {
    fun init(config: PostgresConfig): DataSource {
        val migrationDs = HikariDataSource(baseConfig(config))
        runFlyway(migrationDs)
        migrationDs.close()

        return HikariDataSource(baseConfig(config).apply {
            schema = "identity"
        })
    }

    private fun baseConfig(config: PostgresConfig): HikariConfig {
        return HikariConfig().apply {
            jdbcUrl = config.url
            username = config.user
            password = config.password
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
    }

    private fun runFlyway(datasource: DataSource) {
        val flyway = Flyway.configure()
            .dataSource(datasource)
            .schemas("identity")
            .defaultSchema("identity")
            .createSchemas(true)
            .load()
        flyway.migrate()
    }
}
