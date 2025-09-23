import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import server.config.ServerConfig;

public class ConnectionPoolFactory extends BasePooledObjectFactory<Channel> {
    private final ConnectionFactory factory = new ConnectionFactory();

    @Override
    public Channel create() throws Exception {
        factory.setHost(ServerConfig.getRabbitMqHost());
        factory.setPort(ServerConfig.getRabbitMqPort());
        factory.setUsername(ServerConfig.getRabbitMqUsername());
        factory.setPassword(ServerConfig.getRabbitMqPassword());

        Connection connection = factory.newConnection();
        return connection.createChannel();
    }

    @Override
    public PooledObject<Channel> wrap(Channel channel) {
        return new DefaultPooledObject<>(channel);
    }

    @Override
    public void destroyObject(PooledObject<Channel> p) throws Exception {
        p.getObject().close();
    }
}
