package dev.fiyin.orderflow.service;

public class ShopException extends RuntimeException {
    private final int status;
    public ShopException(int status, String message) {
        super(message);
        this.status = status;
    }
    public int status() { return status; }
}
