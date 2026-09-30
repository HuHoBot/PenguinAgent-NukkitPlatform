package cn.huohuas001.huhobot.graaljs;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * GraalJS entry point that lives inside the engine jar's own classloader.
 *
 * GraalVM 24.1 discovers languages from the classloader that is current when the
 * polyglot context is built, and that API has no classloader parameter. The main
 * plugin jar no longer contains GraalJS, so building the context there never sees
 * the {@code js} language even when this jar is visible as a child loader. Running
 * it here registers the language.
 *
 * The main plugin reaches this class by reflection, so it compiles and runs
 * without GraalJS on its classpath. {@link #createEngine()} is the JSR-223 path;
 * {@link #open()} is the direct polyglot path used when a script needs host
 * callbacks that stay callable after {@code eval} returns.
 */
public final class GraalJsBridge {

    private GraalJsBridge() {
    }

    /** A live GraalJS context. The caller only needs eval / bindings / close. */
    public interface Session {
        void bind(String name, Object value);

        void eval(File file) throws Exception;

        void close();
    }

    /** JSR-223 {@code graal.js} engine, for scripts that only need {@code eval}. */
    public static ScriptEngine createEngine() {
        ScriptEngineManager manager = new ScriptEngineManager(GraalJsBridge.class.getClassLoader());
        return manager.getEngineByName("graal.js");
    }

    public static Session open() {
        Context context = Context.newBuilder("js")
                .allowAllAccess(true)
                .allowHostAccess(hostAccess())
                .allowHostClassLookup(className -> true)
                .allowExperimentalOptions(true)
                .option("js.nashorn-compat", "true")
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        return new JsSession(context);
    }

    /**
     * {@link HostAccess#ALL} maps every JavaScript function onto
     * {@code java.util.function.Function}, so a callback declared with a different
     * shape arrives as the wrong type. Keeping the raw function lets the host adapt
     * it to the exact interface its method declared.
     */
    private static HostAccess hostAccess() {
        return HostAccess.newBuilder(HostAccess.ALL)
                .targetTypeMapping(Value.class, Object.class, Value::canExecute, value -> value, HostAccess.TargetMappingPrecedence.HIGHEST)
                .build();
    }

    /**
     * 把脚本函数适配成 {@code Consumer}。必须在引擎 classloader 里做：
     * 原始 {@link Value} 一旦传到主插件，主插件没有 polyglot，会 NoClassDefFoundError。
     * {@code Consumer} 在 java.base 里，两边都看得见。
     */
    /** 给脚本用的实例。静态方法挂在 Class 上，GraalJS 当成员调用时找不到。 */
    public static Object helper() {
        return new Helper();
    }

    public static final class Helper {
        public Object adaptFunction(Object function) {
            return GraalJsBridge.adaptFunction(function);
        }

        /**
         * 在引擎 classloader 里实现 {@code WebSocket.Listener}，再交给脚本。
         * 主插件的加载器看不见 {@code Value}，不能自己 new 这个监听器。
         */
        public Object webSocketListener(Object onOpen, Object onText, Object onClose, Object onError) {
            return new java.net.http.WebSocket.Listener() {
                @Override
                public void onOpen(java.net.http.WebSocket webSocket) {
                    call(onOpen, webSocket);
                }

                @Override
                public java.util.concurrent.CompletionStage<?> onText(java.net.http.WebSocket webSocket,
                                                                      CharSequence data, boolean last) {
                    call(onText, webSocket, data, last);
                    return null;
                }

                @Override
                public java.util.concurrent.CompletionStage<?> onClose(java.net.http.WebSocket webSocket,
                                                                       int statusCode, String reason) {
                    call(onClose, webSocket, statusCode, reason);
                    return null;
                }

                @Override
                public void onError(java.net.http.WebSocket webSocket, Throwable error) {
                    call(onError, webSocket, error);
                }

                private void call(Object function, Object... args) {
                    if (function instanceof Value && ((Value) function).canExecute()) {
                        ((Value) function).executeVoid(args);
                    }
                }
            };
        }
    }

    public static Object adaptFunction(Object function) {
        if (!(function instanceof Value) || !((Value) function).canExecute()) return function;
        Value value = (Value) function;
        return (java.util.function.Consumer<Object[]>) args -> value.execute(args == null ? new Object[0] : args);
    }

    /** True when {@code value} is a JavaScript function the host can call. */
    public static boolean canExecute(Object value) {
        return value instanceof Value && ((Value) value).canExecute();
    }

    /** Calls a function previously handed to the host by a script. */
    public static Object execute(Object function, Object... args) {
        return ((Value) function).execute(args);
    }

    /** Adapts a JavaScript function to the functional interface {@code type}. */
    public static Object adapt(Object function, Class<?> type) {
        if (!(function instanceof Value) || !((Value) function).canExecute() || type == null || !type.isInterface()) {
            return function;
        }
        Value value = (Value) function;
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                    case "toString": return "js-function";
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    default: return null;
                }
            }
            Value result = value.execute(args == null ? new Object[0] : args);
            return convert(result, method.getGenericReturnType());
        });
    }

    /** Converts a JavaScript return value into the type the host method declared. */
    private static Object convert(Value result, java.lang.reflect.Type type) {
        if (type == void.class || type == Void.class || result == null || result.isNull()) return null;
        if (type instanceof java.lang.reflect.ParameterizedType) {
            java.lang.reflect.ParameterizedType parameterized = (java.lang.reflect.ParameterizedType) type;
            if (parameterized.getRawType() == List.class) {
                return result.as(List.class);
            }
            if (parameterized.getRawType() instanceof Class) {
                return convertAs(result, (Class<?>) parameterized.getRawType());
            }
        }
        if (type instanceof Class) {
            return convertAs(result, (Class<?>) type);
        }
        return result.isString() ? result.asString() : result.toString();
    }

    private static Object convertAs(Value result, Class<?> raw) {
        try {
            return result.as(raw);
        } catch (ClassCastException | IllegalArgumentException unsupported) {
            return result.isString() ? result.asString() : result.toString();
        }
    }

    public static final class JsSession implements Session {
        private final Context context;

        JsSession(Context context) {
            this.context = context;
        }

        @Override
        public void bind(String name, Object value) {
            context.getBindings("js").putMember(name, value);
        }

        @Override
        public void eval(File file) throws Exception {
            context.eval(Source.newBuilder("js", file).build());
        }

        @Override
        public void close() {
            context.close(true);
        }
    }
}
