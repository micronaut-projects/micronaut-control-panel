package example;

import io.micronaut.chatbots.telegram.api.Update;
import io.micronaut.chatbots.telegram.core.TelegramBotConfiguration;
import io.micronaut.chatbots.telegram.core.TelegramHandler;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
final class SupportTelegramHandler implements TelegramHandler<String> {

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public boolean canHandle(TelegramBotConfiguration bot, Update input) {
        return false;
    }

    @Override
    public Optional<String> handle(TelegramBotConfiguration bot, Update input) {
        return Optional.empty();
    }
}
