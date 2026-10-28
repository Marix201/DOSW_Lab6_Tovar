package edu.eci.dosw.todo.repository;

import edu.eci.dosw.todo.entity.Role;
import edu.eci.dosw.todo.entity.TaskEntity;
import edu.eci.dosw.todo.entity.TaskPriority;
import edu.eci.dosw.todo.entity.TaskStatus;
import edu.eci.dosw.todo.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Persistence-level checks: ownership queries, unique email and mandatory owner (rolled back after each test). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskRepositoryOwnershipTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    private UserEntity ana;
    private UserEntity luis;

    @BeforeEach
    void setUp() {
        ana = userRepository.save(newUser("Ana", "ana@example.com"));
        luis = userRepository.save(newUser("Luis", "luis@example.com"));
    }

    private UserEntity newUser(String name, String email) {
        UserEntity user = new UserEntity();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyzABCDE");
        user.setRole(Role.USER);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    private TaskEntity saveTask(String title, UserEntity owner) {
        TaskEntity task = new TaskEntity();
        task.setTitle(title);
        task.setStatus(TaskStatus.PENDING);
        task.setPriority(TaskPriority.MEDIUM);
        task.setCreatedAt(LocalDateTime.now());
        task.setOwner(owner);
        return taskRepository.saveAndFlush(task);
    }

    @Test
    void findAllByOwnerId_shouldReturnOnlyTheOwnersTasks() {
        saveTask("Ana 1", ana);
        saveTask("Ana 2", ana);
        saveTask("Luis 1", luis);

        List<TaskEntity> anaTasks = taskRepository.findAllByOwnerId(ana.getId());

        assertThat(anaTasks).extracting(TaskEntity::getTitle).containsExactlyInAnyOrder("Ana 1", "Ana 2");
        assertThat(taskRepository.findAllByOwnerId(luis.getId())).hasSize(1);
    }

    @Test
    void findByIdAndOwnerId_shouldNotExposeTasksOfOtherUsers() {
        TaskEntity luisTask = saveTask("Luis 1", luis);

        assertThat(taskRepository.findByIdAndOwnerId(luisTask.getId(), ana.getId())).isEmpty();
        assertThat(taskRepository.findByIdAndOwnerId(luisTask.getId(), luis.getId())).isPresent();
    }

    @Test
    void countByStatus_shouldCountAcrossUsers() {
        saveTask("Ana 1", ana);
        saveTask("Luis 1", luis);

        assertThat(taskRepository.countByStatus(TaskStatus.PENDING)).isEqualTo(2);
        assertThat(taskRepository.countByStatus(TaskStatus.COMPLETED)).isZero();
    }

    @Test
    void user_shouldRequireUniqueEmail() {
        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("Another Ana", "ana@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void task_shouldRequireAnOwner() {
        TaskEntity orphan = new TaskEntity();
        orphan.setTitle("Orphan");
        orphan.setStatus(TaskStatus.PENDING);
        orphan.setPriority(TaskPriority.MEDIUM);
        orphan.setCreatedAt(LocalDateTime.now());

        assertThatThrownBy(() -> taskRepository.saveAndFlush(orphan)).isInstanceOf(Exception.class);
    }
}
