package io.micronaut.controlpanel.panels.datasource.model;

import io.micronaut.core.annotation.ReflectiveAccess;

/**
 * Represents the database type, which can be inferred from either the Micronaut Data's configured
 * dialect, or the Micronaut Test Resources' configured db type.
 */
@ReflectiveAccess
public enum DatabaseType {
    MYSQL, POSTGRES, SQL_SERVER, ORACLE, MARIADB, GENERIC;

    public static DatabaseType of(String dialect, String dbType) {
        return switch (dialect) {
            case "MYSQL" -> MYSQL;
            case "POSTGRES" -> POSTGRES;
            case "SQL_SERVER" -> SQL_SERVER;
            case "ORACLE" -> ORACLE;
            default -> ofDbType(dbType);
        };
    }

    private static DatabaseType ofDbType(String dbType) {
        return switch (dbType) {
            case "mariadb" -> MARIADB;
            case "mysql" -> MYSQL;
            case "oracle-xe", "oracle" -> ORACLE;
            case "postgres" -> POSTGRES;
            case "mssql" -> SQL_SERVER;
            default -> GENERIC;
        };
    }
}
