package dev.vriege.eventfw.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Configures asynchronous-only execution of Subscribe methods. A method declaration replaces all class defaults.
 * Executor names resolve against the bus builder during registration; an unknown name rejects registration and rolls
 * back newly installed subscriptions. Policies have the same semantics as the execution DSL, including bounded queues
 * and timeout observation without interrupting user work. Class defaults apply even to inherited listener methods.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Execution {
    /**
     * Returns the configured executor name; empty means direct execution on the async dispatch thread.
     *
     * @return executor identity
     */
    String executor() default "";

    /**
     * Requests sequential invocations of each subscription across publications, waiting for actual work completion.
     *
     * @return whether to serialize all invocations
     */
    boolean sequential() default false;

    /**
     * Returns the maximum queued plus running invocations for each subscription.
     *
     * @return positive capacity, default 256
     */
    int capacity() default 256;

    /**
     * Returns the observed timeout including queue time; zero disables the deadline.
     *
     * @return non-negative timeout quantity
     */
    long timeout() default 0;

    /**
     * Returns the timeout unit.
     *
     * @return unit, defaulting to milliseconds
     */
    TimeUnit timeoutUnit() default TimeUnit.MILLISECONDS;
}
