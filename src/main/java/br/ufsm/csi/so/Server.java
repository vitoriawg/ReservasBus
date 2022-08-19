package br.ufsm.csi.so;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.logging.FileHandler;

import lombok.SneakyThrows;
import org.w3c.dom.ls.LSOutput;

// classe inicial, cria o servidor e espera conexões
public class Server {


    public static Map<Integer, Assento> assentos = new HashMap<>();
    public static Semaphore mutex = new Semaphore(1);

    public static Logger logger = new Logger();

    @SneakyThrows
    public static void main(String[] args) {
        System.out.println("servidor entrou");
        // gerar 24 assentos
        for (int id = 1; id < 19; id++) {
            assentos.put(id, new Assento(id));
        }

        try (ServerSocket server = new ServerSocket(8080)) {
            System.out.println("Rodando servidor em http://localhost:8080");

            while (true) {
                // espera uma conexão e aceita ela
                Socket socket = server.accept();

                // criar um novo objeto de conexão - é um Runnable
                Connection connection = new Connection(socket);

                // passar o runnable pro thread
                Thread thread = new Thread(connection);

                thread.start();
            }
        }
    }
}
