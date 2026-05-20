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

/**
 * Scrapes Imovirtual by reading the __NEXT_DATA__ JSON blob embedded in
 * every page by Next.js, not by parsing DOM elements.
 *
 * -----------------------------------------------------------------
 *  WHY __NEXT_DATA__ INSTEAD OF CSS SELECTORS
 * -----------------------------------------------------------------
 *  Imovirtual is a React/Next.js app. The listing cards you see in the
 *  browser are rendered by JavaScript AFTER the page loads. Jsoup only
 *  gets the raw HTML the server sends, no JS execution, so the cards
 *  are never there for CSS selectors to find.
 *
 *  However, Next.js does something useful: it serialises all server-side
 *  data into a <script id="__NEXT_DATA__" type="application/json"> tag
 *  in the raw HTML. That tag IS visible to Jsoup. We parse its JSON
 *  content and walk the object tree to reach the listings array.
 *
 * -----------------------------------------------------------------
 *  JSON PATH WE FOLLOW
 * -----------------------------------------------------------------
 *  __NEXT_DATA__
 *    .props
 *      .pageProps
 *        .data
 *          .searchAds
 *            .items[]          <- each element is one listing
 *              .id             <- unique listing ID
 *              .slug           <- used to build the listing URL
 *              .title
 *              .totalPrice.value / .totalPrice.currency
 *              .location.address.city.name
 *              .location.address.street.name  (optional)
 *
 *  If Imovirtual restructures their JSON, update the path constants below.
 * -----------------------------------------------------------------
 */
public class ImovirtualScraper {

    private static final Logger log = LoggerFactory.getLogger(ImovirtualScraper.class);

    // -- Next.js data extraction -------------------------------------------
    private static final String NEXT_DATA_SELECTOR = "script#__NEXT_DATA__";
    private static final String BASE_LISTING_URL   = "https://www.imovirtual.com/pt/anuncio/";

    // -- HTTP settings -----------------------------------------------------
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 15_000;

    private final String searchUrl;

    public ImovirtualScraper(String searchUrl) {
        this.searchUrl = searchUrl;
    }

    // -- Public API --------------------------------------------------------

    public List<Listing> scrape() {
        List<Listing> results = new ArrayList<>();

        try {
            log.info("Fetching: {}", searchUrl);

            Document doc = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", "pt-PT,pt;q=0.9,en;q=0.8")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .referrer("https://www.google.com/")
                    .timeout(TIMEOUT_MS)
                    .get();

            // -- Step 1: find the __NEXT_DATA__ script tag -----------------
            Element nextDataScript = doc.selectFirst(NEXT_DATA_SELECTOR);
            if (nextDataScript == null) {
                log.error("__NEXT_DATA__ script tag not found.");
                log.error("The page structure may have changed, or a CAPTCHA was served.");
                log.debug("Page title: {}", doc.title());
                return results;
            }

            // -- Step 2: parse the JSON ------------------------------------
            String json = nextDataScript.html();
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            // -- Step 3: navigate the object tree to the items array -------
            JsonArray items = resolveItemsArray(root);
            if (items == null) {
                log.error("Could not locate listings array in __NEXT_DATA__.");
                log.error("Imovirtual may have changed their JSON structure.");
                log.debug("Top-level keys: {}", root.keySet());
                return results;
            }

            log.info("Found {} listing(s) in __NEXT_DATA__", items.size());

            // -- Step 4: map each JSON object to a Listing -----------------
            for (JsonElement el : items) {
                Listing listing = parseListing(el.getAsJsonObject());
                if (listing != null) {
                    results.add(listing);
                }
            }

        } catch (IOException e) {
            log.error("Network error fetching page: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during scrape: {}", e.getMessage(), e);
        }

        return results;
    }

    // -- Private: JSON navigation ------------------------------------------

    /**
     * Walks the __NEXT_DATA__ tree to find the items array.
     * Returns null if the path doesn't resolve (site may have restructured).
     *
     * Primary path:   props → pageProps → data → searchAds → items
     * Fallback path:  props → pageProps → listings → results   (older layout)
     */
    private JsonArray resolveItemsArray(JsonObject root) {

        // -- Primary path (current layout) ---------------------------------
        try {
            return root
                    .getAsJsonObject("props")
                    .getAsJsonObject("pageProps")
                    .getAsJsonObject("data")
                    .getAsJsonObject("searchAds")
                    .getAsJsonArray("items");
        } catch (Exception ignored) {
            log.debug("Primary JSON path failed, trying fallback…");
        }

        // -- Fallback path (older / alternate layout) ----------------------
        try {
            return root
                    .getAsJsonObject("props")
                    .getAsJsonObject("pageProps")
                    .getAsJsonObject("listings")
                    .getAsJsonArray("results");
        } catch (Exception ignored) {
            log.debug("Fallback JSON path also failed.");
        }

        return null;
    }

    /**
     * Maps one JSON listing object to a {@link Listing}.
     * Returns null if required fields are missing.
     */
    private Listing parseListing(JsonObject item) {
        try {
            // -- ID ---------------------------------------------------------
            String id = getString(item, "id");
            if (id == null) return null;

            // -- Slug → URL -------------------------------------------------
            String slug = getString(item, "slug");
            String url  = (slug != null)
                    ? BASE_LISTING_URL + slug
                    : getString(item, "url");  // some items carry a direct url field
            if (url == null) url = BASE_LISTING_URL + id;

            // -- Title ------------------------------------------------------
            String title = getString(item, "title");
            if (title == null) title = "No title";

            // -- Price ------------------------------------------------------
            String price = parsePrice(item);

            // -- Location ---------------------------------------------------
            String location = parseLocation(item);

            return new Listing(id, title, price, location, url);

        } catch (Exception e) {
            log.debug("Failed to parse a listing item: {}", e.getMessage());
            return null;
        }
    }

    private String parsePrice(JsonObject item) {
        try {
            JsonObject totalPrice = item.getAsJsonObject("totalPrice");
            if (totalPrice == null) {
                // Some listings nest it differently
                totalPrice = item.getAsJsonObject("price");
            }
            if (totalPrice == null) return "Price N/A";

            String value    = getString(totalPrice, "value");
            String currency = getString(totalPrice, "currency");
            String suffix   = getString(totalPrice, "period"); // e.g. "MONTH" for rentals

            if (value == null) return "Price N/A";

            String display = value + " " + (currency != null ? currency : "€");
            if ("MONTH".equalsIgnoreCase(suffix)) display += "/mês";
            return display;

        } catch (Exception e) {
            return "Price N/A";
        }
    }

    private String parseLocation(JsonObject item) {
        try {
            JsonObject location = item.getAsJsonObject("location");
            if (location == null) return "Location N/A";

            JsonObject address = location.getAsJsonObject("address");
            if (address == null) return "Location N/A";

            String city   = null;
            String street = null;

            JsonObject cityObj = address.getAsJsonObject("city");
            if (cityObj != null) city = getString(cityObj, "name");

            JsonObject streetObj = address.getAsJsonObject("street");
            if (streetObj != null) street = getString(streetObj, "name");

            if (street != null && city != null) return street + ", " + city;
            if (city != null) return city;
            return "Location N/A";

        } catch (Exception e) {
            return "Location N/A";
        }
    }

    // -- Utility -----------------------------------------------------------

    /** Safely reads a string field from a JsonObject; returns null if absent. */
    private String getString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return null;
        return el.getAsString();
    }
}