package com.porto.scraper.config;

/**
 * Fluent builder that constructs Imovirtual or Idealista search URLs.
 *
 * Usage:
 *   new UrlBuilder().source(Source.IDEALISTA).buy().houses().inZone(Zone.PORTO).maxPrice(350_000).build()
 *   new UrlBuilder().source(Source.IMOVIRTUAL).rent().apartments().inZone(Zone.MATOSINHOS).maxPrice(1_100).build()
 *
 * Source defaults to IMOVIRTUAL if not specified.
 */
public class UrlBuilder {

    // -- Sources -----------------------------------------------------------

    public enum Source {
        IMOVIRTUAL,
        IDEALISTA
    }

    // -- Imovirtual URL segments -------------------------------------------
    private static final String IMO_BASE  = "https://www.imovirtual.com";
    private static final String IMO_RENT  = "arrendar";
    private static final String IMO_BUY   = "comprar";
    private static final String IMO_APT   = "apartamento";
    private static final String IMO_HOUSE = "moradia";
    private static final String IMO_LAND  = "terreno";

    // -- Idealista URL segments --------------------------------------------
    private static final String IDE_BASE  = "https://www.idealista.pt";
    private static final String IDE_RENT  = "arrendar-casas";
    private static final String IDE_BUY   = "comprar-casas";
    // Idealista shows all property types by default; apartments have a sub-path
    private static final String IDE_APT   = "apartamentos";
    private static final String IDE_HOUSE = "moradias";
    // Idealista land is under comprar-terrenos, handled separately in buildUrl()

    // -- Zones -------------------------------------------------------------

    public enum Zone {
        //                  Imovirtual slug         Idealista slug          Display name
        PORTO           ("porto",                  "porto",                "Porto"),
        MATOSINHOS      ("matosinhos",             "matosinhos",           "Matosinhos"),
        GAIA            ("vila-nova-de-gaia",      "vila-nova-de-gaia",    "Vila Nova de Gaia"),
        MAIA            ("maia",                   "maia",                 "Maia"),
        GONDOMAR        ("gondomar",               "gondomar",             "Gondomar"),
        VALONGO         ("valongo",                "valongo",              "Valongo"),
        POVOA_DE_VARZIM ("povoa-de-varzim",        "povoa-de-varzim",      "Póvoa de Varzim"),
        VILA_DO_CONDE   ("vila-do-conde",          "vila-do-conde",        "Vila do Conde"),
        BRAGA           ("braga",                  "braga",                "Braga"),
        BARCELOS        ("barcelos",               "barcelos",             "Barcelos");

        final String imovirtualSlug;
        final String idealistaSlug;
        final String displayName;

        Zone(String imovirtualSlug, String idealistaSlug, String displayName) {
            this.imovirtualSlug = imovirtualSlug;
            this.idealistaSlug  = idealistaSlug;
            this.displayName    = displayName;
        }
    }

    // -- Builder state -----------------------------------------------------

    private Source  source       = Source.IMOVIRTUAL;
    private String  transaction  = IMO_BUY;
    private String  propertyType = IMO_APT;
    private Zone    zone;
    private Integer minPrice;
    private Integer maxPrice;
    private Integer minRooms;
    private Integer maxRooms;
    private Integer minArea;
    private Integer maxArea;

    // -- Source ------------------------------------------------------------

    public UrlBuilder source(Source source) { this.source = source; return this; }

    // -- Transaction -------------------------------------------------------

    public UrlBuilder buy()  { this.transaction = IMO_BUY;  return this; }
    public UrlBuilder rent() { this.transaction = IMO_RENT; return this; }

    // -- Property type -----------------------------------------------------

    public UrlBuilder apartments() { this.propertyType = IMO_APT;   return this; }
    public UrlBuilder houses()     { this.propertyType = IMO_HOUSE; return this; }
    public UrlBuilder land()       { this.propertyType = IMO_LAND;  return this; }

    // -- Location ----------------------------------------------------------

    public UrlBuilder inZone(Zone zone) { this.zone = zone; return this; }

    // -- Filters -----------------------------------------------------------

