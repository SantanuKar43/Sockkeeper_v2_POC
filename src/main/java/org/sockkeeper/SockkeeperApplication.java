package org.sockkeeper;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import jakarta.websocket.server.ServerEndpointConfig;
import org.apache.curator.framework.CuratorFramework;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;
import org.sockkeeper.bootstrap.SockkeeperModule;
import org.sockkeeper.bootstrap.WebSocketConfigurator;
import org.sockkeeper.config.SockkeeperConfiguration;
import org.sockkeeper.health.Basic;
import org.sockkeeper.resources.v2.PublishResourceV2;
import org.sockkeeper.resources.v2.RegisterResourceV2;
import org.sockkeeper.resources.v3.PublishResourceV3;
import org.sockkeeper.resources.v3.RegisterResourceV3;


public class SockkeeperApplication extends Application<SockkeeperConfiguration> {

    public static void main(final String[] args) throws Exception {
        new SockkeeperApplication().run(args);
    }

    @Override
    public String getName() {
        return "Sockkeeper";
    }

    @Override
    public void initialize(final Bootstrap<SockkeeperConfiguration> bootstrap) {
        // TODO: application initialization
    }

    @Override
    public void run(final SockkeeperConfiguration configuration,
                    final Environment environment) {
        Injector injector = Guice.createInjector(new SockkeeperModule(configuration, environment));
        environment.healthChecks().register("basic", injector.getInstance(Basic.class));
//        environment.jersey().register(injector.getInstance(PublishResource.class));
        environment.jersey().register(injector.getInstance(PublishResourceV3.class));
        String hostname = injector.getInstance(Key.get(String.class, Names.named("hostname")));

        ServletContextHandler contextHandler = environment.getApplicationContext();
        CuratorFramework curatorFramework = injector.getInstance(CuratorFramework.class);
        JakartaWebSocketServletContainerInitializer.configure(contextHandler, (servletContext, wsContainer) -> {
            ServerEndpointConfig.Configurator configurator =
                    new WebSocketConfigurator(curatorFramework, configuration, hostname,
                            injector.getInstance(RegisterResourceV3.class));
            ServerEndpointConfig config = ServerEndpointConfig.Builder
                    .create(RegisterResourceV3.class, "/v3/register/{userId}")
                    .configurator(configurator)
                    .build();
            wsContainer.addEndpoint(config);
        });
    }

}
