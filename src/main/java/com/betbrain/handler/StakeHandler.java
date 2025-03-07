package com.betbrain.handler;

import com.betbrain.server.Handler;
import com.betbrain.service.SessionService;
import com.betbrain.service.StakeService;
import com.betbrain.util.HttpUtil;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles stake submission requests and validation
 */
public class StakeHandler implements Handler {

    private static final Logger logger = Logger.getLogger(StakeHandler.class.getName());

    private final SessionService sessionService = SessionService.getInstance();
    private final StakeService stakeService = StakeService.getInstance();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            // Validate request structure
            String path = exchange.getRequestURI().getPath();
            int betId = parseBetIdFromPath(path);
            String sessionKey = extractSessionKey(exchange);

            // Authenticate session
            int customerId = validateSession(sessionKey);

            // Process stake amount
            int stakeAmount = readStakeAmount(exchange);

            // Record the stake
            stakeService.recordStake(betId, customerId, stakeAmount);

            HttpUtil.sendResponse(exchange,HttpUtil.HTTP_OK,null);
            logger.log(Level.INFO, "Stake recorded - BetID: {0}, Customer: {1}, Amount: {2}",
                    new Object[]{betId, customerId, stakeAmount});
        } catch (IllegalArgumentException e) {
            HttpUtil.sendResponse(exchange, HttpUtil.BAD_REQUEST, "Invalid request: " + e.getMessage());
        } catch (SecurityException e) {
            HttpUtil.sendResponse(exchange, HttpUtil.UNAUTHORIZED, "Authentication failed");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Stake processing error", e);
            HttpUtil.sendResponse(exchange, HttpUtil.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    private int parseBetIdFromPath(String path) {
        String[] segments = path.split("/");
        if (segments.length < 3) {
            throw new IllegalArgumentException("Invalid path format");
        }

        try {
            return Integer.parseUnsignedInt(segments[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid bet ID format");
        }
    }

    private String extractSessionKey(HttpExchange exchange) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null || !query.startsWith("sessionkey=")) {
            throw new IllegalArgumentException("Missing session key");
        }
        return query.substring("sessionkey=".length());
    }

    private int validateSession(String sessionKey) {
        int customerId = sessionService.getCustomerIdBySessionKey(sessionKey);
        if (customerId == -1 || !sessionService.isValidSession(sessionKey)) {
            logger.warning("Invalid session attempt: " + sessionKey);
            throw new SecurityException("Invalid session");
        }
        return customerId;
    }

    private int readStakeAmount(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream is = exchange.getRequestBody()) {
            byte[] data = new byte[1024];
            int bytesRead;
            while ((bytesRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, bytesRead);
            }

            String amountStr = buffer.toString("UTF-8").trim();
            return Integer.parseUnsignedInt(amountStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid stake amount format");
        }
    }


}