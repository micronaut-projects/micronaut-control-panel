package example;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@Property(name = "micronaut.control-panel.chatbots.telegram.webhook-status.enabled", value = "true")
@Property(name = "micronaut.control-panel.chatbots.telegram.webhook-status.base-url", value = "http://[invalid")
@Property(name = "micronaut.control-panel.chatbots.telegram.webhook-status.api-tokens.support", value = "123456:bot-api-token-value")
class ControlPanelRenderingTest {

    @Test
    void rendersObjectStorageCategory(@Client("/") HttpClient client) {
        var body = client.toBlocking().retrieve(HttpRequest.GET("/control-panel/categories/object-storage"));

        assertTrue(body.contains("my-local"));
        assertTrue(body.contains("files stored."));
    }

    @Test
    void rendersChatbotsCategory(@Client("/") HttpClient client) {
        var body = client.toBlocking().retrieve(HttpRequest.GET("/control-panel/chatbots"));

        assertTrue(body.contains("Chatbots"));
        assertTrue(body.contains("support"));
        assertTrue(body.contains("configured"));
        assertTrue(body.contains("ops"));
        assertTrue(body.contains("SupportTelegramHandler"));
        assertTrue(body.contains("lookup configuration error"));
        assertTrue(!body.contains("sample-telegram-webhook-secret"));
        assertTrue(!body.contains("123456:bot-api-token-value"));
        assertTrue(!body.contains("http://[invalid"));
    }
}
