package com.github.joaovictor.productivity_api.repository;

import com.github.joaovictor.productivity_api.domain.Task;
import com.github.joaovictor.productivity_api.domain.enums.Priority;
import com.github.joaovictor.productivity_api.domain.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByStatus(TaskStatus status);

    List<Task> findByPriority(Priority priority);

    List<Task> findByTitleContainingIgnoreCase(String title);
}