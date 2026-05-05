package com.testgen.amazon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Admin-seed + login helpers, derived from the L0 scanner's manifest.
 *
 * <p>The M2 spec deliberately omits a standardized admin-seed mechanism
 * (per the {@code feedback_no_first_admin_bootstrap} decision). Students
 * may use Flyway, CommandLineRunner, or direct DB insert, so the tests
 * cannot rely on any student-shipped seed.
 *
 * <p>We also cannot POST {@code role=ADMIN} through the register endpoint
 * — every M2 theme rejects that payload (Freelance accepts
 * {@code CLIENT|FREELANCER} but not {@code ADMIN}; others ignore role and
 * pin to the theme default). Nor can we BCrypt the password in-test (no
 * BCrypt dep on the grader-controlled test classpath).
 *
 * <p>The workaround: register a normal user over HTTP (student's service
 * hashes the password), then promote to ADMIN via JDBC using the role
 * enum type discovered at runtime. Finally, log in to obtain the JWT.
 *
 * <h2>Talabat-specific constants in this golden</h2>
 * <ul>
 *   <li>Register path: {@code /api/auth/register}</li>
 *   <li>Login path: {@code /api/auth/login}</li>
 *   <li>User table: {@code users}</li>
 *   <li>Role column: {@code role}</li>
 *   <li>Admin label: {@code ADMIN}</li>
 * </ul>
 *
 * <p>The template derived from this golden replaces every one of these
 * constants with {@code {{auth.<field>}}} holes backed by the L0 manifest.
 */
public final class TestAuthHelper {

    /** Shared strong-looking password used for every seeded admin in a run. */
    public static final String ADMIN_PASSWORD = "TestAdmin!Pass2026";

    private static final ObjectMapper OM = new ObjectMapper();

    private TestAuthHelper() {}

    /**
     * Register a user via HTTP, promote to ADMIN via direct JDBC, return the new user id.
     *
     * @return the newly-created user's {@code id}
     * @throws AssertionError if register returns non-2xx, UPDATE affects zero rows,
     *         or the SELECT to fetch the id comes back empty.
     */
    public static long seedAdmin(HttpClient http, JdbcTemplate jdbc, String baseUrl, String email) throws Exception {
        String phone = "+2010" + System.nanoTime();
        String body = String.format("""
                {"name":"TestAdmin","email":"%s","password":"%s","phone":"%s"}
                """, email, ADMIN_PASSWORD, phone.substring(0, 15));

        HttpResponse<String> r = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/auth/register"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() / 100 != 2) {
            throw new AssertionError(
                    "seedAdmin: POST /api/auth/register returned " + r.statusCode()
                            + " body=" + r.body());
        }

        // Promote to ADMIN via JDBC. The role column is a PG native enum,
        // whose type name varies per theme — discover it via pg_type.
        String roleEnumType = discoverEnumTypeForColumn(jdbc, "users", "role");
        String updateSql;
        if (roleEnumType != null) {
            updateSql = "UPDATE users SET role = CAST(? AS " + roleEnumType + ") WHERE email = ?";
        } else {
            updateSql = "UPDATE users SET role = ? WHERE email = ?";
        }
        int affected;
        try {
            affected = jdbc.update(updateSql, "ADMIN", email);
        } catch (DataAccessException e) {
            throw new AssertionError("seedAdmin: UPDATE users.role failed: " + e.getMessage(), e);
        }
        if (affected != 1) {
            throw new AssertionError("seedAdmin: UPDATE affected " + affected + " rows, expected 1");
        }

        Long id;
        try {
            id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        } catch (DataAccessException e) {
            throw new AssertionError("seedAdmin: SELECT id failed: " + e.getMessage(), e);
        }
        if (id == null) {
            throw new AssertionError("seedAdmin: no user row found after register + promote");
        }
        return id;
    }

    /**
     * POST /api/auth/login for the seeded admin, return the JWT from the response.
     *
     * @return the {@code token} field value from the login response
     */
    public static String loginAsAdmin(HttpClient http, String baseUrl, String email) throws Exception {
        String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, ADMIN_PASSWORD);
        HttpResponse<String> r = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() / 100 != 2) {
            throw new AssertionError(
                    "loginAsAdmin: POST /api/auth/login returned " + r.statusCode()
                            + " body=" + r.body());
        }
        JsonNode j = OM.readTree(r.body());
        if (!j.has("token") || j.get("token").asText().isBlank()) {
            throw new AssertionError("loginAsAdmin: response missing or empty token: " + r.body());
        }
        return j.get("token").asText();
    }

    /** Build-once helper: one call to cover both seed and login. */
    public static String seedAndLoginAdmin(HttpClient http, JdbcTemplate jdbc, String baseUrl, String email) throws Exception {
        seedAdmin(http, jdbc, baseUrl, email);
        return loginAsAdmin(http, baseUrl, email);
    }

    // ────────────────────────────────────────────────────────────────

    private static String discoverEnumTypeForColumn(JdbcTemplate jdbc, String table, String col) {
        try {
            String udt = jdbc.queryForObject(
                    "SELECT udt_name FROM information_schema.columns "
                  + "WHERE table_name = ? AND column_name = ?",
                    String.class, table, col);
            if (udt == null) return null;
            if (udt.equals("varchar") || udt.equals("text") || udt.startsWith("int")) return null;
            return udt;
        } catch (DataAccessException e) {
            return null;
        }
    }
}
