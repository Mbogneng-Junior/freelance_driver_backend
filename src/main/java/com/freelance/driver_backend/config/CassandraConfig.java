package com.freelance.driver_backend.config;

import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration;
import org.springframework.data.cassandra.config.SchemaAction;
import org.springframework.data.cassandra.config.SessionBuilderConfigurer;
import org.springframework.data.cassandra.core.cql.keyspace.CreateKeyspaceSpecification;
import org.springframework.data.cassandra.core.cql.keyspace.DropKeyspaceSpecification;
import org.springframework.data.cassandra.core.cql.keyspace.KeyspaceOption;
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Configuration
@EnableReactiveCassandraRepositories(basePackages = "com.freelance.driver_backend.repository")
public class CassandraConfig extends AbstractCassandraConfiguration {

    @Value("${spring.data.cassandra.contact-points:127.0.0.1}")
    private String hostname;

    @Value("${spring.data.cassandra.port:9042}")
    private int port;

    @Value("${spring.data.cassandra.keyspace:freelancebd}")
    private String keyspace;

    @Value("${spring.data.cassandra.datacenter:datacenter1}")
    private String datacenter;

    @Override
    protected String getKeyspaceName() {
        return keyspace;
    }

    @Override
    protected String getContactPoints() {
        return hostname;
    }

    @Override
    protected int getPort() {
        return port;
    }

    @Override
    public SchemaAction getSchemaAction() {
        return SchemaAction.CREATE_IF_NOT_EXISTS;
    }

    @Override
    protected List getStartupScripts() {
        return Collections.singletonList("CREATE KEYSPACE IF NOT EXISTS "
                + keyspace + " WITH replication = {"
                + " 'class': 'SimpleStrategy', "
                + " 'replication_factor': '2' " + "};");

    }

    @Override
    protected List<CreateKeyspaceSpecification> getKeyspaceCreations() {
        final CreateKeyspaceSpecification specification = CreateKeyspaceSpecification.createKeyspace(getKeyspaceName())
                .ifNotExists().with(KeyspaceOption.DURABLE_WRITES, true).withSimpleReplication();
        return Collections.singletonList(specification);
    }

    /*@Override
    protected List<DropKeyspaceSpecification> getKeyspaceDrops() {
        List<DropKeyspaceSpecification> list = new ArrayList<>();
        list.add(DropKeyspaceSpecification.dropKeyspace(getKeyspaceName()));
        return list;
    }*/

    @Override
    protected List<DropKeyspaceSpecification> getKeyspaceDrops() {
        // CORRECTION ICI : Retournez une liste vide pour NE PAS supprimer le keyspace.
        return Collections.emptyList(); // Ou new ArrayList<>() pour plus de clarté
    }

    /**
     * Configuration avancée pour surcharger les timeouts du driver Cassandra.
     * Cette méthode est plus robuste que la configuration via application.properties.
     */
    @Override
    protected SessionBuilderConfigurer getSessionBuilderConfigurer() {
        return sessionBuilder -> {
            DriverConfigLoader configLoader = DriverConfigLoader.programmaticBuilder()
                    // Augmente le timeout pour toutes les requêtes à 20 secondes.
                    .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(20))
                    // Augmente le timeout pour la connexion initiale.
                    .withDuration(DefaultDriverOption.CONNECTION_CONNECT_TIMEOUT, Duration.ofSeconds(20))
                    // Augmente le timeout pour l'initialisation du schéma (création des tables/index).
                    .withDuration(DefaultDriverOption.CONTROL_CONNECTION_TIMEOUT, Duration.ofSeconds(20))
                    .build();
            
            return sessionBuilder.withConfigLoader(configLoader);
        };
    }
}