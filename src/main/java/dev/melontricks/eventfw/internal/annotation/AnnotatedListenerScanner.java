package dev.melontricks.eventfw.internal.annotation;

import dev.melontricks.eventfw.annotation.InvalidListenerException;
import dev.melontricks.eventfw.annotation.Subscribe;
import dev.melontricks.eventfw.dispatch.EventContext;
import dev.melontricks.eventfw.dispatch.EventPhase;
import dev.melontricks.eventfw.event.Event;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AnnotatedListenerScanner {
    private static final Comparator<Method> METHOD_ORDER =
            Comparator.comparing(Method::getName).thenComparing(Method::toGenericString);
    private static final ClassValue<List<AnnotatedMethod>> CACHE = new ClassValue<>() {
        @Override
        protected List<AnnotatedMethod> computeValue(Class<?> type) {
            return scan(type);
        }
    };

    private AnnotatedListenerScanner() {}

    public static List<AnnotatedMethod> methods(Class<?> listenerType) {
        return CACHE.get(listenerType);
    }

    private static List<AnnotatedMethod> scan(Class<?> listenerType) {
        List<AnnotatedMethod> methods = new ArrayList<>();
        Set<MethodSignature> shadowedMethods = new HashSet<>();
        Set<Class<?>> visitedInterfaces = new HashSet<>();
        Class<?> current = listenerType;
        while (current != null && current != Object.class) {
            scanDeclaredMethods(current, shadowedMethods, methods);
            Class<?>[] interfaces = current.getInterfaces();
            Arrays.sort(interfaces, Comparator.comparing(Class::getName));
            for (Class<?> interfaceType : interfaces) {
                scanInterface(interfaceType, shadowedMethods, visitedInterfaces, methods);
            }
            current = current.getSuperclass();
        }
        return List.copyOf(methods);
    }

    private static void scanInterface(
            Class<?> interfaceType,
            Set<MethodSignature> shadowedMethods,
            Set<Class<?>> visitedInterfaces,
            List<AnnotatedMethod> result) {
        if (!visitedInterfaces.add(interfaceType)) {
            return;
        }
        scanDeclaredMethods(interfaceType, shadowedMethods, result);
        Class<?>[] parents = interfaceType.getInterfaces();
        Arrays.sort(parents, Comparator.comparing(Class::getName));
        for (Class<?> parent : parents) {
            scanInterface(parent, shadowedMethods, visitedInterfaces, result);
        }
    }

    private static void scanDeclaredMethods(
            Class<?> type, Set<MethodSignature> shadowedMethods, List<AnnotatedMethod> result) {
        Method[] declaredMethods = type.getDeclaredMethods();
        Arrays.sort(declaredMethods, METHOD_ORDER);
        for (Method method : declaredMethods) {
            if (!method.isBridge() && !method.isSynthetic()) {
                MethodSignature signature = new MethodSignature(method.getName(), List.of(method.getParameterTypes()));
                boolean privateMethod = Modifier.isPrivate(method.getModifiers());
                boolean available = privateMethod || shadowedMethods.add(signature);
                Subscribe annotation = method.getAnnotation(Subscribe.class);
                if (available && annotation != null) {
                    result.add(validateAndCreate(method, annotation));
                }
            }
        }
    }

    private static AnnotatedMethod validateAndCreate(Method method, Subscribe annotation) {
        if (Modifier.isStatic(method.getModifiers())) {
            throw invalid(method, "must not be static");
        }
        if (method.getReturnType() != void.class) {
            throw invalid(method, "must return void");
        }
        Class<?>[] parameters = method.getParameterTypes();
        boolean receivesContext = parameters.length == 2 && parameters[1] == EventContext.class;
        if ((parameters.length != 1 && !receivesContext) || !Event.class.isAssignableFrom(parameters[0])) {
            throw invalid(method, "must accept (Event) or (Event, EventContext)");
        }
        if (!method.trySetAccessible()) {
            throw new InvalidListenerException("Cannot access @Subscribe method " + method);
        }
        return new AnnotatedMethod(
                eventType(parameters[0]),
                annotation.priority(),
                annotation.receiveCancelledEvents(),
                annotation.exactTypeOnly(),
                Set.of(annotation.phases()),
                annotation.once(),
                receivesContext,
                method);
    }

    private static InvalidListenerException invalid(Method method, String requirement) {
        return new InvalidListenerException("@Subscribe method " + method + ' ' + requirement);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Event> eventType(Class<?> type) {
        return (Class<? extends Event>) type;
    }

    public record AnnotatedMethod(
            Class<? extends Event> eventType,
            int priority,
            boolean receivesCancelledEvents,
            boolean exactTypeOnly,
            Set<EventPhase> phases,
            boolean singleUse,
            boolean receivesContext,
            Method method) {
        public void invoke(Object listener, Event event, EventContext<Event> context) {
            try {
                if (receivesContext) {
                    method.invoke(listener, event, context);
                } else {
                    method.invoke(listener, event);
                }
            } catch (IllegalAccessException exception) {
                throw new InvalidListenerException("Cannot access @Subscribe method " + method, exception);
            } catch (InvocationTargetException exception) {
                rethrowTarget(exception);
            }
        }

        private void rethrowTarget(InvocationTargetException exception) {
            Throwable target = exception.getCause();
            if (target instanceof VirtualMachineError error) {
                throw error;
            }
            if (target instanceof LinkageError error) {
                throw error;
            }
            throw new ListenerInvocationException(method, target);
        }
    }

    private record MethodSignature(String name, List<Class<?>> parameterTypes) {}
}
