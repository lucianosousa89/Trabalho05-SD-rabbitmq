import smile.classification.DiscreteNaiveBayes;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModeloNaiveBayes implements Serializable {
    private static final long serialVersionUID = 2L;
    private final List<String> vocabulario;
    private final List<String> categorias;
    private final int[][] vetoresTreinamento;
    private final int[] rotulosTreinamento;
    private transient DiscreteNaiveBayes classificador;

    private ModeloNaiveBayes(List<String> vocabulario, List<String> categorias,
            int[][] vetoresTreinamento, int[] rotulosTreinamento) {
        this.vocabulario = vocabulario;
        this.categorias = categorias;
        this.vetoresTreinamento = vetoresTreinamento;
        this.rotulosTreinamento = rotulosTreinamento;
    }

    public String classificar(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("O texto para classificação não pode estar vazio.");
        }
        return categorias.get(classificador.predict(vetorizar(texto)));
    }

    public static ModeloNaiveBayes carregar(Path arquivo) throws IOException {
        try (ObjectInputStream entrada = new ObjectInputStream(Files.newInputStream(arquivo))) {
            Object modelo = entrada.readObject();
            if (!(modelo instanceof ModeloNaiveBayes)) {
                throw new IOException("O arquivo não contém um modelo Smile válido.");
            }
            return (ModeloNaiveBayes) modelo;
        } catch (ClassNotFoundException e) {
            throw new IOException("Não foi possível ler o modelo salvo.", e);
        }
    }

    private void readObject(ObjectInputStream entrada) throws IOException, ClassNotFoundException {
        entrada.defaultReadObject();
        classificador = new DiscreteNaiveBayes(
                DiscreteNaiveBayes.Model.MULTINOMIAL, categorias.size(), vocabulario.size());
        classificador.update(vetoresTreinamento, rotulosTreinamento);
    }

    private int[] vetorizar(String texto) {
        int[] vetor = new int[vocabulario.size()];
        Map<String, Integer> indices = new LinkedHashMap<>();
        for (int i = 0; i < vocabulario.size(); i++) {
            indices.put(vocabulario.get(i), i);
        }
        String normalizado = texto.toLowerCase().replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        if (!normalizado.isEmpty()) {
            for (String palavra : Arrays.asList(normalizado.split("\\s+"))) {
                Integer indice = indices.get(palavra);
                if (indice != null) {
                    vetor[indice]++;
                }
            }
        }
        return vetor;
    }
}
