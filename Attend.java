import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Attend.java - a tiny attendance sign-in web service.
 *
 * Routes:
 *   GET  /        -> sign-in form (student name + student id)
 *   POST /attend  -> record an attendee, then redirect to /list
 *   GET  /list    -> show the attendee list
 *
 * The port is taken from the PORT environment variable (Render sets it),
 * defaulting to 10000 for local runs.
 */
public class Attend {

    /** One signed-in student. */
    static class Attendee {
        final String name;
        final String sid;
        final String time;

        Attendee(String name, String sid, String time) {
            this.name = name;
            this.sid = sid;
            this.time = time;
        }
    }

    /** All attendees so far (in-memory, thread-safe). */
    static final List<Attendee> ATTENDEES =
            Collections.synchronizedList(new ArrayList<>());

    static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "10000"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", Attend::handleIndex);
        server.createContext("/attend", Attend::handleAttend);
        server.createContext("/list", Attend::handleList);

        server.setExecutor(null);
        server.start();
        System.out.println("Attendance service started on port " + port);
    }

    /** GET / - the sign-in form. */
    private static void handleIndex(HttpExchange t) throws IOException {
        if (!"GET".equals(t.getRequestMethod())) {
            send(t, 405, "Method Not Allowed");
            return;
        }
        String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
                + "<title>Class Attendance</title>"
                + "<style>body{font-family:Arial,sans-serif;max-width:520px;margin:60px auto;"
                + "padding:0 16px;color:#222}input{display:block;width:100%;padding:8px;margin:8px 0 16px;"
                + "font-size:15px}button{background:#2563eb;color:#fff;border:0;padding:10px 22px;"
                + "font-size:15px;cursor:pointer}a{color:#2563eb}</style></head><body>"
                + "<h1>Class Attendance Sign-in</h1>"
                + "<p>Please enter your name and student ID to sign in.</p>"
                + "<form method=\"POST\" action=\"/attend\">"
                + "  <label>Student Name</label>"
                + "  <input name=\"name\" required placeholder=\"e.g. Zhang San\">"
                + "  <label>Student ID</label>"
                + "  <input name=\"sid\" required placeholder=\"e.g. 22110101\">"
                + "  <button type=\"submit\">Sign In</button>"
                + "</form>"
                + "<p><a href=\"/list\">View Attendee List &gt;&gt;</a></p>"
                + "</body></html>";
        send(t, 200, html);
    }

    /** POST /attend - record the attendee and redirect to the list. */
    private static void handleAttend(HttpExchange t) throws IOException {
        if ("POST".equals(t.getRequestMethod())) {
            String body = readBody(t);
            String name = urlDecode(extract(body, "name"));
            String sid = urlDecode(extract(body, "sid"));
            if (name.isEmpty() || sid.isEmpty()) {
                t.getResponseHeaders().set("Location", "/");
                t.sendResponseHeaders(303, -1);
                t.close();
                return;
            }
            synchronized (ATTENDEES) {
                ATTENDEES.add(new Attendee(name, sid, LocalDateTime.now().format(FMT)));
            }
            t.getResponseHeaders().set("Location", "/list");
            t.sendResponseHeaders(303, -1);
            t.close();
            return;
        }
        // GET on /attend just bounces back to the form
        t.getResponseHeaders().set("Location", "/");
        t.sendResponseHeaders(303, -1);
        t.close();
    }

    /** GET /list - the attendee list. */
    private static void handleList(HttpExchange t) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
          .append("<title>Attendee List</title>")
          .append("<style>body{font-family:Arial,sans-serif;max-width:640px;margin:40px auto;"
                + "padding:0 16px}table{border-collapse:collapse;width:100%}"
                + "td,th{border:1px solid #ccc;padding:8px;text-align:left}a{color:#2563eb}</style>")
          .append("</head><body><h1>Attendee List</h1>");

        List<Attendee> snapshot;
        synchronized (ATTENDEES) {
            snapshot = new ArrayList<>(ATTENDEES);
        }

        if (snapshot.isEmpty()) {
            sb.append("<p>No one has signed in yet.</p>");
        } else {
            sb.append("<p>Total: ").append(snapshot.size()).append("</p><table>")
              .append("<tr><th>#</th><th>Name</th><th>Student ID</th><th>Time</th></tr>");
            for (int i = 0; i < snapshot.size(); i++) {
                Attendee a = snapshot.get(i);
                sb.append("<tr><td>").append(i + 1)
                  .append("</td><td>").append(escape(a.name))
                  .append("</td><td>").append(escape(a.sid))
                  .append("</td><td>").append(escape(a.time))
                  .append("</td></tr>");
            }
            sb.append("</table>");
        }
        sb.append("<p><a href=\"/\">&lt;&lt; Back to Sign-in</a></p></body></html>");
        send(t, 200, sb.toString());
    }

    // ---------- small helpers ----------

    private static String readBody(HttpExchange t) throws IOException {
        try (InputStream in = t.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Extract one x-www-form-urlencoded field. */
    private static String extract(String body, String key) {
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(key)) {
                return pair.substring(eq + 1);
            }
        }
        return "";
    }

    private static String urlDecode(String s) {
        return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static void send(HttpExchange t, int code, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        t.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        t.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = t.getResponseBody()) {
            os.write(bytes);
        }
    }
}
