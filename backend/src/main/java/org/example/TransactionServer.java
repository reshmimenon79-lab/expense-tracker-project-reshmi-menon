
package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TransactionServer {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String PATH = "/api/transactions";

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/uuuu")
                    .withResolverStyle(ResolverStyle.STRICT);

    public static void main(String[] args) throws Exception {

        try (Connection connection = Database.getConnection()) {
            System.out.println("PostgreSQL connected successfully!");
        }

        HttpServer server = HttpServer.create(
                new InetSocketAddress(8080), 0);

        server.createContext(PATH, TransactionServer::handleRequest);
        server.start();

        System.out.println(
                "REST API running at http://localhost:8080");
        System.out.println(
                "Transactions API: http://localhost:8080" + PATH);
    }

    private static void handleRequest(HttpExchange exchange)
            throws IOException {

        try {
            exchange.getResponseHeaders().set(
                    "Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set(
                    "Access-Control-Allow-Methods",
                    "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set(
                    "Access-Control-Allow-Headers", "Content-Type");

            if (exchange.getRequestMethod().equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            String suffix = path.substring(PATH.length());
            Long id = null;

            if (!suffix.isEmpty()) {
                if (!suffix.matches("/\\d+")) {
                    send(exchange, 400,
                            Map.of("error", "Invalid transaction ID"));
                    return;
                }

                try {
                    id = Long.parseLong(suffix.substring(1));
                } catch (NumberFormatException e) {
                    send(exchange, 400,
                            Map.of("error", "Invalid transaction ID"));
                    return;
                }
            }

            switch (method) {
                case "GET" -> {
                    if (id == null) {
                        send(exchange, 200, getAll());
                    } else {
                        Map<String, Object> item = getOne(id);

                        if (item == null) {
                            send(exchange, 404,
                                    Map.of("error",
                                            "Transaction not found"));
                        } else {
                            send(exchange, 200, item);
                        }
                    }
                }

                case "POST" -> {
                    if (id != null) {
                        send(exchange, 400,
                                Map.of("error",
                                        "Do not include an ID when adding"));
                        return;
                    }

                    Map<String, Object> data = readBody(exchange);
                    long newId = insert(data);
                    send(exchange, 201, getOne(newId));
                }

                case "PUT" -> {
                    if (id == null) {
                        send(exchange, 400,
                                Map.of("error",
                                        "Transaction ID is required"));
                        return;
                    }

                    Map<String, Object> data = readBody(exchange);

                    if (update(id, data)) {
                        send(exchange, 200, getOne(id));
                    } else {
                        send(exchange, 404,
                                Map.of("error",
                                        "Transaction not found"));
                    }
                }

                case "DELETE" -> {
                    if (id == null) {
                        send(exchange, 400,
                                Map.of("error",
                                        "Transaction ID is required"));
                        return;
                    }

                    if (delete(id)) {
                        exchange.sendResponseHeaders(204, -1);
                    } else {
                        send(exchange, 404,
                                Map.of("error",
                                        "Transaction not found"));
                    }
                }

                default -> send(exchange, 405,
                        Map.of("error", "Method not allowed"));
            }

        } catch (IllegalArgumentException e) {
            send(exchange, 400,
                    Map.of("error", e.getMessage() == null
                            ? "Invalid request" : e.getMessage()));

        } catch (Exception e) {
            e.printStackTrace();

            try {
                send(exchange, 500,
                        Map.of("error",
                                "Server error. Check the Java console."));
            } catch (IOException ignored) {
                // The response may already have been closed.
            }

        } finally {
            exchange.close();
        }
    }

    private static Map<String, Object> readBody(
            HttpExchange exchange) throws IOException {

        return mapper.readValue(
                exchange.getRequestBody(),
                new TypeReference<Map<String, Object>>() {});
    }

    private static void validate(Map<String, Object> data) {

        Object type = data.get("type");
        Object amount = data.get("amount");
        Object category = data.get("category");
        Object date = data.get("date");

        if (!"income".equals(type) && !"expense".equals(type)) {
            throw new IllegalArgumentException(
                    "Type must be income or expense");
        }

        try {
            if (amount == null) {
                throw new IllegalArgumentException(
                        "Amount is required");
            }

            BigDecimal value = new BigDecimal(amount.toString());

            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "Amount must be greater than zero");
            }

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount");
        }

        if (!(category instanceof String)
                || category.toString().isBlank()) {
            throw new IllegalArgumentException(
                    "Category is required");
        }

        parseDate(date);
    }

    private static LocalDate parseDate(Object date) {

        if (date == null || date.toString().isBlank()) {
            throw new IllegalArgumentException("Date is required");
        }

        try {
            return LocalDate.parse(date.toString(), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Date must use DD/MM/YYYY format");
        }
    }

    private static long insert(Map<String, Object> data)
            throws SQLException {

        validate(data);

        String sql = """
                INSERT INTO transactions
                    (type, amount, category, transaction_date, description)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """;

        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            fillStatement(ps, data, 1);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException(
                            "Transaction was not inserted");
                }
                return rs.getLong("id");
            }
        }
    }

    private static boolean update(long id, Map<String, Object> data)
            throws SQLException {

        validate(data);

        String sql = """
                UPDATE transactions
                SET type = ?, amount = ?, category = ?,
                    transaction_date = ?, description = ?
                WHERE id = ?
                """;

        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            fillStatement(ps, data, 1);
            ps.setLong(6, id);

            return ps.executeUpdate() > 0;
        }
    }

    private static void fillStatement(
            PreparedStatement ps,
            Map<String, Object> data,
            int start) throws SQLException {

        ps.setString(start, data.get("type").toString());

        ps.setBigDecimal(start + 1,
                new BigDecimal(data.get("amount").toString()));

        ps.setString(start + 2, data.get("category").toString());

        ps.setDate(start + 3,
                java.sql.Date.valueOf(parseDate(data.get("date"))));

        Object description = data.get("description");

        if (description == null) {
            ps.setNull(start + 4, Types.VARCHAR);
        } else {
            ps.setString(start + 4, description.toString());
        }
    }

    private static boolean delete(long id) throws SQLException {

        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM transactions WHERE id = ?")) {

            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    private static List<Map<String, Object>> getAll()
            throws SQLException {

        List<Map<String, Object>> items = new ArrayList<>();

        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT * FROM transactions ORDER BY id DESC");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                items.add(toMap(rs));
            }
        }

        return items;
    }

    private static Map<String, Object> getOne(long id)
            throws SQLException {

        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT * FROM transactions WHERE id = ?")) {

            ps.setLong(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? toMap(rs) : null;
            }
        }
    }

    private static Map<String, Object> toMap(ResultSet rs)
            throws SQLException {

        Map<String, Object> item = new LinkedHashMap<>();

        item.put("id", rs.getLong("id"));
        item.put("type", rs.getString("type"));
        item.put("amount", rs.getBigDecimal("amount"));
        item.put("category", rs.getString("category"));

        item.put("date",
                rs.getDate("transaction_date")
                        .toLocalDate()
                        .format(DATE_FORMAT));

        item.put("description", rs.getString("description"));

        return item;
    }

    private static void send(
            HttpExchange exchange,
            int status,
            Object data) throws IOException {

        byte[] response = mapper.writeValueAsBytes(data);

        exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8");

        exchange.sendResponseHeaders(status, response.length);

        exchange.getResponseBody().write(response);
    }
}
