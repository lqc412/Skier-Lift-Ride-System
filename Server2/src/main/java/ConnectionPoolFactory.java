import com.rabbitmq.client.*;
import config.AppConfig;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

public class ConnectionPoolFactory extends BasePooledObjectFactory<Channel> {
    ConnectionFactory factory = new ConnectionFactory();

    @Override
    public Channel create() throws Exception {
        factory.setHost(AppConfig.getRabbitHost());
        factory.setPort(AppConfig.getRabbitPort());
        factory.setUsername(AppConfig.getRabbitUsername());
        factory.setPassword(AppConfig.getRabbitPassword());

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
