package cn.huohuas001.huhobotPenguin.spigot.scripting;

/**
 * Outcome of loading one script file. Adapted from BirdLibraryApi
 * (https://github.com/prach1121/birdlibraryapi, Apache-2.0).
 */
public class ScriptLoadResult {

    private final String fileName;
    private final boolean success;
    private final String errorMessage;
    private final int line;
    private final int column;

    public ScriptLoadResult(String fileName, boolean success, String errorMessage, int line, int column) {
        this.fileName = fileName;
        this.success = success;
        this.errorMessage = errorMessage;
        this.line = line;
        this.column = column;
    }

    public static ScriptLoadResult ok(String fileName) {
        return new ScriptLoadResult(fileName, true, null, -1, -1);
    }

    public static ScriptLoadResult error(String fileName, String message) {
        return new ScriptLoadResult(fileName, false, message, -1, -1);
    }

    public static ScriptLoadResult error(String fileName, String message, int line, int column) {
        return new ScriptLoadResult(fileName, false, message, line, column);
    }

    public static ScriptLoadResult notFound(String fileName) {
        return new ScriptLoadResult(fileName, false, "File not found", -1, -1);
    }

    public String fileName() {
        return fileName;
    }

    public boolean success() {
        return success;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public String formatted() {
        if (success) {
            return "§a✔ " + fileName;
        }
        StringBuilder sb = new StringBuilder("§c✘ " + fileName + " §7- §c" + errorMessage);
        if (line >= 0) {
            sb.append(" §7(line ").append(line);
            if (column >= 0) {
                sb.append(", col ").append(column);
            }
            sb.append(")");
        }
        return sb.toString();
    }
}
