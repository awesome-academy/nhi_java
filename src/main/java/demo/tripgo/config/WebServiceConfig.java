package demo.tripgo.config;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

// Dịch vụ SOAP cho hệ thống đối tác.
//
// SOAP chạy trên servlet RIÊNG (MessageDispatcherServlet) chứ không đi qua DispatcherServlet của
// MVC, nên phải đăng ký tường minh ở đây.
@Configuration
@EnableWs
public class WebServiceConfig {

    public static final String PATH = "/ws";

    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(
        ApplicationContext applicationContext
    ) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(applicationContext);
        // Cho phép servlet tự phục vụ file WSDL/XSD theo đường dẫn.
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, PATH + "/*");
    }

    // WSDL sinh từ XSD chứ không viết tay: đổi hợp đồng chỉ cần sửa XSD, WSDL công bố cho đối tác
    // tự khớp theo.
    @Bean(name = "tourAvailability")
    public DefaultWsdl11Definition tourAvailabilityWsdl(XsdSchema tripgoSchema) {
        DefaultWsdl11Definition definition = new DefaultWsdl11Definition();
        definition.setPortTypeName("TourAvailabilityPort");
        definition.setLocationUri(PATH);
        definition.setTargetNamespace("http://tripgo.demo/soap");
        definition.setSchema(tripgoSchema);
        return definition;
    }

    @Bean
    public XsdSchema tripgoSchema() {
        return new SimpleXsdSchema(new ClassPathResource("soap/tripgo.xsd"));
    }
}
