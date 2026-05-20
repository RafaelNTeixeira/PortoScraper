package com.porto.scraper.notification;

import com.porto.scraper.model.Listing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Sends a Discord embed notification for each new listing via a webhook.
 *
 * -----------------------------------------------------------------
 *  HOW TO CREATE A DISCORD WEBHOOK (free, takes 2 minutes)
 * -----------------------------------------------------------------
 *  1. Open Discord and go to the channel you want alerts in
   (create a private channel like #porto-listings if you want)
 *  2. Click the gear icon next to the channel name -> Edit Channel
 *  3. Go to Integrations -> Webhooks -> New Webhook
 *  4. Give it a name (e.g. "Porto Scraper") and copy the Webhook URL
 *  5. Paste it as DISCORD_WEBHOOK_URL in Main.java
 *
 *  The webhook URL looks like:
 *    https://discord.com/api/webhooks/1234567890/xxxxxxxxxxxxxxxxxxxx
 * -----------------------------------------------------------------
 *
 *  MESSAGE FORMAT
 * -----------------------------------------------------------------
 *  Each new listing becomes a Discord embed:
 *
 *  🏠  T3 Moradia em Matosinhos                    [BUY | House | Matosinhos]
 *  ----------------------------------------
 *  💶 Price        📍 Location      🔗 View listing
 *  265 000 EUR     Moreira, Maia    -> direct link
 */
public class DiscordNotifier {

    private static final Logger log = LoggerFactory.getLogger(DiscordNotifier.class);

    // Discord embed colours (decimal form of hex colour codes)
    private static final int COLOUR_BUY  = 3066993;  // green  (#2ECC71)
    private static final int COLOUR_RENT = 3447003;  // blue   (#3498DB)

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final String webhookUrl;
    private final HttpClient http;

    public DiscordNotifier(String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    /**
     * Sends a rich embed to Discord for a single new listing.
     *
     * @param listing     the new listing to announce
     * @param searchLabel the label from SearchTarget, e.g. "BUY | House | Maia"
     */
    public void notify(Listing listing, String searchLabel) {
        try {
            String payload = buildPayload(listing, searchLabel);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            // 204 No Content = success for Discord webhooks
            if (response.statusCode() == 204) {
                log.info("Discord notified for listing {}", listing.getId());
            } else {
                log.warn("Discord returned unexpected status {}: {}", response.statusCode(), response.body());
            }

        } catch (Exception e) {
            // Never let a notification failure crash the main scrape loop
            log.error("Failed to send Discord notification: {}", e.getMessage());
        }
    }

    // -- Payload builder ---------------------------------------------------

    /**
     * Builds a Discord webhook JSON payload with a rich embed.
     *
     * The structure follows Discord's embed spec:
     * https://discord.com/developers/docs/resources/webhook#execute-webhook
     */
    private String buildPayload(Listing listing, String searchLabel) {
        boolean isBuy   = searchLabel.startsWith("BUY");
        int     colour  = isBuy ? COLOUR_BUY : COLOUR_RENT;
        String  emoji   = isBuy ? "🏠" : "🏢";
        String  typeTag = isBuy ? "For Sale" : "For Rent";

        // Escape any quotes in text fields to keep the JSON valid
        String title    = jsonEscape(listing.getTitle());
        String price    = jsonEscape(listing.getPrice());
        String location = jsonEscape(listing.getLocation());
        String url      = jsonEscape(listing.getUrl());
        String label    = jsonEscape(searchLabel);

        return """
            {
              "embeds": [{
                "title": "%s  %s",
                "url": "%s",
                "color": %d,
                "fields": [
                  {
                    "name": "💶 Price",
                    "value": "%s",
                    "inline": true
                  },
                  {
                    "name": "📍 Location",
                    "value": "%s",
                    "inline": true
                  },
                  {
                    "name": "🏷️ Type",
                    "value": "%s",
                    "inline": true
                  },
                  {
                    "name": "🔗 Link",
                    "value": "[View on Imovirtual](%s)",
                    "inline": false
                  }
                ],
                "footer": {
                  "text": "%s"
                }
              }]
            }
            """.formatted(emoji, title, url, colour, price, location, typeTag, url, label);
    }

    /** Escapes characters that would break the JSON string. */
    private String jsonEscape(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", "");
    }
}