package co.edu.escuelaing;

import java.io.File;
import java.lang.reflect.Method;

import co.edu.escuelaing.annotations.GetMapping;
import co.edu.escuelaing.annotations.RestController;
import co.edu.escuelaing.server.HttpServer;

public class MicroSpringBoot {
    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) throws Exception {
        HttpServer server = new HttpServer(resolvePort());

        if (args.length > 0) {
            // Versión 1: clase específica por argumento
            loadClass(args[0], server);
        } else {
            // Versión final: escanear classpath automáticamente
            scanClasspath(server);
        }

        server.start();
    }

    private static int resolvePort() {
        String portValue = System.getenv("PORT");
        if (portValue == null || portValue.isBlank()) {
            return DEFAULT_PORT;
        }

        try {
            return Integer.parseInt(portValue);
        } catch (NumberFormatException exception) {
            System.err.println("Invalid PORT value '" + portValue + "'. Falling back to " + DEFAULT_PORT + ".");
            return DEFAULT_PORT;
        }
    }

    private static void loadClass(String className, HttpServer server) throws Exception {
        Class<?> clazz = Class.forName(className);

        if (clazz.isAnnotationPresent(RestController.class)) {
            Object instance = clazz.getDeclaredConstructor().newInstance();

            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(GetMapping.class)) {
                    String path = method.getAnnotation(GetMapping.class).value();
                    server.registerRoute(path, method, instance);
                    System.out.println("Registered: GET " + path + " -> " + className);
                }
            }
        }
    }

    private static void scanClasspath(HttpServer server) throws Exception {
        String classpath = System.getProperty("java.class.path");
        for (String path : classpath.split(File.pathSeparator)) {
            File file = new File(path);
            if (file.isDirectory()) {
                scanDirectory(file, file, server);
            }
        }
    }

    private static void scanDirectory(File root, File dir, HttpServer server) throws Exception {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                scanDirectory(root, file, server);
            } else if (file.getName().endsWith(".class")) {
                String className = root.toURI().relativize(file.toURI())
                    .getPath()
                    .replace("/", ".")
                    .replace(".class", "");
                try {
                    loadClass(className, server);
                } catch (Exception ignored) {}
            }
        }
    }
}