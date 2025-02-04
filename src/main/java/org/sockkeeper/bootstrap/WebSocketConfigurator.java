package org.sockkeeper.bootstrap;

import jakarta.websocket.server.ServerEndpointConfig;
import org.apache.curator.framework.CuratorFramework;
import org.sockkeeper.config.SockkeeperConfiguration;
import org.sockkeeper.resources.v3.RegisterResourceV3;

public class WebSocketConfigurator extends ServerEndpointConfig.Configurator {

    private CuratorFramework curatorFramework;
    private SockkeeperConfiguration configuration;
    private String hostname;
    private RegisterResourceV3 registerResourceV3;

    public WebSocketConfigurator(CuratorFramework curatorFramework,
                                 SockkeeperConfiguration configuration, String hostname, RegisterResourceV3 registerResourceV3) {
        this.curatorFramework = curatorFramework;
        this.configuration = configuration;
        this.hostname = hostname;
        this.registerResourceV3 = registerResourceV3;
    }

    @Override
    public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
        if (endpointClass == RegisterResourceV3.class) {
            try {
                return endpointClass.cast(registerResourceV3);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return super.getEndpointInstance(endpointClass);
    }
}