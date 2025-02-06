package org.sockkeeper.bootstrap;

import jakarta.websocket.server.ServerEndpointConfig;
import org.apache.curator.framework.CuratorFramework;
import org.sockkeeper.config.SockkeeperConfiguration;
import org.sockkeeper.resources.v4.RegisterResourceV4;

public class WebSocketConfigurator extends ServerEndpointConfig.Configurator {

    private CuratorFramework curatorFramework;
    private SockkeeperConfiguration configuration;
    private String hostname;
    private RegisterResourceV4 registerResourceV4;

    public WebSocketConfigurator(CuratorFramework curatorFramework,
                                 SockkeeperConfiguration configuration, String hostname, RegisterResourceV4 registerResourceV4) {
        this.curatorFramework = curatorFramework;
        this.configuration = configuration;
        this.hostname = hostname;
        this.registerResourceV4 = registerResourceV4;
    }

    @Override
    public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
        if (endpointClass == RegisterResourceV4.class) {
            try {
                return endpointClass.cast(registerResourceV4);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return super.getEndpointInstance(endpointClass);
    }
}