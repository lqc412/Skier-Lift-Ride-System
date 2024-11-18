import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rabbitmq.client.Channel;

import entity.LiftRide;
import entity.ResponseMsg;
import entity.SkierVertical;
import entity.VerticalElement;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "SkierServlet", value = "/skiers/*")
public class SkierServlet extends HttpServlet {
    private static final Logger logger = Logger.getLogger(SkierServlet.class.getName());
    private Gson gson = new Gson();
    private ObjectPool<Channel> pool;
    private final static String QUEUE_NAME = "SkierServletPostQueue";

    public void init() {
        this.pool = new GenericObjectPool<>(new ConnectionPoolFactory());
        logger.info("SkierServlet initialized with a channel pool.");
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
                List<VerticalElement> vrl = new ArrayList<>();
                vrl.add(new VerticalElement("string", 32));
                SkierVertical skierVertical = new SkierVertical(vrl);
                res.getWriter().write(gson.toJson(skierVertical));
                logger.info("Responded with SkierVertical data for URL: " + urlPath);
            } else {
                res.getWriter().write("it works001");
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
}
