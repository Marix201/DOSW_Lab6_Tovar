package edu.eci.dosw.todo.repository;

import edu.eci.dosw.todo.entity.TaskEntity;
import edu.eci.dosw.todo.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, Long> {

    List<TaskEntity> findAllByOwnerId(Long ownerId);

    Optional<TaskEntity> findByIdAndOwnerId(Long id, Long ownerId);

    long countByStatus(TaskStatus status);
}
