package com.webcourse.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "course_materials")
public class CourseMaterial {

    @Id
    @GeneratedValue
    private Long id;

    private String filename;
    private String description;

    private String filePath;

    @ManyToOne
    private Course course;

    private Integer orderIndex;
}
