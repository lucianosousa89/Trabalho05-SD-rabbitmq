package br.edu.gerador;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Container 1 - Gerador de Mensagens.
 *
 * Publica continuamente mensagens (avaliações de produtos e solicitações de
 * suporte) em um Topic Exchange do RabbitMQ, usando como routing key o tipo
 * da mensagem (review.product ou support.request).
 *
 * Configuração por variáveis de ambiente (ver docker-compose.yml):
 *   RABBITMQ_HOST, RABBITMQ_PORT, RABBITMQ_VHOST, RABBITMQ_USER, RABBITMQ_PASS
 *   EXCHANGE_NAME       (padrão: messages.topic)
 *   MESSAGES_PER_SECOND (10)
 *   POISON_EVERY        (padrão: 50; a cada N mensagens uma sai com conteúdo
 *                        vazio, para demonstrar a DLQ.)
 */
class Gerador {

    private static final String HOST = env("RABBITMQ_HOST", "localhost");
    private static final int PORT = Integer.parseInt(env("RABBITMQ_PORT", "5672"));
    private static final String VHOST = env("RABBITMQ_VHOST", "/");
    private static final String USER = env("RABBITMQ_USER", "luciano");
    private static final String PASS = env("RABBITMQ_PASS", "luciano123");
    private static final String EXCHANGE = env("EXCHANGE_NAME", "messages.topic");
    private static final int RATE = Math.max(1, Integer.parseInt(env("MESSAGES_PER_SECOND", "10")));
    private static final int POISON_EVERY = Integer.parseInt(env("POISON_EVERY", "50"));

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final AtomicLong TOTAL = new AtomicLong();
    private static final AtomicLong REVIEWS = new AtomicLong();
    private static final AtomicLong SUPORTES = new AtomicLong();
    private static final AtomicLong VENENO = new AtomicLong();

    public static void main(String[] args) throws Exception {
        if (RATE < 5) {
            System.out.println("[AVISO] A atividade exige pelo menos 5 mensagens/s; configurado: " + RATE);
        }

        Connection conexao = conectarComRetry();
        Channel canal = conexao.createChannel();

        // Declara o Topic Exchange (idempotente: os consumidores também podem declará-lo).
        canal.exchangeDeclare(EXCHANGE, "topic", true);

        System.out.printf("[GERADOR] Conectado a %s:%d/%s | exchange='%s' (topic) | taxa=%d msg/s | veneno a cada %s%n",
            HOST, PORT, VHOST, EXCHANGE, RATE, POISON_EVERY > 0 ? POISON_EVERY + " msgs" : "nunca");

        ScheduledExecutorService agendador = Executors.newSingleThreadScheduledExecutor();

        long periodoNanos = TimeUnit.SECONDS.toNanos(1) / RATE;
        agendador.scheduleAtFixedRate(() -> {
            try {
                publicarUma(canal);
            } catch (Exception e) {
                System.err.println("[GERADOR] Erro ao publicar: " + e.getMessage());
            }
        }, 0, periodoNanos, TimeUnit.NANOSECONDS);

        agendador.scheduleAtFixedRate(Gerador::imprimirEstatisticas, 5, 5, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            agendador.shutdownNow();
            try {
                canal.close();
                conexao.close();
            } catch (Exception ignorada) {
                // encerrando
            }
            System.out.println("[GERADOR] Encerrado. Total publicado: " + TOTAL.get());
        }));

        Thread.currentThread().join();
    }

    private static void publicarUma(Channel canal) throws Exception {
        String tipo = GeradorDeConteudo.sortearTipo();
        long numero = TOTAL.incrementAndGet();

        boolean veneno = POISON_EVERY > 0 && numero % POISON_EVERY == 0;
        String conteudo = veneno ? "" : GeradorDeConteudo.gerar(tipo);

        Mensagem msg = new Mensagem(UUID.randomUUID().toString(), Instant.now(), tipo, conteudo);
        byte[] corpo = MAPPER.writeValueAsBytes(msg);

        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
            .contentType("application/json")
            .contentEncoding(StandardCharsets.UTF_8.name())
            .deliveryMode(2) // persistente
            .messageId(msg.id())
            .type(tipo)
            .timestamp(java.util.Date.from(msg.timestamp()))
            .build();

        // routing key = tipo da mensagem
        canal.basicPublish(EXCHANGE, tipo, props, corpo);

        if (GeradorDeConteudo.REVIEW.equals(tipo)) REVIEWS.incrementAndGet(); else SUPORTES.incrementAndGet();
        if (veneno) VENENO.incrementAndGet();

        System.out.printf("[GERADOR] #%d %-15s id=%s %s%n", numero, tipo, msg.id().substring(0, 8),
            veneno ? "<< MENSAGEM COM ERRO (conteúdo vazio) >>" : "\"" + resumir(conteudo) + "\"");
    }

    private static void imprimirEstatisticas() {
        System.out.printf("[GERADOR] --- total=%d | review.product=%d | support.request=%d | com erro=%d ---%n",
            TOTAL.get(), REVIEWS.get(), SUPORTES.get(), VENENO.get());
    }

    private static Connection conectarComRetry() throws InterruptedException {
        ConnectionFactory fabrica = new ConnectionFactory();
        fabrica.setHost(HOST);
        fabrica.setPort(PORT);
        fabrica.setVirtualHost(VHOST);
        fabrica.setUsername(USER);
        fabrica.setPassword(PASS);
        fabrica.setAutomaticRecoveryEnabled(true);
        fabrica.setNetworkRecoveryInterval(3000);

        for (int tentativa = 1; ; tentativa++) {
            try {
                return fabrica.newConnection("gerador");
            } catch (Exception e) {
                System.out.printf("[GERADOR] RabbitMQ indisponível (tentativa %d): %s. Nova tentativa em 3s...%n",
                        tentativa, e.getMessage());
                Thread.sleep(3000);
            }
        }
    }

    private static String resumir(String s) {
        return s.length() <= 70 ? s : s.substring(0, 67) + "...";
    }

    private static String env(String chave, String padrao) {
        String v = System.getenv(chave);
        return v == null || v.isBlank() ? padrao : v;
    }
}

