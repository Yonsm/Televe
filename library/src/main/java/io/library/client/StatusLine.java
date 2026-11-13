package io.library.client;

public final class StatusLine {

    public static final StatusLine SWITCH_PROTOCOL = new StatusLine(101, "Switching Protocols");

    public static final StatusLine OK = new StatusLine(200, "OK");
    public static final StatusLine CREATED = new StatusLine(201, "Created");
    public static final StatusLine ACCEPTED = new StatusLine(202, "Accepted");
    public static final StatusLine NO_CONTENT = new StatusLine(204, "No Content");
    public static final StatusLine PARTIAL_CONTENT = new StatusLine(206, "Partial Content");
    public static final StatusLine MULTI_STATUS = new StatusLine(207, "Multi-Status");

    public static final StatusLine MOVED_PERMANENTLY = new StatusLine(301, "Moved Permanently");
    public static final StatusLine MOVED_TEMPORARILY = new StatusLine(302, "Moved Temporarily");
    public static final StatusLine REDIRECT_SEE_OTHER = new StatusLine(303, "See Other");
    public static final StatusLine NOT_MODIFIED = new StatusLine(304, "Not Modified");
    public static final StatusLine TEMPORARY_REDIRECT = new StatusLine(307, "Temporary Redirect");
    public static final StatusLine PERMANENT_REDIRECT = new StatusLine(308, "Permanent Redirect");

    public static final StatusLine BAD_REQUEST = new StatusLine(400, "Bad Request");
    public static final StatusLine UNAUTHORIZED = new StatusLine(401, "Unauthorized");
    public static final StatusLine FORBIDDEN = new StatusLine(403, "Forbidden");
    public static final StatusLine NOT_FOUND = new StatusLine(404, "Not Found");
    public static final StatusLine METHOD_NOT_ALLOWED = new StatusLine(405, "Method Not Allowed");
    public static final StatusLine NOT_ACCEPTABLE = new StatusLine(406, "Not Acceptable");
    public static final StatusLine REQUEST_TIMEOUT = new StatusLine(408, "Request Timeout");
    public static final StatusLine CONFLICT = new StatusLine(409, "Conflict");
    public static final StatusLine GONE = new StatusLine(410, "Gone");
    public static final StatusLine LENGTH_REQUIRED = new StatusLine(411, "Length Required");
    public static final StatusLine PRECONDITION_FAILED = new StatusLine(412, "Precondition Failed");
    public static final StatusLine PAYLOAD_TOO_LARGE = new StatusLine(413, "Payload Too Large");
    public static final StatusLine UNSUPPORTED_MEDIA_TYPE = new StatusLine(415, "Unsupported Media Type");
    public static final StatusLine RANGE_NOT_SATISFIABLE = new StatusLine(416, "Requested Range Not Satisfiable");
    public static final StatusLine EXPECTATION_FAILED = new StatusLine(417, "Expectation Failed");
    public static final StatusLine TOO_MANY_REQUESTS = new StatusLine(429, "Too Many Requests");

    public static final StatusLine INTERNAL_ERROR = new StatusLine(500, "Internal Server Error");
    public static final StatusLine NOT_IMPLEMENTED = new StatusLine(501, "Not Implemented");
    public static final StatusLine BAD_GATEWAY = new StatusLine(502, "Bad Gateway");
    public static final StatusLine SERVICE_UNAVAILABLE = new StatusLine(503, "Service Unavailable");
    public static final StatusLine GATEWAY_TIMEOUT = new StatusLine(504, "Gateway Timeout");
    public static final StatusLine UNSUPPORTED_HTTP_VERSION = new StatusLine(505, "HTTP Version Not Supported");

    private static final StatusLine[] KNOWN = {
            SWITCH_PROTOCOL,
            OK, CREATED, ACCEPTED, NO_CONTENT, PARTIAL_CONTENT, MULTI_STATUS,
            MOVED_PERMANENTLY, MOVED_TEMPORARILY, REDIRECT_SEE_OTHER, NOT_MODIFIED, TEMPORARY_REDIRECT, PERMANENT_REDIRECT,
            BAD_REQUEST, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, METHOD_NOT_ALLOWED, NOT_ACCEPTABLE, REQUEST_TIMEOUT,
            CONFLICT, GONE, LENGTH_REQUIRED, PRECONDITION_FAILED, PAYLOAD_TOO_LARGE, UNSUPPORTED_MEDIA_TYPE,
            RANGE_NOT_SATISFIABLE, EXPECTATION_FAILED, TOO_MANY_REQUESTS,
            INTERNAL_ERROR, NOT_IMPLEMENTED, BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT, UNSUPPORTED_HTTP_VERSION
    };

    public final int code;
    public final String description;

    public StatusLine(int code, String description) {
        this.code = code;
        this.description = description;
    }

    @Override
    public String toString() {
        return description.isEmpty() ? Integer.toString(code) : Integer.toString(code) + ' ' + description;
    }

    public static StatusLine lookup(int code) {
        for (StatusLine status : KNOWN) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    public static StatusLine parse(String line) {
        if (line == null || line.isEmpty()) return null;
        int end = line.indexOf(' ');
        String codeText = end < 0 ? line : line.substring(0, end);
        String description = end < 0 ? "" : line.substring(end + 1).trim();
        int code;
        try {
            code = Integer.parseInt(codeText);
        } catch (NumberFormatException ignored) {
            return null;
        }
        if (code < 100 || code > 999) return null;
        StatusLine status = lookup(code);
        return status != null ? status : new StatusLine(code, description);
    }
}
