package steps.sdk.splitter;

import java.lang.reflect.*;
import java.util.*;
import org.mockito.Mockito;

/** Optional component profile: uses the supplied SDK binary, never a copy of production logic. */
public final class SplitterSdkAccess {
    public static final String ROOT = "explab.splitter.";
    public static Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name); }
    public static Object create(String name) throws ReflectiveOperationException { return type(name).getConstructor().newInstance(); }
    public static Object call(Object receiver, String name, Object... args) throws Throwable {
        Class<?> owner = receiver instanceof Class<?> c ? c : receiver.getClass();
        for (Method method : owner.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            boolean match = true;
            for (int i=0; i<args.length; i++)
                if (args[i] != null && !boxed(method.getParameterTypes()[i]).isInstance(args[i])) match = false;
            if (!match) continue;
            try { return method.invoke(receiver instanceof Class<?> ? null : receiver, args); }
            catch (InvocationTargetException failure) { throw failure.getCause(); }
        }
        throw new NoSuchMethodException(owner.getName() + "." + name + " " + Arrays.toString(args));
    }
    private static Class<?> boxed(Class<?> type) {
        if (type == boolean.class) return Boolean.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        return type;
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Object code(String className, String value) throws ClassNotFoundException {
        return Enum.valueOf((Class) type(ROOT + "num." + className), value);
    }
    public static void field(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = type(ROOT + "service.SplittingService").getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    public static Object failingService(Throwable failure) throws Throwable {
        Object service = Mockito.mock(type(ROOT + "service.SplittingService"), Mockito.CALLS_REAL_METHODS);
        Object properties = create(ROOT + "config.props.SplitterConfigProperties");
        call(properties, "setPreliminaryCalculationEnabled", true);
        field(service, "splitterConfigProperties", properties);
        Object config = Mockito.mock(type(ROOT + "service.SplittingConfigService"), invocation -> {
            if (invocation.getMethod().getName().equals("getSplittingConfigHolder")) throw failure;
            return Mockito.RETURNS_DEFAULTS.answer(invocation);
        });
        field(service, "splittingConfigService", config);
        field(service, "monitoringService", Mockito.mock(type(ROOT + "service.SplitterMonitoringService")));
        return service;
    }
    public static Object request(String requestId) throws Throwable {
        Object request = create(ROOT + "service.dto.preliminary.SplitterPreliminaryRequest");
        call(request, "setRequestId", requestId);
        call(request, "setSoConfigVersion", 101L);
        call(request, "setSplittingObjects", List.of());
        return request;
    }
    private SplitterSdkAccess() { }}