    public UrlBuilder minPrice(int v) { this.minPrice = v; return this; }
    public UrlBuilder maxPrice(int v) { this.maxPrice = v; return this; }
    public UrlBuilder minRooms(int v) { this.minRooms = v; return this; }
    public UrlBuilder maxRooms(int v) { this.maxRooms = v; return this; }
    public UrlBuilder minArea(int v)  { this.minArea  = v; return this; }
    public UrlBuilder maxArea(int v)  { this.maxArea  = v; return this; }

    // -- Build -------------------------------------------------------------

    public SearchTarget build() {
        if (zone == null) throw new IllegalStateException("Zone is required - call .inZone(Zone.XXX)");
        return new SearchTarget(buildLabel(), buildUrl(), source);
    }

    // ---------------------------------------------------------------------

    private String buildUrl() {
        return switch (source) {
            case IMOVIRTUAL -> buildImovirtualUrl();
            case IDEALISTA  -> buildIdealistaUrl();
        };
    }

    private String buildImovirtualUrl() {
        StringBuilder sb = new StringBuilder(IMO_BASE)
                .append("/").append(transaction)
                .append("/").append(propertyType)
                .append("/").append(zone.imovirtualSlug)
                .append("/?search[order]=created_at:desc");

        if (minPrice != null) sb.append("&search[filter_float_price:from]=").append(minPrice);
        if (maxPrice != null) sb.append("&search[filter_float_price:to]=").append(maxPrice);
        if (minRooms != null) sb.append("&search[filter_float_m:from]=").append(minRooms);
        if (maxRooms != null) sb.append("&search[filter_float_m:to]=").append(maxRooms);
        if (minArea  != null) sb.append("&search[filter_float_price_per_m:from]=").append(minArea);
        if (maxArea  != null) sb.append("&search[filter_float_price_per_m:to]=").append(maxArea);

        return sb.toString();
    }

    private String buildIdealistaUrl() {
        boolean isBuy = transaction.equals(IMO_BUY);

        // Idealista uses different base paths for buy vs rent
        String txSegment = isBuy ? IDE_BUY : IDE_RENT;

        // Build filter path segment: com-preco-max_X,preco-min_Y,tamanho-min_Z,...
        // Filters are comma-separated path tokens, prefixed with "com-"
        // e.g. /com-preco-max_280000,preco-min_600,tamanho-min_60/
        StringBuilder filters = new StringBuilder();
        if (maxPrice != null) appendFilter(filters, "preco-max_"   + maxPrice);
        if (minPrice != null) appendFilter(filters, "preco-min_"   + minPrice);
        if (minRooms != null) appendFilter(filters, "quartos_"     + minRooms);
        if (minArea  != null) appendFilter(filters, "tamanho-min_" + minArea);
        if (maxArea  != null) appendFilter(filters, "tamanho-max_" + maxArea);

        StringBuilder sb = new StringBuilder(IDE_BASE)
                .append("/").append(txSegment)
                .append("/").append(zone.idealistaSlug)
                .append("/");

        if (filters.length() > 0) {
            sb.append("com-").append(filters).append("/");
        }

        sb.append("#searchHL");
        return sb.toString();
    }

    /** Appends a filter token, adding a comma separator if needed. */
    private void appendFilter(StringBuilder sb, String token) {
        if (sb.length() > 0) sb.append(",");
        sb.append(token);
    }

    private String buildLabel() {
        String src  = source == Source.IDEALISTA ? "Idealista" : "Imovirtual";
        String tx   = transaction.equals(IMO_BUY) ? "BUY" : "RENT";
        String type = switch (propertyType) {
            case IMO_APT   -> "Apartment";
            case IMO_HOUSE -> "House";
            case IMO_LAND  -> "Land";
            default        -> propertyType;
        };

        StringBuilder label = new StringBuilder()
                .append("[").append(src).append("] ")
                .append(tx).append(" | ").append(type)
                .append(" | ").append(zone.displayName);

        if (minPrice != null || maxPrice != null) {
            label.append(" | €");
            if (minPrice != null) label.append(minPrice);
            label.append("–");
            if (maxPrice != null) label.append(maxPrice);
        }
        return label.toString();
    }
}