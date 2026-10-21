package cn.iocoder.yudao.module.ilvy.local;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.context.annotation.Profile("ilvy-local")
@SpringBootApplication(scanBasePackages = "cn.iocoder.yudao.module.ilvy")
public class IlvyLocalApplication {
  public static void main(String[] args) {
    SpringApplication.run(IlvyLocalApplication.class, args);
  }
}
