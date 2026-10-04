package com.coursecompass.item;

import com.coursecompass.course.Course;
import com.coursecompass.course.CourseService;
import com.coursecompass.item.GradedItemDtos.GradedItemRequest;
import com.coursecompass.item.GradedItemDtos.GradedItemResponse;
import com.coursecompass.common.NotFoundException;
import com.coursecompass.rating.SelfRatingRepository;
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
    private final SelfRatingRepository ratings;

    public GradedItemService(
            GradedItemRepository items,
            CourseService courseService,
            SelfRatingService ratingService,
            SelfRatingRepository ratings) {
        this.items = items;
        this.courseService = courseService;
        this.ratingService = ratingService;
        this.ratings = ratings;
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

    @Transactional
    public GradedItemResponse update(Long userId, Long itemId, GradedItemRequest request) {
        GradedItem item = owned(userId, itemId);
        item.update(
                request.name().trim(),
                request.category(),
                request.weight(),
                request.pointsPossible(),
                request.graded() ? request.pointsEarned() : null,
                request.dueDate(),
                request.graded());
        Integer latest = ratings.findFirstByGradedItemIdOrderByCreatedAtDescIdDesc(itemId)
                .map(r -> r.getRating())
                .orElse(null);
        return GradedItemResponse.from(item, latest);
    }

    /** Deletes the item and its self-ratings. */
    @Transactional
    public void delete(Long userId, Long itemId) {
        GradedItem item = owned(userId, itemId);
        ratings.deleteByGradedItemId(itemId);
        items.delete(item);
    }

    private GradedItem owned(Long userId, Long itemId) {
        return items.findById(itemId)
                .filter(i -> i.getCourse().getUser().getId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Item " + itemId + " not found"));
    }
}
