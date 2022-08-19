package br.ufsm.csi.so;

import java.io.File;
import java.io.FileWriter;
import java.net.Socket;
import java.util.concurrent.Semaphore;

import lombok.SneakyThrows;

// classe que gerencia as entradas no log
public class Logger {
    private String logString = "";
    private Semaphore vazio = new Semaphore(1000);
    private Semaphore cheio = new Semaphore(0);
    private Semaphore mutex = new Semaphore(1);

    private File file = new File("log.txt");

    private Socket socket;
    private Assento assento;

    @SneakyThrows
    public Logger() {
        if (file.createNewFile()) {
            System.out.println("Criado arquivo de log: " + this.file.getName());
        }
    }

    public void log(Socket socket, Assento assento) {
        this.socket = socket;
        this.assento = assento;

        Thread produz = new Thread(new ProduzLog());
        Thread armazena = new Thread(new ArmazenaLog());

        produz.start();
        armazena.start();
    }

    // implementação do Produtores-Consumidores
    private class ProduzLog implements Runnable {
        @Override
        @SneakyThrows
        public void run() {
            mutex.acquire();

            String ip = socket.getInetAddress().toString();

            logString = "┌─ NOVA RESERVA ─────────────────────────\n";
            // adicionar ip
            logString += "├- IP      │ " + ip + "\n";
            // adicionar nome
            logString += "├- Nome    │ " + assento.getNome() + "\n";
            // adicionar id do assento reservado
            logString += "├- Assento │ " + assento.getId() + "\n";
            // adicionar data
            logString += "├- Data    │ " + assento.getData() + " " + assento.getHora();
            logString += "\n└──────────────────────────────────────\n";

            vazio.acquire(logString.length());
            cheio.release();
            mutex.release();
        }
    }

    private class ArmazenaLog implements Runnable {
        @Override
        @SneakyThrows
        public void run() {
            mutex.acquire();
            cheio.acquire();

            vazio.release(logString.length());

            // abrir o arquivo de log
            // aquele true no final significa append,
            // pra adicionar no final do arquivo
            FileWriter writer = new FileWriter(file.getName(), true);

            // escrever a entrada do log no arquivo
            writer.write(logString);

            // fechar o arquivo de log
            writer.close();

            mutex.release();
        }
    }
}