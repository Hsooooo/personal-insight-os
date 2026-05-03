package com.pios.spec;

import com.pios.domain.Activity;
import com.pios.dto.ActivityFilterRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ActivitySpecification {

    public static Specification<Activity> withFilter(Long userId, ActivityFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // User filter (always applied)
            predicates.add(cb.equal(root.get("user").get("id"), userId));

            if (filter == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            // Activity type
            if (filter.getActivityType() != null && !filter.getActivityType().isBlank()) {
                predicates.add(cb.equal(root.get("activityType"), filter.getActivityType()));
            }

            // User tag
            if (filter.getUserTag() != null) {
                if (filter.getUserTag().isEmpty()) {
                    predicates.add(cb.isNull(root.get("userTag")));
                } else {
                    predicates.add(cb.equal(root.get("userTag"), filter.getUserTag()));
                }
            }

            // Activity name (partial match, case-insensitive)
            if (filter.getActivityName() != null && !filter.getActivityName().isBlank()) {
                predicates.add(cb.like(
                    cb.lower(root.get("activityName")),
                    "%" + filter.getActivityName().toLowerCase() + "%"
                ));
            }

            // Start time range
            if (filter.getStartTimeFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    root.get("startTime"),
                    filter.getStartTimeFrom().atStartOfDay()
                ));
            }
            if (filter.getStartTimeTo() != null) {
                predicates.add(cb.lessThan(
                    root.get("startTime"),
                    filter.getStartTimeTo().plusDays(1).atStartOfDay()
                ));
            }

            // Distance range — only apply to activities that have distance data
            // (weight training entries have null distance, so they are naturally excluded)
            if (filter.getMinDistance() != null || filter.getMaxDistance() != null) {
                predicates.add(cb.isNotNull(root.get("distanceMeters")));
            }
            if (filter.getMinDistance() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    root.get("distanceMeters"),
                    filter.getMinDistance()
                ));
            }
            if (filter.getMaxDistance() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                    root.get("distanceMeters"),
                    filter.getMaxDistance()
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
