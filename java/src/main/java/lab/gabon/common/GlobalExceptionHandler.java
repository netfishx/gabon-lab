package lab.gabon.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(AppException.class)
  public ResponseEntity<ApiResponse<Void>> handleApp(AppException ex) {
    var error = ex.getError();
    return ResponseEntity.status(error.status()).body(ApiResponse.error(error));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .findFirst()
            .orElse("validation failed");
    var error = new AppError.BadRequest(message);
    return ResponseEntity.badRequest().body(ApiResponse.error(error));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
    var error = new AppError.Internal("internal server error");
    return ResponseEntity.internalServerError().body(ApiResponse.error(error));
  }
}
