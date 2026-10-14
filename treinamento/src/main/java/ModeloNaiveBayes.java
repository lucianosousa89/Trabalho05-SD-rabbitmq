import java.io.BufferedReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

record MensagemRotulada(String texto, String categoria) {}

/**
 * Classificador de texto Multinomial Naive Bayes com suavização de Laplace.
 */
public final class ModeloNaiveBayes implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final double ALFA = 1.0;

    private final Map<String, Integer> documentosPorCategoria;
    private final Map<String, Map<String, Integer>> palavrasPorCategoria;
    private final Map<String, Integer> totalPalavrasPorCategoria;
    private final Set<String> vocabulario;
    private final int totalDocumentos;

    // Cria um modelo com as estatísticas calculadas no treinamento.
    private ModeloNaiveBayes(Map<String, Integer> documentosPorCategoria,
            Map<String, Map<String, Integer>> palavrasPorCategoria,
            Map<String, Integer> totalPalavrasPorCategoria,
            Set<String> vocabulario, int totalDocumentos) {
        this.documentosPorCategoria = documentosPorCategoria;
        this.palavrasPorCategoria = palavrasPorCategoria;
        this.totalPalavrasPorCategoria = totalPalavrasPorCategoria;
        this.vocabulario = vocabulario;
        this.totalDocumentos = totalDocumentos;
    }

    // Treina o classificador usando mensagens rotuladas.
    public static ModeloNaiveBayes treinar(List<MensagemRotulada> mensagens) {
        if (mensagens == null || mensagens.isEmpty()) {
            throw new IllegalArgumentException("A base de treinamento não pode estar vazia.");
        }

        Map<String, Integer> documentos = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> contagens = new LinkedHashMap<>();
        Map<String, Integer> totais = new LinkedHashMap<>();
        Set<String> vocabulario = new TreeSet<>();

        for (MensagemRotulada mensagem : mensagens) {
            if (mensagem == null || mensagem.texto().isBlank() || mensagem.categoria().isBlank()) {
                throw new IllegalArgumentException("A base contém uma mensagem ou categoria vazia.");
            }

            String categoria = mensagem.categoria().trim();
            documentos.merge(categoria, 1, Integer::sum);
            Map<String, Integer> palavras = contagens.computeIfAbsent(categoria,
                    ignorado -> new HashMap<>());
            int total = 0;

            for (String palavra : tokenizar(mensagem.texto())) {
                palavras.merge(palavra, 1, Integer::sum);
                vocabulario.add(palavra);
                total++;
            }
            totais.merge(categoria, total, Integer::sum);
        }

        return new ModeloNaiveBayes(
                Map.copyOf(documentos),
                copiarMapas(contagens),
                Map.copyOf(totais),
                Set.copyOf(vocabulario),
                mensagens.size());
    }

    // Retorna a categoria mais provável para o texto.
    public String classificar(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("O texto para classificação não pode estar vazio.");
        }

        return documentosPorCategoria.keySet().stream()
                .max(Comparator.comparingDouble(categoria -> pontuacao(texto, categoria)))
                .orElseThrow();
    }

    // Retorna as categorias conhecidas pelo modelo.
    public Set<String> categorias() {
        return documentosPorCategoria.keySet();
    }

    // Retorna a quantidade de palavras do vocabulário.
    public int tamanhoDoVocabulario() {
        return vocabulario.size();
    }

    // Salva o modelo em um arquivo.
    public void salvar(Path arquivo) throws IOException {
        if (arquivo.getParent() != null) {
            Files.createDirectories(arquivo.getParent());
        }
        try (ObjectOutputStream saida = new ObjectOutputStream(Files.newOutputStream(arquivo))) {
            saida.writeObject(this);
        }
    }

    // Carrega um modelo salvo em arquivo.
    public static ModeloNaiveBayes carregar(Path arquivo) throws IOException {
        try (ObjectInputStream entrada = new ObjectInputStream(Files.newInputStream(arquivo))) {
            Object modelo = entrada.readObject();
            if (!(modelo instanceof ModeloNaiveBayes)) {
                throw new IOException("O arquivo não contém um modelo Naive Bayes válido.");
            }
            return (ModeloNaiveBayes) modelo;
        } catch (ClassNotFoundException e) {
            throw new IOException("Não foi possível ler o modelo salvo.", e);
        }
    }

    // Lê mensagens rotuladas de um arquivo CSV.
    static List<MensagemRotulada> lerBase(Path arquivo) throws IOException {
        List<MensagemRotulada> mensagens = new ArrayList<>();
        try (BufferedReader leitor = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            leitor.readLine();
            String linha;
            while ((linha = leitor.readLine()) != null) {
                String[] colunas = separarCsv(linha);
                if (colunas.length == 2) {
                    mensagens.add(new MensagemRotulada(
                            removerAspas(colunas[0].trim()),
                            removerAspas(colunas[1].trim())));
                }
            }
        }
        return mensagens;
    }

    // Localiza a raiz do projeto a partir do diretório atual.
    static Path localizarProjeto() throws IOException {
        Path diretorio = Path.of("").toAbsolutePath();
        while (diretorio != null) {
            if (Files.isDirectory(diretorio.resolve("dados"))) {
                return diretorio;
            }
            diretorio = diretorio.getParent();
        }
        throw new IOException("Não foi possível localizar a raiz do projeto.");
    }

    // Calcula a pontuação do texto para uma categoria.
    private double pontuacao(String texto, String categoria) {
        Map<String, Integer> palavras = palavrasPorCategoria.get(categoria);
        double pontuacao = Math.log((double) documentosPorCategoria.get(categoria) / totalDocumentos);
        double denominador = totalPalavrasPorCategoria.get(categoria) + ALFA * vocabulario.size();

        for (String palavra : tokenizar(texto)) {
            double probabilidade = (palavras.getOrDefault(palavra, 0) + ALFA) / denominador;
            pontuacao += Math.log(probabilidade);
        }
        return pontuacao;
    }

    // Cria cópias imutáveis dos mapas de contagem.
    private static Map<String, Map<String, Integer>> copiarMapas(
            Map<String, Map<String, Integer>> mapas) {
        Map<String, Map<String, Integer>> copia = new LinkedHashMap<>();
        mapas.forEach((categoria, palavras) -> copia.put(categoria, Map.copyOf(palavras)));
        return Map.copyOf(copia);
    }

    // Divide o texto normalizado em palavras.
    private static List<String> tokenizar(String texto) {
        String normalizado = Objects.requireNonNull(texto)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        return normalizado.isEmpty() ? List.of() : List.of(normalizado.split("\\s+"));
    }

    // Separa uma linha CSV respeitando textos entre aspas.
    private static String[] separarCsv(String linha) {
        boolean dentroDeAspas = false;
        for (int i = 0; i < linha.length(); i++) {
            char caractere = linha.charAt(i);
            if (caractere == '"') {
                dentroDeAspas = !dentroDeAspas;
            } else if (caractere == ',' && !dentroDeAspas) {
                return new String[] { linha.substring(0, i), linha.substring(i + 1) };
            }
        }
        return new String[] { linha };
    }

    // Remove aspas externas e interpreta aspas duplicadas.
    private static String removerAspas(String valor) {
        if (valor.length() >= 2 && valor.startsWith("\"") && valor.endsWith("\"")) {
            return valor.substring(1, valor.length() - 1).replace("\"\"", "\"");
        }
        return valor;
    }
}
