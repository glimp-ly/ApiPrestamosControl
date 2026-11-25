package com.prestacontrol.AppPrestaControl;

import com.prestacontrol.AppPrestaControl.dto.AuthResponse;
import com.prestacontrol.AppPrestaControl.dto.ErrorResponse;
import com.prestacontrol.AppPrestaControl.dto.LoginRequest;
import com.prestacontrol.AppPrestaControl.dto.PrestamoRequest;
import com.prestacontrol.AppPrestaControl.dto.PrestamoResponse;
import com.prestacontrol.AppPrestaControl.dto.RegisterRequest;
import com.prestacontrol.AppPrestaControl.dto.ResumenCarteraResponse;
import com.prestacontrol.AppPrestaControl.entity.Usuario;
import com.prestacontrol.AppPrestaControl.repository.PrestamoRepository;
import com.prestacontrol.AppPrestaControl.repository.UsuarioRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PrestaControlApiIntegrationTests {

    @LocalServerPort
    private int port;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PrestamoRepository prestamoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        prestamoRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void rejectsMissingTamperedAndExpiredTokensWith401() throws Exception {
        AuthResponse auth = registerUser("Ana", "ana@prestacontrol.com", "secret123");

        HttpResponse<String> noTokenRes = get("/prestamos", null);
        assertEquals(401, noTokenRes.statusCode());

        String validToken = auth.token();
        char lastChar = validToken.charAt(validToken.length() - 1);
        char replacement = lastChar == 'A' ? 'B' : 'A';
        String tamperedToken = validToken.substring(0, validToken.length() - 1) + replacement;

        HttpResponse<String> tamperedRes = get("/prestamos", tamperedToken);
        assertEquals(401, tamperedRes.statusCode());

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        String expiredToken = Jwts.builder()
                .subject("ana@prestacontrol.com")
                .issuedAt(Date.from(now.minusSeconds(7200)))
                .expiration(Date.from(now.minusSeconds(3600)))
                .signWith(key)
                .compact();

        HttpResponse<String> expiredRes = get("/prestamos", expiredToken);
        assertEquals(401, expiredRes.statusCode());
    }

    @Test
    void registerStoresBcryptHashReturnsJwtWithEmailSubjectAndRejectsDuplicateWith409() throws Exception {
        HttpResponse<String> res = post("/auth/register", new RegisterRequest("  Carlos Diaz  ", "Carlos@Example.COM", "claveSegura123"), null);
        assertEquals(201, res.statusCode());

        AuthResponse auth = objectMapper.readValue(res.body(), AuthResponse.class);
        assertEquals("Bearer", auth.tipo());
        assertEquals("Carlos Diaz", auth.nombre());
        assertEquals("carlos@example.com", auth.email());
        assertEquals("USER", auth.rol());
        assertNotNull(auth.token());
        assertFalse(auth.token().isBlank());

        Usuario saved = usuarioRepository.findByEmail("carlos@example.com").orElseThrow();
        assertNotEquals("claveSegura123", saved.getPassword());
        assertTrue(saved.getPassword().startsWith("$2"));
        assertTrue(passwordEncoder.matches("claveSegura123", saved.getPassword()));

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(auth.token()).getPayload();
        assertEquals("carlos@example.com", claims.getSubject());

        HttpResponse<String> duplicateRes = post("/auth/register", new RegisterRequest("Otro", "CARLOS@example.com", "claveSegura123"), null);
        assertEquals(409, duplicateRes.statusCode());
    }

    @Test
    void loginReturns200AndWrongPasswordReturns401() throws Exception {
        registerUser("Luis", "luis@example.com", "miClave2026");

        HttpResponse<String> okRes = post("/auth/login", new LoginRequest("LUIS@example.com", "miClave2026"), null);
        assertEquals(200, okRes.statusCode());
        AuthResponse auth = objectMapper.readValue(okRes.body(), AuthResponse.class);
        assertEquals("luis@example.com", auth.email());
        assertFalse(auth.token().isBlank());

        HttpResponse<String> badRes = post("/auth/login", new LoginRequest("luis@example.com", "incorrecta"), null);
        assertEquals(401, badRes.statusCode());
    }

    @Test
    void crudFiltersCalculationResumenAndPartialUpdateWorkEndToEnd() throws Exception {
        String token = registerUser("Prestamista", "prestamista@example.com", "password123").token();

        PrestamoRequest loan1Req = new PrestamoRequest(
                "  Juan Perez  ",
                "12345678",
                new BigDecimal("1000.00"),
                new BigDecimal("15.00"),
                LocalDate.now().plusDays(30),
                null
        );
        HttpResponse<String> create1Res = post("/prestamos", loan1Req, token);
        assertEquals(201, create1Res.statusCode());
        PrestamoResponse loan1 = objectMapper.readValue(create1Res.body(), PrestamoResponse.class);
        assertNotNull(loan1.id());
        assertEquals("Juan Perez", loan1.cliente());
        assertEquals(0, new BigDecimal("1150.00").compareTo(loan1.totalCobrar()));
        assertEquals(LocalDate.now(), loan1.fechaPrestamo());
        assertFalse(loan1.pagado());

        PrestamoRequest loan2Req = new PrestamoRequest(
                "Maria Lopez",
                "87654321",
                new BigDecimal("500.00"),
                new BigDecimal("10.00"),
                LocalDate.now().plusDays(15),
                false
        );
        HttpResponse<String> create2Res = post("/prestamos", loan2Req, token);
        assertEquals(201, create2Res.statusCode());
        PrestamoResponse loan2 = objectMapper.readValue(create2Res.body(), PrestamoResponse.class);
        assertEquals(0, new BigDecimal("550.00").compareTo(loan2.totalCobrar()));

        List<PrestamoResponse> all = readList(get("/prestamos", token));
        assertEquals(2, all.size());

        List<PrestamoResponse> pending = readList(get("/prestamos?pagado=false", token));
        assertEquals(2, pending.size());

        List<PrestamoResponse> paidEmpty = readList(get("/prestamos?pagado=true", token));
        assertEquals(0, paidEmpty.size());

        List<PrestamoResponse> byName = readList(get("/prestamos?buscar=" + encode("juan"), token));
        assertEquals(1, byName.size());
        assertEquals(loan1.id(), byName.getFirst().id());

        List<PrestamoResponse> byDni = readList(get("/prestamos?buscar=87654321", token));
        assertEquals(1, byDni.size());
        assertEquals(loan2.id(), byDni.getFirst().id());

        List<PrestamoResponse> combined = readList(get("/prestamos?pagado=false&buscar=Maria", token));
        assertEquals(1, combined.size());
        assertEquals(loan2.id(), combined.getFirst().id());

        List<PrestamoResponse> blankSearch = readList(get("/prestamos?buscar=%20%20", token));
        assertEquals(2, blankSearch.size());

        HttpResponse<String> getOneRes = get("/prestamos/" + loan1.id(), token);
        assertEquals(200, getOneRes.statusCode());
        assertEquals(loan1.id(), objectMapper.readValue(getOneRes.body(), PrestamoResponse.class).id());

        ResumenCarteraResponse resumenBefore = objectMapper.readValue(get("/prestamos/resumen", token).body(), ResumenCarteraResponse.class);
        assertEquals(0, new BigDecimal("1500.00").compareTo(resumenBefore.capitalPrestado()));
        assertEquals(0, new BigDecimal("1700.00").compareTo(resumenBefore.totalPorCobrar()));
        assertEquals(0, new BigDecimal("200.00").compareTo(resumenBefore.gananciaEsperada()));
        assertEquals(2, resumenBefore.totalPrestamos());
        assertEquals(2, resumenBefore.prestamosPendientes());
        assertEquals(0, resumenBefore.prestamosPagados());

        HttpResponse<String> updateRes = put("/prestamos/" + loan1.id(), Map.of("pagado", true), token);
        assertEquals(200, updateRes.statusCode());
        PrestamoResponse updated = objectMapper.readValue(updateRes.body(), PrestamoResponse.class);
        assertTrue(updated.pagado());
        assertEquals("Juan Perez", updated.cliente());
        assertEquals(loan1.fechaPrestamo(), updated.fechaPrestamo());

        List<PrestamoResponse> paidList = readList(get("/prestamos?pagado=true", token));
        assertEquals(1, paidList.size());
        assertEquals(loan1.id(), paidList.getFirst().id());

        List<PrestamoResponse> paidSearch = readList(get("/prestamos?pagado=true&buscar=Juan", token));
        assertEquals(1, paidSearch.size());

        ResumenCarteraResponse resumenAfter = objectMapper.readValue(get("/prestamos/resumen", token).body(), ResumenCarteraResponse.class);
        assertEquals(0, new BigDecimal("1500.00").compareTo(resumenAfter.capitalPrestado()));
        assertEquals(0, new BigDecimal("550.00").compareTo(resumenAfter.totalPorCobrar()));
        assertEquals(0, new BigDecimal("200.00").compareTo(resumenAfter.gananciaEsperada()));
        assertEquals(2, resumenAfter.totalPrestamos());
        assertEquals(1, resumenAfter.prestamosPendientes());
        assertEquals(1, resumenAfter.prestamosPagados());

        HttpResponse<String> deleteRes = delete("/prestamos/" + loan2.id(), token);
        assertEquals(204, deleteRes.statusCode());
        assertEquals(404, get("/prestamos/" + loan2.id(), token).statusCode());
    }

    @Test
    void enforcesMultiTenantIsolationWith404() throws Exception {
        String tokenA = registerUser("User A", "usera@example.com", "password123").token();
        String tokenB = registerUser("User B", "userb@example.com", "password123").token();

        PrestamoRequest req = new PrestamoRequest(
                "Cliente A",
                "11223344",
                new BigDecimal("800.00"),
                new BigDecimal("20.00"),
                LocalDate.now().plusDays(10),
                false
        );
        PrestamoResponse created = objectMapper.readValue(post("/prestamos", req, tokenA).body(), PrestamoResponse.class);

        assertEquals(0, readList(get("/prestamos", tokenB)).size());
        ResumenCarteraResponse resumenB = objectMapper.readValue(get("/prestamos/resumen", tokenB).body(), ResumenCarteraResponse.class);
        assertEquals(0, resumenB.totalPrestamos());
        assertEquals(0, BigDecimal.ZERO.compareTo(resumenB.capitalPrestado()));
        assertEquals(404, get("/prestamos/" + created.id(), tokenB).statusCode());
        assertEquals(404, put("/prestamos/" + created.id(), Map.of("pagado", true), tokenB).statusCode());
        assertEquals(404, delete("/prestamos/" + created.id(), tokenB).statusCode());

        PrestamoResponse stillIntact = objectMapper.readValue(get("/prestamos/" + created.id(), tokenA).body(), PrestamoResponse.class);
        assertFalse(stillIntact.pagado());
    }

    @Test
    void returns400OnInvalidDniAndNonPositiveMonto() throws Exception {
        String token = registerUser("Validator", "val@example.com", "password123").token();

        PrestamoRequest invalidDniReq = new PrestamoRequest(
                "Cliente",
                "1234",
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                LocalDate.now().plusDays(5),
                false
        );
        HttpResponse<String> dniRes = post("/prestamos", invalidDniReq, token);
        assertEquals(400, dniRes.statusCode());
        ErrorResponse dniErr = objectMapper.readValue(dniRes.body(), ErrorResponse.class);
        assertTrue(dniErr.details().containsKey("dni"));

        PrestamoRequest zeroMontoReq = new PrestamoRequest(
                "Cliente",
                "12345678",
                BigDecimal.ZERO,
                new BigDecimal("10.00"),
                LocalDate.now().plusDays(5),
                false
        );
        HttpResponse<String> zeroRes = post("/prestamos", zeroMontoReq, token);
        assertEquals(400, zeroRes.statusCode());
        ErrorResponse zeroErr = objectMapper.readValue(zeroRes.body(), ErrorResponse.class);
        assertTrue(zeroErr.details().containsKey("monto"));

        PrestamoRequest negativeMontoReq = new PrestamoRequest(
                "Cliente",
                "12345678",
                new BigDecimal("-50.00"),
                new BigDecimal("10.00"),
                LocalDate.now().plusDays(5),
                false
        );
        HttpResponse<String> negRes = post("/prestamos", negativeMontoReq, token);
        assertEquals(400, negRes.statusCode());
        ErrorResponse negErr = objectMapper.readValue(negRes.body(), ErrorResponse.class);
        assertTrue(negErr.details().containsKey("monto"));
    }

    private AuthResponse registerUser(String nombre, String email, String password) throws Exception {
        HttpResponse<String> res = post("/auth/register", new RegisterRequest(nombre, email, password), null);
        assertEquals(201, res.statusCode());
        return objectMapper.readValue(res.body(), AuthResponse.class);
    }

    private List<PrestamoResponse> readList(HttpResponse<String> response) {
        assertEquals(200, response.statusCode());
        return objectMapper.readValue(response.body(), new TypeReference<>() {});
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, Object body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, Object body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .DELETE();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
