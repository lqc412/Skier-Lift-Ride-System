import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rabbitmq.client.Channel;

import entity.LiftRide;
import entity.ResponseMsg;
import entity.SkierVertical;
import entity.VerticalElement;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "SkierServlet", value = "/skiers/*")
public class SkierServlet extends HttpServlet {
    private static final Logger logger = Logger.getLogger(SkierServlet.class.getName());
    private final Gson gson = new Gson();
    private ObjectPool<Channel> pool;
    private JedisPool jedisPool;
    private final static String QUEUE_NAME = "SkierServletPostQueue";
    private final static String DEFAULT_REDIS_URI = "redis://lqc412:Password@35.165.107.222:6379";

    public void init() {
        this.pool = new GenericObjectPool<>(new ConnectionPoolFactory());
        this.jedisPool = buildJedisPool();
        logger.info("SkierServlet initialized with a channel pool and Redis connection pool.");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        String urlPath = req.getPathInfo();

        if (urlPath == null || urlPath.isEmpty()) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            res.getWriter().write(gson.toJson(new ResponseMsg("Missing Parameter")));
            logger.warning("GET request with missing parameters.");
            return;
        }

        String[] urlParts = urlPath.split("/");
        if (!isUrlValid(urlParts)) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            logger.warning("GET request with invalid URL: " + urlPath);
        } else {
            res.setStatus(HttpServletResponse.SC_OK);
            if (urlParts.length == 3) {
                String skierId = urlParts[1];
                SkierVertical skierVertical = fetchTotalVertical(skierId);
                res.getWriter().write(gson.toJson(skierVertical));
                logger.info("Responded with SkierVertical data for URL: " + urlPath);
            } else {
                int resortId = Integer.parseInt(urlParts[1]);
                String seasonId = urlParts[3];
                String dayId = urlParts[5];
                String skierId = urlParts[7];
                SkierVertical skierVertical = fetchDailyVertical(resortId, seasonId, dayId, skierId);
                res.getWriter().write(gson.toJson(skierVertical));
                logger.info("GET request successful for URL: " + urlPath);
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        String urlPath = req.getPathInfo();
        logger.info("Received POST request at URL: " + urlPath);

        if (urlPath == null || urlPath.isEmpty()) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            res.getWriter().write("missing parameters");
            logger.warning("POST request with missing parameters.");
            return;
        }

        String[] urlParts = urlPath.split("/");
        if (!isUrlValid(urlParts)) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            ResponseMsg msg = new ResponseMsg("NOT FOUND");
            res.getWriter().write(gson.toJson(msg));
            logger.warning("POST request with invalid URL: " + urlPath);
        } else {
            try {
                StringBuilder sb = new StringBuilder();
                String s;
                while ((s = req.getReader().readLine()) != null) {
                    sb.append(s);
                }

                logger.info("POST request body: " + sb.toString());
                LiftRide liftRide = gson.fromJson(sb.toString(), LiftRide.class);
                logger.info("Parsed LiftRide data: " + liftRide.toString());

                int resortID = Integer.parseInt(urlParts[1]);
                String seasonID = urlParts[3];
                String dayID = urlParts[5];
                int skierID = Integer.parseInt(urlParts[7]);

                JsonObject liftInfo = new JsonObject();
                liftInfo.addProperty("time", liftRide.getTime());
                liftInfo.addProperty("liftID", liftRide.getLiftID());
                liftInfo.addProperty("skierID", skierID);
                liftInfo.addProperty("resortID", resortID);
                liftInfo.addProperty("seasonID", seasonID);
                liftInfo.addProperty("day", dayID);

                Channel channel = null;
                try {
                    channel = pool.borrowObject();
                    channel.queueDeclare(QUEUE_NAME, false, false, false, null);
                    channel.basicPublish("", QUEUE_NAME, null, liftInfo.toString().getBytes());
                    logger.info("Message published to queue: " + QUEUE_NAME + " with data: " + liftInfo);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Unable to borrow channel from pool", e);
                    throw new RuntimeException("Unable to borrow from pool", e);
                } finally {
                    if (channel != null) {
                        pool.returnObject(channel);
                        logger.info("Channel returned to pool successfully.");
                    }
                }
                res.setStatus(HttpServletResponse.SC_CREATED);
            } catch (Exception ex) {
                res.setStatus(HttpServletResponse.SC_NOT_FOUND);
                logger.log(Level.SEVERE, "Error processing POST request", ex);
            }
        }
    }


    private boolean isUrlValid(String[] urlPath) {
        if (urlPath.length == 3) {
            return urlPath[1].chars().allMatch(Character::isDigit) && urlPath[2].contains("vertical");
        } else if (urlPath.length == 8) {
            return urlPath[1].chars().allMatch(Character::isDigit) && urlPath[2].equals("seasons") &&
                    urlPath[3].chars().allMatch(Character::isDigit) && urlPath[4].equals("days") &&
                    urlPath[5].chars().allMatch(Character::isDigit) && urlPath[6].equals("skiers") &&
                    urlPath[7].chars().allMatch(Character::isDigit) && Integer.parseInt(urlPath[5]) >= 1 &&
                    Integer.parseInt(urlPath[5]) <= 365;
        }
        return false;
    }

    private JedisPool buildJedisPool() {
        String redisUri = Optional.ofNullable(System.getenv("REDIS_URI"))
                .filter(s -> !s.isEmpty())
                .orElse(DEFAULT_REDIS_URI);
        try {
            return new JedisPool(new JedisPoolConfig(), new URI(redisUri));
        } catch (URISyntaxException e) {
            logger.log(Level.SEVERE, "Invalid Redis URI", e);
            throw new RuntimeException("Unable to configure Redis connection", e);
        }
    }

    private SkierVertical fetchTotalVertical(String skierId) {
        Map<String, Integer> seasonTotals = new HashMap<>();
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> seasonDays = jedis.smembers("skier:" + skierId + ":days");
            for (String seasonDay : seasonDays) {
                String seasonId = "TOTAL";
                String dayId = seasonDay;
                if (seasonDay.contains("|")) {
                    String[] parts = seasonDay.split("\\|", 2);
                    seasonId = parts[0];
                    dayId = parts[1];
                }
                String verticalKey = "skier:" + skierId + ":day:" + dayId + ":vertical";
                String verticalString = jedis.get(verticalKey);
                if (verticalString == null) {
                    continue;
                }
                int vertical = Integer.parseInt(verticalString);
                seasonTotals.merge(seasonId, vertical, Integer::sum);
            }
        }

        if (seasonTotals.isEmpty()) {
            seasonTotals.put("TOTAL", 0);
        }

        List<VerticalElement> elements = new ArrayList<>();
        seasonTotals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> elements.add(new VerticalElement(entry.getKey(), entry.getValue())));
        return new SkierVertical(elements);
    }

    private SkierVertical fetchDailyVertical(int resortId, String seasonId, String dayId, String skierId) {
        int dailyVertical = 0;
        try (Jedis jedis = jedisPool.getResource()) {
            boolean visited = jedis.sismember("resort:" + resortId + ":day:" + dayId + ":visitors", skierId);
            if (!visited) {
                return new SkierVertical(Collections.singletonList(new VerticalElement(seasonId, 0)));
            }
            String verticalString = jedis.get("skier:" + skierId + ":day:" + dayId + ":vertical");
            if (verticalString != null) {
                dailyVertical = Integer.parseInt(verticalString);
            }
        }
        return new SkierVertical(Collections.singletonList(new VerticalElement(seasonId, dailyVertical)));
    }

    @Override
    public void destroy() {
        super.destroy();
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}
