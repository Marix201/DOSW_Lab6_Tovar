package edu.eci.dosw.todo.service;

import edu.eci.dosw.todo.dto.TaskCreateRequest;
import edu.eci.dosw.todo.dto.TaskResponse;
import edu.eci.dosw.todo.dto.TaskUpdateRequest;
import edu.eci.dosw.todo.entity.TaskEntity;
import edu.eci.dosw.todo.entity.TaskPriority;
import edu.eci.dosw.todo.entity.TaskStatus;
import edu.eci.dosw.todo.exception.TaskNotFoundException;
import edu.eci.dosw.todo.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskServiceImpl taskService;

    private TaskEntity buildTask(Long id, String title) {
        TaskEntity task = new TaskEntity();
        task.setId(id);
        task.setTitle(title);
        task.setDescription("Description of " + title);
        task.setStatus(TaskStatus.PENDING);
        task.setPriority(TaskPriority.MEDIUM);
        task.setDueDate(LocalDate.of(2026, 9, 30));
        task.setCreatedAt(LocalDateTime.of(2026, 9, 28, 8, 0));
        return task;
    }

    @Test
    void findAll_shouldReturnTasks() {
        when(taskRepository.findAll()).thenReturn(List.of(buildTask(1L, "Task A"), buildTask(2L, "Task B")));

        List<TaskResponse> result = taskService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TaskResponse::title).containsExactly("Task A", "Task B");
    }

    @Test
    void findById_shouldReturnTaskWhenExists() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(buildTask(1L, "Task A")));

        TaskResponse result = taskService.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.title()).isEqualTo("Task A");
    }

    @Test
    void findById_shouldThrowExceptionWhenTaskDoesNotExist() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.findById(99L))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessage("Task with id 99 was not found");
    }

    @Test
    void create_shouldCreateTask() {
        TaskCreateRequest request = new TaskCreateRequest(
                "New task", "Some description", TaskPriority.HIGH, LocalDate.of(2026, 10, 1));
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> {
            TaskEntity saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        TaskResponse result = taskService.create(request);

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.title()).isEqualTo("New task");
        assertThat(result.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(result.dueDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(result.createdAt()).isNotNull();
    }

    @Test
    void create_shouldAssignDefaultStatus() {
        TaskCreateRequest request = new TaskCreateRequest("Default task", null, null, null);
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse result = taskService.create(request);

        ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(result.status()).isEqualTo(TaskStatus.PENDING);
        assertThat(result.priority()).isEqualTo(TaskPriority.MEDIUM);
    }

    @Test
    void update_shouldUpdateExistingTask() {
        TaskEntity existing = buildTask(1L, "Old title");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TaskUpdateRequest request = new TaskUpdateRequest(
                "New title", "New description", TaskStatus.IN_PROGRESS, TaskPriority.LOW, LocalDate.of(2026, 11, 5));

        TaskResponse result = taskService.update(1L, request);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.title()).isEqualTo("New title");
        assertThat(result.description()).isEqualTo("New description");
        assertThat(result.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(result.priority()).isEqualTo(TaskPriority.LOW);
        assertThat(result.dueDate()).isEqualTo(LocalDate.of(2026, 11, 5));
    }

    @Test
    void update_shouldThrowExceptionWhenTaskDoesNotExist() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());
        TaskUpdateRequest request = new TaskUpdateRequest(
                "Title", null, TaskStatus.PENDING, TaskPriority.MEDIUM, null);

        assertThatThrownBy(() -> taskService.update(99L, request))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessage("Task with id 99 was not found");
        verify(taskRepository, never()).save(any(TaskEntity.class));
    }

    @Test
    void delete_shouldDeleteExistingTask() {
        when(taskRepository.existsById(1L)).thenReturn(true);

        taskService.delete(1L);

        verify(taskRepository).deleteById(1L);
    }

    @Test
    void delete_shouldThrowExceptionWhenTaskDoesNotExist() {
        when(taskRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> taskService.delete(99L))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessage("Task with id 99 was not found");
        verify(taskRepository, never()).deleteById(any());
    }
}
