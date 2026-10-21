package cn.iocoder.yudao.module.ilvy.local;

import org.springframework.context.annotation.*;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
@Profile("ilvy-local")
public class LocalWebConfiguration implements WebMvcConfigurer {
  @Override
  public void configurePathMatch(PathMatchConfigurer c) {
    c.addPathPrefix(
        "/app-api",
        HandlerTypePredicate.forBasePackage("cn.iocoder.yudao.module.ilvy.controller.app"));
  }
}
