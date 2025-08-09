package com.freelance.driver_backend.model;

import lombok.Data;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;
import java.util.UUID;

@Table("device_tokens")
@Data
public class DeviceToken {

    @PrimaryKeyColumn(name = "user_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    @Column("user_id")
    private UUID userId;

    @PrimaryKeyColumn(name = "device_token", ordinal = 1, type = PrimaryKeyType.CLUSTERED)
    @Column("device_token")
    private String token;
}