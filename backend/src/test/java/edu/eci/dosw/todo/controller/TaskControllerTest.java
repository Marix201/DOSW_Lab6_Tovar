package edu.eci.dosw.todo.controller;

import edu.eci.dosw.todo.dto.TaskCreateRequest;
import edu.eci.dosw.todo.dto.TaskResponse;
import edu.eci.dosw.todo.dto.TaskUpdateRequest;
import edu.eci.dosw.todo.entity.TaskPriority;
import edu.eci.dosw.todo.entity.TaskStatus;
import edu.eci.dosw.todo.exception.TaskNotFoundException;
import edu.eci.dosw.todo.security.RestAccessDeniedHandler;
import edu.eci.dosw.todo.security.RestAuthenticationEntryPoint;
import edu.eci.dosw.todo.security.SecurityConfig;
import edu.eci.dosw.todo.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class TaskControllerTest {

    private static final Long USER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TaskService taskService;

    // Required by the resource-server configuration; authentication is simulated with jwt() below.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private RequestPostProcessor asUser() {
        return jwt().jwt(token -> token.subject(String.valueOf(USER_ID)))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private TaskResponse buildResponse(Long id, String title) {
        return new TaskResponse(
                id,
                title,
                "Description of " + title,
                TaskStatus.PENDING,
                TaskPriority.MEDIUM,
                LocalDate.of(2026, 9, 30),
                LocalDateTime.of(2026, 9, 28, 8, 0));
    }

    @Test
    void findAll_shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void findAll_shouldReturnOnlyTheTasksOfTheTokenSubject() throws Exception {
        when(taskService.findAll(USER_ID)).thenReturn(List.of(buildResponse(1L, "Task A"), buildResponse(2L, "Task B")));

        mockMvc.perform(get("/api/v1/tasks").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Task A"))
                .andExpect(jsonPath("$[1].title").value("Task B"));
    }

    @Test
    void findById_shouldReturn200WhenTaskExists() throws Exception {
        when(taskService.findById(USER_ID, 1L)).thenReturn(buildResponse(1L, "Task A"));

        mockMvc.perform(get("/api/v1/tasks/1").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"));
    }

    @Test
    void findById_shouldReturn404WhenTaskDoesNotExistOrIsNotOwned() throws Exception {
        when(taskService.findById(USER_ID, 99L)).thenThrow(new TaskNotFoundException(99L));

        mockMvc.perform(get("/api/v1/tasks/99").with(asUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Task with id 99 was not found"));
    }

    @Test
    void findById_shouldReturn400WhenIdIsNotNumeric() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/abc").with(asUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_shouldReturn201AndUseTheTokenSubjectAsOwner() throws Exception {
        TaskCreateRequest request = new TaskCreateRequest(
                "New task", "Some description", TaskPriority.HIGH, LocalDate.of(2026, 10, 1));
        when(taskService.create(eq(USER_ID), any(TaskCreateRequest.class))).thenReturn(buildResponse(10L, "New task"));

        mockMvc.perform(post("/api/v1/tasks").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void create_shouldIgnoreAnOwnerSentByTheClient() throws Exception {
        when(taskService.create(eq(USER_ID), any(TaskCreateRequest.class))).thenReturn(buildResponse(10L, "New task"));

        mockMvc.perform(post("/api/v1/tasks").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New task\",\"ownerId\":999,\"owner_id\":999}"))
                .andExpect(status().isCreated());

        verify(taskService).create(eq(USER_ID), any(TaskCreateRequest.class));
    }

    @Test
    void create_shouldReturn400WhenTitleIsBlank() throws Exception {
        TaskCreateRequest request = new TaskCreateRequest("", "Some description", TaskPriority.HIGH, null);

        mockMvc.perform(post("/api/v1/tasks").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("title: Title is required"));
    }

    @Test
    void update_shouldReturn200WhenTaskIsOwned() throws Exception {
        TaskUpdateRequest request = new TaskUpdateRequest(
                "Updated title", "Updated description", TaskStatus.IN_PROGRESS, TaskPriority.LOW, null);
        TaskResponse updated = new TaskResponse(
                1L, "Updated title", "Updated description", TaskStatus.IN_PROGRESS,
                TaskPriority.LOW, null, LocalDateTime.of(2026, 9, 28, 8, 0));
        when(taskService.update(eq(USER_ID), eq(1L), any(TaskUpdateRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/tasks/1").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void update_shouldReturn404WhenTaskIsNotOwned() throws Exception {
        TaskUpdateRequest request = new TaskUpdateRequest("Title", null, TaskStatus.PENDING, TaskPriority.MEDIUM, null);
        when(taskService.update(eq(USER_ID), eq(25L), any(TaskUpdateRequest.class)))
                .thenThrow(new TaskNotFoundException(25L));

        mockMvc.perform(put("/api/v1/tasks/25").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_shouldReturn204WhenTaskIsOwned() throws Exception {
        mockMvc.perform(delete("/api/v1/tasks/1").with(asUser()))
                .andExpect(status().isNoContent());

        verify(taskService).delete(USER_ID, 1L);
    }

    @Test
    void delete_shouldReturn404WhenTaskIsNotOwned() throws Exception {
        doThrow(new TaskNotFoundException(25L)).when(taskService).delete(USER_ID, 25L);

        mockMvc.perform(delete("/api/v1/tasks/25").with(asUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
