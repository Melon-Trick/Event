package dev.vriege.eventfw.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Selects the exact channel of a Subscribe method. Method declarations override the listener class's default. Without
 * this annotation, the default channel is used. Channel selection is independent of event-type polymorphism.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Channel {
    /**
     * Returns the channel name; an empty value explicitly selects the default channel.
     *
     * @return exact channel name
     */
    String value();
}
