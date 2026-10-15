import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Consumidor responsável pela análise de sentimento das avaliações.
 */
public class ConsumidorAvaliacoes {
    private static final String HOST = env("RABBITMQ_HOST", "localhost");
    private static final int PORT = Integer.parseInt(env("RABBITMQ_PORT", "5672"));
    private static final String VHOST = env("RABBITMQ_VHOST", "/");
    private static final String USER = env("RABBITMQ_USER", "luciano");
    private static final String PASS = env("RABBITMQ_PASS", "luciano123");
    private static final String QUEUE = env("REVIEW_QUEUE", "review.product.queue");
    private static final String MODEL_PATH = env("MODEL_PATH", "/app/modelos/modelo-avaliacoes.bin");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Inicia a conexão e consome avaliações continuamente.
    public static void main(String[] args) throws Exception {
        ModeloNaiveBayes modelo = ModeloNaiveBayes.carregar(Path.of(MODEL_PATH));
        ConnectionFactory fabrica = criarFabrica();

        try (Connection conexao = conectarComRetry(fabrica);
             Channel canal = conexao.createChannel()) {
            canal.basicQos(1);
            canal.queueDeclare(QUEUE, true, false, false, argumentosDaFila());

            System.out.printf("[AVALIACOES] Conectado a %s:%d | fila='%s' | modelo='%s'%n",
                    HOST, PORT, QUEUE, MODEL_PATH);

            DeliverCallback callback = (consumerTag, entrega) -> processar(canal, entrega, modelo);
            canal.basicConsume(QUEUE, false, callback, consumerTag -> {
            });
            Thread.currentThread().join();
        }
    }

    // Mantém a configuração da fila compatível com a DLQ do RabbitMQ.
    private static Map<String, Object> argumentosDaFila() {
        return Map.of(
                "x-dead-letter-exchange", "dead-letter.exchange",
                "x-dead-letter-routing-key", "dead");
    }

    // Classifica uma mensagem e confirma ou rejeita o processamento.
    private static void processar(Channel canal, com.rabbitmq.client.Delivery entrega,
            ModeloNaiveBayes modelo) {
        long tag = entrega.getEnvelope().getDeliveryTag();
        try {
            JsonNode mensagem = MAPPER.readTree(entrega.getBody());
            JsonNode conteudo = mensagem.get("conteudo");
            if (conteudo == null || !conteudo.isTextual() || conteudo.asText().isBlank()) {
                throw new IllegalArgumentException("campo 'conteudo' vazio ou inválido");
            }

            String categoria = modelo.classificar(conteudo.asText());
            String id = mensagem.path("id").asText("sem-id");
            System.out.printf("[AVALIACOES] id=%s | sentimento=%s | texto=\"%s\"%n",
                    id, categoria, conteudo.asText());
            canal.basicAck(tag, false);
        } catch (Exception erro) {
            System.err.printf("[AVALIACOES] Mensagem rejeitada: %s%n", erro.getMessage());
            try {
                canal.basicNack(tag, false, false);
            } catch (Exception nackErro) {
                System.err.printf("[AVALIACOES] Falha ao enviar mensagem para a DLQ: %s%n",
                        nackErro.getMessage());
            }
        }
    }

    // Cria a fábrica de conexões com as configurações do ambiente.
    private static ConnectionFactory criarFabrica() {
        ConnectionFactory fabrica = new ConnectionFactory();
        fabrica.setHost(HOST);
        fabrica.setPort(PORT);
        fabrica.setVirtualHost(VHOST);
        fabrica.setUsername(USER);
        fabrica.setPassword(PASS);
        fabrica.setAutomaticRecoveryEnabled(true);
        fabrica.setNetworkRecoveryInterval(3000);
        return fabrica;
    }

    // Aguarda o RabbitMQ ficar disponível antes de conectar.
    private static Connection conectarComRetry(ConnectionFactory fabrica)
            throws InterruptedException {
        for (int tentativa = 1; ; tentativa++) {
            try {
                return fabrica.newConnection("consumidor-avaliacoes");
            } catch (Exception erro) {
                System.out.printf("[AVALIACOES] RabbitMQ indisponível (tentativa %d): %s%n",
                        tentativa, erro.getMessage());
                Thread.sleep(3000);
            }
        }
    }

    // Lê uma variável de ambiente ou usa o valor padrão.
    private static String env(String chave, String padrao) {
        String valor = System.getenv(chave);
        return valor == null || valor.isBlank() ? padrao : valor;
    }
}
