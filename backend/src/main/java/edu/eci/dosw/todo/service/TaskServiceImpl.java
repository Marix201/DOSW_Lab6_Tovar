package edu.eci.dosw.todo.service;

import edu.eci.dosw.todo.dto.TaskCreateRequest;
import edu.eci.dosw.todo.dto.TaskResponse;
import edu.eci.dosw.todo.dto.TaskUpdateRequest;
import edu.eci.dosw.todo.entity.TaskEntity;
import edu.eci.dosw.todo.entity.TaskPriority;
import edu.eci.dosw.todo.entity.TaskStatus;
import edu.eci.dosw.todo.exception.TaskNotFoundException;
import edu.eci.dosw.todo.repository.TaskRepository;
import edu.eci.dosw.todo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public TaskServiceImpl(TaskRepository taskRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> findAll(Long ownerId) {
        return taskRepository.findAllByOwnerId(ownerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponse findById(Long ownerId, Long id) {
        return toResponse(getOwnedOrThrow(ownerId, id));
    }

    @Override
    @Transactional
    public TaskResponse create(Long ownerId, TaskCreateRequest request) {
        TaskEntity task = new TaskEntity();
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setStatus(TaskStatus.PENDING);
        task.setPriority(request.priority() != null ? request.priority() : TaskPriority.MEDIUM);
        task.setDueDate(request.dueDate());
        task.setCreatedAt(LocalDateTime.now());
        task.setOwner(userRepository.getReferenceById(ownerId));
        return toResponse(taskRepository.save(task));
    }

    @Override
    @Transactional
    public TaskResponse update(Long ownerId, Long id, TaskUpdateRequest request) {
        TaskEntity task = getOwnedOrThrow(ownerId, id);
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setStatus(request.status());
        task.setPriority(request.priority());
        task.setDueDate(request.dueDate());
        return toResponse(taskRepository.save(task));
    }

    @Override
    @Transactional
    public void delete(Long ownerId, Long id) {
        taskRepository.delete(getOwnedOrThrow(ownerId, id));
    }

    private TaskEntity getOwnedOrThrow(Long ownerId, Long id) {
        return taskRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    private TaskResponse toResponse(TaskEntity task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                task.getCreatedAt()
        );
    }
}
