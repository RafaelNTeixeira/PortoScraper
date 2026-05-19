package com.porto.scraper.config;

/**
 * Fluent builder that constructs valid Imovirtual search URLs.
 *
 * -----------------------------------------------------------------
 *  USAGE EXAMPLES
 * -----------------------------------------------------------------
 *
 *  // Buy a house in Matosinhos, up to €300k
 *  new UrlBuilder().buy().houses().inZone(Zone.MATOSINHOS).maxPrice(300_000).build()
 *
 *  // Rent an apartment in Porto city, €600-€1200/month
 *  new UrlBuilder().rent().apartments().inZone(Zone.PORTO).minPrice(600).maxPrice(1_200).build()
 *
 *  // Buy apartment OR house in Gaia - call build() once per property type
 *  new UrlBuilder().buy().apartments().inZone(Zone.GAIA).maxPrice(200_000).build()
 *  new UrlBuilder().buy().houses().inZone(Zone.GAIA).maxPrice(200_000).build()
 *
 * -----------------------------------------------------------------
 *  AVAILABLE ZONES  (Porto district + surroundings)
 * -----------------------------------------------------------------
 *  Zone.PORTO           - Porto city centre
 *  Zone.MATOSINHOS      - coastal, north of Porto
 *  Zone.GAIA            - south bank of the Douro
 *  Zone.MAIA            - north, near airport
 *  Zone.GONDOMAR        - east of Porto
 *  Zone.VALONGO         - northeast, cheaper
 *  Zone.POVOA_DE_VARZIM - northern coast
 *  Zone.VILA_DO_CONDE   - northern coast, quieter
 *  Zone.BRAGA           - 50 min north, very affordable
 *  Zone.BARCELOS        - rural Minho, budget option
 */
public class UrlBuilder {

    // -- Imovirtual URL segments -------------------------------------------

    private static final String BASE    = "https://www.imovirtual.com";
    private static final String RENT    = "arrendar";
    private static final String BUY     = "comprar";
    private static final String APT     = "apartamento";
    private static final String HOUSE   = "moradia";        // detached/semi-detached house
    private static final String LAND    = "terreno";        // building plot

    // -- Zones -------------------------------------------------------------

    public enum Zone {
        PORTO           ("porto",           "Porto"),
        MATOSINHOS      ("matosinhos",      "Matosinhos"),
        GAIA            ("vila-nova-de-gaia","Vila Nova de Gaia"),
        MAIA            ("maia",            "Maia"),
        GONDOMAR        ("gondomar",        "Gondomar"),
        VALONGO         ("valongo",         "Valongo"),
        POVOA_DE_VARZIM ("povoa-de-varzim", "Póvoa de Varzim"),
        VILA_DO_CONDE   ("vila-do-conde",   "Vila do Conde"),
        BRAGA           ("braga",           "Braga"),
        BARCELOS        ("barcelos",        "Barcelos");

        final String slug;        // used in URL path
        final String displayName; // used in label

        Zone(String slug, String displayName) {
            this.slug        = slug;
            this.displayName = displayName;
        }
    }

    // -- Builder state -----------------------------------------------------

    private String transaction = BUY;    // default: buy
    private String propertyType = APT;   // default: apartment
    private Zone zone;                   // required - no default
    private Integer minPrice;            // optional
    private Integer maxPrice;            // optional
    private Integer minRooms;            // optional - bedrooms (T1, T2, ...)
    private Integer maxRooms;            // optional
    private Integer minArea;             // optional - m²
    private Integer maxArea;             // optional

    // -- Transaction -------------------------------------------------------

    /** Search for properties to BUY (comprar). */
    public UrlBuilder buy()  { this.transaction = BUY;  return this; }

    /** Search for properties to RENT (arrendar). */
    public UrlBuilder rent() { this.transaction = RENT; return this; }

    // -- Property type -----------------------------------------------------

    /** Apartments (T0, T1, T2, T3, ...). */
    public UrlBuilder apartments() { this.propertyType = APT;   return this; }

    /** Houses / moradias (detached, semi-detached, townhouse). */
    public UrlBuilder houses()     { this.propertyType = HOUSE; return this; }

    /** Building plots / terrenos. */
    public UrlBuilder land()       { this.propertyType = LAND;  return this; }

    // -- Location ----------------------------------------------------------

    /** Set the zone to search in. This is REQUIRED. */
    public UrlBuilder inZone(Zone zone) {
        this.zone = zone;
        return this;
    }

    // -- Price filters -----------------------------------------------------

    /** Minimum price in € (or €/month for rent). */
    public UrlBuilder minPrice(int euros) { this.minPrice = euros; return this; }

    /** Maximum price in € (or €/month for rent). */
    public UrlBuilder maxPrice(int euros) { this.maxPrice = euros; return this; }

    // -- Size / rooms filters ----------------------------------------------

    /** Minimum number of bedrooms (e.g. 2 = T2 or larger). */
    public UrlBuilder minRooms(int rooms) { this.minRooms = rooms; return this; }

    /** Maximum number of bedrooms. */
    public UrlBuilder maxRooms(int rooms) { this.maxRooms = rooms; return this; }

    /** Minimum gross area in m². */
    public UrlBuilder minArea(int sqm)   { this.minArea = sqm; return this; }

    /** Maximum gross area in m². */
    public UrlBuilder maxArea(int sqm)   { this.maxArea = sqm; return this; }

    // -- Build -------------------------------------------------------------

    /**
     * Assembles the final {@link SearchTarget}.
     *
     * @throws IllegalStateException if {@code inZone()} was not called.
     */
    public SearchTarget build() {
        if (zone == null) {
            throw new IllegalStateException("Zone is required. Call .inZone(Zone.XXX) before .build()");
        }

        String url   = buildUrl();
        String label = buildLabel();

        return new SearchTarget(label, url);
    }

    // ---------------------------------------------------------------------

    private String buildUrl() {
        StringBuilder sb = new StringBuilder();
        sb.append(BASE)
                .append("/").append(transaction)
                .append("/").append(propertyType)
                .append("/").append(zone.slug)
                .append("/?search[order]=created_at:desc"); // newest first - critical for speed

        // Price filters
        if (minPrice != null) {
            sb.append("&search[filter_float_price:from]=").append(minPrice);
        }
        if (maxPrice != null) {
            sb.append("&search[filter_float_price:to]=").append(maxPrice);
        }

        // Rooms filters
        if (minRooms != null) {
            sb.append("&search[filter_float_m:from]=").append(minRooms);
        }
        if (maxRooms != null) {
            sb.append("&search[filter_float_m:to]=").append(maxRooms);
        }

        // Area filters
        if (minArea != null) {
            sb.append("&search[filter_float_price_per_m:from]=").append(minArea);
        }
        if (maxArea != null) {
            sb.append("&search[filter_float_price_per_m:to]=").append(maxArea);
        }

        return sb.toString();
    }

    private String buildLabel() {
        String tx   = transaction.equals(BUY) ? "BUY" : "RENT";
        String type = switch (propertyType) {
            case APT   -> "Apartment";
            case HOUSE -> "House";
            case LAND  -> "Land";
            default    -> propertyType;
        };

        StringBuilder label = new StringBuilder();
        label.append(tx)
                .append(" | ").append(type)
                .append(" | ").append(zone.displayName);

        if (minPrice != null || maxPrice != null) {
            label.append(" | €");
            if (minPrice != null) label.append(minPrice);
            label.append("-");
            if (maxPrice != null) label.append(maxPrice);
        }

        return label.toString();
    }
}