record Mensagem(String id, Instant timestamp, String tipo, String conteudo) {
}

final class GeradorDeConteudo {

    static final String REVIEW = "review.product";
    static final String SUPORTE = "support.request";

    private static final String[] AVALIACOES = {
        // Positivas
        "O produto é excelente e superou minhas expectativas.",
        "A qualidade é ótima e o preço é justo.",
        "A entrega foi rápida e o produto chegou em perfeito estado.",
        "Estou muito satisfeito com a compra e recomendo.",
        "O produto funciona perfeitamente e vale cada centavo.",

        // Negativas
        "O produto chegou quebrado e não funciona.",
        "A qualidade é péssima e não vale o preço.",
        "A entrega atrasou muito e ninguém deu satisfação.",
        "O produto apresentou defeito poucos dias após a compra.",
        "Estou decepcionado com a compra e não recomendo.",

        // Neutras
        "O produto chegou ontem, conforme o prazo informado.",
        "O produto está disponível em três opções de cores.",
        "A embalagem contém o produto e o manual de instruções.",
        "Recebi o pedido com as características descritas no anúncio.",
        "O produto possui dimensões de 30 por 20 centímetros."
    };

    private static final String[] SOLICITACOES = {
        // Problema de acesso
        "Não consigo entrar na minha conta, mesmo com a senha correta.",
        "Esqueci minha senha e não recebo o e-mail de recuperação.",
        "Minha conta foi bloqueada e não consigo fazer login.",
        "O sistema informa que minhas credenciais são inválidas.",
        "Não consigo autenticar minha conta usando o código de verificação.",

        // Problema de desempenho
        "O aplicativo demora muito para carregar as páginas.",
        "O sistema fica lento quando acesso a área de relatórios.",
        "A aplicação trava sempre que tento abrir um arquivo grande.",
        "As pesquisas estão demorando vários segundos para apresentar resultados.",
        "O sistema apresenta lentidão quando muitos usuários estão conectados.",

        // Problema de conexão
        "A conexão com o servidor cai constantemente.",
        "Não consigo conectar o aplicativo à rede Wi-Fi.",
        "O sistema informa que não foi possível estabelecer conexão com o servidor.",
        "A conexão é interrompida toda vez que tento enviar uma solicitação.",
        "O aplicativo não consegue se conectar à API."
    };

    private GeradorDeConteudo() {
    }

    static String sortearTipo() {
        return java.util.concurrent.ThreadLocalRandom.current().nextBoolean() ? REVIEW : SUPORTE;
    }

    static String gerar(String tipo) {
        String[] opcoes = REVIEW.equals(tipo) ? AVALIACOES : SOLICITACOES;
        return opcoes[java.util.concurrent.ThreadLocalRandom.current().nextInt(opcoes.length)];
    }
}