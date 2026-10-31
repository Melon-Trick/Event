package dev.vriege.eventfw.annotation;

import dev.vriege.eventfw.listener.EventFilter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Adds contextual filters to a Subscribe method, evaluated in array order with short-circuit conjunction. Each filter
 * must accept the method's event type and have an accessible no-argument constructor. A new filter instance is created
 * for each registration, not each invocation. Filters may be called concurrently and must manage their own state.
 * Constructor failures reject registration and roll back newly installed subscriptions.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FilterWith {
    /**
     * Returns filter implementation classes in evaluation order. Empty means no additional filtering.
     *
     * @return filter classes
     */
    Class<? extends EventFilter<?>>[] value();
}
