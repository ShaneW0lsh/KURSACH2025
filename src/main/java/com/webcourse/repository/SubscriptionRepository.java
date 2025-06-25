package com.webcourse.repository;

import com.webcourse.model.Course;
import com.webcourse.model.Subscription;
import com.webcourse.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    boolean existsByUserAndCourse(User user, Course course);
    void deleteByUserAndCourse(User user, Course course);
    List<Subscription> findByUser(User user);
}