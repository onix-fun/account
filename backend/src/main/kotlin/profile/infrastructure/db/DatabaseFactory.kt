package profile.infrastructure.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import profile.infrastructure.config.PostgresConfig
import javax.sql.DataSource

object DatabaseFactory {
    fun init(config: PostgresConfig): DataSource {
        return HikariDataSource(runtimeConfig(config))
    }

    fun migrate(config: PostgresConfig) {
        HikariDataSource(baseConfig(config)).use(::runFlyway)
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

    private fun runtimeConfig(config: PostgresConfig): HikariConfig {
        return baseConfig(config).apply {
            schema = "identity"
            if (config.url.startsWith("jdbc:postgresql:")) {
                addDataSourceProperty("currentSchema", "identity")
                connectionInitSql = "SET search_path TO identity"
            }
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
