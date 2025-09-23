import com.rabbitmq.client.Channel;
import org.apache.commons.pool2.ObjectPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SkierServletTest {

    private SkierServlet servlet;
    private ObjectPool<Channel> pool;
    private Channel channel;

    @BeforeEach
    void setUp() {
        servlet = new SkierServlet();
        pool = mock(ObjectPool.class);
        channel = mock(Channel.class);
        servlet.setChannelPool(pool);
    }

    @Test
    void doPostPublishesLiftRideToQueue() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        when(request.getPathInfo()).thenReturn("/1/seasons/2024/days/7/skiers/12");
        when(pool.borrowObject()).thenReturn(channel);
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{\"time\":15,\"liftID\":3}")));

        StringWriter responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));

        servlet.doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_CREATED);
        verify(channel).queueDeclare("SkierServletPostQueue", false, false, false, null);
        ArgumentCaptor<byte[]> messageCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(channel).basicPublish(eq(""), eq("SkierServletPostQueue"), isNull(), messageCaptor.capture());
        verify(pool).returnObject(channel);

        String payload = new String(messageCaptor.getValue());
        assertThat(payload).contains("\"skierID\":12");
        assertThat(payload).contains("\"liftID\":3");
        assertThat(payload).contains("\"resortID\":1");
    }

    @Test
    void doPostRejectsInvalidPath() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        when(request.getPathInfo()).thenReturn("/invalid");
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{}")));

        StringWriter responseWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(responseWriter);
        when(response.getWriter()).thenReturn(writer);

        servlet.doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
        writer.flush();
        assertThat(responseWriter.toString()).contains("NOT FOUND");
        verify(pool, never()).borrowObject();
    }

    @Test
    void doGetReturnsVerticalPayload() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        when(request.getPathInfo()).thenReturn("/12/vertical");

        StringWriter responseWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(responseWriter);
        when(response.getWriter()).thenReturn(writer);

        servlet.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        writer.flush();
        assertThat(responseWriter.toString()).contains("SkierVertical");
    }
}
