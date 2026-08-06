package com.b2ta.api.demo;

import com.b2ta.api.controller.CanvasController;
import com.b2ta.api.controller.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Runs the Canvas grading flow on its own, without a database.
 *
 * <p>The Canvas path — rubric, queue, submission, analysis, sync — touches no
 * repository, so a demo does not need Postgres, Flyway, or the session/upload layer.
 * This entry point scans only the Canvas and analysis packages and switches the JPA
 * autoconfiguration off, which makes it startable from a bare checkout.
 *
 * <p>This is a demo harness, not a deployment target. {@code ApiApplication} remains
 * the real service; nothing here changes how it runs.
 *
 * <pre>
 *   mvn -pl api spring-boot:run \
 *     -Dspring-boot.run.main-class=com.b2ta.api.demo.DemoApplication \
 *     -Dspring-boot.run.profiles=demo
 * </pre>
 */
@SpringBootApplication(
        scanBasePackages = {
                "com.b2ta.api.canvas",
                "com.b2ta.api.analyze",
                "com.b2ta.api.config",
                "com.b2ta.api.security",
                "com.b2ta.api.util",
        },
        exclude = {
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                FlywayAutoConfiguration.class,
        })
@Import({CanvasController.class, GlobalExceptionHandler.class})
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
