package example;

import io.micronaut.chatbots.basecamp.api.Query;
import io.micronaut.chatbots.basecamp.core.BasecampBotConfiguration;
import io.micronaut.chatbots.basecamp.core.BasecampHandler;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
final class OpsBasecampHandler implements BasecampHandler {

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public boolean canHandle(BasecampBotConfiguration bot, Query input) {
        return false;
    }

    @Override
    public Optional<String> handle(BasecampBotConfiguration bot, Query input) {
        return Optional.empty();
    }
}
