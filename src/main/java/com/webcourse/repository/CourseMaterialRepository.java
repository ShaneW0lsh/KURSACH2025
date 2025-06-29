package com.webcourse.repository;

import com.webcourse.model.Course;
import com.webcourse.model.CourseMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseMaterialRepository extends JpaRepository<CourseMaterial, Long> {
    List<CourseMaterial> findByCourseOrderByOrderIndexAsc(Course course);
}
