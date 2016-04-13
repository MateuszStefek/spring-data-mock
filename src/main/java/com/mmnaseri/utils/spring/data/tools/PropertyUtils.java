package com.mmnaseri.utils.spring.data.tools;

import com.mmnaseri.utils.spring.data.query.PropertyDescriptor;
import com.mmnaseri.utils.spring.data.query.impl.ImmutablePropertyDescriptor;
import com.mmnaseri.utils.spring.data.string.DocumentReader;
import com.mmnaseri.utils.spring.data.string.impl.DefaultDocumentReader;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Mohammad Milad Naseri (m.m.naseri@gmail.com)
 * @since 1.0 (9/17/15)
 */
public final class PropertyUtils {

    private static final Map<Class<?>, Class<?>> types = new HashMap<>();

    private PropertyUtils() {
        throw new UnsupportedOperationException();
    }

    static {
        types.put(int.class, Integer.class);
        types.put(short.class, Short.class);
        types.put(long.class, Long.class);
        types.put(double.class, Double.class);
        types.put(char.class, Character.class);
        types.put(float.class, Float.class);
        types.put(boolean.class, Boolean.class);
        types.put(byte.class, Byte.class);
    }

    public static Object getPropertyValue(Object context, String property) {
        final String[] chain = property.split("\\.");
        final List<Object> accessors = new ArrayList<>();
        Class<?> type = context.getClass();
        final StringBuilder path = new StringBuilder();
        for (String current : chain) {
            if (path.length() > 0) {
                path.append('.');
            }
            path.append(current);
            final Method getter = ReflectionUtils.findMethod(type, "get" + StringUtils.capitalize(current));
            if (getter != null) {
                accessors.add(getter);
                type = getter.getReturnType();
            } else {
                final Field field = ReflectionUtils.findField(type, current);
                if (field != null) {
                    field.setAccessible(true);
                    accessors.add(field);
                    type = field.getType();
                } else {
                    throw new IllegalStateException("Cannot find property `" + path + "` on type `" + context.getClass() + "`");
                }
            }
        }
        Object result = context;
        for (Object accessor : accessors) {
            try {
                if (accessor instanceof Method) {
                    final Method method = (Method) accessor;
                    result = method.invoke(result);
                } else {
                    final Field field = (Field) accessor;
                    result = field.get(result);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Failed to access property `" + property + "`", e);
            }
            if (result == null) {
                return null;
            }
        }
        return result;
    }

    public static Object setPropertyValue(Object context, String property, Object value) {
        if (property.contains(".")) {
            final String[] split = property.split("\\.", 2);
            final Object propertyValue = getPropertyValue(context, split[0]);
            if (propertyValue == null) {
                throw new IllegalStateException("Root property " + split[0] + " was null when reading " + property);
            }
            return setPropertyValue(propertyValue, split[1], value);
        }
        Method setter = null;
        final String setterName = "set" + StringUtils.capitalize(property);
        if (value != null) {
            setter = ReflectionUtils.findMethod(context.getClass(), setterName, value.getClass());
        } else {
            for (Method method : ReflectionUtils.getAllDeclaredMethods(context.getClass())) {
                if (method.getName().equals(setterName) && method.getParameterTypes().length == 1 && !method.getParameterTypes()[0].isPrimitive()) {
                    setter = method;
                    break;
                }
            }
        }
        if (setter != null) {
            try {
                setter.invoke(context, value);
                return context;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to set property value through the setter method " + setter);
            }
        } else {
            final Field field = ReflectionUtils.findField(context.getClass(), property);
            if (field != null) {
                if (!Modifier.isFinal(field.getModifiers())) {
                    field.setAccessible(true);
                }
                try {
                    field.set(context, value);
                    return context;
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to set property value through the field " + field);
                }
            }
        }
        throw new IllegalStateException("Failed to find property " + property + " on " + context.getClass());
    }

    public static PropertyDescriptor getPropertyDescriptor(Class<?> domainType, String expression) {
        String search = expression;
        final String followUp;
        if (search.contains("_")) {
            final String[] split = search.split("_", 2);
            search = split[0];
            followUp = split[1];
        } else {
            followUp = "";
        }
        Class<?> context = domainType;
        final DocumentReader reader = new DefaultDocumentReader(search);
        final List<String> tokens = new ArrayList<>();
        while (reader.hasMore()) {
            tokens.add(reader.expect("[A-Z][a-z]*"));
        }
        int cursor = 0;
        final StringBuilder path = new StringBuilder();
        while (cursor < tokens.size()) {
            boolean found = false;
            for (int i = tokens.size(); i >= cursor; i--) {
                final String propertyName = getPropertyName(tokens, cursor, i);
                final Method getter = ReflectionUtils.findMethod(context, "get" + StringUtils.capitalize(propertyName));
                if (getter != null) {
                    context = getter.getReturnType();
                    cursor = i;
                    found = true;
                    path.append(".").append(propertyName);
                    break;
                }
                final Field field = ReflectionUtils.findField(context, propertyName);
                if (field != null) {
                    context = field.getType();
                    cursor = i;
                    found = true;
                    path.append(".").append(propertyName);
                    break;
                }
            }
            if (!found) {
                throw new IllegalStateException("Could not find property `" + getPropertyName(tokens, cursor, tokens.size()) + "` on `" + context + "`");
            }
        }
        if (!followUp.isEmpty()) {
            final PropertyDescriptor descriptor = getPropertyDescriptor(context, followUp);
            return new ImmutablePropertyDescriptor(path.substring(1) + "." + descriptor.getPath(), descriptor.getType());
        }
        return new ImmutablePropertyDescriptor(path.substring(1), context);
    }
    
    private static String getPropertyName(List<String> tokens, int from, int to) {
        final StringBuilder builder = new StringBuilder();
        for (int i = from; i < to; i++) {
            builder.append(tokens.get(i));
        }
        return StringUtils.uncapitalize(builder.toString());
    }

    public static Class<?> getTypeOf(Class<?> type) {
        if (type.isPrimitive()) {
            return types.get(type);
        } else {
            return type;
        }
    }

    public static String getPropertyName(Method getter) {
        return StringUtils.uncapitalize(getter.getName().substring(3));
    }

}