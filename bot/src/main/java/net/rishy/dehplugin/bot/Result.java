package net.rishy.dehplugin.bot;

public record Result(boolean ok, String detail) {

    public static Result ok(String detail) {
        return new Result(true, detail);
    }

    public static Result err(String detail) {
        return new Result(false, detail);
    }
}
