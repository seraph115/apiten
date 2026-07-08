package cn.iocoder.yudao.gateway.filter.openapi;

public final class OpenApiPathUtils {

    private static final String PREFIX = "/api/v1/";

    private OpenApiPathUtils() {}

    public static boolean isOpenApiPath(String path) {
        return path != null && path.startsWith(PREFIX);
    }

    public static String extractProductCode(String path) {
        if (!isOpenApiPath(path)) {
            return null;
        }
        String rest = path.substring(PREFIX.length()); // e.g. "P000001/query"
        int slash = rest.indexOf('/');
        String code = slash >= 0 ? rest.substring(0, slash) : rest;
        return code.isEmpty() ? null : code;
    }
}
