package co.edu.escuelaing.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
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
            PrintWriter output = new PrintWriter(socket.getOutputStream(), false)
        ) {
            String requestLine = input.readLine();
            if (requestLine == null || requestLine.isBlank()) {
                return;
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                sendTextResponse(output, 400, "<h1>400 - Bad Request</h1>");
                return;
            }

            String fullPath = parts[1];
            String path = fullPath.contains("?") ? fullPath.substring(0, fullPath.indexOf('?')) : fullPath;
            String query = fullPath.contains("?") ? fullPath.substring(fullPath.indexOf('?') + 1) : "";

            // Primero intenta servir un archivo estático
            if (serveStaticFile(output, path)) {
                return;
            }

            // Si no es un archivo estático, busca en los controladores
            Method method = routes.get(path);
            Object controller = controllers.get(path);

            if (method == null || controller == null) {
                sendTextResponse(output, 404, "<h1>404 - Not Found</h1>");
                return;
            }

            try {
                String response = invokeMethod(method, controller, query);
                sendTextResponse(output, 200, response);
            } catch (Exception exception) {
                sendTextResponse(output, 500, "<h1>500 - Internal Server Error</h1><p>" + exception.getMessage() + "</p>");
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

    private void sendTextResponse(PrintWriter output, int statusCode, String body) {
        output.println("HTTP/1.1 " + statusCode + " " + reasonPhrase(statusCode));
        output.println("Content-Type: text/html; charset=UTF-8");
        output.println("Connection: close");
        output.println();
        output.println(body);
        output.flush();
    }

    private boolean serveStaticFile(PrintWriter output, String path) throws IOException {
        String filePath = "public" + (path.equals("/") ? "/index.html" : path);
        File file = new File(filePath);

        if (!file.exists() || !file.isFile()) {
            return false;
        }

        byte[] fileContent = Files.readAllBytes(file.toPath());
        String mimeType = getMimeType(file.getName());

        output.println("HTTP/1.1 200 OK");
        output.println("Content-Type: " + mimeType);
        output.println("Content-Length: " + fileContent.length);
        output.println("Connection: close");
        output.println();
        output.flush();

        try {
            clientSocket.getOutputStream().write(fileContent);
            clientSocket.getOutputStream().flush();
        } catch (IOException exception) {
            System.err.println("Error writing file content: " + exception.getMessage());
        }

        return true;
    }

    private String getMimeType(String fileName) {
        if (fileName.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        } else if (fileName.endsWith(".png")) {
            return "image/png";
        } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (fileName.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        } else if (fileName.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        } else if (fileName.endsWith(".json")) {
            return "application/json; charset=UTF-8";
        } else if (fileName.endsWith(".gif")) {
            return "image/gif";
        } else if (fileName.endsWith(".svg")) {
            return "image/svg+xml; charset=UTF-8";
        } else if (fileName.endsWith(".pdf")) {
            return "application/pdf";
        }
        return "application/octet-stream";
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