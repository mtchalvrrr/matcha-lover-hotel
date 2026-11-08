package com.hotel.system.launch;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ProgramBrowserLauncher {
    private static final Logger log = LoggerFactory.getLogger(ProgramBrowserLauncher.class);
    private static final List<String> PROGRAM_PATHS = List.of(
            "/customer",
            "/admin",
            "/super-admin"
    );

    @EventListener(ApplicationReadyEvent.class)
    public void openProgramsWhenReady() {
        String codespaceName = System.getenv("CODESPACE_NAME");
        String forwardingDomain = System.getenv("GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN");

        if (codespaceName != null && !codespaceName.isBlank()) {
            String domain = forwardingDomain == null || forwardingDomain.isBlank()
                    ? "app.github.dev"
                    : forwardingDomain;
            String baseUrl = "https://" + codespaceName + "-8080." + domain;
            log.info("Application is running in GitHub Codespaces. Open these program URLs in browser tabs:");
            PROGRAM_PATHS.forEach(path -> log.info("{}{}", baseUrl, path));
            return;
        }

        if (GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            log.info("No desktop browser is available. Open the programs manually:");
            PROGRAM_PATHS.forEach(path -> log.info("http://localhost:8080{}", path));
            return;
        }

        for (String path : PROGRAM_PATHS) {
            try {
                Desktop.getDesktop().browse(URI.create("http://localhost:8080" + path));
                log.info("Opened program: http://localhost:8080{}", path);
            } catch (Exception exception) {
                log.warn("Could not open program {} automatically. Open http://localhost:8080{} manually.",
                        path, path, exception);
            }
        }
    }
}
