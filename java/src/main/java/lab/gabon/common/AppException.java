package lab.gabon.common;

public class AppException extends RuntimeException {

  private final AppError error;

  public AppException(AppError error) {
    super(error.errorCode() + ": " + error.message());
    this.error = error;
  }

  public AppError getError() {
    return error;
  }
}
