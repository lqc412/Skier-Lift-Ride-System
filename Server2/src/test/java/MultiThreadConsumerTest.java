import com.google.gson.Gson;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class MultiThreadConsumerTest {

    @Test
    void deliverCallbackProcessesMessageAndAcknowledges() throws Exception {
        Channel channel = mock(Channel.class);
        JedisPool jedisPool = mock(JedisPool.class);
        Jedis jedis = mock(Jedis.class);
        Pipeline pipeline = mock(Pipeline.class);
        when(jedisPool.getResource()).thenReturn(jedis);
        when(jedis.pipelined()).thenReturn(pipeline);

        DeliverCallback callback = MultiThreadConsumer.createDeliverCallback(channel, jedisPool, new Gson());

        String message = "{\"skierID\":5,\"day\":\"10\",\"liftID\":3,\"resortID\":\"77\"}";
        Delivery delivery = new Delivery(new Envelope(42L, false, "", ""), new AMQP.BasicProperties(),
                message.getBytes(StandardCharsets.UTF_8));

        callback.handle("consumer", delivery);

        verify(pipeline).sadd("skier:5:days", "10");
        verify(pipeline).incrBy("skier:5:day:10:vertical", 30L);
        verify(pipeline).rpush("skier:5:day:10:lifts", "3");
        verify(pipeline).sadd("resort:77:day:10:visitors", "5");
        verify(pipeline).sync();
        verify(channel).basicAck(42L, false);
        verify(jedis).close();
    }

    @Test
    void deliverCallbackRejectsMessagesOnFailure() throws Exception {
        Channel channel = mock(Channel.class);
        JedisPool jedisPool = mock(JedisPool.class);
        Jedis jedis = mock(Jedis.class);
        Pipeline pipeline = mock(Pipeline.class);
        when(jedisPool.getResource()).thenReturn(jedis);
        when(jedis.pipelined()).thenReturn(pipeline);
        doThrow(new RuntimeException("boom")).when(pipeline).sync();

        DeliverCallback callback = MultiThreadConsumer.createDeliverCallback(channel, jedisPool, new Gson());

        String message = "{\"skierID\":1,\"day\":\"2\",\"liftID\":4,\"resortID\":\"100\"}";
        Delivery delivery = new Delivery(new Envelope(7L, false, "", ""), new AMQP.BasicProperties(),
                message.getBytes(StandardCharsets.UTF_8));

        callback.handle("consumer", delivery);

        verify(channel).basicNack(7L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(jedis).close();
    }
}
