package com.miadvisor.course;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CourseDtos {

    private CourseDtos() {}

    public record CourseRequest(
            @NotBlank @Size(max = 20) String courseCode,
            @NotBlank @Size(max = 200) String courseName,
            @Min(0) @Max(12) Integer creditHours,
            @Size(max = 40) String semester) {}

    public record CourseResponse(
            Long id, String courseCode, String courseName, Integer creditHours, String semester) {

        static CourseResponse from(Course course) {
            return new CourseResponse(
                    course.getId(),
                    course.getCourseCode(),
                    course.getCourseName(),
                    course.getCreditHours(),
                    course.getSemester());
        }
    }
}
