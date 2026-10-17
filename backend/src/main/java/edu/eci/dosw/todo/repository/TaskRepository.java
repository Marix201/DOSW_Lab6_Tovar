package edu.eci.dosw.todo.repository;

import edu.eci.dosw.todo.entity.TaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, Long> {

    List<TaskEntity> findByCategoryId(Long categoryId);

    List<TaskEntity> findByCategoryIsNull();

    boolean existsByCategoryId(Long categoryId);
}
