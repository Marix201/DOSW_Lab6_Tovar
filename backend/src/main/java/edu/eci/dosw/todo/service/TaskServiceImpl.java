package edu.eci.dosw.todo.service;

import edu.eci.dosw.todo.dto.CategoryResponse;
import edu.eci.dosw.todo.dto.TaskCreateRequest;
import edu.eci.dosw.todo.dto.TaskResponse;
import edu.eci.dosw.todo.dto.TaskUpdateRequest;
import edu.eci.dosw.todo.entity.CategoryEntity;
import edu.eci.dosw.todo.entity.TaskEntity;
import edu.eci.dosw.todo.entity.TaskPriority;
import edu.eci.dosw.todo.entity.TaskStatus;
import edu.eci.dosw.todo.exception.CategoryNotFoundException;
import edu.eci.dosw.todo.exception.TaskNotFoundException;
import edu.eci.dosw.todo.repository.CategoryRepository;
import edu.eci.dosw.todo.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final CategoryRepository categoryRepository;

    public TaskServiceImpl(TaskRepository taskRepository, CategoryRepository categoryRepository) {
        this.taskRepository = taskRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> findAll() {
        return taskRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> findByCategory(Long categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new CategoryNotFoundException(categoryId);
        }
        return taskRepository.findByCategoryId(categoryId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> findWithoutCategory() {
        return taskRepository.findByCategoryIsNull().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponse findById(Long id) {
        return toResponse(getEntityOrThrow(id));
    }

    @Override
    @Transactional
    public TaskResponse create(TaskCreateRequest request) {
        TaskEntity task = new TaskEntity();
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setStatus(TaskStatus.PENDING);
        task.setPriority(request.priority() != null ? request.priority() : TaskPriority.MEDIUM);
        task.setDueDate(request.dueDate());
        task.setCategory(resolveCategory(request.categoryId()));
        task.setCreatedAt(LocalDateTime.now());
        return toResponse(taskRepository.save(task));
    }

    @Override
    @Transactional
    public TaskResponse update(Long id, TaskUpdateRequest request) {
        TaskEntity task = getEntityOrThrow(id);
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setStatus(request.status());
        task.setPriority(request.priority());
        task.setDueDate(request.dueDate());
        task.setCategory(resolveCategory(request.categoryId()));
        return toResponse(taskRepository.save(task));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!taskRepository.existsById(id)) {
            throw new TaskNotFoundException(id);
        }
        taskRepository.deleteById(id);
    }

    private TaskEntity getEntityOrThrow(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    private CategoryEntity resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    private TaskResponse toResponse(TaskEntity task) {
        CategoryEntity category = task.getCategory();
        CategoryResponse categoryResponse = category == null
                ? null
                : new CategoryResponse(category.getId(), category.getName(), category.getColor());
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                task.getCreatedAt(),
                categoryResponse
        );
    }
}
