package com.miadvisor.course;

import com.miadvisor.common.NotFoundException;
import com.miadvisor.course.CourseDtos.CourseRequest;
import com.miadvisor.course.CourseDtos.CourseResponse;
import com.miadvisor.user.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseService {

    private final CourseRepository courses;
    private final UserRepository users;

    public CourseService(CourseRepository courses, UserRepository users) {
        this.courses = courses;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> list(Long userId) {
        return courses.findByUserIdOrderByIdAsc(userId).stream().map(CourseResponse::from).toList();
    }

    @Transactional
    public CourseResponse create(Long userId, CourseRequest request) {
        Course course = new Course(
                users.getReferenceById(userId),
                request.courseCode().trim(),
                request.courseName().trim(),
                request.creditHours(),
                request.semester());
        return CourseResponse.from(courses.save(course));
    }

    /** Returns the course if it belongs to the user; otherwise 404 (never reveal other users' data). */
    @Transactional(readOnly = true)
    public Course getOwned(Long userId, Long courseId) {
        return courses.findByIdAndUserId(courseId, userId)
                .orElseThrow(() -> new NotFoundException("Course " + courseId + " not found"));
    }
}
