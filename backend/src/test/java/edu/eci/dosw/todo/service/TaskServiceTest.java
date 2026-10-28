package edu.eci.dosw.todo.service;

import edu.eci.dosw.todo.dto.TaskCreateRequest;
import edu.eci.dosw.todo.dto.TaskResponse;
import edu.eci.dosw.todo.dto.TaskUpdateRequest;
import edu.eci.dosw.todo.entity.TaskEntity;
import edu.eci.dosw.todo.entity.TaskPriority;
import edu.eci.dosw.todo.entity.TaskStatus;
import edu.eci.dosw.todo.entity.UserEntity;
import edu.eci.dosw.todo.exception.TaskNotFoundException;
import edu.eci.dosw.todo.repository.TaskRepository;
import edu.eci.dosw.todo.repository.UserRepository;
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

    private static final Long OWNER_ID = 7L;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

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
    void findAll_shouldOnlyQueryTheOwnersTasks() {
        when(taskRepository.findAllByOwnerId(OWNER_ID))
                .thenReturn(List.of(buildTask(1L, "Task A"), buildTask(2L, "Task B")));

        List<TaskResponse> result = taskService.findAll(OWNER_ID);

        assertThat(result).extracting(TaskResponse::title).containsExactly("Task A", "Task B");
        verify(taskRepository, never()).findAll();
    }

    @Test
    void findById_shouldReturnTaskWhenOwned() {
        when(taskRepository.findByIdAndOwnerId(1L, OWNER_ID)).thenReturn(Optional.of(buildTask(1L, "Task A")));

        TaskResponse result = taskService.findById(OWNER_ID, 1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.title()).isEqualTo("Task A");
    }

    @Test
    void findById_shouldThrowNotFoundWhenMissingOrOwnedBySomeoneElse() {
        when(taskRepository.findByIdAndOwnerId(99L, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.findById(OWNER_ID, 99L))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessage("Task with id 99 was not found");
    }

    @Test
    void create_shouldAssignTheAuthenticatedOwner() {
        UserEntity owner = new UserEntity();
        owner.setId(OWNER_ID);
        when(userRepository.getReferenceById(OWNER_ID)).thenReturn(owner);
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> {
            TaskEntity saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        TaskResponse result = taskService.create(OWNER_ID, new TaskCreateRequest(
                "New task", "Some description", TaskPriority.HIGH, LocalDate.of(2026, 10, 1)));

        ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getOwner()).isSameAs(owner);
        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(result.createdAt()).isNotNull();
    }

    @Test
    void create_shouldAssignDefaultStatusAndPriority() {
        when(userRepository.getReferenceById(OWNER_ID)).thenReturn(new UserEntity());
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse result = taskService.create(OWNER_ID, new TaskCreateRequest("Default task", null, null, null));

        assertThat(result.status()).isEqualTo(TaskStatus.PENDING);
        assertThat(result.priority()).isEqualTo(TaskPriority.MEDIUM);
    }

    @Test
    void update_shouldModifyOwnedTask() {
        TaskEntity existing = buildTask(1L, "Old title");
        when(taskRepository.findByIdAndOwnerId(1L, OWNER_ID)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse result = taskService.update(OWNER_ID, 1L, new TaskUpdateRequest(
                "New title", "New description", TaskStatus.COMPLETED, TaskPriority.LOW, null));

        assertThat(result.title()).isEqualTo("New title");
        assertThat(result.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.priority()).isEqualTo(TaskPriority.LOW);
    }

    @Test
    void update_shouldNotTouchTasksOfOtherUsers() {
        when(taskRepository.findByIdAndOwnerId(25L, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.update(OWNER_ID, 25L, new TaskUpdateRequest(
                "Hack", null, TaskStatus.PENDING, TaskPriority.LOW, null)))
                .isInstanceOf(TaskNotFoundException.class);

        verify(taskRepository, never()).save(any(TaskEntity.class));
    }

    @Test
    void delete_shouldRemoveOwnedTask() {
        TaskEntity existing = buildTask(1L, "Task A");
        when(taskRepository.findByIdAndOwnerId(1L, OWNER_ID)).thenReturn(Optional.of(existing));

        taskService.delete(OWNER_ID, 1L);

        verify(taskRepository).delete(existing);
    }

    @Test
    void delete_shouldNotRemoveTasksOfOtherUsers() {
        when(taskRepository.findByIdAndOwnerId(25L, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.delete(OWNER_ID, 25L)).isInstanceOf(TaskNotFoundException.class);

        verify(taskRepository, never()).delete(any(TaskEntity.class));
    }
}
