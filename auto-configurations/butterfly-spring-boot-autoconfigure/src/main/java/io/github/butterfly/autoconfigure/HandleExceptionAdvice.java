package io.github.butterfly.autoconfigure;


import io.github.butterfly.core.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
@Slf4j
public class HandleExceptionAdvice {
    @ExceptionHandler(value = Throwable.class)
    public R<Object> handleThrowable(Throwable t) {
        R<Object> error = R.error(t.getMessage());
        log.error("error: ", t);
        return error;
    }
}
