package dev.fiyin.orderflow.api;

import dev.fiyin.orderflow.service.ShopException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ShopException.class)
    public ResponseEntity<?> shop(ShopException exception) {
        return ResponseEntity.status(exception.status()).body(Map.of("message",exception.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,MissingRequestHeaderException.class})
    public ResponseEntity<?> badRequest(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("message","Check the product, quantity (1–10), payment scenario, and request key."));
    }
}
