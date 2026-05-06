/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Named;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest(environments = "hibernate")
class HibernateDemoControllerTest {

    @Test
    void fillsStatisticsForSessionFactoriesWithAndWithoutQueryCache(@Client("/") HttpClient client,
                                                                    @Named("my-postgres") SessionFactory sessionFactory) {
        sessionFactory.getSchemaManager().dropMappedObjects(false);

        var cachedSessionFactory = fillStatistics(client, "my-postgres");
        assertFilledStatistics(cachedSessionFactory, "my-postgres");

        var uncachedSessionFactory = fillStatistics(client, "hibernate-reporting");
        assertFilledStatistics(uncachedSessionFactory, "hibernate-reporting");
    }

    @Test
    void returnsNotFoundForUnknownSessionFactory(@Client("/") HttpClient client) {
        var exception = assertThrows(HttpClientResponseException.class, () ->
            requestMissingSessionFactory(client)
        );
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    private static void requestMissingSessionFactory(HttpClient client) {
        client.toBlocking().exchange(HttpRequest.GET("/hibernate-demo/missing/statistics/fill"));
    }

    private static Map<String, Object> fillStatistics(HttpClient client, String sessionFactory) {
        return client.toBlocking().retrieve(
            HttpRequest.GET("/hibernate-demo/" + sessionFactory + "/statistics/fill?runs=1"),
            Argument.mapOf(String.class, Object.class)
        );
    }

    private static void assertFilledStatistics(Map<String, Object> result, String sessionFactory) {
        assertEquals(sessionFactory, result.get("sessionFactory"));
        assertEquals(Boolean.TRUE, result.get("statisticsEnabled"));
        assertTrue(((Number) result.get("rowsRead")).intValue() > 0);
        assertTrue(((Number) result.get("cacheWarmupAuthors")).intValue() >= 6);
        assertTrue(((Number) result.get("cacheWarmupBooks")).intValue() >= 18);
        assertTrue(((Number) result.get("cacheWarmupCollectionItems")).intValue() >= 18);
        assertTrue(((Number) result.get("nativeRowsRead")).intValue() > 0);
        assertNativeSqlQueryTypes(result);
    }

    private static void assertNativeSqlQueryTypes(Map<String, Object> result) {
        var queryTypes = (List<?>) result.get("queryTypes");
        assertTrue(queryTypes.contains("Native SQL book count"));
        assertTrue(queryTypes.contains("Native SQL book list"));
        assertTrue(queryTypes.contains("Native SQL aggregate by country"));
    }
}
