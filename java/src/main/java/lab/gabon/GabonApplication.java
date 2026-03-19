package lab.gabon;

import lab.gabon.config.AppConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppConfig.class)
public class GabonApplication {

  public static void main(String[] args) {
    SpringApplication.run(GabonApplication.class, args);
  }
}
