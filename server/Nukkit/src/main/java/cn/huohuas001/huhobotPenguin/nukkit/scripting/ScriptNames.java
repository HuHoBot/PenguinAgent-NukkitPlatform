package cn.huohuas001.huhobotPenguin.nukkit.scripting;

import cn.nukkit.event.Event;

/** Shared name helpers for the JS and Python addon loaders. */
final class ScriptNames {

    private ScriptNames() {
    }

    static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    static boolean isScript(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".js") || lower.endsWith(".py") || lower.endsWith(".lua");
    }

    /** Resolves a Nukkit event by simple name or fully-qualified class name. */
    @SuppressWarnings("unchecked")
    static Class<? extends Event> findEventClass(String eventName) {
        if (eventName == null || eventName.trim().isEmpty()) return null;
        String trimmed = eventName.trim();
        if (trimmed.indexOf('.') >= 0) {
            return asEvent(load(trimmed));
        }
        String[] prefixes = {
                "cn.nukkit.event.player.",
                "cn.nukkit.event.block.",
                "cn.nukkit.event.entity.",
                "cn.nukkit.event.inventory.",
                "cn.nukkit.event.level.",
                "cn.nukkit.event.server.",
                "cn.nukkit.event.plugin.",
                "cn.nukkit.event."
        };
        for (String prefix : prefixes) {
            Class<? extends Event> found = asEvent(load(prefix + trimmed));
            if (found != null) return found;
        }
        return null;
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static Class<? extends Event> asEvent(Class<?> type) {
        if (type != null && Event.class.isAssignableFrom(type)) {
            return type.asSubclass(Event.class);
        }
        return null;
    }
}
