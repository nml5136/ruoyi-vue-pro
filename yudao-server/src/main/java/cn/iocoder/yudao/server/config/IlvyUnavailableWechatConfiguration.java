package cn.iocoder.yudao.server.config;

import cn.binarywang.wx.miniapp.api.WxMaService;
import java.lang.reflect.Proxy;
import me.chanjar.weixin.mp.api.WxMpService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Explicit unavailable adapters for the isolated host profile; never fabricates WeChat identity.
 */
@Configuration
@Profile("ilvy-host")
public class IlvyUnavailableWechatConfiguration {
  @Bean
  public WxMpService wxMpService() {
    return unavailable(WxMpService.class);
  }

  @Bean
  public WxMaService wxMaService() {
    return unavailable(WxMaService.class);
  }

  private static <T> T unavailable(Class<T> type) {
    return type.cast(
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (proxy, method, args) -> {
              if (method.getDeclaringClass() == Object.class) {
                if ("toString".equals(method.getName()))
                  return "Unconfigured " + type.getSimpleName();
                if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                if ("equals".equals(method.getName())) return proxy == args[0];
              }
              throw new IllegalStateException("微信服务未配置；当前隔离环境仅验证本地会员密码登录，不能执行微信授权或对外调用");
            }));
  }
}
