package com.porto.scraper;

import com.porto.scraper.config.AppConfig;
import com.porto.scraper.config.SearchTarget;
import com.porto.scraper.database.ListingRepository;
import com.porto.scraper.notification.DiscordNotifier;
import com.porto.scraper.scheduler.ScraperJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void main(String[] args) {

        // -- Load config ---------------------------------------------------
        AppConfig config = new AppConfig();

        int              pollMinutes = config.getPollIntervalMinutes();
        String           webhookUrl  = config.getDiscordWebhookUrl();
        List<SearchTarget> targets   = config.getSearchTargets();

        if (targets.isEmpty()) {
            log.error("No search targets configured. Edit scraper.properties and restart.");
            return;
        }

        log.info("═══════════════════════════════════════════");
        log.info("         Porto Real Estate Scraper         ");
        log.info("         Polling every {} minute(s)        ", pollMinutes);
        log.info("         {} search target(s) loaded        ", targets.size());
        log.info("═══════════════════════════════════════════");

        // -- Discord -------------------------------------------------------
        DiscordNotifier discord = null;
        if (webhookUrl != null) {
            discord = new DiscordNotifier(webhookUrl);
            log.info("Discord notifications: ENABLED");
        } else {
            log.info("Discord notifications: DISABLED (add discord.webhook.url to scraper.properties)");
        }

        // -- Database ------------------------------------------------------
        final ListingRepository db;
        try {
            db = new ListingRepository();
        } catch (SQLException e) {
            log.error("Failed to open database: {}", e.getMessage());
            return;
        }

        // -- Scheduler -----------------------------------------------------
        final DiscordNotifier finalDiscord = discord;
        ScraperJob job = new ScraperJob(targets, db, finalDiscord);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "scraper-thread");
            t.setDaemon(false);
            return t;
        });

        scheduler.scheduleAtFixedRate(
                () -> { job.run(); logNextRun(pollMinutes); },
                0,
                pollMinutes,
                TimeUnit.MINUTES
        );

        log.info("Scheduler started - first run beginning now.");
        log.info("Press Ctrl+C to stop.");

        // -- Graceful shutdown ---------------------------------------------
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down…");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) scheduler.shutdownNow();
                db.close();
                log.info("Shutdown complete.");
            } catch (Exception e) {
                log.error("Error during shutdown: {}", e.getMessage());
            }
        }, "shutdown-hook"));
    }

    private static void logNextRun(int pollMinutes) {
        log.info("Next run at {}", LocalTime.now().plusMinutes(pollMinutes).format(TIME_FMT));
    }
}