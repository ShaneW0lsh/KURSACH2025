package com.webcourse.controller;

import com.webcourse.model.Course;
import com.webcourse.model.CourseMaterial;
import com.webcourse.model.Review;
import com.webcourse.model.Subscription;
import com.webcourse.model.User;
import com.webcourse.repository.CourseMaterialRepository;
import com.webcourse.repository.CourseRepository;
import com.webcourse.repository.ReviewRepository;
import com.webcourse.repository.SubscriptionRepository;
import com.webcourse.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/courses")
public class CourseController {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CourseMaterialRepository courseMaterialRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ReviewRepository reviewRepository;

    @GetMapping("/{id}")
    public String courseDetails(@PathVariable Long id, Model model, Principal principal) {
        User user = userRepository.findByUsername(principal.getName());

        Course course = courseRepository.findById(id).orElseThrow();
        model.addAttribute("course", course);

        boolean isAuthor = user.getUsername().equals(course.getAuthor().getUsername());
        boolean isAdmin = user.getRole().name().equals("ROLE_ADMIN");
        boolean isSubscribed = subscriptionRepository.existsByUserAndCourse(user, course);

        if (!isAuthor && !isAdmin && !isSubscribed) {
            return "redirect:/error/403";
        }

        List<CourseMaterial> materials = courseMaterialRepository.findByCourseOrderByOrderIndexAsc(course);
        model.addAttribute("materials", materials);

        model.addAttribute("reviews", reviewRepository.findByCourseOrderByCreatedAtDesc(course));
        model.addAttribute("newReview", new Review());

        return "course-details";
    }

    @PostMapping("/{id}/reviews")
    public String submitReview(@PathVariable Long id,
                               @ModelAttribute("newReview") Review review,
                               Principal principal) {
        Course course = courseRepository.findById(id).orElseThrow();
        User user = userRepository.findByUsername(principal.getName());

        review.setCourse(course);
        review.setUser(user);
        review.setCreatedAt(LocalDateTime.now());

        reviewRepository.save(review);

        return "redirect:/courses/" + id;
    }

    @GetMapping
    public String listCourses(Model model, Principal principal) {
        model.addAttribute("courses", courseRepository.findAll());
        if (principal != null) {
            User user = userRepository.findByUsername(principal.getName());
            List<Subscription> subs = subscriptionRepository.findByUser(user);
            Set<Long> subscribedCourseIds = subs.stream()
                    .map(s -> s.getCourse().getId())
                    .collect(Collectors.toSet());
            model.addAttribute("subscribedIds", subscribedCourseIds);
        }
        return "courses";
    }

    @GetMapping("/create")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public String createForm(Model model) {
        model.addAttribute("course", new Course());
        return "course-form";
    }

    @PostMapping("/create")
    public String createCourse(@ModelAttribute Course course,
                               @RequestParam("files") List<MultipartFile> files,
                               Principal principal) throws IOException {

        User teacher = userRepository.findByUsername(principal.getName());
        course.setAuthor(teacher);
        courseRepository.save(course);

        int index = 0;
        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
                Path filePath = Paths.get("uploads/" + filename);
                Files.createDirectories(filePath.getParent());
                Files.write(filePath, file.getBytes());

                CourseMaterial material = new CourseMaterial();
                material.setFilename(file.getOriginalFilename());
                material.setFilePath(filePath.toString());
                material.setOrderIndex(index++);
                material.setCourse(course);

                courseMaterialRepository.save(material);
            }
        }

        return "redirect:/courses";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public String editCourseForm(@PathVariable Long id, Model model, Principal principal) {
        Course course = courseRepository.findById(id).orElseThrow();

        if (!principal.getName().equals(course.getAuthor().getUsername())) {
            return "redirect:/courses";
        }

        model.addAttribute("course", course);

        return "edit-course";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Transactional
    public String updateCourse(@PathVariable Long id,
                               @ModelAttribute("course") Course updatedCourse,
                               @RequestParam(value = "files", required = false) List<MultipartFile> files,
                               Principal principal) throws IOException {
        Course course = courseRepository.findById(id).orElseThrow();

        if (!principal.getName().equals(course.getAuthor().getUsername())) {
            return "redirect:/courses";
        }

        course.setTitle(updatedCourse.getTitle());
        course.setDescription(updatedCourse.getDescription());
        course.setSubject(updatedCourse.getSubject());
        courseRepository.save(course);

        return "redirect:/courses/" + id;
    }

    @PostMapping("/materials/{id}/delete")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Transactional
    public String deleteMaterial(@PathVariable Long id, Principal principal) {
        CourseMaterial material = courseMaterialRepository.findById(id).orElseThrow();
        Course course = material.getCourse();

        if (!principal.getName().equals(course.getAuthor().getUsername())) {
            return "redirect:/courses";
        }

        try {
            Files.deleteIfExists(Paths.get(material.getFilePath()));
        } catch (IOException e) {
            // Optional: log error
        }

        courseMaterialRepository.delete(material);

        // Optional: reset orderIndex values
        List<CourseMaterial> remaining = courseMaterialRepository.findByCourseOrderByOrderIndexAsc(course);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setOrderIndex(i);
        }
        courseMaterialRepository.saveAll(remaining);

        return "redirect:/courses/" + course.getId() + "/edit";
    }

    @GetMapping("/materials/{id}/download")
    public ResponseEntity<UrlResource> downloadMaterial(@PathVariable Long id) throws IOException {
        CourseMaterial material = courseMaterialRepository.findById(id).orElseThrow();
        Path path = Paths.get(material.getFilePath());
        UrlResource resource = new UrlResource(path.toUri());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + material.getFilename() + "\"")
                .body(resource);
    }

    @GetMapping("/delete/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public String delete(@PathVariable Long id, Principal principal) {
        Course course = courseRepository.findById(id).orElseThrow();
        if (principal.getName().equals(course.getAuthor().getUsername())) {
            courseRepository.delete(course);
        }
        return "redirect:/courses";
    }

    @PostMapping("/{id}/subscribe")
    public String subscribe(@PathVariable Long id, Principal principal) {
        User user = userRepository.findByUsername(principal.getName());
        Course course = courseRepository.findById(id).orElseThrow();

        boolean alreadySubscribed = subscriptionRepository.existsByUserAndCourse(user, course);
        if (!alreadySubscribed) {
            Subscription sub = new Subscription();
            sub.setUser(user);
            sub.setCourse(course);
            subscriptionRepository.save(sub);
        }

        return "redirect:/courses";
    }

    @GetMapping("/my-courses")
    public String myCourses(Model model, Principal principal) {
        User user = userRepository.findByUsername(principal.getName());
        List<Subscription> subscriptions = subscriptionRepository.findByUser(user);
        model.addAttribute("subscriptions", subscriptions);
        return "my-courses";
    }

    @PostMapping("/{id}/unsubscribe")
    @Transactional
    public String unsubscribe(@PathVariable Long id, Principal principal) {
        User user = userRepository.findByUsername(principal.getName());
        Course course = courseRepository.findById(id).orElseThrow();

        if (subscriptionRepository.existsByUserAndCourse(user, course)) {
            subscriptionRepository.deleteByUserAndCourse(user, course);
        }

        return "redirect:/courses/my-courses";
    }
}
