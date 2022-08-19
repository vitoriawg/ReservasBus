package br.ufsm.csi.so;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import lombok.SneakyThrows;

// Runnable criado a cada nova conexão, 
// ele responde a requisição do cliente
public class Connection implements Runnable {
    private Socket socket;

    public Connection(Socket socket) {
        this.socket = socket;
    }

    @Override
    @SneakyThrows
    public void run() {
        InputStream in = this.socket.getInputStream();
        OutputStream out = this.socket.getOutputStream();

        // scanner pra ler o InputStream
        // cada .next() le uma palavra por vez
        Scanner scanner = new Scanner(in);

        if (!scanner.hasNext()) {
            scanner.close();

            return;
        }

        String method = scanner.next();
        String path = scanner.next();

        // printar o request no console
        System.out.println(method + " " + path);

        // separar /reservar?id=1 em ["/reservar", "id=1"], por exemplo
        String[] dirAndParams = path.split("\\?");

        // recurso acessado é o indice 0
        String recurso = dirAndParams[0];
        // queries foram interpretadas do url
        Map<String, String> query = this.parseQuery(dirAndParams.length > 1
                ? dirAndParams[1].split("&")
                : null);

        byte[] contentBytes = null;

        // cabeçalho da resposta - por padrão é 200 OK
        String header = """
                HTTP/1.1 200 OK
                Content-Type: text/html; charset=UTF-8

                """;

        /*
         * Esse template não tem imagem, mas aqui tem um código pra
         * responder a request de imagem.
         * 
         * Ali onde tem image/png pode ser diferente (se a imagem for jpg, por exemplo),
         * então se não quiser ter que fazer gambiarra (kkk), sugiro que use sempre o
         * mesmo.
         * 
         * As imagens que o HTML pedir que estejam na pasta resources/img/ serão
         * respondidas aqui.
         */
          if (recurso.startsWith("/img/")) {
             contentBytes = this.getBytes(recurso);

             if (contentBytes != null)
                 header = header.replace("text/html", "image/png");
         }


        // request de CSS
        if (recurso.startsWith("/css/")) {
            contentBytes = this.getBytes(recurso);

            if (contentBytes != null)
                header = header.replace("text/html", "text/css");
        }

        // home page!
        if (recurso.equals("/")) {
            contentBytes = this.getBytes("index.html");

            String html = new String(contentBytes);

            String elementos = "";

            // iterar sobre os assentos
            for (Assento assento : Server.assentos.values()) {
                // criar elemento anchor
                String elemento = "<a";

                elemento += " class=\"assento\"";
                // TODO: não adicionar se o assento estiver ocupado!
                elemento += " href=\"/reservar?id=" + assento.getId() + "\"";
                elemento += ">" + assento.getId() + "</a>";

                elementos += elemento + "\n";
            }

            // substituir os assentos no html
            html = html.replace("<assentos />", elementos);

            contentBytes = html.getBytes();
        }

        if (recurso.equals("/reservar")) {

            contentBytes = this.getBytes("reservar.html");

            String html = new String(contentBytes);

            // substituir o ID no html
            html = html.replace("{{id}}", query.get("id"));
            int id = Integer.parseInt(query.get("id"));
            Assento assento = Server.assentos.get(id);

            if(assento.isOcupado() == true){
                header = """
                    HTTP/1.1 302 Found
                    Content-Type: text/html; charset=UTF-8
                    Location: /

                    """;

            }else{
                contentBytes = html.getBytes();

                System.out.println("/reservar ativou");
            }

        }

        if (recurso.equals("/confirmar")) {
            // header de redirecionar
            // status 302 Found, o browser entende que deve redirecionar para Location,
            // ali embaixo
            header = """
                    HTTP/1.1 302 Found
                    Content-Type: text/html; charset=UTF-8
                    Location: /

                    """;
            contentBytes = "<p>Redirecionando...</p>".getBytes();
            // trancar entrada na região crítica
            Server.mutex.acquire();

            int id = Integer.parseInt(query.get("id"));
            Assento assento = Server.assentos.get(id);

            // TODO: Verificar se o assento está vago
            if (assento != null) {
                String nome = query.get("nome");
                String dataHora[] = query.get("data_hora").split("T");
                String data = dataHora[0];
                String hora = dataHora[1];

                assento.setNome(nome);
                assento.setData(data);
                assento.setHora(hora);
                assento.setOcupado(true);

                Server.logger.log(socket, assento);

                System.out.println("LOG Nova reserva adicionada: " + assento.getId() + " | " + assento.getNome());
            }

            // liberar mutex
            Server.mutex.release();
        }

        // caso não tenha conteúdo nenhum (não caiu em nenhum if ali em cima),
        // mostrar página de 404
        if (contentBytes == null) {
            contentBytes = this.getBytes("404.html");

            header = """
                    HTTP/1.1 404 Not Found
                    Content-Type: text/html; charset=UTF-8

                    """;
        }

        // escrever o cabeçalho e o conteúdo gerado da página
        out.write(header.getBytes());
        out.write(contentBytes);

        // fechar os streams
        in.close();
        out.close();

        scanner.close();

        // fechar a conexão
        this.socket.close();
    }

    // FUNÇÕES UTEIS USADAS NO CÓDIGO

    // gerar pares da query
    // isso gera um map nome -> valor
    // então pra acessar um parametro é map.get("nome")
    @SneakyThrows
    private Map<String, String> parseQuery(String[] query) {
        // não tem query!
        if (query == null)
            return null;

        Map<String, String> queries = new HashMap<>();

        for (String s : query) {
            // isso separa id=1 em [id, 1]
            String[] kvPair = s.split("=");

            // se for só id (sem =1), então o valor é nulo
            if (kvPair.length == 1) {
                queries.put(kvPair[0], null);
            } else { // se não, o nome é o indice 0 e o valor é o indice 1 decodificado
                // ele vem no url cheio de %
                queries.put(kvPair[0], URLDecoder.decode(kvPair[1], "UTF-8"));
            }
        }

        return queries;
    }

    // pegar os bytes de um recurso (pasta resource)
    @SneakyThrows
    private byte[] getBytes(String recurso) {
        // recurso não pode começar com /
        if (recurso.startsWith("/"))
            recurso = recurso.substring(1);

        InputStream is = this.getClass().getClassLoader().getResourceAsStream(recurso);

        if (is != null)
            return is.readAllBytes();

        return null;
    }
}
