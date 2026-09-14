package api;

public class JsonUtil {
    public static String quote(String s) {
        if (s == null) {
            return "null";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static String obj(String... pairs) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(quote(pairs[i])).append(":").append(pairs[i + 1]);
        }
        return sb.append("}").toString();
    }

    public static String ok(String msg) {
        return obj("status", quote("ok"), "message", quote(msg));
    }

    public static String error(String msg) {
        return obj("status", quote("error"), "message", quote(msg));
    }
}