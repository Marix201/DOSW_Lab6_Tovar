package edu.eci.dosw.todo.service;

import edu.eci.dosw.todo.dto.TaskCreateRequest;
import edu.eci.dosw.todo.dto.TaskResponse;
import edu.eci.dosw.todo.dto.TaskUpdateRequest;

import java.util.List;

/**
 * Every operation is scoped to the owner, whose id always comes from the validated token and never
 * from the request body or URL. A task that does not exist or belongs to someone else is reported
 * as not found.
 */
public interface TaskService {

    List<TaskResponse> findAll(Long ownerId);

    TaskResponse findById(Long ownerId, Long id);

    TaskResponse create(Long ownerId, TaskCreateRequest request);

    TaskResponse update(Long ownerId, Long id, TaskUpdateRequest request);

    void delete(Long ownerId, Long id);
}
