package works.weave.socks.orders.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.connection.SocketSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;

import java.util.concurrent.TimeUnit;

@Configuration
public class MongoConfiguration extends AbstractMongoClientConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(MongoConfiguration.class);

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Value("${spring.application.name:orders}")
    private String databaseName;

    @Override
    protected String getDatabaseName() {
        return databaseName;
    }

    @Override
    protected void configureClientSettings(MongoClientSettings.Builder builder) {
        LOG.info("Configuring MongoDB client with connection pooling and retry settings");
        LOG.info("MongoDB URI: {}", mongoUri);

        // Parse connection string
        ConnectionString connectionString = new ConnectionString(mongoUri);

        // Configure connection pool settings
        ConnectionPoolSettings poolSettings = ConnectionPoolSettings.builder()
                .maxSize(100)                          // Maximum connections in pool
                .minSize(10)                           // Minimum connections to maintain
                .maxWaitTime(30, TimeUnit.SECONDS)     // Max time to wait for connection
                .maxConnectionIdleTime(60, TimeUnit.SECONDS)  // Close idle connections after 60s
                .maxConnectionLifeTime(0, TimeUnit.SECONDS)   // No max lifetime (0 = unlimited)
                .maintenanceInitialDelay(0, TimeUnit.SECONDS)
                .maintenanceFrequency(10, TimeUnit.SECONDS)   // Check pool every 10s
                .maxConnecting(2)                      // Max concurrent connection establishments
                .build();

        // Configure socket settings with retry
        SocketSettings socketSettings = SocketSettings.builder()
                .connectTimeout(10, TimeUnit.SECONDS)   // Timeout for initial connection
                .readTimeout(30, TimeUnit.SECONDS)      // Timeout for read operations
                .build();

        // Apply settings
        builder.applyConnectionString(connectionString)
                .applyToConnectionPoolSettings(b -> {
                    b.applySettings(poolSettings);
                    LOG.info("Connection pool configured: maxSize=100, minSize=10, maxWait=30s");
                })
                .applyToSocketSettings(b -> {
                    b.applySettings(socketSettings);
                    LOG.info("Socket settings configured: connectTimeout=10s, readTimeout=30s");
                })
                .applyToClusterSettings(b -> {
                    b.serverSelectionTimeout(10, TimeUnit.SECONDS);  // Timeout for server selection
                    LOG.info("Cluster settings configured: serverSelectionTimeout=10s");
                })
                .retryReads(true)      // Enable retry for read operations
                .retryWrites(true);    // Enable retry for write operations

        LOG.info("MongoDB client configuration completed with retry enabled");
    }
}
