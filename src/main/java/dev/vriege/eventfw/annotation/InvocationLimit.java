package dev.vriege.eventfw.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limits invocation attempts of a Subscribe method atomically across concurrent publishers. Filter rejections do not
 * consume attempts; failed handlers and rejected executor submissions do. Combined with once=true, the value must be 1.
 * The last claim removes the subscription before the handler starts.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface InvocationLimit {
    /**
     * Returns the positive invocation-attempt limit, validated before registration.
     *
     * @return maximum attempts
     */
    long value();
}
