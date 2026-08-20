package com.example.bookstore.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class MailConfigChecker {
    private static final Logger log = LoggerFactory.getLogger(MailConfigChecker.class);

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.port:}")
    private String mailPort;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (mailHost == null || mailHost.isBlank()) {
            log.warn("SMTP not configured (spring.mail.host is empty). For local development use MailHog: e.g. run `docker run -p 1025:1025 -p 8025:8025 mailhog/mailhog` and set MAIL_HOST=localhost, MAIL_PORT=1025 in your .env");
            return;
        }

        if (mailHost.contains("mailhog") || "localhost".equals(mailHost) || "127.0.0.1".equals(mailHost)) {
            log.info("SMTP configured to {}:{} — looks like MailHog or local SMTP. Web UI: http://localhost:8025", mailHost, mailPort == null ? "" : mailPort);
        } else {
            log.info("SMTP configured to {}:{} — outgoing emails will be sent via SMTP provider.", mailHost, mailPort == null ? "" : mailPort);
        }
    }
}
