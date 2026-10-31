/**
 * Borrowed-executor policies for ordered asynchronous subscriptions, including per-key serialization, bounded
 * admission, and observation deadlines. Policies do not create or shut down executors and do not promise interruption
 * of user work. Subscription builders capture immutable policies while each installed subscription owns independent
 * queue state.
 */
package dev.vriege.eventfw.execution;
