package cn.huohuas001.huhobotPenguin.spigot.scripting;

import org.bukkit.event.Event;

@FunctionalInterface
public interface EventCallback {
    void execute(Event event);
}
