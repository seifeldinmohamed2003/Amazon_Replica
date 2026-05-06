package com.testgen.amazon;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Shared base class for every generated test class.
 *
 * <p>Owns the JdbcTemplate, HttpClient, ObjectMapper, BASE_URL and common SQL /
 * HTTP helpers. Each test class in the factory's output extends this to get a
 * consistent setup without re-initializing these in every file.
 *
 * <p>This class has no {@code @Test} methods, so Surefire's
 * {@code **<!---->/*Tests.java} include filter (it ends in {@code TestBase}, not
 * {@code Tests}) will skip it as a runnable class. The {@code @TestInstance} +
 * {@code @BeforeAll} shape is inherited by subclasses.
 *
 * <p>The "package com.testgen.talabat" header is rewritten by the grader's
 * test_deployer to match the student's detected package at deploy time.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
@Timeout(30)
@SuppressWarnings("all")
public abstract class TestBase {

    protected static volatile JdbcTemplate jdbc;
    private static final Object _JDBC_INIT_LOCK = new Object();
    protected static volatile HttpClient http;
    private static final Object _HTTP_INIT_LOCK = new Object();
    protected String BASE_URL;
    // Per-service URLs (Amazon: 5 services bound to ports 8081–8085).
    // Each test method's primary service is picked from these via TC range
    // in routeBaseUrl() — see @BeforeEach below.
    protected String userServiceUrl;
    protected String catalogServiceUrl;       // product-service
    protected String orderServiceUrl;
    protected String deliveryServiceUrl;      // shipping-service
    protected String checkoutServiceUrl;      // billing-service
    protected final ObjectMapper OM = new ObjectMapper();

    // ─── manifest-driven helpers (auto-installed) ───
    // Every path / table / enum / column referenced in a test body is
    // resolved here at runtime from manifest.json on the test classpath.
    // The manifest shape mirrors test-generator/scanner/.../Scanner.java's
    // Manifest record (entities[], enums[], controllers[].endpoints[],
    // auth). At grading time the grader runs scanner.jar against the
    // student's project and writes the unified JSON; at factory time a
    // placeholder scanner-shape JSON sits in src/test/resources/. See
    // memory/reference_grader_manifest_integration.md for the deferred
    // grader-side wiring.

    private static volatile java.util.Map<String, Object> _manifestCache;
    private static volatile java.util.Map<String, Object> _themeCache;

    /** Lazily load and cache manifest.json from the test classpath. */
    @SuppressWarnings("unchecked")
    protected static java.util.Map<String, Object> manifest() {
        java.util.Map<String, Object> m = _manifestCache;
        if (m == null) {
            try (java.io.InputStream is = TestBase.class.getClassLoader()
                    .getResourceAsStream("manifest.json")) {
                if (is == null) {
                    throw new IllegalStateException(
                        "manifest.json not on test classpath — check src/test/resources/manifest.json");
                }
                m = new ObjectMapper().readValue(is,
                        new TypeReference<java.util.Map<String, Object>>() {});
                _manifestCache = m;
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to read manifest.json: " + e.getMessage(), e);
            }
        }
        return m;
    }

    /** Lazily load and cache theme.json from the test classpath (factory-time spec metadata). */
    @SuppressWarnings("unchecked")
    protected static java.util.Map<String, Object> theme() {
        java.util.Map<String, Object> m = _themeCache;
        if (m == null) {
            try (java.io.InputStream is = TestBase.class.getClassLoader()
                    .getResourceAsStream("theme.json")) {
                if (is == null) throw new IllegalStateException("theme.json not on test classpath");
                m = new ObjectMapper().readValue(is, new TypeReference<java.util.Map<String, Object>>() {});
                _themeCache = m;
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to read theme.json: " + e.getMessage(), e);
            }
        }
        return m;
    }

    protected String s2CatalogEntity() {
        String v = (String) theme().get("s2CatalogEntity");
        if (v == null) throw new IllegalStateException("theme.json missing 's2CatalogEntity'");
        return v;
    }
    protected String s3OrderEntity() {
        String v = (String) theme().get("s3OrderEntity");
        if (v == null) throw new IllegalStateException("theme.json missing 's3OrderEntity'");
        return v;
    }
    protected String s2EventsCollection() {
        String v = (String) theme().get("s2EventsCollection");
        if (v == null) throw new IllegalStateException("theme.json missing 's2EventsCollection'");
        return mongoCollectionByName(v);
    }
    protected String s2SearchIndex() {
        String v = (String) theme().get("s2SearchIndex");
        if (v == null) throw new IllegalStateException("theme.json missing 's2SearchIndex'");
        return esIndexByName(v);
    }

    /** S3 events Mongo collection — strict spec-name lookup against
     *  manifest.mongoCollections. For Amazon this is "order_events". */
    protected String s3EventsCollection() {
        String v = (String) theme().get("s3EventsCollection");
        if (v == null) throw new IllegalStateException("theme.json missing 's3EventsCollection'");
        return mongoCollectionByName(v);
    }

    /** S3 recommendation graph: User-side node label (e.g., "User" for Talabat,
     *  "Product" for Amazon since Amazon's graph is Product↔Product BOUGHT_TOGETHER).
     *  Strict spec-name lookup against manifest.neo4jNodes. */
    protected String s3GraphUserLabel() {
        String v = (String) theme().get("s3GraphUserLabel");
        if (v == null) throw new IllegalStateException("theme.json missing 's3GraphUserLabel'");
        return neo4jLabelByName(v);
    }

    /** S3 recommendation graph: catalog-side node label (e.g., "Restaurant"
     *  for Talabat, "Product" for Amazon). Strict spec-name lookup against manifest.neo4jNodes. */
    protected String s3GraphCatalogLabel() {
        String v = (String) theme().get("s3GraphCatalogLabel");
        if (v == null) throw new IllegalStateException("theme.json missing 's3GraphCatalogLabel'");
        return neo4jLabelByName(v);
    }

    /** S3 recommendation graph: relationship type connecting User → catalog
     *  (e.g., "ORDERED_FROM" for Talabat, "BOUGHT_TOGETHER" for Amazon). Read
     *  straight from theme.json — the type is a runtime literal, not a class
     *  annotation, so the scanner cannot cross-check it. */
    protected String s3GraphRelationship() {
        String v = (String) theme().get("s3GraphRelationship");
        if (v == null) throw new IllegalStateException("theme.json missing 's3GraphRelationship'");
        return v;
    }

    /** S4 entity managed by the delivery/tracking service (e.g., "Delivery"
     *  for Talabat, "Shipment" for Amazon). Read from theme.json. */
    protected String s4Entity() {
        String v = (String) theme().get("s4Entity");
        if (v == null) throw new IllegalStateException("theme.json missing 's4Entity'");
        return v;
    }

    /** S4 events Mongo collection — strict spec-name lookup against
     *  manifest.mongoCollections. For Amazon this is "shipment_events". */
    protected String s4EventsCollection() {
        String v = (String) theme().get("s4EventsCollection");
        if (v == null) throw new IllegalStateException("theme.json missing 's4EventsCollection'");
        return mongoCollectionByName(v);
    }

    /** S4 Cassandra time-series table — strict spec-name lookup against
     *  manifest.cassandraTables. For Amazon this is "shipment_tracking_events". */
    protected String s4TimeseriesTable() {
        String v = (String) theme().get("s4TimeseriesTable");
        if (v == null) throw new IllegalStateException("theme.json missing 's4TimeseriesTable'");
        return cassandraTableByName(v);
    }

    /** S4 Cassandra partition-key field name on the time-series entity
     *  (e.g., "shipmentId" for Amazon). Read from theme.json. */
    protected String s4TimeseriesPartitionField() {
        String v = (String) theme().get("s4TimeseriesPartitionField");
        if (v == null) throw new IllegalStateException("theme.json missing 's4TimeseriesPartitionField'");
        return v;
    }

    /** S4 Cassandra clustering-key field name on the time-series entity
     *  (e.g., "eventTime"). Read from theme.json. */
    protected String s4TimeseriesClusteringField() {
        String v = (String) theme().get("s4TimeseriesClusteringField");
        if (v == null) throw new IllegalStateException("theme.json missing 's4TimeseriesClusteringField'");
        return v;
    }

    /** S4 actor field on the time-series entity (e.g., "carrier" for Amazon,
     *  sourced from PG via cross-service SQL). Read from theme.json. */
    protected String s4ActorField() {
        String v = (String) theme().get("s4ActorField");
        if (v == null) throw new IllegalStateException("theme.json missing 's4ActorField'");
        return v;
    }

    /** S5 entity managed by the checkout/billing service (e.g., "Payment" for
     *  Talabat, "Transaction" for Amazon). Read from theme.json. */
    protected String s5RefundEntity() {
        String v = (String) theme().get("s5RefundEntity");
        if (v == null) throw new IllegalStateException("theme.json missing 's5RefundEntity'");
        return v;
    }

    /** S5 audit-trail Mongo collection — strict spec-name lookup against
     *  manifest.mongoCollections. For Amazon this is "transaction_audit_trail". */
    protected String s5AuditCollection() {
        String v = (String) theme().get("s5AuditCollection");
        if (v == null) throw new IllegalStateException("theme.json missing 's5AuditCollection'");
        return mongoCollectionByName(v);
    }

    /** S5 service cache-key prefix (e.g., "checkout-service" for Talabat,
     *  "billing-service" for Amazon). Used by S5-F12 to verify cache
     *  invalidation. Read from theme.json — read literal, no manifest cross-check. */
    protected String s5ServiceCachePrefix() {
        String v = (String) theme().get("s5ServiceCachePrefix");
        if (v == null) throw new IllegalStateException("theme.json missing 's5ServiceCachePrefix'");
        return v;
    }

    /** S5-F12 strategy class names per spec — read straight from theme.json so
     *  per-theme strategy renames stay declarative. */
    protected String s5StrategyFullRefund() {
        String v = (String) theme().get("s5StrategyFullRefund");
        if (v == null) throw new IllegalStateException("theme.json missing 's5StrategyFullRefund'");
        return v;
    }
    protected String s5StrategyFoodOnly() {
        String v = (String) theme().get("s5StrategyFoodOnly");
        if (v == null) throw new IllegalStateException("theme.json missing 's5StrategyFoodOnly'");
        return v;
    }
    protected String s5StrategyNoRefund() {
        String v = (String) theme().get("s5StrategyNoRefund");
        if (v == null) throw new IllegalStateException("theme.json missing 's5StrategyNoRefund'");
        return v;
    }

    // Spec-defined String-typed categorical filter values — for themes whose
    // catalog entity stores its primary categorical filter as a free-form
    // String (Amazon Replica M1.tex L506: `category String not null e.g.,
    // ELECTRONICS, CLOTHING`). The values here come straight from the spec's
    // example list.
    private static final java.util.Map<String, java.util.List<String>>
        SPEC_STRING_FILTER_VALUES = java.util.Map.of(
            "Product.category", java.util.List.of("ELECTRONICS", "CLOTHING", "BOOKS"));

    @SuppressWarnings("unchecked")
    protected String s2CategoricalFilterParam() {
        String entity = s2CatalogEntity();
        // 1. Prefer a non-status enum field (most themes: Restaurant.cuisineType etc.)
        for (java.util.Map<String, Object> col : entityColumns(entity)) {
            if (Boolean.TRUE.equals(col.get("isId"))) continue;
            String fieldName = (String) col.get("fieldName");
            if ("status".equalsIgnoreCase(fieldName)) continue;
            if (Boolean.TRUE.equals(col.get("isEnum"))) return fieldName;
        }
        // 2. Fall back to a spec-defined String-typed filter (Amazon's `category`).
        for (java.util.Map<String, Object> col : entityColumns(entity)) {
            String fieldName = (String) col.get("fieldName");
            if (SPEC_STRING_FILTER_VALUES.containsKey(entity + "." + fieldName)) return fieldName;
        }
        throw new IllegalStateException("No categorical filter field on entity " + entity);
    }

    @SuppressWarnings("unchecked")
    protected String enumValueAt(String entityClass, String fieldName, int index) {
        for (java.util.Map<String, Object> col : entityColumns(entityClass)) {
            if (fieldName.equals(col.get("fieldName"))) {
                String javaType = (String) col.get("javaType");
                if (Boolean.TRUE.equals(col.get("isEnum"))) {
                    java.util.List<String> values = enumValues(javaType);
                    if (values == null || values.size() <= index) {
                        throw new IllegalStateException(
                            "Enum " + javaType + " has only "
                          + (values == null ? 0 : values.size()) + " values, can't pick index " + index);
                    }
                    return values.get(index);
                }
                // Non-enum: fall back to spec-defined String filter values if registered.
                java.util.List<String> spec = SPEC_STRING_FILTER_VALUES.get(entityClass + "." + fieldName);
                if (spec != null) {
                    if (index >= spec.size()) {
                        throw new IllegalStateException(
                            "Spec-defined String filter " + entityClass + "." + fieldName +
                            " has only " + spec.size() + " values, can't pick index " + index);
                    }
                    return spec.get(index);
                }
                throw new IllegalArgumentException(
                    "Field " + fieldName + " on " + entityClass + " is not an enum (javaType=" + javaType + ")");
            }
        }
        throw new IllegalArgumentException("No field '" + fieldName + "' on entity '" + entityClass + "'");
    }

    @SuppressWarnings("unchecked")
    protected String buildKitchenSinkBody(String entityClass, java.util.Map<String, Object> overrides) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (java.util.Map<String, Object> col : entityColumns(entityClass)) {
            if (Boolean.TRUE.equals(col.get("isId"))) continue;
            String relation = (String) col.get("relationKind");
            if (relation != null && !relation.isEmpty()) continue;
            String fieldName = (String) col.get("fieldName");
            Object value = (overrides != null && overrides.containsKey(fieldName))
                    ? overrides.get(fieldName) : _defaultValueFor(col);
            if (value == null) continue;
            if (!first) json.append(",");
            first = false;
            json.append("\"").append(fieldName).append("\":");
            json.append(_jsonSerialize(value));
        }
        json.append("}");
        return json.toString();
    }

    @SuppressWarnings("unchecked")
    private Object _defaultValueFor(java.util.Map<String, Object> col) {
        boolean isEnum = Boolean.TRUE.equals(col.get("isEnum"));
        String javaType = (String) col.get("javaType");
        String fieldName = (String) col.get("fieldName");
        if (isEnum) {
            java.util.List<String> values = enumValues(javaType);
            return (values == null || values.isEmpty()) ? null : values.get(0);
        }
        if (javaType == null) return null;
        switch (javaType) {
            case "String": return "Default " + fieldName;
            case "Long": case "long": case "Integer": case "int": return 1;
            case "Double": case "double": case "BigDecimal": case "Float": case "float": return 4.5;
            case "Boolean": case "boolean": return true;
            case "Instant": case "LocalDateTime": return "2026-04-29T12:00:00";
            case "LocalDate": return "2026-04-29";
            case "JsonNode": case "Map": case "ObjectNode":
                return java.util.Map.of("description", "default " + fieldName);
            default: return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String _jsonSerialize(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) {
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof java.util.Map) {
            java.util.Map<String, Object> m = (java.util.Map<String, Object>) value;
            StringBuilder sb = new StringBuilder("{");
            boolean f = true;
            for (java.util.Map.Entry<String, Object> e : m.entrySet()) {
                if (!f) sb.append(",");
                f = false;
                sb.append("\"").append(e.getKey()).append("\":").append(_jsonSerialize(e.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        return "null";
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> _entityRecord(String entityClass) {
        java.util.List<java.util.Map<String, Object>> entities =
            (java.util.List<java.util.Map<String, Object>>) manifest().get("entities");
        if (entities != null) {
            for (java.util.Map<String, Object> e : entities) {
                if (entityClass.equals(e.get("className"))) return e;
            }
        }
        throw new IllegalStateException("Entity not in manifest: " + entityClass);
    }

    /** PG table name for an entity (e.g. "User" → "users"). */
    protected String tableName(String entityClass) {
        return (String) _entityRecord(entityClass).get("tableName");
    }

    /** Full column metadata for an entity. */
    @SuppressWarnings("unchecked")
    protected java.util.List<java.util.Map<String, Object>> entityColumns(String entityClass) {
        return (java.util.List<java.util.Map<String, Object>>) _entityRecord(entityClass).get("columns");
    }

    /** Enum values for a Java enum type (e.g. "Role" → ["ADMIN","CUSTOMER"]). */
    @SuppressWarnings("unchecked")
    protected java.util.List<String> enumValues(String enumName) {
        java.util.List<java.util.Map<String, Object>> enums =
            (java.util.List<java.util.Map<String, Object>>) manifest().get("enums");
        if (enums != null) {
            for (java.util.Map<String, Object> e : enums) {
                if (enumName.equals(e.get("enumName"))) {
                    return (java.util.List<String>) e.get("values");
                }
            }
        }
        throw new IllegalStateException("Enum not in manifest: " + enumName);
    }

    /** Login path from manifest.auth.loginPath. */
    @SuppressWarnings("unchecked")
    protected String loginPath() {
        java.util.Map<String, Object> auth = (java.util.Map<String, Object>) manifest().get("auth");
        if (auth == null) throw new IllegalStateException("manifest.auth missing");
        String p = (String) auth.get("loginPath");
        if (p == null) throw new IllegalStateException("manifest.auth.loginPath missing");
        return p;
    }

    /** Register path — discovered by walking controller endpoints for a POST whose full path ends with "/auth/register". */
    protected String registerPath() {
        java.util.regex.Pattern re = java.util.regex.Pattern.compile(".*/auth/register$");
        for (java.util.Map<String, Object> ep : _allEndpointsWithFullPath()) {
            String verb = (String) ep.get("verb");
            String full = (String) ep.get("_fullPath");
            if ("POST".equalsIgnoreCase(verb) && full != null && re.matcher(full).matches()) {
                return full;
            }
        }
        throw new IllegalStateException("No POST endpoint matching '/auth/register' found in manifest controllers");
    }

    @SuppressWarnings("unchecked")
    private java.util.List<java.util.Map<String, Object>> _allEndpointsWithFullPath() {
        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        java.util.List<java.util.Map<String, Object>> controllers =
            (java.util.List<java.util.Map<String, Object>>) manifest().get("controllers");
        if (controllers == null) return out;
        for (java.util.Map<String, Object> ctrl : controllers) {
            java.util.List<String> basePaths = (java.util.List<String>) ctrl.get("basePaths");
            if (basePaths == null || basePaths.isEmpty()) basePaths = java.util.List.of("");
            java.util.List<java.util.Map<String, Object>> eps =
                (java.util.List<java.util.Map<String, Object>>) ctrl.get("endpoints");
            if (eps == null) continue;
            for (java.util.Map<String, Object> ep : eps) {
                String path = (String) ep.get("path");
                for (String bp : basePaths) {
                    // Scanner sometimes emits `path` already prefixed with the
                    // controller's basePath (i.e. the FULL endpoint path), and
                    // sometimes emits just the @{Get,Post,…}Mapping suffix
                    // ("/register"). Detect & avoid double-joining.
                    String b = bp == null ? "" : bp;
                    String p = path == null ? "" : path;
                    String full;
                    if (!b.isEmpty() && p.startsWith(b)) {
                        full = p;          // path already absolute
                    } else {
                        full = b + p;
                    }
                    java.util.Map<String, Object> withFull = new java.util.HashMap<>(ep);
                    withFull.put("_fullPath", full);
                    out.add(withFull);
                }
            }
        }
        return out;
    }

    private java.util.List<String> _pathSegmentCandidates(String table) {
        java.util.List<String> result = new java.util.ArrayList<>();
        if (table == null) return result;
        result.add(table);
        if (table.contains("_")) {
            result.add(table.replace('_', '-'));
            result.add(table.replace("_", ""));
            StringBuilder camel = new StringBuilder();
            boolean upNext = false;
            for (char c : table.toCharArray()) {
                if (c == '_') { upNext = true; continue; }
                camel.append(upNext ? Character.toUpperCase(c) : c);
                upNext = false;
            }
            String c = camel.toString();
            if (!result.contains(c)) result.add(c);
        }
        return result;
    }

    /** Full path TEMPLATE for an entity's "read by id" endpoint. Walks every
     *  controller's endpoints; picks the GET whose full path ends with
     *  "/<segment>/{var}" for any variant of the entity's tableName. */
    protected String crudReadPath(String entityClass) {
        String table = tableName(entityClass);
        java.util.List<String> segs = _pathSegmentCandidates(table);
        String best = null;
        for (java.util.Map<String, Object> ep : _allEndpointsWithFullPath()) {
            String verb = (String) ep.get("verb");
            if (!"GET".equalsIgnoreCase(verb)) continue;
            String full = (String) ep.get("_fullPath");
            if (full == null) continue;
            for (String seg : segs) {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "/" + java.util.regex.Pattern.quote(seg) + "/\\{[^}]+\\}$");
                if (p.matcher(full).find()) {
                    if (best == null || full.length() > best.length()) best = full;
                }
            }
        }
        if (best == null) {
            throw new IllegalStateException(
                "No CRUD read path found in manifest for entity '" + entityClass
              + "' (table=" + table + "; tried segments=" + segs + ")");
        }
        return best;
    }

    /** Collection-level path template (no trailing /{id}). */
    protected String crudCollectionPath(String entityClass) {
        return crudReadPath(entityClass).replaceFirst("/\\{[^}]+\\}$", "");
    }

    /** Substitute {placeholder} variables in a path template. */
    protected String fillPath(String template, java.util.Map<String, Object> vars) {
        String out = template;
        for (java.util.Map.Entry<String, Object> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }
        return out;
    }

    /** Convenience: full read path for a top-level entity by id. */
    protected String crudReadPathFor(String entityClass, long id) {
        return crudReadPath(entityClass).replaceFirst("\\{[^}]+\\}$", String.valueOf(id));
    }

    /** Read the role column for a user, looked up by email — fully
     *  manifest-driven (uses auth.userTable / auth.emailColumn /
     *  auth.roleColumn). Returns the role label as a String, with the
     *  column cast to text so PG-native ENUM types come back as their
     *  label rather than their internal OID. Used by privilege-
     *  escalation tests (e.g. "register with role=ADMIN in body,
     *  verify role didn't get honored"). Returns null if the row
     *  doesn't exist. */
    @SuppressWarnings("unchecked")
    protected String fetchUserRole(String email) {
        java.util.Map<String, Object> auth = (java.util.Map<String, Object>) manifest().get("auth");
        if (auth == null) throw new IllegalStateException("manifest.auth missing");
        String userTable = (String) auth.get("userTable");
        String roleCol = (String) auth.get("roleColumn");
        String emailCol = (String) auth.get("emailColumn");
        // Fallback: scanner couldn't identify the User entity → discover from information_schema.
        if (userTable == null) {
            try {
                userTable = jdbc.queryForObject(
                    "SELECT table_name FROM information_schema.tables " +
                    "WHERE table_schema='public' " +
                    "AND table_name IN ('users','user','app_user','app_users','customers','customer','accounts','account') " +
                    "ORDER BY CASE table_name " +
                    "WHEN 'users' THEN 1 WHEN 'user' THEN 2 WHEN 'app_user' THEN 3 WHEN 'app_users' THEN 4 " +
                    "WHEN 'customers' THEN 5 WHEN 'customer' THEN 6 WHEN 'accounts' THEN 7 WHEN 'account' THEN 8 END " +
                    "LIMIT 1",
                    String.class);
            } catch (DataAccessException ignored) {}
        }
        if (emailCol == null && userTable != null) {
            try {
                emailCol = jdbc.queryForObject(
                    "SELECT column_name FROM information_schema.columns " +
                    "WHERE table_schema='public' AND table_name=? " +
                    "AND column_name IN ('email','user_email','username','login') " +
                    "ORDER BY ordinal_position LIMIT 1",
                    String.class, userTable);
            } catch (DataAccessException ignored) {}
        }
        if (roleCol == null && userTable != null) {
            try {
                roleCol = jdbc.queryForObject(
                    "SELECT column_name FROM information_schema.columns " +
                    "WHERE table_schema='public' AND table_name=? " +
                    "AND column_name IN ('role','user_role','roles','authority') " +
                    "ORDER BY ordinal_position LIMIT 1",
                    String.class, userTable);
            } catch (DataAccessException ignored) {}
        }
        if (userTable == null || roleCol == null || emailCol == null) {
            throw new IllegalStateException(
                "manifest.auth missing one of {userTable, roleColumn, emailColumn}");
        }
        // Defensive identifier whitelist — manifest values flow into raw SQL.
        java.util.regex.Pattern ident = java.util.regex.Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
        if (!ident.matcher(userTable).matches()
            || !ident.matcher(roleCol).matches()
            || !ident.matcher(emailCol).matches()) {
            throw new IllegalStateException(
                "Manifest auth identifiers contain unsafe characters; refusing to build SQL");
        }
        try {
            return jdbc.queryForObject(
                "SELECT " + roleCol + "::text FROM " + userTable + " WHERE " + emailCol + " = ?",
                String.class, email);
        } catch (DataAccessException e) {
            return null;
        }
    }

    /** Look up a MongoDB collection by its SPEC-DEFINED name. Strict —
     *  throws AssertionError with a remediation hint if the student's
     *  @Document doesn't map to that name. */
    @SuppressWarnings("unchecked")
    protected String mongoCollectionByName(String specCollectionName) {
        java.util.List<java.util.Map<String, Object>> colls =
            (java.util.List<java.util.Map<String, Object>>) manifest().get("mongoCollections");
        if (colls != null) {
            for (java.util.Map<String, Object> c : colls) {
                if (specCollectionName.equals(c.get("collectionName"))) return specCollectionName;
            }
        }
        throw new AssertionError(
            "No @Document class maps to MongoDB collection '" + specCollectionName
          + "'. Per the M2 spec, this collection name is mandatory. Rename your "
          + "Mongo entity's annotation to: @Document(collection = \"" + specCollectionName
          + "\"). Make sure the import is "
          + "org.springframework.data.mongodb.core.mapping.Document.");
    }

    /** Look up an Elasticsearch index by its SPEC-DEFINED name. Strict —
     *  throws AssertionError with a remediation hint if the student's
     *  @Document doesn't map to that name. */
    @SuppressWarnings("unchecked")
    protected String esIndexByName(String specIndexName) {
        java.util.List<java.util.Map<String, Object>> idxs =
            (java.util.List<java.util.Map<String, Object>>) manifest().get("esIndices");
        if (idxs != null) {
            for (java.util.Map<String, Object> i : idxs) {
                if (specIndexName.equals(i.get("indexName"))) return specIndexName;
            }
        }
        throw new AssertionError(
            "No @Document class maps to Elasticsearch index '" + specIndexName
          + "'. Per the M2 spec, this index name is mandatory. Rename your "
          + "Elasticsearch entity's annotation to: @Document(indexName = \""
          + specIndexName + "\"). Make sure the import is "
          + "org.springframework.data.elasticsearch.annotations.Document.");
    }

    /** Look up the PG column name backing a Java field on an entity. Strict —
     *  throws AssertionError with a remediation hint if the field isn't on the
     *  entity in the manifest. Use this everywhere a test would otherwise
     *  hard-code a column name; the manifest is regenerated per-student at
     *  grading time so a student who declared @JoinColumn(name="userId") gets
     *  the right column name out the other side. */
    @SuppressWarnings("unchecked")
    protected String columnByField(String entityClass, String... fieldNames) {
        // Lenient field-name resolution. The spec mandates the STRUCTURE
        // (entity has a FK to user, entity has a totalAmount column, etc.);
        // it does NOT mandate which exact Java identifier the student picks
        // for the field. Three-layer resolution — tries the spec name(s)
        // first, falls back to the student's actual naming if the spec
        // names don't match anything:
        //
        //   1. EXACT MATCH on any alias
        //        columnByField("Order", "user") → matches if a field named
        //        "user" exists.
        //
        //   2. Id-SUFFIX FLIP on any alias
        //        Catches the canonical FK pattern split: spec might say
        //        `user` (JPA @ManyToOne) but student declared `userId`
        //        (plain Long, per the spec's "FK reference, not JPA-managed"
        //        note). Tries `user` ↔ `userId` automatically. No call-site
        //        change needed — the test author writes the spec name; the
        //        helper finds it under either pattern.
        //
        //   3. FUZZY SUBSTRING MATCH (last resort, must be unique)
        //        Scans every field on the entity for one whose name contains
        //        any of the requested aliases (or vice versa, case-insensitive).
        //        Only returns if EXACTLY ONE field matches — multiple matches
        //        means the request was ambiguous and we'd rather fail than
        //        guess wrong. Catches student-specific renames like
        //        `customer` for `user`, `eatery` for `restaurant`, etc.
        //
        // Throws a descriptive error only if NONE of the layers find a
        // unique match.
        //
        // Each candidate is then validated against the actual PG schema —
        // when the manifest scanner emits {fieldName="restaurant",
        // columnName="restaurant"} for a @ManyToOne field that Hibernate
        // actually maps to "restaurant_id" (because @JoinColumn(name=...)
        // wasn't read), the candidate "restaurant" doesn't exist in PG. We
        // then try "restaurant_id" (Hibernate's default FK column suffix)
        // before falling back.
        for (String fieldName : fieldNames) {
            for (java.util.Map<String, Object> col : entityColumns(entityClass)) {
                if (fieldName.equals(col.get("fieldName"))) {
                    String c = (String) col.get("columnName");
                    if (c != null) {
                        String resolved = _validateOrFlipFk(entityClass, c);
                        if (resolved != null) return resolved;
                    }
                }
            }
        }
        for (String fieldName : fieldNames) {
            String alt = fieldName.endsWith("Id") && fieldName.length() > 2
                    ? fieldName.substring(0, fieldName.length() - 2)
                    : fieldName + "Id";
            for (java.util.Map<String, Object> col : entityColumns(entityClass)) {
                if (alt.equals(col.get("fieldName"))) {
                    String c = (String) col.get("columnName");
                    if (c != null) {
                        String resolved = _validateOrFlipFk(entityClass, c);
                        if (resolved != null) return resolved;
                    }
                }
            }
        }
        java.util.LinkedHashSet<String> fuzzy = new java.util.LinkedHashSet<>();
        for (java.util.Map<String, Object> col : entityColumns(entityClass)) {
            String fname = (String) col.get("fieldName");
            if (fname == null) continue;
            String fnameLc = fname.toLowerCase();
            for (String alias : fieldNames) {
                String aLc = alias.toLowerCase();
                if (fnameLc.contains(aLc) || aLc.contains(fnameLc)) {
                    String c = (String) col.get("columnName");
                    if (c != null) fuzzy.add(c);
                    break;
                }
            }
        }
        for (String c : fuzzy) {
            String resolved = _validateOrFlipFk(entityClass, c);
            if (resolved != null) return resolved;
        }
        if (fuzzy.size() == 1) return fuzzy.iterator().next();
        throw new AssertionError(
            "Entity '" + entityClass + "' has no field matching any of "
          + java.util.Arrays.toString(fieldNames) + " (also tried Id-suffix flips "
          + "and fuzzy substring match). Fuzzy candidates: " + fuzzy + ". "
          + "Per the M2 spec this field is mandatory. Re-check your @Entity declaration.");
    }

    /** Cache: table → set of actual column names from information_schema. */
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.Set<String>>
        _COLUMN_NAMES_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private java.util.Set<String> _columnsOf(String table) {
        return _COLUMN_NAMES_CACHE.computeIfAbsent(table, t -> {
            try {
                return new java.util.HashSet<>(jdbc.queryForList(
                    "SELECT column_name FROM information_schema.columns "
                  + "WHERE table_schema = 'public' AND table_name = ?",
                    String.class, t));
            } catch (DataAccessException e) {
                return java.util.Collections.emptySet();
            }
        });
    }

    /** Post-resolution validator: if the manifest's columnName doesn't exist
     *  in the actual table (typical when the scanner missed @JoinColumn), try
     *  Hibernate's default FK column suffix `<col>_id`. Returns the column
     *  the DB actually has, or null if neither candidate exists.
     *
     *  Why: the L0 scanner reads @Column(name=...) but does NOT always read
     *  @JoinColumn(name=...) on @ManyToOne fields. A student with
     *  `@ManyToOne @JoinColumn(name="restaurant_id") Restaurant restaurant`
     *  ends up in the manifest as {fieldName="restaurant", columnName="restaurant"}
     *  but PG actually has the column as "restaurant_id". This validator
     *  catches that case and remaps. */
    private String _validateOrFlipFk(String entityClass, String candidate) {
        if (candidate == null) return null;
        String table;
        try { table = tableName(entityClass); } catch (Exception e) { return candidate; }
        if (table == null) return candidate;
        java.util.Set<String> cols = _columnsOf(table);
        if (cols.isEmpty()) return candidate;          // schema unreadable; trust manifest
        if (cols.contains(candidate)) return candidate;
        String withId = candidate + "_id";
        if (cols.contains(withId)) return withId;
        return null;                                    // caller will keep searching aliases
    }

    /** Set a single timestamp/date value on every "date column" of `table` (e.g.,
     *  order_date, created_at, transaction_date — but NOT updated_at, which is
     *  Hibernate-managed). Discovers existing date columns via
     *  information_schema.columns. Throws AssertionError if the table has no
     *  date/timestamp columns at all — date-range filters can't possibly work
     *  in that case, so the test cannot proceed. */
    protected void setAllDateColumns(String table, long rowId, java.sql.Timestamp ts) {
        java.util.List<String> dateCols;
        try {
            dateCols = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
              + "WHERE table_schema='public' AND table_name=? "
              + "  AND data_type IN ('timestamp without time zone','timestamp with time zone','date') "
              + "  AND column_name <> 'updated_at'",
                String.class, table);
        } catch (DataAccessException e) {
            throw new AssertionError("setAllDateColumns: failed to read information_schema for '"
                + table + "': " + e.getMessage(), e);
        }
        if (dateCols.isEmpty()) {
            throw new AssertionError(
                "Table '" + table + "' has no date/timestamp column (excluding updated_at). "
              + "S3-F10 dashboard date-range filtering needs an orderDate or createdAt column. "
              + "Add @Column private LocalDateTime orderDate; or rely on the baseline createdAt.");
        }
        for (String col : dateCols) {
            jdbc.update("UPDATE \"" + table + "\" SET \"" + col + "\" = ? WHERE id = ?", ts, rowId);
        }
    }

    /** Look up a Neo4j node label by its SPEC-DEFINED name. Strict — throws
     *  AssertionError with a remediation hint if the student's @Node doesn't
     *  declare that label. */
    @SuppressWarnings("unchecked")
    protected String neo4jLabelByName(String specLabel) {
        java.util.List<java.util.Map<String, Object>> nodes =
            (java.util.List<java.util.Map<String, Object>>) manifest().get("neo4jNodes");
        if (nodes != null) {
            for (java.util.Map<String, Object> n : nodes) {
                if (specLabel.equals(n.get("label"))) return specLabel;
            }
        }
        throw new AssertionError(
            "No @Node class declares Neo4j label '" + specLabel
          + "'. Per the M2 spec, this label is mandatory for the recommendation "
          + "graph. Annotate your Neo4j entity with: @Node(\"" + specLabel
          + "\"). Make sure the import is "
          + "org.springframework.data.neo4j.core.schema.Node.");
    }

    /** Look up a Cassandra time-series table by its SPEC-DEFINED name. Strict —
     *  throws AssertionError with a remediation hint if no @Table class declares
     *  that table name. */
    @SuppressWarnings("unchecked")
    protected String cassandraTableByName(String specTableName) {
        java.util.List<java.util.Map<String, Object>> tables =
            (java.util.List<java.util.Map<String, Object>>) manifest().get("cassandraTables");
        if (tables != null) {
            for (java.util.Map<String, Object> t : tables) {
                if (specTableName.equals(t.get("tableName"))) return specTableName;
            }
        }
        throw new AssertionError(
            "No Spring Data Cassandra @Table class declares table '" + specTableName
          + "'. Per the M2 spec, this table name is mandatory for the time-series tracking. "
          + "Annotate your Cassandra entity with: @Table(\"" + specTableName
          + "\"). Make sure the import is "
          + "org.springframework.data.cassandra.core.mapping.Table.");
    }

    /** Look up the Cassandra column name backing a Java field on a time-series
     *  table. Strict — throws AssertionError if the field isn't on the table
     *  in the manifest. Mirrors {@link #columnByField} but for cassandraTables. */
    @SuppressWarnings("unchecked")
    protected String cassandraColumnByField(String tableClassName, String fieldName) {
        java.util.List<java.util.Map<String, Object>> tables =
            (java.util.List<java.util.Map<String, Object>>) manifest().get("cassandraTables");
        if (tables != null) {
            for (java.util.Map<String, Object> t : tables) {
                if (!tableClassName.equals(t.get("className"))) continue;
                java.util.List<java.util.Map<String, Object>> cols =
                    (java.util.List<java.util.Map<String, Object>>) t.get("columns");
                if (cols == null) break;
                for (java.util.Map<String, Object> col : cols) {
                    if (fieldName.equals(col.get("fieldName"))) {
                        String c = (String) col.get("columnName");
                        if (c != null) return c;
                    }
                }
            }
        }
        throw new AssertionError(
            "Cassandra table class '" + tableClassName + "' has no field '" + fieldName
          + "' in the manifest. Per the M2 spec this field is mandatory.");
    }

    /** Look up the Cassandra @Table class name (Java class) corresponding to
     *  a SPEC-DEFINED table name. Used to chain into cassandraColumnByField. */
    @SuppressWarnings("unchecked")
    protected String cassandraTableClassByName(String specTableName) {
        java.util.List<java.util.Map<String, Object>> tables =
            (java.util.List<java.util.Map<String, Object>>) manifest().get("cassandraTables");
        if (tables != null) {
            for (java.util.Map<String, Object> t : tables) {
                if (specTableName.equals(t.get("tableName"))) return (String) t.get("className");
            }
        }
        throw new AssertionError(
            "No Cassandra @Table class declares table name '" + specTableName + "'.");
    }

    // ────────────────────────────────────────────────────────────────
    // Neo4j helpers — bolt-driver based. Tests verify recommendation
    // graph state (Product → Product BOUGHT_TOGETHER edges, idempotency
    // for Amazon). All return null/-1/false on null driver so individual
    // tests can throw a clear "Neo4j required" assertion.
    // ────────────────────────────────────────────────────────────────

    /** Run a write/read Cypher statement, return all records as a list of
     *  Map (column → value). Returns empty list if driver is null. */
    @SuppressWarnings("unchecked")
    protected java.util.List<java.util.Map<String, Object>> neo4jExec(
            String cypher, java.util.Map<String, Object> params) {
        if (neo4j == null) return java.util.List.of();
        try (org.neo4j.driver.Session s = neo4j.session()) {
            org.neo4j.driver.Result r = (params == null || params.isEmpty())
                ? s.run(cypher)
                : s.run(cypher, params);
            java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
            while (r.hasNext()) out.add(r.next().asMap());
            return out;
        }
    }

    protected java.util.List<java.util.Map<String, Object>> neo4jExec(String cypher) {
        return neo4jExec(cypher, java.util.Map.of());
    }

    /** Wipe the entire graph (nodes + relationships). Called from
     *  autoTruncateAllData() so each test starts from an empty graph. */
    protected void neo4jClear() {
        if (neo4j == null) return;
        try { neo4jExec("MATCH (n) DETACH DELETE n"); } catch (Exception ignored) { }
    }

    /** Count nodes with a given label and id property. Returns -1 if driver is null. */
    protected long neo4jNodeCount(String label, long id) {
        if (neo4j == null) return -1L;
        java.util.List<java.util.Map<String, Object>> rows = neo4jExec(
            "MATCH (n:`" + label + "` {id: $id}) RETURN count(n) AS c",
            java.util.Map.of("id", id));
        if (rows.isEmpty()) return 0L;
        Object c = rows.get(0).get("c");
        return c instanceof Number n ? n.longValue() : 0L;
    }

    /** Returns true if a relationship of given type exists between two
     *  labelled-id nodes. Returns false if driver is null. */
    protected boolean neo4jRelExists(String fromLabel, long fromId,
                                     String relType,
                                     String toLabel,   long toId) {
        if (neo4j == null) return false;
        java.util.List<java.util.Map<String, Object>> rows = neo4jExec(
            "MATCH (a:`" + fromLabel + "` {id: $fromId})-[r:`" + relType
          + "`]->(b:`" + toLabel + "` {id: $toId}) RETURN count(r) AS c",
            java.util.Map.of("fromId", fromId, "toId", toId));
        if (rows.isEmpty()) return false;
        Object c = rows.get(0).get("c");
        return c instanceof Number n && n.longValue() > 0L;
    }

    /** Read the orderCount property on the first matching ORDERED_FROM-style
     *  relationship between (fromLabel:fromId) and (toLabel:toId). Returns
     *  -1 if no edge or driver is null. */
    protected long neo4jRelOrderCount(String fromLabel, long fromId,
                                      String relType,
                                      String toLabel,   long toId) {
        if (neo4j == null) return -1L;
        java.util.List<java.util.Map<String, Object>> rows = neo4jExec(
            "MATCH (a:`" + fromLabel + "` {id: $fromId})-[r:`" + relType
          + "`]->(b:`" + toLabel + "` {id: $toId}) RETURN r.orderCount AS oc LIMIT 1",
            java.util.Map.of("fromId", fromId, "toId", toId));
        if (rows.isEmpty()) return -1L;
        Object oc = rows.get(0).get("oc");
        return oc instanceof Number n ? n.longValue() : -1L;
    }

    /** Read a string-typed relationship property (e.g. "lastOrderDate" if
     *  stored as ISO string). Returns null if no edge or property missing. */
    protected String neo4jRelStringProp(String fromLabel, long fromId,
                                        String relType,
                                        String toLabel,   long toId,
                                        String propName) {
        if (neo4j == null) return null;
        java.util.List<java.util.Map<String, Object>> rows = neo4jExec(
            "MATCH (a:`" + fromLabel + "` {id: $fromId})-[r:`" + relType
          + "`]->(b:`" + toLabel + "` {id: $toId}) RETURN r.`" + propName
          + "` AS v LIMIT 1",
            java.util.Map.of("fromId", fromId, "toId", toId));
        if (rows.isEmpty()) return null;
        Object v = rows.get(0).get("v");
        return v == null ? null : v.toString();
    }

    // ────────────────────────────────────────────────────────────────
    // Redis helpers — Jedis client. Tests verify cache key TTL after
    // endpoint calls. Values may be Spring-serialized binary / JSON;
    // tests should use redisExists / redisTtl rather than redisGet for
    // reliable assertions.
    // ────────────────────────────────────────────────────────────────

    /** Wipe the current Redis DB — called from autoTruncateAllData() so each
     *  test starts with an empty cache. Reconnects if the persistent connection
     *  went stale (idle timeout or server restart between tests). */
    protected void redisFlushDb() {
        if (redis == null) return;
        try {
            redis.flushDB();
        } catch (Exception e) {
            // Connection went stale — rebuild and retry once
            String redisHost = envOr("SPRING_DATA_REDIS_HOST", "localhost");
            int    redisPort  = Integer.parseInt(envOr("SPRING_DATA_REDIS_PORT", "6379"));
            String redisPass  = envOr("SPRING_DATA_REDIS_PASSWORD", "");
            try {
                redis = new redis.clients.jedis.Jedis(redisHost, redisPort);
                if (!redisPass.isBlank()) redis.auth(redisPass);
                redis.flushDB();
            } catch (Exception ignored) { }
        }
    }

    /** Drop every non-system collection in the test Mongo database. Called
     *  from autoTruncateAllData() so each test starts with a clean event log
     *  (transaction_audit_trail / order_events / shipment_events / auth_events
     *  /etc.). PG truncate resets identity, so without this, txId/orderId
     *  values collide across tests and the lifecycle endpoint returns events
     *  from prior runs. Soft-fail when Mongo is unreachable. */
    protected void mongoClearAllCollections() {
        if (mongo == null) return;
        try {
            for (String coll : mongo.listCollectionNames()) {
                if (coll.startsWith("system.")) continue;
                try { mongo.getCollection(coll).drop(); } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
    }

    /** Match keys via a glob pattern (e.g., "*", "dashboard*").
     *  Returns empty set if driver is null. */
    protected java.util.Set<String> redisKeys(String pattern) {
        if (redis == null) return java.util.Set.of();
        try { return redis.keys(pattern); }
        catch (Exception e) { return java.util.Set.of(); }
    }

    /** TTL in seconds. -1 = key has no expiry; -2 = key doesn't exist;
     *  -3 = driver null. */
    protected long redisTtl(String key) {
        if (redis == null) return -3L;
        try { return redis.ttl(key); }
        catch (Exception e) { return -2L; }
    }

    /** True if the key currently exists in Redis. */
    protected boolean redisExists(String key) {
        if (redis == null) return false;
        try { return redis.exists(key); }
        catch (Exception e) { return false; }
    }

    /** Raw value lookup (string-encoded). May be Spring-serialized binary
     *  on the wire, in which case the returned string is best-effort and
     *  may contain non-printable characters — comparisons should be on
     *  exact equality, not parsing. */
    protected String redisGet(String key) {
        if (redis == null) return null;
        try { return redis.get(key); }
        catch (Exception e) { return null; }
    }

    // ────────────────────────────────────────────────────────────────
    // Cassandra helpers — DataStax driver. S4-F11 verifies tracking-event
    // writes; S4-F12 verifies clustering-ordered reads. Tests must guard
    // with a null check on `cassandra` and fail with a clear "Cassandra
    // required" message.
    // ────────────────────────────────────────────────────────────────

    /** Run a CQL statement with positional bind values. Returns null on
     *  driver-null. */
    protected com.datastax.oss.driver.api.core.cql.ResultSet cassandraExec(String cql, Object... params) {
        if (cassandra == null) return null;
        return cassandra.execute(
            com.datastax.oss.driver.api.core.cql.SimpleStatement.newInstance(cql, params));
    }

    /** Truncate a Cassandra table (keyspace-qualified is unnecessary —
     *  session is bound to the keyspace). Called from autoTruncateAllData()
     *  so each test starts with an empty time-series. */
    protected void cassandraClear(String tableName) {
        if (cassandra == null) return;
        try { cassandra.execute("TRUNCATE TABLE \"" + tableName + "\""); }
        catch (Exception ignored) { }
    }

    /** Count rows in a partition (e.g., all tracking events for one shipment).
     *  Returns -1 if driver is null. */
    protected long cassandraCount(String tableName, String partitionCol, Object partitionVal) {
        if (cassandra == null) return -1L;
        com.datastax.oss.driver.api.core.cql.ResultSet rs =
            cassandraExec("SELECT count(*) FROM \"" + tableName + "\" WHERE \"" + partitionCol + "\" = ?", partitionVal);
        com.datastax.oss.driver.api.core.cql.Row row = rs == null ? null : rs.one();
        return row == null ? 0L : row.getLong(0);
    }

    /** Read all rows for a partition (returns most-recent-first per the
     *  table's clustering order, which spec mandates as DESC by event-time).
     *  Each row is exposed as a Map<columnName, value>. Returns empty list
     *  on driver-null. */
    protected java.util.List<java.util.Map<String, Object>> cassandraRows(
            String tableName, String partitionCol, Object partitionVal) {
        if (cassandra == null) return java.util.List.of();
        com.datastax.oss.driver.api.core.cql.ResultSet rs = cassandraExec(
            "SELECT * FROM \"" + tableName + "\" WHERE \"" + partitionCol + "\" = ?", partitionVal);
        if (rs == null) return java.util.List.of();
        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        for (com.datastax.oss.driver.api.core.cql.Row r : rs) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            com.datastax.oss.driver.api.core.cql.ColumnDefinitions defs = r.getColumnDefinitions();
            for (int i = 0; i < defs.size(); i++) {
                String col = defs.get(i).getName().asInternal();
                m.put(col, r.getObject(i));
            }
            out.add(m);
        }
        return out;
    }

    /** Discover the FK column on the S3 order table that points to the S2
     *  catalog table — looked up at runtime via information_schema. Robust
     *  to any FK column naming the student picked. Throws AssertionError
     *  with a remediation hint if no such FK exists. */
    protected String s2CatalogFkColumn() {
        String orderTable   = tableName(s3OrderEntity());
        String catalogTable = tableName(s2CatalogEntity());
        try {
            return jdbc.queryForObject(
                "SELECT kcu.column_name "
              + "FROM information_schema.table_constraints tc "
              + "JOIN information_schema.key_column_usage kcu "
              + "  ON tc.constraint_name = kcu.constraint_name "
              + " AND tc.table_schema = kcu.table_schema "
              + "JOIN information_schema.constraint_column_usage ccu "
              + "  ON tc.constraint_name = ccu.constraint_name "
              + " AND tc.table_schema = ccu.table_schema "
              + "WHERE tc.constraint_type = 'FOREIGN KEY' "
              + "  AND tc.table_schema = 'public' "
              + "  AND tc.table_name = ? AND ccu.table_name = ? "
              + "ORDER BY kcu.ordinal_position LIMIT 1",
                String.class, orderTable, catalogTable);
        } catch (DataAccessException e) {
            throw new AssertionError(
                "No FK from order table '" + orderTable
              + "' to catalog table '" + catalogTable
              + "'. The S3 order entity must have a relation to the S2 catalog entity. "
              + e.getMessage());
        }
    }

    /** Allow-list of standard PG type udt names that are NOT enums. */
    private static final java.util.Set<String> _NON_ENUM_UDTS = java.util.Set.of(
        "varchar", "text", "bpchar", "char", "name",
        "int2", "int4", "int8", "float4", "float8", "numeric", "money",
        "bool", "date", "timestamp", "timestamptz", "time", "timetz",
        "uuid", "bytea", "json", "jsonb", "interval", "cidr", "inet"
    );

    private boolean _isPgEnumUdt(String udt) {
        if (udt == null) return false;
        String lc = udt.toLowerCase();
        if (lc.startsWith("_")) return false;            // arrays
        if (!lc.matches("^[a-z_][a-z0-9_]*$")) return false;
        return !_NON_ENUM_UDTS.contains(lc);
    }

    /** Pick the first label of a PG-native enum type. Returns null on error. */
    private String _firstEnumLabel(String udt) {
        if (!_isPgEnumUdt(udt)) return null;
        try {
            return jdbc.queryForObject(
                "SELECT (enum_range(NULL::" + udt + "))[1]::text", String.class);
        } catch (DataAccessException e) {
            return null;
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.List<String>>
        _ENUM_LABELS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private java.util.List<String> _enumLabels(String udt) {
        if (!_isPgEnumUdt(udt)) return java.util.Collections.emptyList();
        return _ENUM_LABELS_CACHE.computeIfAbsent(udt, u -> {
            try {
                String arr = jdbc.queryForObject(
                    "SELECT enum_range(NULL::" + u + ")::text", String.class);
                if (arr == null) return java.util.Collections.emptyList();
                String[] raw = arr.replaceAll("^\\{|\\}$", "").split(",");
                java.util.List<String> out = new java.util.ArrayList<>(raw.length);
                for (String s : raw) out.add(s.trim().replaceAll("^\"|\"$", ""));
                return out;
            } catch (DataAccessException e) {
                return java.util.Collections.emptyList();
            }
        });
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.Map<String, java.util.List<String>>>
        _CHECK_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    private java.util.Map<String, java.util.List<String>> _checkInListsFor(String table) {
        return _CHECK_CACHE.computeIfAbsent(table, t -> {
            java.util.Map<String, java.util.List<String>> out = new java.util.HashMap<>();
            try {
                java.util.List<java.util.Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT pg_get_constraintdef(c.oid) AS def "
                  + "FROM pg_constraint c "
                  + "JOIN pg_class cl ON c.conrelid = cl.oid "
                  + "JOIN pg_namespace n ON cl.relnamespace = n.oid "
                  + "WHERE c.contype = 'c' AND n.nspname = 'public' AND cl.relname = ?",
                    t);
                java.util.regex.Pattern anyForm = java.util.regex.Pattern.compile(
                    "\\(\\s*(\\w+)\\s*\\)\\s*::\\s*\\w+\\s*=\\s*ANY\\s*\\(+\\s*ARRAY\\s*\\[(.+?)\\]",
                    java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
                java.util.regex.Pattern inForm = java.util.regex.Pattern.compile(
                    "\\b(\\w+)\\s+IN\\s*\\((.+?)\\)",
                    java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
                java.util.regex.Pattern litExtract = java.util.regex.Pattern.compile("'([^']*)'");
                for (java.util.Map<String, Object> r : rows) {
                    String def = String.valueOf(r.get("def"));
                    String col = null, body = null;
                    java.util.regex.Matcher m = anyForm.matcher(def);
                    if (m.find()) { col = m.group(1); body = m.group(2); }
                    else { m = inForm.matcher(def); if (m.find()) { col = m.group(1); body = m.group(2); } }
                    if (col == null || body == null) continue;
                    java.util.List<String> labels = new java.util.ArrayList<>();
                    java.util.regex.Matcher lm = litExtract.matcher(body);
                    while (lm.find()) labels.add(lm.group(1));
                    if (!labels.isEmpty()) out.put(col, labels);
                }
            } catch (DataAccessException ignored) { }
            return out;
        });
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.List<java.util.Map<String, String>>>
        _FK_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    private java.util.List<java.util.Map<String, String>> _fkColumnsOf(String table) {
        return _FK_CACHE.computeIfAbsent(table, t -> {
            try {
                java.util.List<java.util.Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT kcu.column_name AS column, ccu.table_name AS ref "
                  + "FROM information_schema.table_constraints tc "
                  + "JOIN information_schema.key_column_usage kcu "
                  + "  ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema "
                  + "JOIN information_schema.constraint_column_usage ccu "
                  + "  ON tc.constraint_name = ccu.constraint_name AND tc.table_schema = ccu.table_schema "
                  + "WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'public' AND tc.table_name = ?",
                    t);
                java.util.List<java.util.Map<String, String>> out = new java.util.ArrayList<>();
                for (java.util.Map<String, Object> r : rows) {
                    java.util.Map<String, String> m = new java.util.HashMap<>();
                    m.put("column", String.valueOf(r.get("column")));
                    m.put("ref",    String.valueOf(r.get("ref")));
                    out.add(m);
                }
                return out;
            } catch (DataAccessException e) {
                return java.util.Collections.emptyList();
            }
        });
    }

    private String _fkRefTable(String table, String column) {
        for (java.util.Map<String, String> fk : _fkColumnsOf(table)) {
            if (column.equals(fk.get("column"))) return fk.get("ref");
        }
        return null;
    }

    private Long _anyExistingId(String refTable) {
        try {
            return jdbc.queryForObject(
                "SELECT id FROM \"" + refTable + "\" ORDER BY id LIMIT 1", Long.class);
        } catch (DataAccessException e) {
            return null;
        }
    }

    private Object _coerceValue(String table, String column, String dataType, String udt, Object value) {
        if (!(value instanceof String)) return value;
        String s = (String) value;
        if ("USER-DEFINED".equals(dataType) && _isPgEnumUdt(udt)) {
            java.util.List<String> labels = _enumLabels(udt);
            if (!labels.isEmpty() && !labels.contains(s)) {
                for (String l : labels) if (l.equalsIgnoreCase(s)) return l;
                return labels.get(0);
            }
            return s;
        }
        java.util.List<String> chk = _checkInListsFor(table).get(column);
        if (chk != null && !chk.isEmpty() && !chk.contains(s)) {
            for (String l : chk) if (l.equalsIgnoreCase(s)) return l;
            return chk.get(0);
        }
        return value;
    }

    /** Per-process counter so every text/email/phone auto-default is unique
     *  even within a single transaction (UNIQUE constraint friendly). */
    private static final java.util.concurrent.atomic.AtomicLong _DEFAULT_NONCE =
        new java.util.concurrent.atomic.AtomicLong(System.nanoTime());

    /** Generate a sane default scalar for a PG column based on data_type/udt.
     *  Text columns are shape-aware (email → email-form, phone → E.164-ish, etc.)
     *  AND uniqueness-tagged so UNIQUE-constraint columns don't collide. */
    private Object _defaultForPgColumn(String dataType, String udt, String column) {
        if (dataType == null) return null;
        String nameLc = column == null ? "" : column.toLowerCase();
        long nonce = _DEFAULT_NONCE.incrementAndGet();
        switch (dataType) {
            case "character varying":
            case "character":
            case "text": {
                if (nameLc.contains("email"))    return "sample_" + nonce + "@grader.testgen.io";
                if (nameLc.contains("phone") || nameLc.contains("mobile"))
                                                  return "+201" + String.format("%09d", nonce % 1_000_000_000L);
                if (nameLc.contains("password") || nameLc.contains("hash"))
                                                  return "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
                if (nameLc.contains("url") || nameLc.contains("link") || nameLc.contains("href"))
                                                  return "https://example.com/" + nonce;
                if (nameLc.equals("currency") || nameLc.endsWith("_currency"))
                                                  return "USD";
                if (nameLc.equals("locale") || nameLc.equals("language"))
                                                  return "en";
                if (nameLc.equals("country") || nameLc.endsWith("_country"))
                                                  return "EG";
                return "Sample " + column + " " + nonce;
            }
            case "smallint":
            case "integer":
            case "bigint":
                // Counter-shaped columns that semantically start at zero.
                // Defaulting "totalRatings"/"orderCount" to 1 corrupts running-
                // average and totalCount semantics on the very first event the
                // student handles (their math is correct against a 0 baseline).
                if (nameLc.startsWith("total") || nameLc.endsWith("count") || nameLc.endsWith("_count")) return 0;
                return 1;
            case "numeric":
            case "real":
            case "double precision":
                // Same logic for averages / aggregates that start at 0.0.
                if (nameLc.equals("rating") || nameLc.endsWith("rating") || nameLc.endsWith("_rating")) return 0.0;
                if (nameLc.startsWith("avg") || nameLc.startsWith("total") || nameLc.startsWith("sum")) return 0.0;
                return 4.5;
            case "money":
                return new java.math.BigDecimal("0.00");
            case "boolean":
                return true;
            case "date":
                return java.sql.Date.valueOf("2026-01-01");
            case "timestamp without time zone":
            case "timestamp with time zone":
                return java.sql.Timestamp.valueOf("2026-01-01 12:00:00");
            case "time without time zone":
            case "time with time zone":
                return java.sql.Time.valueOf("12:00:00");
            case "interval":
                return "0 seconds";
            case "json":
            case "jsonb":
                return "{\"note\":\"true\"}";
            case "uuid":
                return java.util.UUID.randomUUID().toString();
            case "bytea":
                return new byte[0];
            case "inet":
                return "127.0.0.1";
            case "cidr":
                return "127.0.0.0/24";
            case "macaddr":
                return "08:00:2b:01:02:03";
            case "bit":
            case "bit varying":
                return "0";
            case "xml":
                return "<x/>";
            case "USER-DEFINED": {
                String label = _firstEnumLabel(udt);
                return label;
            }
            case "ARRAY":
                return "{}";
            default:
                return null;
        }
    }

    /** Generic INSERT into a PG table. Walks information_schema for the column
     *  list, fills overrides + sane defaults per type. Skips columns it can't
     *  default. PG-native ENUM values render as ``'VAL'::udt`` literals; jsonb
     *  values render as ``?::jsonb``. Errors are swallowed via ``_tryUpdate``-style
     *  semantics so a single failure doesn't block the rest of the seed. */
    @SuppressWarnings("unchecked")
    protected void _seedTableRow(String table, java.util.Map<String, Object> overrides) {
        if (jdbc == null) return;
        java.util.List<java.util.Map<String, Object>> cols;
        try {
            cols = jdbc.queryForList(
                "SELECT column_name, data_type, udt_name, is_nullable, column_default "
              + "FROM information_schema.columns "
              + "WHERE table_schema = 'public' AND table_name = ? "
              + "ORDER BY ordinal_position",
                table);
        } catch (DataAccessException e) { return; }
        if (cols == null || cols.isEmpty()) return;

        StringBuilder sql  = new StringBuilder("INSERT INTO \"").append(table).append("\" (");
        StringBuilder vals = new StringBuilder(") VALUES (");
        java.util.List<Object> params = new java.util.ArrayList<>();
        boolean first = true;
        for (java.util.Map<String, Object> c : cols) {
            String name = (String) c.get("column_name");
            if (name == null) continue;
            if ("id".equalsIgnoreCase(name)) continue;             // serial PK
            String dataType = (String) c.get("data_type");
            String udt      = (String) c.get("udt_name");

            Object value;
            if (overrides != null && overrides.containsKey(name)) {
                value = overrides.get(name);
                if (value == null) continue;
            } else {
                value = _defaultForPgColumn(dataType, udt, name);
                if (value == null) continue;
            }
            value = _coerceValue(table, name, dataType, udt, value);

            if (!first) { sql.append(", "); vals.append(", "); }
            first = false;
            sql.append("\"").append(name).append("\"");

            if ("USER-DEFINED".equals(dataType) && _isPgEnumUdt(udt) && value instanceof String s) {
                vals.append("'").append(s.replace("'", "''")).append("'::").append(udt);
            } else if (("json".equals(dataType) || "jsonb".equals(dataType))) {
                vals.append("?::").append(dataType);
                params.add(value);
            } else {
                vals.append("?");
                params.add(value);
            }
        }
        sql.append(vals).append(")");
        try { jdbc.update(sql.toString(), params.toArray()); }
        catch (DataAccessException ignored) { }
    }

    @SuppressWarnings("unchecked")
    protected Long insertRowReturningId(String table, java.util.Map<String, Object> overrides) {
        if (jdbc == null) {
            throw new AssertionError("jdbc not initialized");
        }
        java.util.List<java.util.Map<String, Object>> cols;
        try {
            cols = jdbc.queryForList(
                "SELECT column_name, data_type, udt_name, is_nullable, column_default "
              + "FROM information_schema.columns "
              + "WHERE table_schema = 'public' AND table_name = ? "
              + "ORDER BY ordinal_position",
                table);
        } catch (DataAccessException e) {
            throw new AssertionError("Cannot read schema for table '" + table + "': " + e.getMessage(), e);
        }
        if (cols == null || cols.isEmpty()) {
            throw new AssertionError("No columns found for table: " + table);
        }
        StringBuilder sql  = new StringBuilder("INSERT INTO \"").append(table).append("\" (");
        StringBuilder vals = new StringBuilder(") VALUES (");
        java.util.List<Object> params = new java.util.ArrayList<>();
        boolean first = true;
        for (java.util.Map<String, Object> c : cols) {
            String name = (String) c.get("column_name");
            if (name == null) continue;
            if ("id".equalsIgnoreCase(name)) continue;
            String dataType = (String) c.get("data_type");
            String udt      = (String) c.get("udt_name");
            String isNullable = (String) c.get("is_nullable");
            String columnDefault = (String) c.get("column_default");
            boolean nullable = "YES".equalsIgnoreCase(isNullable);
            boolean hasDbDefault = columnDefault != null && !columnDefault.toString().isBlank();

            Object value;
            if (overrides != null && overrides.containsKey(name)) {
                value = overrides.get(name);
                if (value == null) continue;
            } else if (!nullable) {
                String fkRef = _fkRefTable(table, name);
                if (fkRef != null) {
                    Long existing = _anyExistingId(fkRef);
                    if (existing == null) {
                        if (hasDbDefault) continue;
                        throw new AssertionError(
                            "Cannot auto-default NOT NULL FK column " + table + "." + name
                          + " → " + fkRef + " (parent is empty).");
                    }
                    value = existing;
                } else {
                    java.util.List<String> labels = _checkInListsFor(table).get(name);
                    if (labels != null && !labels.isEmpty()) {
                        value = labels.get(0);
                        if (!first) { sql.append(", "); vals.append(", "); }
                        first = false;
                        sql.append("\"").append(name).append("\"");
                        vals.append("?");
                        params.add(value);
                        continue;
                    }
                    value = _defaultForPgColumn(dataType, udt, name);
                    if (value == null) {
                        if (hasDbDefault) continue;
                        throw new AssertionError(
                            "Cannot auto-default NOT NULL column " + table + "." + name);
                    }
                }
            } else if (hasDbDefault) {
                continue;
            } else {
                continue;
            }
            value = _coerceValue(table, name, dataType, udt, value);
            if (!first) { sql.append(", "); vals.append(", "); }
            first = false;
            sql.append("\"").append(name).append("\"");
            if ("USER-DEFINED".equals(dataType) && _isPgEnumUdt(udt) && value instanceof String s) {
                vals.append("'").append(s.replace("'", "''")).append("'::").append(udt);
            } else if ("json".equals(dataType) || "jsonb".equals(dataType)) {
                vals.append("?::").append(dataType);
                params.add(value);
            } else {
                vals.append("?");
                params.add(value);
            }
        }
        sql.append(vals).append(") RETURNING id");
        return jdbc.queryForObject(sql.toString(), Long.class, params.toArray());
    }

    /** Schema-aware Cassandra insert. Walks {@code system_schema.columns} for
     *  the table; non-PK columns may be null (Cassandra's only NOT NULL is on
     *  partition + clustering keys). PK columns missing from overrides throw
     *  with a clear message — Cassandra requires a complete primary key. */
    @SuppressWarnings("unchecked")
    protected void cassandraInsertRow(String table, java.util.Map<String, Object> overrides) {
        if (cassandra == null) return;
        String keyspace = cassandra.getKeyspace().map(ks -> ks.asInternal()).orElse(null);
        if (keyspace == null) {
            throw new AssertionError("cassandraInsertRow: session has no keyspace bound");
        }
        com.datastax.oss.driver.api.core.cql.ResultSet rs = cassandra.execute(
            com.datastax.oss.driver.api.core.cql.SimpleStatement.newInstance(
                "SELECT column_name, kind FROM system_schema.columns "
              + "WHERE keyspace_name = ? AND table_name = ?",
                keyspace, table));
        java.util.LinkedHashMap<String, String> kind = new java.util.LinkedHashMap<>();
        for (com.datastax.oss.driver.api.core.cql.Row r : rs) {
            kind.put(r.getString("column_name"), r.getString("kind"));
        }
        if (kind.isEmpty()) {
            throw new AssertionError("cassandraInsertRow: table '" + table
                + "' not found in keyspace '" + keyspace + "'");
        }
        StringBuilder cols  = new StringBuilder();
        StringBuilder marks = new StringBuilder();
        java.util.List<Object> params = new java.util.ArrayList<>();
        boolean first = true;
        for (java.util.Map.Entry<String, String> e : kind.entrySet()) {
            String name = e.getKey();
            String k    = e.getValue();
            boolean isPk = "partition_key".equals(k) || "clustering".equals(k);
            Object v;
            if (overrides != null && overrides.containsKey(name)) {
                v = overrides.get(name);
                if (v == null) {
                    if (isPk) {
                        throw new AssertionError("cassandraInsertRow: PK column '" + name
                            + "' on " + table + " cannot be null (kind=" + k + ")");
                    }
                    continue;
                }
            } else if (isPk) {
                throw new AssertionError("cassandraInsertRow: PK column '" + name
                    + "' on " + table + " missing from overrides (kind=" + k + ").");
            } else {
                continue;
            }
            if (!first) { cols.append(", "); marks.append(", "); }
            first = false;
            cols.append("\"").append(name).append("\"");
            marks.append("?");
            params.add(v);
        }
        cassandraExec(
            "INSERT INTO \"" + table + "\" (" + cols + ") VALUES (" + marks + ")",
            params.toArray());
    }

    /** Cross-theme baseline admin seed: inserts a known BCrypt-hashed admin row
     *  at id=1 so order seeding has a valid user_id FK target. The hash is for
     *  the plaintext "password" but tests never log in as this user — they go
     *  through ``adminToken()`` which registers a fresh admin via the HTTP API. */
    private static final String _BASELINE_BCRYPT =
        "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    protected void _seedBaselineUser(String role, String email) {
        java.util.Map<String, Object> auth = (java.util.Map<String, Object>) manifest().get("auth");
        if (auth == null) return;
        String userTable = (String) auth.get("userTable");
        if (userTable == null) return;
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        // Best-effort: try common column names; _seedTableRow will use what exists.
        ov.put("email", email);
        ov.put("password", _BASELINE_BCRYPT);
        ov.put("name", "Baseline " + role);
        ov.put("phone", "+201" + nonce().substring(0, 9));
        ov.put("role", role);
        ov.put("status", "ACTIVE");
        _seedTableRow(userTable, ov);
    }

    /** Seed `count` rows into the S2 catalog table with per-row unique names. */
    protected void _seedS2Catalog(int count) {
        String table = tableName(s2CatalogEntity());
        for (int i = 1; i <= count; i++) {
            java.util.Map<String, Object> ov = new java.util.HashMap<>();
            ov.put("name", "Preseed " + s2CatalogEntity() + " " + i);
            ov.put("title", "Preseed " + s2CatalogEntity() + " " + i);
            ov.put("description", "Preseed " + s2CatalogEntity() + " " + i + " description");
            _seedTableRow(table, ov);
        }
    }

    /** Seed S3 orders attached to S2 catalog ids 1, 1, 2, 4 (skips id=3
     *  intentionally — TC52 needs a catalog row with no orders). */
    protected void _seedS3Orders(long userId) {
        String table = tableName(s3OrderEntity());
        String fk;
        try { fk = s2CatalogFkColumn(); }
        catch (AssertionError e) { return; }
        long[] catalogIds = {1L, 1L, 2L, 4L};
        for (long cId : catalogIds) {
            java.util.Map<String, Object> ov = new java.util.HashMap<>();
            ov.put(fk, cId);
            ov.put("user_id", userId);
            ov.put("total_amount", 50.0);
            ov.put("amount", 50.0);
            ov.put("price", 50.0);
            _seedTableRow(table, ov);
        }
    }

    /** Pick the first manifest entity (in declared order) that is NOT
     *  "User" AND has a top-level CRUD collection path (no unfilled
     *  {placeholder}). Used by tests that want a non-User endpoint
     *  but mustn't hardcode a specific entity (so the same test code
     *  maps cleanly across all 8 themes). */
    @SuppressWarnings("unchecked")
    protected String firstTopLevelNonUserEntity() {
        java.util.List<java.util.Map<String, Object>> entities =
            (java.util.List<java.util.Map<String, Object>>) manifest().get("entities");
        if (entities != null) {
            for (java.util.Map<String, Object> e : entities) {
                String cls = (String) e.get("className");
                if (cls == null || "User".equals(cls)) continue;
                try {
                    String collPath = crudCollectionPath(cls);
                    if (collPath != null && !collPath.contains("{")) return cls;
                } catch (IllegalStateException ignored) {
                    // Entity has no resolvable CRUD path — skip.
                }
            }
        }
        throw new IllegalStateException("No top-level non-User CRUD entity in manifest");
    }

    /** Resolve the service URL that hosts the given entity class, based on
     *  the entity's manifest packageName. Used by TC06/TC07 to call a non-User
     *  CRUD endpoint without hardcoding which service hosts it. Falls back to
     *  the current BASE_URL if no mapping matches. */
    @SuppressWarnings("unchecked")
    protected String serviceUrlForEntity(String entityClass) {
        java.util.List<java.util.Map<String, Object>> entities =
            (java.util.List<java.util.Map<String, Object>>) manifest().get("entities");
        if (entities == null) return BASE_URL;
        String pkg = null;
        for (java.util.Map<String, Object> e : entities) {
            if (entityClass.equals(e.get("className"))) {
                pkg = (String) e.get("packageName");
                break;
            }
        }
        if (pkg == null) return BASE_URL;
        // Match by service-name token anywhere in the package path (students
        // often nest entities under ".model" so `endsWith` misses them; we
        // really want "is this entity owned by service X").
        String p = "." + pkg.toLowerCase() + ".";
        if      (p.contains(".user."))        return userServiceUrl;
        else if (p.contains(".product."))     return catalogServiceUrl;
        else if (p.contains(".catalog."))     return catalogServiceUrl;
        else if (p.contains(".order."))       return orderServiceUrl;
        else if (p.contains(".shipment."))    return deliveryServiceUrl;
        else if (p.contains(".shipping."))    return deliveryServiceUrl;
        else if (p.contains(".delivery."))    return deliveryServiceUrl;
        else if (p.contains(".transaction.")) return checkoutServiceUrl;
        else if (p.contains(".billing."))     return checkoutServiceUrl;
        else if (p.contains(".checkout."))    return checkoutServiceUrl;
        return BASE_URL;
    }
    // ─── end manifest-driven helpers ───

    // ─── mongo+es fields (auto-installed) ───
    /**
     * Direct MongoDB access for tests. Soft dep — null if Mongo
     * unreachable. Lazy-initialized in initBase().
     */
    protected static volatile com.mongodb.client.MongoClient mongoClient;
    private static final Object _MONGO_INIT_LOCK = new Object();
    protected static volatile com.mongodb.client.MongoDatabase mongo;
    /** Elasticsearch base URL (no trailing slash). */
    protected String esBaseUrl;
    // ─── end mongo+es fields ───

    /**
     * Direct Neo4j (bolt) access for tests that need to verify the
     * recommendation graph (S3-F11/F12). Lazily initialized in
     * {@link #initBase()}; null if Neo4j is unreachable (Neo4j is a
     * "soft" dependency in the M2 spec). Tests must guard with a null
     * check and fail with a clear "Neo4j required for this test" message.
     */
    protected static volatile org.neo4j.driver.Driver neo4j;
    private static final Object _NEO4J_INIT_LOCK = new Object();

    /**
     * Direct Redis (Jedis) access for tests that need to verify cache
     * state (S3-F10 + S3-F12 confirm cache TTL after endpoint calls).
     * Lazily initialized in {@link #initBase()}; null if Redis is
     * unreachable. Bound to the same DB index as the student's app
     * (default 0).
     */
    protected static volatile redis.clients.jedis.Jedis redis;
    private static final Object _REDIS_INIT_LOCK = new Object();

    /**
     * Direct Cassandra (CqlSession) access for tests that verify the
     * time-series tracking-events table (S4-F11 writes, S4-F12 reads).
     * Lazily initialized in {@link #initBase()}; null if Cassandra is
     * unreachable. Bound to the keyspace declared by the student's app
     * (Amazon: ``amazonks``).
     */
    // STATIC: built once per JVM (not per test class). The prior per-class
    // build leaked sessions (driver `advanced.session-leak.threshold` warning
    // fired at 200+ active) AND wasted ~5s per class on cluster handshake. A
    // JVM shutdown hook installed on first build closes the singleton, so we
    // no longer depend on every @AfterAll firing cleanly.
    protected static volatile com.datastax.oss.driver.api.core.CqlSession cassandra;
    private static final Object _CASSANDRA_INIT_LOCK = new Object();

    @BeforeAll
    protected void initBase() {
        // Per-service URL fields — populated from env vars the grader sets
        // (one per service slot). Defaults match Amazon's M2 §6.4 port plan
        // (8081–8085) when running tests against a localhost stack.
        userServiceUrl     = envOr("USER_SERVICE_URL",     "http://localhost:8081");
        catalogServiceUrl  = envOr("CATALOG_SERVICE_URL",  envOr("PRODUCT_SERVICE_URL", "http://localhost:8082"));
        orderServiceUrl    = envOr("ORDER_SERVICE_URL",    "http://localhost:8083");
        deliveryServiceUrl = envOr("DELIVERY_SERVICE_URL", envOr("SHIPPING_SERVICE_URL", "http://localhost:8084"));
        checkoutServiceUrl = envOr("CHECKOUT_SERVICE_URL", envOr("BILLING_SERVICE_URL",  "http://localhost:8085"));
        // Default BASE_URL = user-service. Per-test methods may override
        // (none currently do; routeBaseUrl @BeforeEach fills the slot from
        // the test class's TC range so ConnectExceptions stop happening).
        BASE_URL = envOr("APP_BASE_URL", userServiceUrl);
        String dbUrl = envOr("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/amazondb");
        String dbUser = envOr("SPRING_DATASOURCE_USERNAME", "postgres");
        String dbPass = envOr("SPRING_DATASOURCE_PASSWORD", "postgres");

        // HttpClient is heavyweight (own thread pool). JVM-singleton
        // so we don't spawn one per test class.
        if (http == null) {
            synchronized (_HTTP_INIT_LOCK) {
                if (http == null) {
                    http = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .build();
                }
            }
        }
        // JdbcTemplate is thread-safe; wraps a DriverManagerDataSource.
        // JVM-singleton — one DataSource for the whole test run.
        if (jdbc == null) {
            synchronized (_JDBC_INIT_LOCK) {
                if (jdbc == null) {
                    jdbc = new JdbcTemplate(new DriverManagerDataSource(dbUrl, dbUser, dbPass));
                }
            }
        }

        // ─── mongo+es init (auto-installed) ───
        // ── MongoDB (soft, JVM-singleton) ────────────────────────────
        if (mongoClient == null) {
            synchronized (_MONGO_INIT_LOCK) {
                if (mongoClient == null) {
                    String mongoUri = envOr("SPRING_DATA_MONGODB_URI", "mongodb://localhost:27017");
                    try {
                        com.mongodb.ConnectionString cs = new com.mongodb.ConnectionString(mongoUri);
                        mongoClient = com.mongodb.client.MongoClients.create(cs);
                        String dbName = cs.getDatabase();
                        if (dbName == null || dbName.isBlank()) dbName = envOr("SPRING_DATA_MONGODB_DATABASE", "appdb");
                        mongo = mongoClient.getDatabase(dbName);
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            try { mongoClient.close(); } catch (Exception ignored) {}
                        }, "m2-tests-mongo-close"));
                    } catch (Exception ignored) {
                        mongoClient = null;
                        mongo = null;
                    }
                }
            }
        }

        // ── Elasticsearch (soft) ────────────────────────────────────
        String esUris = envOr("SPRING_ELASTICSEARCH_URIS", "http://localhost:9200");
        String firstUri = esUris.split(",")[0].trim();
        if (firstUri.endsWith("/")) firstUri = firstUri.substring(0, firstUri.length() - 1);
        this.esBaseUrl = firstUri;
        // ─── end mongo+es init ───

        // ── Neo4j (soft, JVM-singleton) ──────────────────────────────
        // Neo4j Driver is explicitly designed as one-per-application.
        // Singleton + shutdown hook for the close.
        if (neo4j == null) {
            synchronized (_NEO4J_INIT_LOCK) {
                if (neo4j == null) {
                    String neoUri  = envOr("SPRING_NEO4J_URI",                     "bolt://localhost:7687");
                    String neoUser = envOr("SPRING_NEO4J_AUTHENTICATION_USERNAME", "neo4j");
                    String neoPass = envOr("SPRING_NEO4J_AUTHENTICATION_PASSWORD", "neo4j");
                    try {
                        neo4j = org.neo4j.driver.GraphDatabase.driver(
                            neoUri,
                            org.neo4j.driver.AuthTokens.basic(neoUser, neoPass));
                        neo4j.verifyConnectivity();  // fail-fast on bad creds
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            try { neo4j.close(); } catch (Exception ignored) {}
                        }, "m2-tests-neo4j-close"));
                    } catch (Exception ignored) {
                        neo4j = null;
                    }
                }
            }
        }

        // ── Redis (soft, JVM-singleton) ──────────────────────────────
        if (redis == null) {
            synchronized (_REDIS_INIT_LOCK) {
                if (redis == null) {
                    String redisHost = envOr("SPRING_DATA_REDIS_HOST", "localhost");
                    int    redisPort = Integer.parseInt(envOr("SPRING_DATA_REDIS_PORT", "6379"));
                    String redisPass = envOr("SPRING_DATA_REDIS_PASSWORD", "");
                    try {
                        redis = new redis.clients.jedis.Jedis(redisHost, redisPort);
                        if (!redisPass.isBlank()) redis.auth(redisPass);
                        redis.ping();
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            try { redis.close(); } catch (Exception ignored) {}
                        }, "m2-tests-redis-close"));
                    } catch (Exception ignored) {
                        redis = null;
                    }
                }
            }
        }

        // ── Cassandra (soft, JVM-singleton) ──────────────────────────
        if (cassandra == null) {
            synchronized (_CASSANDRA_INIT_LOCK) {
                if (cassandra == null) {
                    String casHost = envOr("SPRING_CASSANDRA_CONTACT_POINTS", "localhost:9042");
                    String casDc   = envOr("SPRING_CASSANDRA_LOCAL_DATACENTER", "datacenter1");
                    String casKs   = envOr("SPRING_CASSANDRA_KEYSPACE_NAME", "amazonks");
                    try {
                        String[] hp = casHost.split(":");
                        int port = hp.length > 1 ? Integer.parseInt(hp[1]) : 9042;
                        cassandra = com.datastax.oss.driver.api.core.CqlSession.builder()
                                .addContactPoint(new java.net.InetSocketAddress(hp[0], port))
                                .withLocalDatacenter(casDc)
                                .withKeyspace(casKs)
                                .build();
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            try { cassandra.close(); } catch (Exception ignored) {}
                        }, "m2-tests-cassandra-close"));
                    } catch (Exception ignored) {
                        cassandra = null;
                    }
                }
            }
        }
    }

    // ─── es+mongo helpers (auto-installed) ───
    protected HttpResponse<String> esGet(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(esBaseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> esPost(String path, String jsonBody) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(esBaseUrl + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected boolean esDocExists(String index, Object id) throws Exception {
        HttpResponse<String> r = esGet("/" + index + "/_doc/" + id);
        if (r.statusCode() / 100 == 2) return true;
        if (r.statusCode() == 404) return false;
        throw new AssertionError("esDocExists(" + index + "/" + id + "): unexpected " + r.statusCode());
    }

    protected long esSearchCount(String index, String fieldName, String fieldValue) throws Exception {
        // Try three query shapes in order — the spec doesn't pin a specific
        // mapping for text fields, so different student mappings (Text-only,
        // Text+keyword multi-field, Keyword-only) need to all be matched:
        //   1. term on `name.keyword` — works if student has @MultiField w/ keyword subfield
        //   2. term on `name`         — works if the field is Keyword-typed
        //   3. match on `name`        — works if the field is Text-typed (tokenized)
        String[] bodies = new String[] {
            String.format("{\"query\":{\"term\":{\"%s.keyword\":{\"value\":\"%s\"}}}}", fieldName, fieldValue),
            String.format("{\"query\":{\"term\":{\"%s\":{\"value\":\"%s\"}}}}", fieldName, fieldValue),
            String.format("{\"query\":{\"match\":{\"%s\":\"%s\"}}}", fieldName, fieldValue),
        };
        long best = -1L;
        for (String body : bodies) {
            HttpResponse<String> r = esPost("/" + index + "/_search", body);
            if (r.statusCode() / 100 != 2) continue;
            try {
                JsonNode j = parseNode(r.body());
                if (j.has("hits") && j.get("hits").has("total")) {
                    JsonNode total = j.get("hits").get("total");
                    long count = total.isObject() && total.has("value") ? total.get("value").asLong()
                            : total.isNumber() ? total.asLong() : -1L;
                    if (count > 0) return count;
                    if (count == 0 && best < 0) best = 0;
                }
            } catch (Exception ignored) { }
        }
        return best;
    }
    // ─── end es+mongo helpers ───

    // ────────────────────────────────────────────────────────────────
    // BSON tree-walking helpers — used by Mongo-event tests that must
    // tolerate per-student variation in field names AND nesting depth.
    // The spec mandates WHAT (e.g., "an INTERACTION_RECORDED event with
    // orderId in details") but not WHICH field names students use to
    // store it. These walkers search the entire decoded BSON tree
    // (Document / Map / List / scalar) for a target value, so an
    // assertion like "this orderId appears somewhere in the doc"
    // matches whether the student stored it as `orderId`, `order_id`,
    // `orderID`, top-level or nested under `details` / `data` /
    // `payload` / etc.
    // ────────────────────────────────────────────────────────────────

    /** True if a target string literal appears as ANY leaf value in the
     *  decoded BSON tree under {@code node}. Walks Document / Map /
     *  Iterable / scalar uniformly. Use to match action-type literals
     *  like "INTERACTION_RECORDED" / "TRACKING_RECORDED" without
     *  pinning the field name. */
    protected boolean bsonContainsString(Object node, String target) {
        if (node == null) return false;
        if (node instanceof String s) return target.equals(s);
        if (node instanceof org.bson.Document d) {
            for (String k : d.keySet()) {
                if (bsonContainsString(d.get(k), target)) return true;
            }
            return false;
        }
        if (node instanceof java.util.Map<?, ?> m) {
            for (Object v : m.values()) {
                if (bsonContainsString(v, target)) return true;
            }
            return false;
        }
        if (node instanceof Iterable<?> it) {
            for (Object v : it) {
                if (bsonContainsString(v, target)) return true;
            }
            return false;
        }
        return false;
    }

    /** True if a target numeric appears as ANY leaf value in the decoded
     *  BSON tree. Tolerates Long / Integer / Double / stringified-id
     *  storage. Use to match identifiers (orderId / shipmentId /
     *  productId) without pinning the field name. */
    protected boolean bsonContainsLong(Object node, long target) {
        if (node == null) return false;
        if (node instanceof Number n) return n.longValue() == target;
        if (node instanceof String s) {
            try { return Long.parseLong(s) == target; } catch (NumberFormatException e) { return false; }
        }
        if (node instanceof org.bson.Document d) {
            for (String k : d.keySet()) {
                if (bsonContainsLong(d.get(k), target)) return true;
            }
            return false;
        }
        if (node instanceof java.util.Map<?, ?> m) {
            for (Object v : m.values()) {
                if (bsonContainsLong(v, target)) return true;
            }
            return false;
        }
        if (node instanceof Iterable<?> it) {
            for (Object v : it) {
                if (bsonContainsLong(v, target)) return true;
            }
            return false;
        }
        return false;
    }

    // ────────────────────────────────────────────────────────────────
    // HTTP helpers
    // ────────────────────────────────────────────────────────────────

    protected HttpResponse<String> httpGet(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> httpGetAuth(String path, String token) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Authorization", "Bearer " + token)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> httpPost(String path, String jsonBody) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> httpPostAuth(String path, String jsonBody, String token) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> httpPut(String path, String jsonBody) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> httpPutAuth(String path, String jsonBody, String token) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token)
                        .PUT(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> httpPatch(String path, String jsonBody) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Content-Type", "application/json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> httpPatchAuth(String path, String jsonBody, String token) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token)
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> httpDelete(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path)).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> httpDeleteAuth(String path, String token) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Authorization", "Bearer " + token)
                        .DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * GET with an arbitrary Authorization header value — useful for malformed-
     * scheme / empty-Bearer negative tests that need to send something other
     * than "Bearer <valid-jwt>".
     */
    protected HttpResponse<String> httpGetWithRawAuth(String path, String authHeaderValue) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Authorization", authHeaderValue)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** POST + custom Authorization header. */
    protected HttpResponse<String> httpPostWithRawAuth(String path, String jsonBody, String authHeaderValue) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Content-Type", "application/json")
                        .header("Authorization", authHeaderValue)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    /** PUT + custom Authorization header. */
    protected HttpResponse<String> httpPutWithRawAuth(String path, String jsonBody, String authHeaderValue) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Content-Type", "application/json")
                        .header("Authorization", authHeaderValue)
                        .PUT(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** PATCH + custom Authorization header. */
    protected HttpResponse<String> httpPatchWithRawAuth(String path, String jsonBody, String authHeaderValue) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Content-Type", "application/json")
                        .header("Authorization", authHeaderValue)
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** DELETE + custom Authorization header. */
    protected HttpResponse<String> httpDeleteWithRawAuth(String path, String authHeaderValue) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Authorization", authHeaderValue)
                        .DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    // ────────────────────────────────────────────────────────────────
    // JSON helpers
    // ────────────────────────────────────────────────────────────────

    protected Map<String, Object> parseMap(String json) throws Exception {
        return OM.readValue(json, new TypeReference<>() {});
    }

    protected List<Map<String, Object>> parseList(String json) throws Exception {
        return OM.readValue(json, new TypeReference<>() {});
    }

    protected JsonNode parseNode(String json) throws Exception {
        return OM.readTree(json);
    }

    protected String toJson(Object o) throws Exception {
        return OM.writeValueAsString(o);
    }

    /** Decode the unsigned payload of a JWT (header.payload.signature) and
     *  return it as a JsonNode. Spec-compliant per Talabat M2 §5.2 — the
     *  token must carry `sub` (email), `uid` (numeric User.id), `role`,
     *  `iat`, `exp`. We do NOT verify the signature; this is for reading
     *  the user's id post-register/login when the spec response body is
     *  {token, expiresIn} (no `id` field). */
    protected JsonNode decodeJwtPayload(String token) throws Exception {
        if (token == null) throw new AssertionError("decodeJwtPayload: token is null");
        String[] parts = token.split("\\.", -1);
        if (parts.length < 2) {
            throw new AssertionError("decodeJwtPayload: not a JWT (no '.' separator): " + token);
        }
        byte[] payloadBytes = java.util.Base64.getUrlDecoder().decode(parts[1]);
        return OM.readTree(payloadBytes);
    }

    /** Convenience: extract the spec-mandated `uid` claim from a JWT.
     *  Falls back to `sub` if `uid` missing AND sub looks numeric (defensive
     *  cross-theme tolerance). Throws AssertionError if neither yields a Long. */
    protected long uidFromJwt(String token) throws Exception {
        JsonNode payload = decodeJwtPayload(token);
        if (payload.has("uid") && !payload.get("uid").isNull()) {
            return payload.get("uid").asLong();
        }
        if (payload.has("sub")) {
            String sub = payload.get("sub").asText();
            try { return Long.parseLong(sub); } catch (NumberFormatException ignored) { }
        }
        throw new AssertionError(
            "JWT has no `uid` claim and `sub` is not numeric. Spec (M2 §5.2) "
          + "mandates `uid` containing User.id. Payload: " + payload);
    }

    // ────────────────────────────────────────────────────────────────
    // PostgreSQL enum resolution helpers
    //
    // Enums on the student's entities are stored as native PG ENUM types
    // (per feedback_enum_storage: @JdbcTypeCode(NAMED_ENUM)). The type
    // name varies per theme and sometimes per student, so tests must
    // discover it at runtime.
    // ────────────────────────────────────────────────────────────────

    /** Find the PG enum TYPE name whose labels include every given value. */
    protected String findEnumType(String... labels) {
        try {
            StringBuilder sql = new StringBuilder(
                    "SELECT t.typname FROM pg_type t JOIN pg_namespace n ON t.typnamespace = n.oid "
                  + "WHERE n.nspname = 'public' AND t.typtype = 'e'");
            for (String label : labels) {
                sql.append(" AND EXISTS (SELECT 1 FROM pg_enum e WHERE e.enumtypid = t.oid AND e.enumlabel = '")
                   .append(label).append("')");
            }
            sql.append(" LIMIT 1");
            return jdbc.queryForObject(sql.toString(), String.class);
        } catch (DataAccessException e) {
            return null;
        }
    }

    /** Like {@link #findEnumType} but returns {@code VARCHAR(50)} as a VARCHAR fallback. */
    protected String resolveColType(String... labels) {
        String t = findEnumType(labels);
        return t != null ? t : "VARCHAR(50)";
    }

    /** Return a JDBC placeholder with the correct type cast for the column. */
    protected String ec(String table, String col) {
        try {
            String udt = jdbc.queryForObject(
                    "SELECT udt_name FROM information_schema.columns "
                  + "WHERE table_name = ? AND column_name = ?",
                    String.class, table, col);
            if (udt != null && !udt.equals("varchar") && !udt.equals("text") && !udt.startsWith("int")) {
                return "?::" + udt;
            }
        } catch (DataAccessException ignored) { }
        return "?";
    }

    /** Render a literal enum value correctly cast for the column. */
    protected String el(String table, String col, String value) {
        try {
            String udt = jdbc.queryForObject(
                    "SELECT udt_name FROM information_schema.columns "
                  + "WHERE table_name = ? AND column_name = ?",
                    String.class, table, col);
            if (udt != null && !udt.equals("varchar") && !udt.equals("text") && !udt.startsWith("int")) {
                return "'" + value + "'::" + udt;
            }
        } catch (DataAccessException ignored) { }
        return "'" + value + "'";
    }

    // ────────────────────────────────────────────────────────────────
    // Misc
    // ────────────────────────────────────────────────────────────────

    protected static String envOr(String name, String fallback) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    /** Unique-ish suffix based on nanoTime, good enough to avoid cross-test collisions. */
    protected static String nonce() {
        return String.format("%09d", System.nanoTime() % 1_000_000_000L);
    }

    protected void assert2xx(HttpResponse<String> r, String ctx) {
        assertTrue(r.statusCode() >= 200 && r.statusCode() < 300,
                ctx + ": expected 2xx but got " + r.statusCode() + " body=" + r.body());
    }

    // ────────────────────────────────────────────────────────────────
    // Auth helpers — cache an admin token for the lifetime of the test
    // class (PER_CLASS lifecycle) so every @Test in the same class pays
    // the register+login cost at most once.
    // ────────────────────────────────────────────────────────────────

    private String _cachedAdminToken;
    private String _cachedAdminEmail;
    private Long   _cachedAdminId;

    /** Lazily seed+login an admin, memoise per test-class. Always hits
     *  user-service regardless of which service the test's primary path
     *  goes to — auth lives in S1. */
    protected String adminToken() throws Exception {
        if (_cachedAdminToken == null) {
            _cachedAdminEmail = "admin_" + nonce() + "@testgen.io";
            _cachedAdminId = TestAuthHelper.seedAdmin(http, jdbc, userServiceUrl, _cachedAdminEmail);
            _cachedAdminToken = TestAuthHelper.loginAsAdmin(http, userServiceUrl, _cachedAdminEmail);
        }
        return _cachedAdminToken;
    }

    /** Id of the cached admin; triggers {@link #adminToken} on first call. */
    protected long adminId() throws Exception {
        adminToken();
        return _cachedAdminId;
    }

    /**
     * Register a fresh non-admin user, log them in, return {token, id, email}.
     * Each call creates a new user — use this when a test needs two distinct
     * identities (e.g. ownership / IDOR tests).
     */
    protected Map<String, Object> seedAndLoginUser(String emailPrefix) throws Exception {
        String email = emailPrefix + "_" + nonce() + "@testgen.io";
        String pwd = "UserPwd!2026";
        String phone = "+2010" + nonce().substring(0, 9);
        String regBody = String.format("""
                {"name":"%s","email":"%s","password":"%s","phone":"%s"}
                """, emailPrefix, email, pwd, phone);
        // Auth endpoints live on user-service regardless of which service
        // the calling test's BASE_URL points to.
        String prevBase = BASE_URL;
        BASE_URL = userServiceUrl;
        HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
        assert2xx(reg, "seedAndLoginUser register");
        String loginBody = String.format("""
                {"email":"%s","password":"%s"}
                """, email, pwd);
        HttpResponse<String> login = httpPost("/api/auth/login", loginBody);
        BASE_URL = prevBase;
        assert2xx(login, "seedAndLoginUser login");
        String token = parseNode(login.body()).get("token").asText();
        Map<String, Object> claims = decodeJwtClaims(token);
        long uid = ((Number) claims.get("uid")).longValue();
        return Map.of("token", token, "id", uid, "email", email);
    }

    /**
     * Decode the payload segment of a JWT without verifying the signature.
     * Purpose is test-side inspection only (uid/role claims for assertions
     * or tampered-signature golden construction).
     */
    protected Map<String, Object> decodeJwtClaims(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("not a 3-segment JWT: " + token);
        }
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        return OM.readValue(payload, new TypeReference<>() {});
    }

    /**
     * Produce a JWT whose signature segment is garbage — same header + payload,
     * tampered signature. The server must reject with 401 without distinguishing
     * "bad sig" from "malformed".
     */
    protected String tamperSignature(String validToken) {
        String[] parts = validToken.split("\\.");
        if (parts.length != 3) {
            return validToken + "tampered";
        }
        String bogus = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("tampered-signature-does-not-verify".getBytes());
        return parts[0] + "." + parts[1] + "." + bogus;
    }

    // ─── auto-truncate hooks (per-test wipe) ───
    //
    // Why: tests that hit /api/<entity>/<id> assume data exists at
    // that id, but the grader DB starts empty. Tests that seed via
    // JDBC leave rows behind, so the NEXT test sees stale state.
    // The fix: wipe row data BEFORE and AFTER every @Test method.
    //
    // Schema discovery (student-friendly):
    //   * We ask the student's own DB which tables exist
    //     (information_schema.tables, public schema). The M2 spec
    //     defines the HTTP contract and entity model, but doesn't
    //     mandate @Table(name=...) values — students may legitimately
    //     name their tables differently. Hardcoding "users" would
    //     skip a student who used "user_account", letting rows
    //     accumulate. Discovery wipes whatever they actually built.
    //   * Migration-tracker tables (Flyway/Liquibase) are skipped so
    //     we don't trigger re-migration on app restart.
    //
    // Safety:
    //   * TRUNCATE ... RESTART IDENTITY CASCADE deletes rows + resets
    //     the id sequence; it does NOT touch the schema, indexes, FKs,
    //     OR PG-native enum types — so enum-typed columns stay intact.
    //   * Per-table try/catch: a single failing TRUNCATE (locked, FK
    //     weirdness) doesn't break the rest of the wipe.
    //   * CASCADE wipes student-added child tables that FK back to a
    //     spec entity, even if we never list them.
    //   * Cached admin token is invalidated so the next adminToken()
    //     call lazily re-seeds the admin row.
    private static final java.util.Set<String> _AUTO_TRUNCATE_SKIP = java.util.Set.of(
        "flyway_schema_history",
        "databasechangelog",
        "databasechangeloglock",
        "schema_version"
    );

    @org.junit.jupiter.api.BeforeEach
    protected void truncateBeforeEach(org.junit.jupiter.api.TestInfo info) {
        routeBaseUrl(info);
        autoTruncateAllData();
        _cachedAdminToken = null;
        _cachedAdminId = null;
        // Baseline seed is opt-in: only test classes/methods tagged
        // @Tag("with-baseline") get the heavy 50+-row preseed. Most tests seed
        // exactly what they need inline, so paying ~1.5s for the baseline on
        // every test was pure overhead.
        if (info.getTags().contains("with-baseline")) {
            autoSeedBaselineData();
        }
    }

    /**
     * Auto-route BASE_URL by test-class TC-number range. The hand-written
     * Amazon M2 test classes don't set BASE_URL per method (unlike Talabat),
     * so the slot is inferred from the class name:
     *   TC01–TC34   → user-service     (S1: auth, profile, addresses, activity feed)
     *   TC35–TC53   → product-service  (S2: search, index, dashboard)
     *   TC54–TC99   → order-service    (S3: order analytics, co-purchase, recommendations)
     *   TC100–TC135 → shipping-service (S4: shipment analytics, tracking, timeline)
     *   TC136–TC190 → billing-service  (S5: revenue breakdown, refund, audit)
     *   S1F* / CC*  → user-service     (cross-cutting auth tests)
     *   S2F* / S3F* / S4F* / S5F* → respective service
     * Falls back to userServiceUrl for anything unrecognized.
     */
    private void routeBaseUrl(org.junit.jupiter.api.TestInfo info) {
        String cls = info.getTestClass().map(Class::getSimpleName).orElse("");
        // S{X}F* prefix shortcut
        if      (cls.startsWith("S2F")) { BASE_URL = catalogServiceUrl;  return; }
        else if (cls.startsWith("S3F")) { BASE_URL = orderServiceUrl;    return; }
        else if (cls.startsWith("S4F")) { BASE_URL = deliveryServiceUrl; return; }
        else if (cls.startsWith("S5F")) { BASE_URL = checkoutServiceUrl; return; }
        else if (cls.startsWith("S1F") || cls.startsWith("CC")) {
            BASE_URL = userServiceUrl;
            return;
        }
        // TC{N} prefix: extract N, range-route
        int tc = -1;
        if (cls.startsWith("TC")) {
            int j = 2;
            while (j < cls.length() && Character.isDigit(cls.charAt(j))) j++;
            try { tc = Integer.parseInt(cls.substring(2, j)); } catch (Exception ignored) {}
        }
        if      (tc >= 1   && tc <= 34)  BASE_URL = userServiceUrl;
        else if (tc >= 35  && tc <= 53)  BASE_URL = catalogServiceUrl;
        else if (tc >= 54  && tc <= 99)  BASE_URL = orderServiceUrl;
        else if (tc >= 100 && tc <= 135) BASE_URL = deliveryServiceUrl;
        else if (tc >= 136 && tc <= 190) BASE_URL = checkoutServiceUrl;
        else                             BASE_URL = userServiceUrl;
    }

    @org.junit.jupiter.api.AfterEach
    protected void truncateAfterEach() {
        autoTruncateAllData();
        _cachedAdminToken = null;
        _cachedAdminId = null;
    }

    private void autoTruncateAllData() {
        if (jdbc == null) return;     // initBase() hasn't run yet
        java.util.List<String> tables;
        try {
            tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
              + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String.class);
        } catch (DataAccessException e) {
            return;     // DB not ready — let the test's own seed surface the issue
        }
        // Single compound TRUNCATE — PG handles the comma-separated list
        // atomically and ~10× faster than 30 individual round-trips. CASCADE
        // covers any student-added child tables that FK back to spec entities.
        java.util.List<String> wipe = new java.util.ArrayList<>();
        for (String t : tables) {
            if (!_AUTO_TRUNCATE_SKIP.contains(t)) wipe.add("\"" + t + "\"");
        }
        if (!wipe.isEmpty()) {
            try {
                jdbc.execute("TRUNCATE TABLE " + String.join(", ", wipe) + " RESTART IDENTITY CASCADE");
            } catch (DataAccessException compound) {
                for (String t : tables) {
                    if (_AUTO_TRUNCATE_SKIP.contains(t)) continue;
                    try {
                        jdbc.execute("TRUNCATE TABLE \"" + t + "\" RESTART IDENTITY CASCADE");
                    } catch (DataAccessException ignored) { }
                }
            }
        }
        // Also wipe auxiliary stores so each test starts from a clean slate.
        // Soft-fail: if the driver is null (store unreachable) the helpers no-op.
        neo4jClear();
        redisFlushDb();
        mongoClearAllCollections();
        // Truncate every Cassandra table declared in the manifest.
        if (cassandra != null) {
            try {
                @SuppressWarnings("unchecked")
                java.util.List<java.util.Map<String, Object>> casTables =
                    (java.util.List<java.util.Map<String, Object>>) manifest().get("cassandraTables");
                if (casTables != null) {
                    for (java.util.Map<String, Object> t : casTables) {
                        String name = (String) t.get("tableName");
                        if (name != null) cassandraClear(name);
                    }
                }
            } catch (Exception ignored) { }
        }
    }
    // ─── auto-seed baseline (Amazon-bespoke) ───
    //
    // Theme-customised baseline seed for Amazon. Mirrors Talabat's structure
    // (rich seed: 5 users + 5 catalog + 12 orders + supporting tables + offers
    // junction) but uses Amazon-specific tables (products / order_items /
    // shipments / transactions / vouchers / transaction_vouchers) and Amazon-
    // specific enum values per the M1 spec (cross-checked against
    // 04-Projects/Project Tex Files/M1/Amazon Replica M1.tex):
    //   * User.role            : CUSTOMER, ADMIN
    //   * User.status          : ACTIVE, DEACTIVATED
    //   * Product.status       : ACTIVE, INACTIVE, OUT_OF_STOCK
    //   * Order.status         : PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED, RETURNED
    //   * Shipment.status      : PROCESSING, SHIPPED, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, RETURNED
    //   * Transaction.method   : CREDIT_CARD, CASH_ON_DELIVERY, WALLET
    //   * Transaction.status   : PENDING, COMPLETED, FAILED, REFUNDED
    //   * Voucher.discountType : PERCENTAGE, FIXED
    //
    // Dynamic via manifest: every INSERT/UPDATE references its table through
    // ``tableName(<EntityClass>)`` which maps via manifest.json — at grading
    // time the scanner regenerates the manifest from the student's project,
    // so a student who renamed their @Table(name=…) on Product to "items"
    // still gets seeded correctly. ``el(table, col, value)`` resolves PG
    // native ENUM types at runtime.
    //
    // FK integrity is preserved because every row inserts with explicit FK
    // values pointing to ids known to exist (users 1-5; products 1-4; orders
    // 1-8). Optional columns (review rating, jsonb details/metadata, expiry
    // dates, courier names) live in separate UPDATE statements via _tryUpdate
    // so an absent column doesn't block the rest of the seed.
    //
    // Distribution covers TC48-53 (S2-F12 dashboard):
    //   * Product 1: 4 order_items across 4 distinct orders → totalOrders=4
    //   * Product 2: 2 order_items in 2 orders                → totalOrders=2
    //   * Product 3: 0 order_items                            → totalOrders=0 (TC52)
    //   * Product 4: 2 order_items in 2 orders                → totalOrders=2
    private static final String _PRESEED_BCRYPT =
        "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private void _tryUpdate(String sql, Object... params) {
        try { jdbc.update(sql, params); } catch (DataAccessException ignored) { }
    }

    private void _amzUser(String role, String email, String name, String phone) {
        String t = tableName("User");
        _tryUpdate(
            "INSERT INTO \"" + t + "\" (name, email, phone, password, role, status) "
          + "VALUES (?, ?, ?, ?, "
          + el(t, "role", role) + ", "
          + el(t, "status", "ACTIVE") + ")",
            name, email, phone, _PRESEED_BCRYPT);
    }

    private void _amzProduct(String name, String desc, double price, String category,
                             String brand, int stock, String status) {
        String t = tableName("Product");
        _tryUpdate(
            "INSERT INTO \"" + t + "\" (name, description, price, category, brand, stock_quantity, status) "
          + "VALUES (?, ?, ?, ?, ?, ?, "
          + el(t, "status", status) + ")",
            name, desc, price, category, brand, stock);
    }

    private void _amzOrder(long userId, double totalAmount, String status) {
        String t = tableName("Order");
        _tryUpdate(
            "INSERT INTO \"" + t + "\" (user_id, total_amount, status) "
          + "VALUES (?, ?, "
          + el(t, "status", status) + ")",
            userId, totalAmount);
    }

    private void _amzOrderItem(long orderId, long productId, int quantity, double price) {
        String t = tableName("OrderItem");
        _tryUpdate(
            "INSERT INTO \"" + t + "\" (order_id, product_id, quantity, price) "
          + "VALUES (?, ?, ?, ?)",
            orderId, productId, quantity, price);
    }

    private void _amzShipment(long orderId, String carrier, String tracking, String status) {
        String t = tableName("Shipment");
        _tryUpdate(
            "INSERT INTO \"" + t + "\" (order_id, carrier, tracking_number, status) "
          + "VALUES (?, ?, ?, "
          + el(t, "status", status) + ")",
            orderId, carrier, tracking);
    }

    private void _amzTransaction(long orderId, long userId, double amount,
                                 String method, String status) {
        String t = tableName("Transaction");
        _tryUpdate(
            "INSERT INTO \"" + t + "\" (order_id, user_id, amount, method, status) "
          + "VALUES (?, ?, ?, "
          + el(t, "method", method) + ", "
          + el(t, "status", status) + ")",
            orderId, userId, amount);
    }

    private void _amzVoucher(String code, String type, double value, int maxUses) {
        String t = tableName("Voucher");
        _tryUpdate(
            "INSERT INTO \"" + t + "\" (code, discount_type, discount_value, max_uses, expiry_date) "
          + "VALUES (?, "
          + el(t, "discount_type", type) + ", ?, ?, ?)",
            code, value, maxUses, java.sql.Date.valueOf("2030-12-31"));
    }

    private void _amzShippingAddress(long userId, String line, String city, boolean isDefault) {
        String t = tableName("ShippingAddress");
        _tryUpdate(
            "INSERT INTO \"" + t + "\" (user_id, address_line, city, is_default) "
          + "VALUES (?, ?, ?, ?)",
            userId, line, city, isDefault);
    }

    private void _amzReview(long userId, long productId, int rating, String content) {
        String t = tableName("ProductReview");
        _tryUpdate(
            "INSERT INTO \"" + t + "\" (user_id, product_id, rating, content) "
          + "VALUES (?, ?, ?, ?)",
            userId, productId, rating, content);
    }

    /** _t — short alias for tableName(entityClass) used in optional UPDATE/INSERT
     *  statements below where keeping the literal table name keeps SQL terse. */
    private String _t(String entityClass) {
        try { return tableName(entityClass); } catch (Exception e) { return null; }
    }

    private void autoSeedBaselineData() {
        if (jdbc == null) return;

        String tUser     = _t("User");
        String tProduct  = _t("Product");
        String tOrder    = _t("Order");
        String tShipment = _t("Shipment");
        String tTrans    = _t("Transaction");
        String tTxVouch  = _t("TransactionVoucher");

        // ── Users (5) — id=1 admin; id=2..5 customers ─────────────────
        // user 1 (admin) gets the rich profile so /users/1/profile finds the
        // shape S1-F8 expects (user + preferences + multiple addresses).
        _amzUser("ADMIN",    "_preseed_admin@grader.testgen.io",  "Preseed Admin",   "+201000000001");
        _amzUser("CUSTOMER", "_preseed_user@grader.testgen.io",   "Preseed User",    "+201000000002");
        _amzUser("CUSTOMER", "_preseed_buyer@grader.testgen.io",  "Preseed Buyer",   "+201000000003");
        _amzUser("CUSTOMER", "_preseed_active@grader.testgen.io", "Active Buyer",    "+201000000004");
        _amzUser("CUSTOMER", "_preseed_extra@grader.testgen.io",  "Extra Buyer",     "+201000000005");
        // Optional preferences jsonb — covers /metadata/search?key=note&value=true
        if (tUser != null) {
            _tryUpdate("UPDATE \"" + tUser + "\" SET preferences = ?::jsonb WHERE id = ?",
                "{\"category\":\"electronics\",\"language\":\"en\",\"note\":\"true\"}", 1L);
            _tryUpdate("UPDATE \"" + tUser + "\" SET preferences = ?::jsonb WHERE id = ?",
                "{\"category\":\"books\",\"note\":\"true\"}", 2L);
            _tryUpdate("UPDATE \"" + tUser + "\" SET preferences = ?::jsonb WHERE id = ?",
                "{\"category\":\"electronics\",\"note\":\"true\"}", 4L);
            _tryUpdate("UPDATE \"" + tUser + "\" SET preferences = ?::jsonb WHERE id = ?",
                "{\"category\":\"kitchen\",\"note\":\"true\"}", 5L);
        }

        // ── Shipping addresses (8) ──────────────────────────────────────
        _amzShippingAddress(1L, "1 Tahrir Sq",        "Cairo",       true);   // 1 — user 1 default
        _amzShippingAddress(1L, "10 Maadi St",        "Cairo",       false);  // 2 — user 1
        _amzShippingAddress(1L, "20 Heliopolis Ave",  "Cairo",       false);  // 3 — user 1
        _amzShippingAddress(2L, "5 Zamalek St",       "Cairo",       true);   // 4 — user 2 default
        _amzShippingAddress(2L, "55 Garden City",     "Cairo",       false);  // 5 — user 2
        _amzShippingAddress(3L, "12 Nasr St",         "Cairo",       true);   // 6 — user 3 default
        _amzShippingAddress(4L, "30 Dokki St",        "Giza",        true);   // 7 — user 4 default
        _amzShippingAddress(5L, "8 Sheraton",         "Heliopolis",  true);   // 8 — user 5 default

        // ── Products (5) — id=1 active electronics; id=2 active books; ──
        //   id=3 INACTIVE (no orders — TC52); id=4 active kitchen; id=5 OUT_OF_STOCK
        _amzProduct("Preseed Wireless Headphones", "Bluetooth over-ear, 30h battery",  250.00, "electronics", "Sony",     50,  "ACTIVE");
        _amzProduct("Preseed Cooking Cookbook",    "Italian recipes hardcover",         45.00, "books",       "Penguin",  100, "ACTIVE");
        _amzProduct("Preseed Old Phone",           "Discontinued model — no orders",   200.00, "electronics", "Nokia",    0,   "INACTIVE");
        _amzProduct("Preseed Chef Knife",          "Stainless steel 8-inch",            30.00, "kitchen",     "WMF",      75,  "ACTIVE");
        _amzProduct("Preseed Out-Of-Stock Drone",  "Currently unavailable",            900.00, "electronics", "DJI",      0,   "OUT_OF_STOCK");
        // Optional details jsonb (covers /products/details/search?key=note&value=true)
        if (tProduct != null) {
            _tryUpdate("UPDATE \"" + tProduct + "\" SET details = ?::jsonb WHERE id = ?",
                "{\"warranty\":\"2 years\",\"note\":\"true\",\"color\":\"black\"}", 1L);
            _tryUpdate("UPDATE \"" + tProduct + "\" SET details = ?::jsonb WHERE id = ?",
                "{\"isbn\":\"978-0-14-001\",\"note\":\"true\"}", 2L);
            _tryUpdate("UPDATE \"" + tProduct + "\" SET details = ?::jsonb WHERE id = ?",
                "{\"material\":\"stainless\",\"note\":\"true\"}", 4L);
            // Optional rating column (running average for S2-F1 search-by-rating)
            _tryUpdate("UPDATE \"" + tProduct + "\" SET rating = ? WHERE id = ?", 4.7, 1L);
            _tryUpdate("UPDATE \"" + tProduct + "\" SET rating = ? WHERE id = ?", 4.2, 2L);
            _tryUpdate("UPDATE \"" + tProduct + "\" SET rating = ? WHERE id = ?", 3.8, 3L);
            _tryUpdate("UPDATE \"" + tProduct + "\" SET rating = ? WHERE id = ?", 4.5, 4L);
            _tryUpdate("UPDATE \"" + tProduct + "\" SET rating = ? WHERE id = ?", 4.9, 5L);
        }

        // ── Product reviews (8) ─────────────────────────────────────────
        _amzReview(2L, 1L, 5, "Excellent sound quality");
        _amzReview(3L, 1L, 4, "Comfortable but a bit pricey");
        _amzReview(4L, 1L, 5, "Worth every penny");
        _amzReview(2L, 2L, 5, "Loved every recipe");
        _amzReview(5L, 2L, 4, "Great photographs");
        _amzReview(4L, 4L, 4, "Sharp and well-balanced");
        _amzReview(3L, 4L, 5, "Best knife I've owned");
        _amzReview(5L, 5L, 5, "Hope it comes back in stock");

        // ── Orders (12) — user 1=2, user 2=3, user 3=1, user 4=5, user 5=1 ───
        // Distribution covers TC48-53 (S2-F12 dashboard via order_items.product_id):
        //   * Product 1: 5 distinct orders → totalOrders=5
        //   * Product 2: 3 orders          → totalOrders=3
        //   * Product 3: 0 orders          → totalOrders=0 (TC52 happy path uses id=3)
        //   * Product 4: 3 orders          → totalOrders=3
        //   * Product 5: 1 order           → totalOrders=1
        _amzOrder(1L, 250.00, "DELIVERED");   //  1 — admin / electronics
        _amzOrder(1L,  75.00, "CONFIRMED");   //  2 — admin / books+knife
        _amzOrder(2L,  45.00, "DELIVERED");   //  3 — user 2 / books
        _amzOrder(2L, 250.00, "SHIPPED");     //  4 — user 2 / headphones
        _amzOrder(2L,  90.00, "CONFIRMED");   //  5 — user 2 / books×2
        _amzOrder(3L, 250.00, "PENDING");     //  6 — user 3 / headphones
        _amzOrder(4L,  30.00, "DELIVERED");   //  7 — user 4 / knife
        _amzOrder(4L, 280.00, "DELIVERED");   //  8 — user 4 / headphones+knife
        _amzOrder(4L,  45.00, "DELIVERED");   //  9 — user 4 / books
        _amzOrder(4L, 250.00, "DELIVERED");   // 10 — user 4 / headphones
        _amzOrder(4L,  60.00, "RETURNED");    // 11 — user 4 / knife×2
        _amzOrder(5L, 900.00, "CANCELLED");   // 12 — user 5 / drone
        // Optional order_date column
        if (tOrder != null) {
            _tryUpdate("UPDATE \"" + tOrder + "\" SET order_date = ? WHERE id = ?", java.sql.Date.valueOf("2026-01-01"),  1L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET order_date = ? WHERE id = ?", java.sql.Date.valueOf("2026-01-15"),  2L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET order_date = ? WHERE id = ?", java.sql.Date.valueOf("2026-01-20"),  3L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET order_date = ? WHERE id = ?", java.sql.Date.valueOf("2026-02-01"),  4L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET order_date = ? WHERE id = ?", java.sql.Date.valueOf("2026-02-05"),  5L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET order_date = ? WHERE id = ?", java.sql.Date.valueOf("2026-02-10"),  6L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET order_date = ? WHERE id = ?", java.sql.Date.valueOf("2026-01-10"),  7L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET order_date = ? WHERE id = ?", java.sql.Date.valueOf("2026-01-12"),  8L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET order_date = ? WHERE id = ?", java.sql.Date.valueOf("2026-01-18"),  9L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET order_date = ? WHERE id = ?", java.sql.Date.valueOf("2026-01-25"), 10L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET order_date = ? WHERE id = ?", java.sql.Date.valueOf("2026-02-15"), 11L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET order_date = ? WHERE id = ?", java.sql.Date.valueOf("2026-02-20"), 12L);
            // Optional metadata jsonb
            _tryUpdate("UPDATE \"" + tOrder + "\" SET metadata = ?::jsonb WHERE id = ?",
                "{\"note\":\"true\",\"channel\":\"web\",\"priority\":\"HIGH\"}",   1L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET metadata = ?::jsonb WHERE id = ?",
                "{\"note\":\"true\",\"channel\":\"app\",\"giftWrap\":\"true\"}",   3L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET metadata = ?::jsonb WHERE id = ?",
                "{\"note\":\"true\",\"channel\":\"web\"}",                         8L);
            // Optional shipping_address_id FK (user→address ownership preserved)
            _tryUpdate("UPDATE \"" + tOrder + "\" SET shipping_address_id = ? WHERE id = ?", 1L,  1L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET shipping_address_id = ? WHERE id = ?", 2L,  2L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET shipping_address_id = ? WHERE id = ?", 4L,  3L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET shipping_address_id = ? WHERE id = ?", 4L,  4L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET shipping_address_id = ? WHERE id = ?", 5L,  5L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET shipping_address_id = ? WHERE id = ?", 6L,  6L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET shipping_address_id = ? WHERE id = ?", 7L,  7L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET shipping_address_id = ? WHERE id = ?", 7L,  8L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET shipping_address_id = ? WHERE id = ?", 7L,  9L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET shipping_address_id = ? WHERE id = ?", 7L, 10L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET shipping_address_id = ? WHERE id = ?", 7L, 11L);
            _tryUpdate("UPDATE \"" + tOrder + "\" SET shipping_address_id = ? WHERE id = ?", 8L, 12L);
        }

        // ── Order items (16) — links orders ↔ products with FK integrity ─
        // Order 1 (admin) has 1 item, order 2 has 2 items (covers multi-item),
        // others typically 1 item, order 5 has 2, order 8 has 2.
        _amzOrderItem( 1L, 1L, 1, 250.00);                          // O1: headphones
        _amzOrderItem( 2L, 2L, 1,  45.00); _amzOrderItem( 2L, 4L, 1, 30.00);  // O2: book + knife
        _amzOrderItem( 3L, 2L, 1,  45.00);                          // O3
        _amzOrderItem( 4L, 1L, 1, 250.00);                          // O4
        _amzOrderItem( 5L, 2L, 2,  45.00);                          // O5
        _amzOrderItem( 6L, 1L, 1, 250.00);                          // O6
        _amzOrderItem( 7L, 4L, 1,  30.00);                          // O7
        _amzOrderItem( 8L, 1L, 1, 250.00); _amzOrderItem( 8L, 4L, 1, 30.00);  // O8: 2 items
        _amzOrderItem( 9L, 2L, 1,  45.00);                          // O9
        _amzOrderItem(10L, 1L, 1, 250.00);                          // O10
        _amzOrderItem(11L, 4L, 2,  30.00);                          // O11
        _amzOrderItem(12L, 5L, 1, 900.00);                          // O12: drone

        // ── Shipments (12) — one per order ──────────────────────────────
        _amzShipment( 1L, "DHL",    "DHL-PRE-1",  "DELIVERED");
        _amzShipment( 2L, "FedEx",  "FDX-PRE-2",  "PROCESSING");
        _amzShipment( 3L, "Aramex", "ARX-PRE-3",  "DELIVERED");
        _amzShipment( 4L, "DHL",    "DHL-PRE-4",  "SHIPPED");
        _amzShipment( 5L, "DHL",    "DHL-PRE-5",  "OUT_FOR_DELIVERY");
        _amzShipment( 6L, "FedEx",  "FDX-PRE-6",  "PROCESSING");
        _amzShipment( 7L, "DHL",    "DHL-PRE-7",  "DELIVERED");
        _amzShipment( 8L, "Aramex", "ARX-PRE-8",  "DELIVERED");
        _amzShipment( 9L, "DHL",    "DHL-PRE-9",  "DELIVERED");
        _amzShipment(10L, "DHL",    "DHL-PRE-10", "DELIVERED");
        _amzShipment(11L, "DHL",    "DHL-PRE-11", "RETURNED");
        _amzShipment(12L, "DHL",    "DHL-PRE-12", "RETURNED");
        // Optional metadata jsonb — courier "Hassan" attributed to multiple shipments
        if (tShipment != null) {
            _tryUpdate("UPDATE \"" + tShipment + "\" SET metadata = ?::jsonb WHERE id = ?",
                "{\"courierName\":\"Hassan\",\"speed\":40,\"note\":\"true\"}",  4L);
            _tryUpdate("UPDATE \"" + tShipment + "\" SET metadata = ?::jsonb WHERE id = ?",
                "{\"courierName\":\"Hassan\",\"speed\":50,\"note\":\"true\"}",  5L);
            _tryUpdate("UPDATE \"" + tShipment + "\" SET metadata = ?::jsonb WHERE id = ?",
                "{\"courierName\":\"Hassan\",\"speed\":35,\"note\":\"true\"}",  7L);
            _tryUpdate("UPDATE \"" + tShipment + "\" SET metadata = ?::jsonb WHERE id = ?",
                "{\"courierName\":\"Hassan\",\"speed\":45,\"note\":\"true\"}",  8L);
            _tryUpdate("UPDATE \"" + tShipment + "\" SET metadata = ?::jsonb WHERE id = ?",
                "{\"courierName\":\"Hassan\",\"speed\":30,\"note\":\"true\"}", 10L);
        }

        // ── Transactions (12) — one per order; mostly COMPLETED ─────────
        _amzTransaction( 1L, 1L, 250.00, "CREDIT_CARD",      "COMPLETED");
        _amzTransaction( 2L, 1L,  75.00, "WALLET",           "COMPLETED");
        _amzTransaction( 3L, 2L,  45.00, "WALLET",           "COMPLETED");
        _amzTransaction( 4L, 2L, 250.00, "CASH_ON_DELIVERY", "PENDING");
        _amzTransaction( 5L, 2L,  90.00, "CREDIT_CARD",      "COMPLETED");
        _amzTransaction( 6L, 3L, 250.00, "CREDIT_CARD",      "PENDING");
        _amzTransaction( 7L, 4L,  30.00, "CREDIT_CARD",      "COMPLETED");
        _amzTransaction( 8L, 4L, 280.00, "CREDIT_CARD",      "COMPLETED");
        _amzTransaction( 9L, 4L,  45.00, "WALLET",           "COMPLETED");
        _amzTransaction(10L, 4L, 250.00, "CREDIT_CARD",      "COMPLETED");
        _amzTransaction(11L, 4L,  60.00, "CREDIT_CARD",      "REFUNDED");
        _amzTransaction(12L, 5L, 900.00, "CASH_ON_DELIVERY", "FAILED");
        // Optional transaction_details jsonb
        if (tTrans != null) {
            _tryUpdate("UPDATE \"" + tTrans + "\" SET transaction_details = ?::jsonb WHERE id = ?",
                "{\"receipt\":\"abc-123\",\"note\":\"true\",\"gateway\":\"stripe\"}", 1L);
            _tryUpdate("UPDATE \"" + tTrans + "\" SET transaction_details = ?::jsonb WHERE id = ?",
                "{\"receipt\":\"def-456\",\"note\":\"true\"}", 3L);
            _tryUpdate("UPDATE \"" + tTrans + "\" SET transaction_details = ?::jsonb WHERE id = ?",
                "{\"receipt\":\"ghi-789\",\"note\":\"true\"}", 5L);
        }

        // ── Vouchers (4) ────────────────────────────────────────────────
        _amzVoucher("PRESEED10", "PERCENTAGE", 10.0, 100);
        _amzVoucher("PRESEED20", "FIXED",      20.0,  50);
        _amzVoucher("PRESEED5",  "PERCENTAGE",  5.0, 200);
        _amzVoucher("WELCOME15", "PERCENTAGE", 15.0,  30);

        // ── transaction_vouchers junction (top-used distribution 5/3/2/1) ─
        if (tTxVouch != null) {
            _tryUpdate("INSERT INTO \"" + tTxVouch + "\" (transaction_id, voucher_id) VALUES (?, ?)",  1L, 1L);
            _tryUpdate("INSERT INTO \"" + tTxVouch + "\" (transaction_id, voucher_id) VALUES (?, ?)",  2L, 1L);
            _tryUpdate("INSERT INTO \"" + tTxVouch + "\" (transaction_id, voucher_id) VALUES (?, ?)",  3L, 1L);
            _tryUpdate("INSERT INTO \"" + tTxVouch + "\" (transaction_id, voucher_id) VALUES (?, ?)",  5L, 1L);
            _tryUpdate("INSERT INTO \"" + tTxVouch + "\" (transaction_id, voucher_id) VALUES (?, ?)",  7L, 1L);
            _tryUpdate("INSERT INTO \"" + tTxVouch + "\" (transaction_id, voucher_id) VALUES (?, ?)",  8L, 2L);
            _tryUpdate("INSERT INTO \"" + tTxVouch + "\" (transaction_id, voucher_id) VALUES (?, ?)",  9L, 2L);
            _tryUpdate("INSERT INTO \"" + tTxVouch + "\" (transaction_id, voucher_id) VALUES (?, ?)", 10L, 2L);
            _tryUpdate("INSERT INTO \"" + tTxVouch + "\" (transaction_id, voucher_id) VALUES (?, ?)", 11L, 3L);
            _tryUpdate("INSERT INTO \"" + tTxVouch + "\" (transaction_id, voucher_id) VALUES (?, ?)",  4L, 3L);
            _tryUpdate("INSERT INTO \"" + tTxVouch + "\" (transaction_id, voucher_id) VALUES (?, ?)",  6L, 4L);
        }
    }
    // ─── end auto-seed baseline ───

    // ─── end auto-truncate hooks ───

    // ─── Source-scan helpers for design-pattern tests (TC379–TC425) ───
    protected static final String REPO_PATH_ENV = System.getenv("REPO_PATH");
    private static final java.util.Set<String> _SCAN_PRUNE_DIRS = java.util.Set.of(
        "target", "node_modules", ".git", ".idea", ".gradle", ".mvn",
        "build", "out", "dist", "bin", ".next", ".vscode",
        ".m2", "logs", "tmp", "temp", ".cache", "coverage", ".nyc_output"
    );
    private static volatile java.util.List<java.nio.file.Path> _ALL_JAVA_FILES_CACHE;
    private static volatile java.util.Map<String, String> _SOURCE_BY_NAME_CACHE;
    private static volatile String _ALL_SOURCES_CONCAT_CACHE;
    private static final Object _ALL_JAVA_FILES_LOCK = new Object();

    protected java.util.List<java.nio.file.Path> allJavaFiles() {
        if (REPO_PATH_ENV == null || REPO_PATH_ENV.isEmpty())
            return java.util.Collections.emptyList();
        java.util.List<java.nio.file.Path> cached = _ALL_JAVA_FILES_CACHE;
        if (cached != null) return cached;
        synchronized (_ALL_JAVA_FILES_LOCK) {
            if (_ALL_JAVA_FILES_CACHE != null) return _ALL_JAVA_FILES_CACHE;
            java.util.List<java.nio.file.Path> out = new java.util.ArrayList<>();
            try {
                java.nio.file.Files.walkFileTree(
                    java.nio.file.Path.of(REPO_PATH_ENV),
                    java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class),
                    Integer.MAX_VALUE,
                    new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                        @Override public java.nio.file.FileVisitResult preVisitDirectory(
                                java.nio.file.Path dir, java.nio.file.attribute.BasicFileAttributes a) {
                            java.nio.file.Path nm = dir.getFileName();
                            if (nm != null && _SCAN_PRUNE_DIRS.contains(nm.toString()))
                                return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                            return java.nio.file.FileVisitResult.CONTINUE;
                        }
                        @Override public java.nio.file.FileVisitResult visitFile(
                                java.nio.file.Path file, java.nio.file.attribute.BasicFileAttributes a) {
                            String s = file.toString();
                            if (s.endsWith(".java") && s.contains("/src/main/java/"))
                                out.add(file);
                            return java.nio.file.FileVisitResult.CONTINUE;
                        }
                        @Override public java.nio.file.FileVisitResult visitFileFailed(
                                java.nio.file.Path file, java.io.IOException exc) {
                            return java.nio.file.FileVisitResult.CONTINUE;
                        }
                    }
                );
            } catch (java.io.IOException ignored) { }
            _ALL_JAVA_FILES_CACHE = java.util.Collections.unmodifiableList(out);
            return _ALL_JAVA_FILES_CACHE;
        }
    }

    /** Lazily build per-fileName content map AND a single concatenated-all-sources string.
     *  Both built in one pass — each file is read at most once per JVM. */
    private void _ensureSourceCaches() {
        if (_SOURCE_BY_NAME_CACHE != null && _ALL_SOURCES_CONCAT_CACHE != null) return;
        synchronized (_ALL_JAVA_FILES_LOCK) {
            if (_SOURCE_BY_NAME_CACHE != null && _ALL_SOURCES_CONCAT_CACHE != null) return;
            java.util.Map<String, String> byName = new java.util.HashMap<>();
            StringBuilder concat = new StringBuilder(2_000_000);
            for (java.nio.file.Path p : allJavaFiles()) {
                String fname = p.getFileName().toString();
                String content;
                try { content = java.nio.file.Files.readString(p); }
                catch (java.io.IOException e) { continue; }
                String existing = byName.get(fname);
                byName.put(fname, existing == null
                    ? content
                    : existing + "
// ─── next file ───
" + content);
                concat.append("
// ─── ").append(fname).append(" ───
").append(content);
            }
            _SOURCE_BY_NAME_CACHE = java.util.Collections.unmodifiableMap(byName);
            _ALL_SOURCES_CONCAT_CACHE = concat.toString();
        }
    }

    protected String readClassSource(String simpleClassName) {
        _ensureSourceCaches();
        return _SOURCE_BY_NAME_CACHE.getOrDefault(simpleClassName + ".java", "");
    }

    protected String readAllSourcesNamed(String simpleClassName) {
        _ensureSourceCaches();
        return _SOURCE_BY_NAME_CACHE.getOrDefault(simpleClassName + ".java", "");
    }

    protected long countImplementors(String interfaceName) {
        _ensureSourceCaches();
        String needle = "implements " + interfaceName;
        String selfFile = interfaceName + ".java";
        long count = 0;
        for (java.util.Map.Entry<String, String> e : _SOURCE_BY_NAME_CACHE.entrySet()) {
            if (e.getKey().equals(selfFile)) continue;
            if (e.getValue().contains(needle)) count++;
        }
        return count;
    }

    protected boolean anySourceContains(String pattern) {
        _ensureSourceCaches();
        return _ALL_SOURCES_CONCAT_CACHE.contains(pattern);
    }

    protected boolean noSourceContains(String pattern) { return !anySourceContains(pattern); }
    // ─── end source-scan helpers ───


}
