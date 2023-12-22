package ru.chirkov;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;

public class Server {

    // message broker (kafka, redis, rabbitmq, ...)
    // client sent letter to broker

    // server sent to SMTP-server

    public static final int PORT = 8181;

    private static long clientIdCounter = 1L;
    private static Map<Long, SocketWrapper> clients = new HashMap<>();

    public static void main(String[] args) throws IOException {
        try (ServerSocket server = new ServerSocket(PORT)) {
            System.out.println("Сервер запущен на порту " + PORT);
            while (!server.isClosed()) {
                final Socket client = server.accept();
                final long clientId = clientIdCounter++;

                SocketWrapper wrapper = new SocketWrapper(clientId, client);
                System.out.println("Подключился новый клиент[" + wrapper + "]");
                clients.put(clientId, wrapper);

                new Thread(() -> {
                    try (Scanner input = wrapper.getInput(); PrintWriter output = wrapper.getOutput()) {
                        output.println("Подключение успешно. Список всех клиентов: " + clients);
//                         clientInput = "";
                        while (!server.isClosed()) {
                            String clientInput = input.nextLine().strip();
                            if (Objects.equals("q", clientInput)) {
                                clients.values().forEach(it -> {
                                    if (!(it.getId() == clientId)) {
                                        it.getOutput().println("Клиент[" + clientId + "] отключился");
                                    }
                                });
                                clients.remove(clientId);
                                break;
                            }

                            if (clientInput.equals("$sudo")) {
                                wrapper.setAdmin(true);
                                output.println("administrator mode: " + wrapper.isAdmin());
                            }
                            if (clientInput.equals("&sudo")) {
                                wrapper.setAdmin(false);
                                output.println("administrator mode: " + wrapper.isAdmin());
                            }
                            // формат сообщения: "цифра сообщение"
                            if (clientInput.startsWith("@")) {
                                String[] msg = clientInput.split(" ");
                                long destinationId = Long.parseLong(msg[0].replace("@", ""));
                                if (clients.containsKey(destinationId)) {
                                    msg[0] = "@" + clientId + " private: ";
                                    clients.get(destinationId).getOutput().println(client + ":\n {\n" +
                                            String.join(" ", msg) + "\n}");
                                } else {
                                    output.println("Клиент с id " + destinationId + " не найден");
                                }
                            } else if (!(clientInput.startsWith("$") && !(clientInput.startsWith("%")))) {
                                clients.values().forEach(it -> {
                                    if (!(it.getId() == clientId)) {
                                        it.getOutput().println(client + " : @" + clientId + "\n" + clientInput);
                                    }
                                });
                            }

                            if (clientInput.startsWith("%") && wrapper.isAdmin()) {
                                String temp = clientInput.replace("%", "");
                                if (temp.startsWith("kick")) {
                                    temp = temp.split(" ")[1];
                                    if (temp.matches("^\\d+$")) {
                                        long tempId = Long.parseLong(temp);
                                        clients.values().forEach(us -> {
                                            if (us.getId() == tempId) {
                                                try {
                                                    us.close();
                                                    clients.values().forEach(it -> {
                                                        if (!(it.getId() == clientId)) {
                                                            it.getOutput().println("Клиент[" + us + "] отключился");
                                                        }
                                                    });
                                                    System.out.println("Отключился клиент[" + us + "]");

                                                } catch (Exception e) {
                                                    throw new RuntimeException(e);
                                                }
                                            }
                                        });
                                    }
                                }
                            }
                        }
                    }
                }).start();
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
