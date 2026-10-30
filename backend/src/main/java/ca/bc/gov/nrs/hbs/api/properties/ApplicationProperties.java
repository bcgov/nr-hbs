package ca.bc.gov.nrs.hbs.api.properties;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
@Getter
public class ApplicationProperties {
    public static final Executor bgTask = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
            new ThreadFactoryBuilder().setNameFormat("bg-task-executor-%d").build());
  public static final String HBS_API = "HBS-API";
  public static final String CORRELATION_ID = "correlationID";
  
}
