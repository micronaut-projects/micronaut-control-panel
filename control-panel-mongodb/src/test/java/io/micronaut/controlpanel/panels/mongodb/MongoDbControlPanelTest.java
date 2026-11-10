package io.micronaut.controlpanel.panels.mongodb;

import com.mongodb.client.MongoClient;
import io.micronaut.context.ApplicationContext;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoDbControlPanelTest {

    @Test
    void listsServersDatabasesAndCollections() {
        // mongodb.uri is provided by Micronaut Test Resources
        try (ApplicationContext context = ApplicationContext.run()) {
            MongoClient client = context.getBean(MongoClient.class);
            client.getDatabase("library").getCollection("books").insertMany(List.of(new Document("title", "A"), new Document("title", "B")));
            client.getDatabase("library").createView("titles", "books", List.of(new Document("$project", new Document("title", 1))));

            MongoDbControlPanel panel = context.getBean(MongoDbControlPanel.class);
            assertEquals("MongoDB", panel.getTitle());
            assertEquals("datasource", panel.getCategory().id());
            assertEquals("", panel.getBadge());

            List<MongoClientInfo> summary = panel.getBody().clients();
            assertTrue(summary.stream().allMatch(info -> info.databases() == null), "the dashboard must not query MongoDB");

            List<MongoClientInfo> clients = panel.getDetailedBody().clients();
            assertEquals(List.of("default/reactive", "default/sync"),
                clients.stream().map(info -> info.name() + "/" + info.driver()).toList());

            MongoClientInfo sync = clients.get(1);
            assertTrue(sync.connected());
            assertNull(sync.error());
            MongoClientInfo.Database library = sync.databases().stream().filter(database -> database.name().equals("library")).findFirst().orElseThrow();
            assertEquals(List.of(
                new MongoClientInfo.Collection("books", "collection", 2L),
                new MongoClientInfo.Collection("titles", "view", null)
            ), library.collections().stream().filter(collection -> !collection.name().startsWith("system.")).toList());

            MongoClientInfo reactive = clients.getFirst();
            assertTrue(reactive.databases().stream().anyMatch(database -> database.name().equals("library")));

            assertTrue(context.getBean(ControlPanelRepository.class).findByName(MongoDbControlPanel.NAME).isPresent());
        }
    }

    @Test
    void doesNotQueryClientsWithoutAConnectedServer() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "mongodb.servers.offline.uri", "mongodb://admin:s3cret@localhost:1/?serverSelectionTimeoutMS=500&connectTimeoutMS=500"
        ))) {
            List<MongoClientInfo> clients = context.getBean(MongoDbControlPanel.class).getDetailedBody().clients();
            MongoClientInfo offline = clients.stream().filter(client -> client.name().equals("offline")).findFirst().orElseThrow();
            assertFalse(offline.connected());
            assertNull(offline.databases());
            assertNotNull(offline.error());
            assertEquals("localhost:1", offline.servers().getFirst().address());
            assertFalse(clients.toString().contains("s3cret"));
            assertFalse(clients.toString().contains("admin"));
        }
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(MongoDbControlPanel.ENABLED_PROPERTY, "false"))) {
            assertFalse(context.containsBean(MongoDbControlPanel.class));
        }
    }

    @Test
    void formatsSizes() {
        assertEquals("unknown", MongoDbInspector.formatSize(null));
        assertEquals("512 B", MongoDbInspector.formatSize(512L));
        assertEquals("8.0 KB", MongoDbInspector.formatSize(8192L));
        assertEquals("1.5 MB", MongoDbInspector.formatSize(1572864L));
        assertEquals("2048.0 TB", MongoDbInspector.formatSize(2L << 50));
    }

    @Test
    void labelsDocumentCounts() {
        assertNull(new MongoClientInfo.Collection("books", "collection", null).documentsLabel());
        assertEquals("~1 document", new MongoClientInfo.Collection("books", "collection", 1L).documentsLabel());
        assertEquals("~2 documents", new MongoClientInfo.Collection("books", "collection", 2L).documentsLabel());
    }
}
