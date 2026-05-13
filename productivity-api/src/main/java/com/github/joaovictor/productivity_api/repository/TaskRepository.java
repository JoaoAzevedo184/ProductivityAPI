package com.github.joaovictor.productivity_api.repository;

import com.github.joaovictor.productivity_api.domain.Task;
import com.github.joaovictor.productivity_api.domain.enums.Priority;
import com.github.joaovictor.productivity_api.domain.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

  Page<Task> findByStatus(TaskStatus status, Pageable pageable);

  Page<Task> findByPriority(Priority priority, Pageable pageable);

  Page<Task> findByTitleContainingIgnoreCase(String title, Pageable pageable);
}
