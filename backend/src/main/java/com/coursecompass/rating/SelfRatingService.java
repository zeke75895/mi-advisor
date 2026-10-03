package com.coursecompass.rating;

import com.coursecompass.common.NotFoundException;
import com.coursecompass.item.GradedItem;
import com.coursecompass.item.GradedItemRepository;
import com.coursecompass.rating.SelfRatingDtos.RatingResponse;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SelfRatingService {

    private final SelfRatingRepository ratings;
    private final GradedItemRepository items;

    public SelfRatingService(SelfRatingRepository ratings, GradedItemRepository items) {
        this.ratings = ratings;
        this.items = items;
    }

    @Transactional
    public RatingResponse rate(Long userId, Long itemId, int rating) {
        GradedItem item = items.findById(itemId)
                .filter(i -> i.getCourse().getUser().getId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Item " + itemId + " not found"));
        SelfRating saved = ratings.save(new SelfRating(item, rating));
        return new RatingResponse(saved.getId(), item.getId(), saved.getRating(), saved.getCreatedAt());
    }

    /** Latest rating per graded item in the course, keyed by item id. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> latestRatingsForCourse(Long courseId) {
        Map<Long, Integer> latest = new HashMap<>();
        for (SelfRating r : ratings.findByGradedItemCourseIdOrderByCreatedAtAscIdAsc(courseId)) {
            latest.put(r.getGradedItem().getId(), r.getRating()); // later rows overwrite earlier ones
        }
        return latest;
    }
}
