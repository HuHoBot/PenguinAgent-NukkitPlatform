package cn.huohuas001.huhobot.graalpy;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;

import java.io.File;

/**
 * Creates the GraalPy engine from inside the engine jar's own classloader.
 *
 * GraalVM 24.1 discovers languages from the classloader of the class that calls
 * {@code Engine.newBuilder}, and that API has no classloader parameter. The main
 * plugin jar no longer contains the polyglot runtime, so calling it there never
 * sees Python even when this jar is visible as a child loader. Running the call
 * here registers it.
 *
 * The main plugin reaches this class by reflection only, so it compiles and runs
 * without GraalPy on its classpath. {@link Failure} carries the source line of a
 * script error back across that boundary, because {@link PolyglotException} itself
 * is not on the main plugin's classpath.
 */
public final class GraalPyBridge {

    private GraalPyBridge() {
    }

    /** A live GraalPy context. The main plugin only needs bindings / eval / close. */
    public interface Session {
        void bind(String name, Object value);

        void eval(File file) throws Failure;

        /** 在当前上下文里求一段 Python 源码，依赖预检用。失败时抛 {@link Failure}。 */
        void evalSource(String source) throws Failure;

        /** 顶层变量的值；不存在返回 null。 */
        Object get(String name);

        void close();

        /** 关掉这个 Session 自己建的 Engine。共享 Engine 的调用方不要用。 */
        void closeEngine();
    }

    /** A script failure, with the source line when GraalPy reported one. */
    public static final class Failure extends Exception {
        private final int line;

        public Failure(String message, int line, Throwable cause) {
            super(message, cause);
            this.line = line;
        }

        /** Source line, or {@code -1} when the failure has no source location. */
        public int line() {
            return line;
        }
    }

    public static Engine createEngine() {
        return Engine.newBuilder("python")
                .allowExperimentalOptions(true)
                .option("engine.WarnInterpreterOnly", "false")
                .build();
    }

    /** True when {@code engine} was built by {@link #createEngine()} and speaks Python. */
    public static boolean providesPython(Object engine) {
        return engine instanceof Engine && ((Engine) engine).getLanguages().containsKey("python");
    }

    /** 自建 Engine 再开一个上下文。Nukkit 走这条；Spigot 用 {@link #open(Object)} 共享 Engine。 */
    public static Session open() {
        Engine engine = createEngine();
        return new PySession(context(engine), engine);
    }

    public static boolean canExecute(Object value) {
        return value instanceof Value && ((Value) value).canExecute();
    }

    public static Object execute(Object function, Object... args) {
        return ((Value) function).execute(args);
    }

    public static String asString(Object value) {
        if (!(value instanceof Value)) return value == null ? null : String.valueOf(value);
        Value v = (Value) value;
        try {
            if (v.isNull()) return null;
            return v.isString() ? v.asString() : v.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 函数保留为原始 {@link Value}，避免被映射成 {@code java.util.function.Function}。
     * 目标类型是具体函数式接口时不受影响，宿主仍能直接收到适配后的回调。
     */
    private static HostAccess hostAccess() {
        return HostAccess.newBuilder(HostAccess.ALL)
                .targetTypeMapping(Value.class, Object.class, Value::canExecute, value -> value, HostAccess.TargetMappingPrecedence.HIGHEST)
                .build();
    }

    public static Session open(Object engine) {
        if (!(engine instanceof Engine)) {
            throw new IllegalArgumentException("不是 GraalPy 引擎: " + engine);
        }
        // engine.WarnInterpreterOnly 是引擎级选项，共享 Engine 的 Context 上再设会被拒绝。
        // ownedEngine 传 null：这个 Engine 是调用方的，Session 关掉时不能连带关掉。
        return new PySession(context((Engine) engine), null);
    }

    private static Context context(Engine engine) {
        return Context.newBuilder("python")
                .engine(engine)
                .allowAllAccess(true)
                .allowHostAccess(hostAccess())
                .allowHostClassLookup(className -> true)
                .build();
    }

    public static final class PySession implements Session {
        private final Context context;
        private final Engine ownedEngine;

        /** {@code ownedEngine} 非空时，{@link #closeEngine()} 会把它关掉。 */
        PySession(Context context, Engine ownedEngine) {
            this.context = context;
            this.ownedEngine = ownedEngine;
        }

        @Override
        public void bind(String name, Object value) {
            context.getBindings("python").putMember(name, value);
        }

        @Override
        public void eval(File file) throws Failure {
            try {
                context.eval(Source.newBuilder("python", file).build());
            } catch (PolyglotException error) {
                int line = -1;
                SourceSection section = error.getSourceLocation();
                if (section != null && section.isAvailable()) line = section.getStartLine();
                throw new Failure(error.getMessage(), line, error);
            } catch (Exception error) {
                throw new Failure(error.getMessage(), -1, error);
            }
        }

        @Override
        public Object get(String name) {
            Value value = context.getBindings("python").getMember(name);
            return value == null || value.isNull() ? null : value;
        }

        public void evalSource(String source) throws Failure {
            try {
                context.eval("python", source);
            } catch (PolyglotException error) {
                throw new Failure(error.getMessage(), -1, error);
            }
        }

        @Override
        public void close() {
            context.close(true);
        }

        @Override
        public void closeEngine() {
            if (ownedEngine != null) ownedEngine.close();
        }
    }
}
