package edu.eci.dosw.todo.integration;

import edu.eci.dosw.todo.entity.Role;
import edu.eci.dosw.todo.entity.TaskEntity;
import edu.eci.dosw.todo.entity.UserEntity;
import edu.eci.dosw.todo.repository.TaskRepository;
import edu.eci.dosw.todo.repository.UserRepository;
import edu.eci.dosw.todo.security.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end HTTP tests of the real filter chain, real JWT validation and a real (H2) database.
 * They cover the security matrix PT-01 .. PT-15 and the 401 / 403 / 404 policy.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    private static final String PASSWORD = "ContrasenaDemo123!";
    private static final Pattern TOKEN = Pattern.compile("\"accessToken\":\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @BeforeEach
    void cleanDatabase() {
        taskRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------- helpers ----------

    private ResultActions register(String name, String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    /** Registers the user (USER role) and returns a real token obtained through /auth/login. */
    private String registerAndLogin(String name, String email) throws Exception {
        register(name, email, PASSWORD).andExpect(status().isCreated());
        return tokenFrom(login(email, PASSWORD).andExpect(status().isOk()));
    }

    private String tokenFrom(ResultActions login) throws Exception {
        Matcher matcher = TOKEN.matcher(login.andReturn().getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String adminToken() throws Exception {
        UserEntity admin = new UserEntity();
        admin.setName("Admin");
        admin.setEmail("admin@example.com");
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setRole(Role.ADMIN);
        admin.setCreatedAt(LocalDateTime.now());
        userRepository.save(admin);
        return tokenFrom(login("admin@example.com", PASSWORD).andExpect(status().isOk()));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private Long createTask(String token, String title) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
        Matcher matcher = Pattern.compile("\"id\":(\\d+)").matcher(response.getContentAsString());
        assertThat(matcher.find()).isTrue();
        return Long.valueOf(matcher.group(1));
    }

    // ---------- registration (PT-01, PT-02, PT-03) ----------

    @Test
    void pt01_register_shouldCreateUserWithRoleUserAndNoSecretsInResponse() throws Exception {
        register("Ana Pérez", "ana@example.com", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void register_shouldNotAllowChoosingTheAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Eve\",\"email\":\"eve@example.com\",\"password\":\"" + PASSWORD + "\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));

        assertThat(userRepository.findByEmail("eve@example.com").orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void pt02_register_shouldReturn409ForDuplicatedEmailEvenWithDifferentCase() throws Exception {
        register("Ana", "ana@example.com", PASSWORD).andExpect(status().isCreated());

        register("Ana Again", "ANA@Example.com", PASSWORD).andExpect(status().isConflict());

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void register_shouldReturn400ForInvalidData() throws Exception {
        register("", "ana@example.com", PASSWORD).andExpect(status().isBadRequest());
        register("Ana", "not-an-email", PASSWORD).andExpect(status().isBadRequest());
        register("Ana", "ana@example.com", "short").andExpect(status().isBadRequest());
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void pt03_register_shouldStorePasswordAsBcryptHash() throws Exception {
        register("Ana", "ana@example.com", PASSWORD).andExpect(status().isCreated());

        String stored = userRepository.findByEmail("ana@example.com").orElseThrow().getPasswordHash();
        assertThat(stored).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, stored)).isTrue();
    }

    // ---------- login (PT-04, PT-05) ----------

    @Test
    void pt04_login_shouldReturnSignedBearerToken() throws Exception {
        register("Ana", "ana@example.com", PASSWORD).andExpect(status().isCreated());

        login("ana@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.expiresIn").value(1800));
    }

    @Test
    void pt05_login_shouldReturn401WithoutTokenWhenPasswordIsWrong() throws Exception {
        register("Ana", "ana@example.com", PASSWORD).andExpect(status().isCreated());

        login("ana@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("accessToken"))));
        login("nobody@example.com", PASSWORD).andExpect(status().isUnauthorized());
    }

    // ---------- protected resources (PT-06 .. PT-09) ----------

    @Test
    void pt06_tasks_shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/tasks")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void pt07_tasks_shouldReturn200WithOnlyOwnTasks() throws Exception {
        String ana = registerAndLogin("Ana", "ana@example.com");
        String luis = registerAndLogin("Luis", "luis@example.com");
        createTask(ana, "Ana task 1");
        createTask(ana, "Ana task 2");
        createTask(luis, "Luis task");

        mockMvc.perform(get("/api/v1/tasks").header("Authorization", bearer(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].title").value(org.hamcrest.Matchers.containsInAnyOrder("Ana task 1", "Ana task 2")));
        mockMvc.perform(get("/api/v1/tasks").header("Authorization", bearer(luis)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Luis task"));
    }

    @Test
    void pt08_tasks_shouldReturn401WhenTokenIsModified() throws Exception {
        String token = registerAndLogin("Ana", "ana@example.com");
        String[] parts = token.split("\\.");
        char[] signature = parts[2].toCharArray();
        int middle = signature.length / 2;
        signature[middle] = signature[middle] == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + new String(signature);

        mockMvc.perform(get("/api/v1/tasks").header("Authorization", bearer(tampered)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/tasks").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pt09_tasks_shouldReturn401WhenTokenIsExpired() throws Exception {
        UserEntity ana = userRepository.save(newUser("ana@example.com", Role.USER));
        Instant now = Instant.now();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .issuer(jwtProperties.issuer()).subject(String.valueOf(ana.getId()))
                        .issuedAt(now.minusSeconds(3600)).expiresAt(now.minusSeconds(1800))
                        .claim("roles", List.of("USER")).build())).getTokenValue();

        mockMvc.perform(get("/api/v1/tasks").header("Authorization", bearer(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tasks_shouldReturn401ForTokenSignedWithAnotherIssuer() throws Exception {
        UserEntity ana = userRepository.save(newUser("ana@example.com", Role.USER));
        Instant now = Instant.now();
        String forged = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .issuer("evil-issuer").subject(String.valueOf(ana.getId()))
                        .issuedAt(now).expiresAt(now.plusSeconds(600)).build())).getTokenValue();

        mockMvc.perform(get("/api/v1/tasks").header("Authorization", bearer(forged)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- ownership isolation (PT-10 .. PT-13) ----------

    @Test
    void pt10_getTask_shouldReturn404ForTaskOfAnotherUser() throws Exception {
        String ana = registerAndLogin("Ana", "ana@example.com");
        String luis = registerAndLogin("Luis", "luis@example.com");
        Long luisTask = createTask(luis, "Luis secret");

        mockMvc.perform(get("/api/v1/tasks/" + luisTask).header("Authorization", bearer(ana)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Luis secret"))));
        mockMvc.perform(get("/api/v1/tasks/" + luisTask).header("Authorization", bearer(luis)))
                .andExpect(status().isOk());
    }

    @Test
    void pt11_updateTask_shouldReturn404AndLeaveDatabaseUntouched() throws Exception {
        String ana = registerAndLogin("Ana", "ana@example.com");
        String luis = registerAndLogin("Luis", "luis@example.com");
        Long luisTask = createTask(luis, "Luis original");

        mockMvc.perform(put("/api/v1/tasks/" + luisTask)
                        .header("Authorization", bearer(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hacked\",\"status\":\"COMPLETED\",\"priority\":\"LOW\"}"))
                .andExpect(status().isNotFound());

        TaskEntity unchanged = taskRepository.findById(luisTask).orElseThrow();
        assertThat(unchanged.getTitle()).isEqualTo("Luis original");
    }

    @Test
    void pt12_deleteTask_shouldReturn404AndKeepTheTask() throws Exception {
        String ana = registerAndLogin("Ana", "ana@example.com");
        String luis = registerAndLogin("Luis", "luis@example.com");
        Long luisTask = createTask(luis, "Luis keep");

        mockMvc.perform(delete("/api/v1/tasks/" + luisTask).header("Authorization", bearer(ana)))
                .andExpect(status().isNotFound());

        assertThat(taskRepository.existsById(luisTask)).isTrue();
    }

    @Test
    void pt13_createTask_shouldAssignTheAuthenticatedUserAsOwner() throws Exception {
        String ana = registerAndLogin("Ana", "ana@example.com");
        Long anaId = userRepository.findByEmail("ana@example.com").orElseThrow().getId();

        // A malicious client tries to impose a different owner in the body; it must be ignored.
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", bearer(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Mine\",\"ownerId\":999}"))
                .andExpect(status().isCreated());

        List<TaskEntity> stored = taskRepository.findAllByOwnerId(anaId);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getTitle()).isEqualTo("Mine");
        assertThat(taskRepository.count()).isEqualTo(1);
    }

    @Test
    void crud_shouldKeepWorkingForTheOwner() throws Exception {
        String ana = registerAndLogin("Ana", "ana@example.com");
        Long id = createTask(ana, "Entregar laboratorio");

        mockMvc.perform(put("/api/v1/tasks/" + id)
                        .header("Authorization", bearer(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Entregado\",\"status\":\"COMPLETED\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Entregado"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mockMvc.perform(delete("/api/v1/tasks/" + id).header("Authorization", bearer(ana)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/tasks/" + id).header("Authorization", bearer(ana)))
                .andExpect(status().isNotFound());
    }

    // ---------- profile and roles (PT-14, PT-15) ----------

    @Test
    void me_shouldReturnTheProfileOfTheAuthenticatedUserWithoutSecrets() throws Exception {
        String ana = registerAndLogin("Ana Pérez", "ana@example.com");

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana Pérez"))
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void pt14_adminSummary_shouldReturn403ForRegularUser() throws Exception {
        String ana = registerAndLogin("Ana", "ana@example.com");

        mockMvc.perform(get("/api/v1/admin/summary").header("Authorization", bearer(ana)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void adminSummary_shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/summary")).andExpect(status().isUnauthorized());
    }

    @Test
    void pt15_adminSummary_shouldReturnAggregatedMetricsForAdmin() throws Exception {
        String ana = registerAndLogin("Ana", "ana@example.com");
        createTask(ana, "Private task title");
        String admin = adminToken();

        mockMvc.perform(get("/api/v1/admin/summary").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(2))
                .andExpect(jsonPath("$.totalTasks").value(1))
                .andExpect(jsonPath("$.tasksByStatus.PENDING").value(1))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Private task title"))));
    }

    private UserEntity newUser(String email, Role role) {
        UserEntity user = new UserEntity();
        user.setName("Test");
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }
}
