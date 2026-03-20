package uk.l3si.eclipse.mcp.agent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflection helpers that walk the class hierarchy and set members accessible.
 */
final class ReflectionUtils {

    private ReflectionUtils() {}

    /**
     * Finds a method by name and parameter types, walking up the class
     * hierarchy and then checking implemented interfaces.
     */
    static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {}
        }
        for (Class<?> iface : clazz.getInterfaces()) {
            try {
                Method m = iface.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {}
        }
        throw new IllegalStateException("Cannot find method " + name + " on " + clazz.getName());
    }

    /**
     * Finds a field by name, walking up the class hierarchy.
     */
    static Field findField(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new IllegalStateException("Cannot find field " + name + " on " + clazz.getName());
    }

    /**
     * Finds the {@code loadTests} method on an ITestLoader implementation.
     * Matches a 7-parameter signature where the last parameter is assignable
     * from {@code runnerType}.
     */
    static Method findLoadTestsMethod(Class<?> loaderClass, Class<?> runnerType) {
        for (Method m : loaderClass.getMethods()) {
            if ("loadTests".equals(m.getName()) && m.getParameterCount() == 7
                    && m.getParameterTypes()[6].isAssignableFrom(runnerType)) {
                m.setAccessible(true);
                return m;
            }
        }
        for (Class<?> iface : loaderClass.getInterfaces()) {
            for (Method m : iface.getMethods()) {
                if ("loadTests".equals(m.getName()) && m.getParameterCount() == 7) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        throw new IllegalStateException("Cannot find loadTests method on " + loaderClass.getName());
    }

    /**
     * Simple classloader that defines a single class from a byte array.
     */
    static class ByteArrayClassLoader extends ClassLoader {
        ByteArrayClassLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> defineClass(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
