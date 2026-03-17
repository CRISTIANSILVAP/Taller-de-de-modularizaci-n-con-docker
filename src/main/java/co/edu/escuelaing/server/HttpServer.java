package co.edu.escuelaing.server;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class HttpServer {
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

    private final int port;
    private final Map<String, Method> routes = new ConcurrentHashMap<>();
    private final Map<String, Object> controllers = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final int threadPoolSize;

    private volatile ServerSocket serverSocket;
    private volatile ExecutorService executorService;

    public HttpServer(int port) {
        this(port, Math.max(4, Runtime.getRuntime().availableProcessors() * 2));
    }

    public HttpServer(int port, int threadPoolSize) {
        this.port = port;
        this.threadPoolSize = threadPoolSize;
    }

    public void registerRoute(String path, Method method, Object instance) {
        routes.put(path, method);
        controllers.put(path, instance);
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("The server is already running");
        }

        executorService = Executors.newFixedThreadPool(threadPoolSize, new WorkerThreadFactory());
        serverSocket = new ServerSocket(port);
        registerShutdownHook();

        System.out.println("Server running on http://localhost:" + port);
        System.out.println("Worker pool size: " + threadPoolSize);

        try {
            while (running.get()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    if (!running.get()) {
                        clientSocket.close();
                        break;
                    }
                    try {
                        executorService.submit(new RequestHandler(clientSocket, routes, controllers));
                    } catch (RejectedExecutionException exception) {
                        clientSocket.close();
                    }
                } catch (IOException exception) {
                    if (running.get()) {
                        throw exception;
                    }
                }
            }
        } finally {
            shutdown();
        }
    }

    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        System.out.println("Shutting down server...");
        closeServerSocket();

        ExecutorService executor = executorService;
        if (executor == null) {
            return;
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                System.out.println("Forcing shutdown after timeout...");
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("Server stopped.");
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "micro-http-shutdown"));
    }

    private void closeServerSocket() {
        ServerSocket socket = serverSocket;
        if (socket == null || socket.isClosed()) {
            return;
        }

        try {
            socket.close();
        } catch (IOException exception) {
            System.err.println("Error closing server socket: " + exception.getMessage());
        }
    }

    private static final class WorkerThreadFactory implements ThreadFactory {
        private int counter = 1;

        @Override
        public synchronized Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "micro-http-worker-" + counter++);
            thread.setDaemon(false);
            return thread;
        }
    }
}