package cn.iocoder.yudao.module.ilvy.local;

import cn.iocoder.yudao.module.ilvy.dal.IlvyCatalogRepository;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
@ConditionalOnProperty(name = "ilvy.catalog-reindex", havingValue = "true")
public class IlvyCatalogIndexInitializer implements ApplicationRunner {
  private final IlvyCatalogRepository catalog;

  public IlvyCatalogIndexInitializer(IlvyCatalogRepository catalog) {
    this.catalog = catalog;
  }

  public void run(ApplicationArguments args) {
    catalog.rebuild();
  }
}
