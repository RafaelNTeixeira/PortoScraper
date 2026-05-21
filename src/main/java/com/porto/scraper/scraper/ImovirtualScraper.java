package com.porto.scraper.scraper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.porto.scraper.model.Listing;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ImovirtualScraper implements Scraper {

    private static final Logger log = LoggerFactory.getLogger(ImovirtualScraper.class);

    private static final String IMOVIRTUAL_BASE    = "https://www.imovirtual.com";
    private static final String BASE_LISTING_URL   = "https://www.imovirtual.com/pt/anuncio/";
    private static final String NEXT_DATA_SELECTOR = "script#__NEXT_DATA__";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 15_000;

    private static final boolean DEBUG_FIRST_ITEM = false;

    private final String searchUrl;

    public ImovirtualScraper(String searchUrl) {
        this.searchUrl = searchUrl;
    }

    @Override
    public List<Listing> scrape() {
        List<Listing> results = new ArrayList<>();
        try {
            log.info("  [Imovirtual] Fetching: {}", searchUrl);

            Document doc = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", "pt-PT,pt;q=0.9,en;q=0.8")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .referrer("https://www.google.com/")
                    .timeout(TIMEOUT_MS)
                    .get();

            Element nextDataScript = doc.selectFirst(NEXT_DATA_SELECTOR);
            if (nextDataScript == null) {
                log.error("  [Imovirtual] __NEXT_DATA__ tag not found.");
                return results;
            }

            JsonObject root  = JsonParser.parseString(nextDataScript.html()).getAsJsonObject();
            JsonArray  items = resolveItemsArray(root);

            if (items == null) {
                log.error("  [Imovirtual] Listings array not found in __NEXT_DATA__.");
                return results;
            }

            log.info("  [Imovirtual] Found {} item(s)", items.size());

            boolean debugDone = false;
            for (JsonElement el : items) {
                JsonObject item = el.getAsJsonObject();
                if (DEBUG_FIRST_ITEM && !debugDone) {
                    log.info("=== DEBUG keys: {}", item.keySet());
                    log.info("=== DEBUG item: {}", item);
                    debugDone = true;
                }
                Listing listing = parseListing(item);
                if (listing != null) results.add(listing);
            }

        } catch (IOException e) {
            log.error("  [Imovirtual] Network error: {}", e.getMessage());
        } catch (Exception e) {
            log.error("  [Imovirtual] Unexpected error: {}", e.getMessage(), e);
        }
        return results;
    }

    private JsonArray resolveItemsArray(JsonObject root) {
        try {
            return root.getAsJsonObject("props")
                    .getAsJsonObject("pageProps")
                    .getAsJsonObject("data")
                    .getAsJsonObject("searchAds")
                    .getAsJsonArray("items");
        } catch (Exception ignored) {}
        try {
            return root.getAsJsonObject("props")
                    .getAsJsonObject("pageProps")
                    .getAsJsonObject("listings")
                    .getAsJsonArray("results");
        } catch (Exception ignored) {}
        return null;
    }

    private Listing parseListing(JsonObject item) {
        try {
            String id = getString(item, "id");
            if (id == null) {
                log.warn("  [Imovirtual] Skipping item - no id. Keys: {}", item.keySet());
                return null;
            }

            String url   = resolveUrl(item, id);
            String title = getString(item, "title");
            if (title == null) title = "No title";

            return new Listing(id, title, parsePrice(item), parseLocation(item), url);

        } catch (Exception e) {
            log.warn("  [Imovirtual] Failed to parse item: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private String resolveUrl(JsonObject item, String id) {
        String href = getString(item, "href");
        if (href != null && !href.isBlank()) {
            String path = href.replace("[lang]", "pt");
            if (!path.startsWith("/")) path = "/" + path;
            return IMOVIRTUAL_BASE + path;
        }
        String slug = getString(item, "slug");
        if (slug != null && !slug.isBlank()) return BASE_LISTING_URL + slug;
        return IMOVIRTUAL_BASE;
    }

    private String parsePrice(JsonObject item) {
        try {
            JsonObject p = item.getAsJsonObject("totalPrice");
            if (p == null) p = item.getAsJsonObject("rentPrice");
            if (p == null) return "Price N/A";
            String value    = getString(p, "value");
            String currency = getString(p, "currency");
            if (value == null) return "Price N/A";
            String display = value + " " + (currency != null ? currency : "€");
            if ("RENT".equalsIgnoreCase(getString(item, "transaction"))) display += "/mês";
            return display;
        } catch (Exception e) { return "Price N/A"; }
    }

    private String parseLocation(JsonObject item) {
        try {
            JsonObject address = item.getAsJsonObject("location").getAsJsonObject("address");
            String city = null, street = null;
            JsonElement ce = address.get("city");
            JsonElement se = address.get("street");
            if (ce != null && ce.isJsonObject()) city   = getString(ce.getAsJsonObject(), "name");
            if (se != null && se.isJsonObject()) street = getString(se.getAsJsonObject(), "name");
            if (street != null && !street.isBlank() && city != null) return street + ", " + city;
            return city != null ? city : "Location N/A";
        } catch (Exception e) { return "Location N/A"; }
    }

    private String getString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return null;
        return el.getAsString();
    }
}