package io.library.net;


public class ResponseException extends RuntimeException {
    public final int statusCode;


    public ResponseException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    @Override
    public String toString() {
        return "Status: " + statusCode + ", " + getMessage();
    }

    public static boolean isTokenError(Throwable e) {
        return e instanceof ResponseException && ((ResponseException) e).statusCode == 401;
    }
}
