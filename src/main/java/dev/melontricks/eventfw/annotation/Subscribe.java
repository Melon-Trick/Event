package dev.melontricks.eventfw.annotation;

import dev.melontricks.eventfw.dispatch.EventPhase;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an instance method for declarative registration with an event bus.
 *
 * <p>A valid method returns {@code void} and has one of the following signatures:
 *
 * <ul>
 *   <li>{@code void method(MyEvent event)}
 *   <li>{@code void method(MyEvent event, EventContext<MyEvent> context)}
 * </ul>
 *
 * <p>The first parameter must implement {@link dev.melontricks.eventfw.event.Event}. The optional second parameter must
 * erase exactly to {@link dev.melontricks.eventfw.dispatch.EventContext}. Static methods, non-void methods, and every
 * other parameter shape are rejected before registration begins.
 *
 * <p>Private, package-private, protected, public, inherited, and interface methods are discovered. Method discovery is
 * deterministic. An overriding subclass method shadows the corresponding inherited method even when the override is not
 * annotated; private superclass methods are independent and remain eligible. Access to non-public methods is subject to
 * Java module reflective-access rules.
 *
 * <p>The listener object is used as the subscription owner. Closing the returned registration or calling
 * {@code EventBus.unregister(listener)} removes its subscriptions.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {
    /**
     * Selects dispatch order relative to other matching subscriptions.
     *
     * <p>Greater values execute first. Equal priorities are ordered by event-type specificity and then by registration
     * order.
     *
     * @return the numeric priority, defaulting to normal priority {@code 0}
     */
    int priority() default 0;

    /**
     * Controls whether this method remains eligible after a cancellable event has been cancelled.
     *
     * @return {@code true} to observe cancelled events; {@code false} to skip them
     */
    boolean receiveCancelledEvents() default false;

    /**
     * Restricts the method to the parameter's exact runtime class.
     *
     * <p>When disabled on a polymorphic bus, a method accepting a parent event class or interface also receives subtype
     * instances. This flag cannot enable polymorphic delivery when the entire bus uses exact matching.
     *
     * @return {@code true} to reject subtype instances
     */
    boolean exactTypeOnly() default false;

    /**
     * Restricts delivery to selected publication phases.
     *
     * <p>An empty array means all phases. Supplying one or more values means the method runs only when the publication
     * uses one of those phases.
     *
     * @return accepted phases, or an empty array for every phase
     */
    EventPhase[] phases() default {};

    /**
     * Makes the generated subscription single-use.
     *
     * <p>The subscription is atomically claimed and removed immediately before invocation. Concurrent publishers
     * therefore cannot invoke a single-use method more than once. A rejected or failing filter does not consume a
     * programmatic single-use subscription; annotated subscriptions have no filter.
     *
     * @return {@code true} for one successful invocation attempt at most
     */
    boolean once() default false;
}
