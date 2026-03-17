package co.edu.escuelaing.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import co.edu.escuelaing.annotations.RequestParam;

public class RequestHandler implements Runnable {
    private final Socket clientSocket;
    private final Map<String, Method> routes;
    private final Map<String, Object> controllers;

    public RequestHandler(Socket clientSocket, Map<String, Method> routes, Map<String, Object> controllers) {
        this.clientSocket = clientSocket;
        this.routes = routes;
        this.controllers = controllers;
    }

    @Override
    public void run() {
        try (
            Socket socket = clientSocket;
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter output = new PrintWriter(socket.getOutputStream(), false, StandardCharsets.UTF_8)
        ) {
            String requestLine = input.readLine();
            if (requestLine == null || requestLine.isBlank()) {
                return;
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                sendResponse(output, 400, "<h1>400 - Bad Request</h1>");
                return;
            }

            String fullPath = parts[1];
            String path = fullPath.contains("?") ? fullPath.substring(0, fullPath.indexOf('?')) : fullPath;
            String query = fullPath.contains("?") ? fullPath.substring(fullPath.indexOf('?') + 1) : "";

            Method method = routes.get(path);
            Object controller = controllers.get(path);

            if (method == null || controller == null) {
                sendResponse(output, 404, "<h1>404 - Not Found</h1>");
                return;
            }

            try {
                String response = invokeMethod(method, controller, query);
                sendResponse(output, 200, response);
            } catch (Exception exception) {
                sendResponse(output, 500, "<h1>500 - Internal Server Error</h1><p>" + exception.getMessage() + "</p>");
            }
        } catch (IOException exception) {
            System.err.println("Error handling request: " + exception.getMessage());
        }
    }

    private String invokeMethod(Method method, Object instance, String query) throws Exception {
        Map<String, String> params = parseQuery(query);
        Parameter[] parameters = method.getParameters();
        Object[] arguments = new Object[parameters.length];

        for (int index = 0; index < parameters.length; index++) {
            RequestParam requestParam = parameters[index].getAnnotation(RequestParam.class);
            if (requestParam != null) {
                arguments[index] = params.getOrDefault(requestParam.value(), requestParam.defaultValue());
            }
        }

        return (String) method.invoke(instance, arguments);
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) {
            return params;
        }

        for (String pair : query.split("&")) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                params.put(decode(keyValue[0]), decode(keyValue[1]));
            }
        }
        return params;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private void sendResponse(PrintWriter output, int statusCode, String body) {
        output.println("HTTP/1.1 " + statusCode + " " + reasonPhrase(statusCode));
        output.println("Content-Type: text/html; charset=UTF-8");
        output.println("Connection: close");
        output.println();
        output.println(body);
        output.flush();
    }

    private String reasonPhrase(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 500 -> "Internal Server Error";
            default -> "OK";
        };
    }
}