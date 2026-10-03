package com.coursecompass.item;

import com.coursecompass.course.Course;
import com.coursecompass.course.CourseService;
import com.coursecompass.item.GradedItemDtos.GradedItemRequest;
import com.coursecompass.item.GradedItemDtos.GradedItemResponse;
import com.coursecompass.rating.SelfRatingService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GradedItemService {

    private final GradedItemRepository items;
    private final CourseService courseService;
    private final SelfRatingService ratingService;

    public GradedItemService(
            GradedItemRepository items, CourseService courseService, SelfRatingService ratingService) {
        this.items = items;
        this.courseService = courseService;
        this.ratingService = ratingService;
    }

    @Transactional(readOnly = true)
    public List<GradedItemResponse> list(Long userId, Long courseId) {
        courseService.getOwned(userId, courseId);
        Map<Long, Integer> latest = ratingService.latestRatingsForCourse(courseId);
        return items.findByCourseIdOrderByDueDateAscIdAsc(courseId).stream()
                .map(item -> GradedItemResponse.from(item, latest.get(item.getId())))
                .toList();
    }

    @Transactional
    public GradedItemResponse create(Long userId, Long courseId, GradedItemRequest request) {
        Course course = courseService.getOwned(userId, courseId);
        GradedItem item = items.save(new GradedItem(
                course,
                request.name().trim(),
                request.category(),
                request.weight(),
                request.pointsPossible(),
                request.pointsEarned(),
                request.dueDate(),
                request.graded()));
        return GradedItemResponse.from(item, null);
    }
}
