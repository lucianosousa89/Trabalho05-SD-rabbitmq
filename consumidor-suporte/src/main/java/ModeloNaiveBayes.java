import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Modelo carregado e utilizado pelos consumidores.
 */
public final class ModeloNaiveBayes implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String, Integer> documentosPorCategoria;
    private final Map<String, Map<String, Integer>> palavrasPorCategoria;
    private final Map<String, Integer> totalPalavrasPorCategoria;
    private final Set<String> vocabulario;
    private final int totalDocumentos;

    // Mantém a estrutura serializada do modelo treinado.
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

    // Carrega o modelo persistido em arquivo.
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

    // Retorna a categoria mais provável para o texto.
    public String classificar(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("O texto para classificação não pode estar vazio.");
        }

        String melhorCategoria = null;
        double maiorPontuacao = Double.NEGATIVE_INFINITY;
        for (String categoria : documentosPorCategoria.keySet()) {
            double pontuacao = pontuacao(texto, categoria);
            if (pontuacao > maiorPontuacao) {
                maiorPontuacao = pontuacao;
                melhorCategoria = categoria;
            }
        }
        return melhorCategoria;
    }

    // Calcula a pontuação do texto para uma categoria.
    private double pontuacao(String texto, String categoria) {
        Map<String, Integer> palavras = palavrasPorCategoria.get(categoria);
        double pontuacao = Math.log((double) documentosPorCategoria.get(categoria) / totalDocumentos);
        double denominador = totalPalavrasPorCategoria.get(categoria) + vocabulario.size();

        String normalizado = texto.toLowerCase()
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        if (!normalizado.isEmpty()) {
            for (String palavra : normalizado.split("\\s+")) {
                double probabilidade = (palavras.getOrDefault(palavra, 0) + 1.0) / denominador;
                pontuacao += Math.log(probabilidade);
            }
        }
        return pontuacao;
    }
}
