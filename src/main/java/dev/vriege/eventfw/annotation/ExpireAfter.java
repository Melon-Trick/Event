package dev.vriege.eventfw.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Removes a Subscribe method's subscription after the specified lifetime measured from installation. Closing the
 * subscription cancels its timer; expiration does not interrupt already claimed work.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ExpireAfter {
    /**
     * Returns the positive lifetime quantity, which must fit in nanoseconds after conversion.
     *
     * @return lifetime quantity
     */
    long value();

    /**
     * Returns the lifetime unit.
     *
     * @return unit, defaulting to milliseconds
     */
    TimeUnit unit() default TimeUnit.MILLISECONDS;
}
