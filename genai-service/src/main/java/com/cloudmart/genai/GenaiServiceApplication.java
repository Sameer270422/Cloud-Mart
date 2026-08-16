package com.cloudmart.genai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@SpringBootApplication
public class GenaiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GenaiServiceApplication.class, args);
    }

    // The assistant degrades gracefully (503 on its own endpoints) rather
    // than failing to start when no key is configured, so a missing key
    // doesn't take down the rest of the stack - but it's easy to miss in
    // logs otherwise, hence the loud warning.
    @Component
    @Slf4j
    static class ApiKeyStartupCheck {

        @Value("${cloudmart.anthropic.api-key:}")
        private String apiKey;

        @EventListener(ApplicationReadyEvent.class)
        public void checkApiKey() {
            if (!StringUtils.hasText(apiKey)) {
                log.warn("ANTHROPIC_API_KEY is not set - /api/assistant/** will respond with 503 " +
                        "until it's configured.");
            }
        }
    }
}
