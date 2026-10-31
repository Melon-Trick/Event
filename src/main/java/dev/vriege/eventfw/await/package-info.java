/**
 * Fluent, cancellable waits for future events. Waits install no listener until explicitly started and release their
 * registration and deadline task on completion. They observe future in-process delivery and provide neither replay nor
 * durable storage. Closing the associated bus or scope cancels outstanding waits.
 */
package dev.vriege.eventfw.await;
