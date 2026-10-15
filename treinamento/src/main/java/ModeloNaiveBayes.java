import smile.classification.DiscreteNaiveBayes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

record MensagemRotulada(String texto, String categoria) {}

/**
 * Classificador de texto multinomial baseado no Smile DiscreteNaiveBayes.
 */
public final class ModeloNaiveBayes implements Serializable {
    private static final long serialVersionUID = 2L;

    private final List<String> vocabulario;
    private final List<String> categorias;
    private final int[][] vetoresTreinamento;
    private final int[] rotulosTreinamento;
    private transient DiscreteNaiveBayes classificador;

    private ModeloNaiveBayes(List<String> vocabulario, List<String> categorias,
            int[][] vetoresTreinamento, int[] rotulosTreinamento) {
        this.vocabulario = List.copyOf(vocabulario);
        this.categorias = List.copyOf(categorias);
        this.vetoresTreinamento = vetoresTreinamento;
        this.rotulosTreinamento = rotulosTreinamento;
        reconstruirClassificador();
    }

    public static ModeloNaiveBayes treinar(List<MensagemRotulada> mensagens) {
        if (mensagens == null || mensagens.isEmpty()) {
            throw new IllegalArgumentException("A base de treinamento não pode estar vazia.");
        }

        TreeMap<String, Integer> indicesPalavras = new TreeMap<>();
        TreeMap<String, Integer> indicesCategorias = new TreeMap<>();
        for (MensagemRotulada mensagem : mensagens) {
            if (mensagem == null || mensagem.texto().isBlank() || mensagem.categoria().isBlank()) {
                throw new IllegalArgumentException("A base contém uma mensagem ou categoria vazia.");
            }
            tokenizar(mensagem.texto()).forEach(palavra ->
                    indicesPalavras.putIfAbsent(palavra, indicesPalavras.size()));
            indicesCategorias.putIfAbsent(mensagem.categoria().trim(), indicesCategorias.size());
        }

        List<String> vocabulario = new ArrayList<>(indicesPalavras.keySet());
        List<String> categorias = new ArrayList<>(indicesCategorias.keySet());
        int[][] vetores = new int[mensagens.size()][vocabulario.size()];
        int[] rotulos = new int[mensagens.size()];

        for (int i = 0; i < mensagens.size(); i++) {
            MensagemRotulada mensagem = mensagens.get(i);
            for (String palavra : tokenizar(mensagem.texto())) {
                vetores[i][indicesPalavras.get(palavra)]++;
            }
            rotulos[i] = indicesCategorias.get(mensagem.categoria().trim());
        }
        return new ModeloNaiveBayes(vocabulario, categorias, vetores, rotulos);
    }

    public String classificar(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("O texto para classificação não pode estar vazio.");
        }
        return categorias.get(classificador.predict(vetorizar(texto)));
    }

    public Set<String> categorias() {
        return Set.copyOf(categorias);
    }

    public int tamanhoDoVocabulario() {
        return vocabulario.size();
    }

    public void salvar(Path arquivo) throws IOException {
        if (arquivo.getParent() != null) {
            Files.createDirectories(arquivo.getParent());
        }
        try (ObjectOutputStream saida = new ObjectOutputStream(Files.newOutputStream(arquivo))) {
            saida.writeObject(this);
        }
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

    static List<MensagemRotulada> lerBase(Path arquivo) throws IOException {
        List<MensagemRotulada> mensagens = new ArrayList<>();
        try (BufferedReader leitor = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            leitor.readLine();
            String linha;
            while ((linha = leitor.readLine()) != null) {
                String[] colunas = separarCsv(linha);
                if (colunas.length == 2) {
                    mensagens.add(new MensagemRotulada(removerAspas(colunas[0].trim()),
                            removerAspas(colunas[1].trim())));
                }
            }
        }
        return mensagens;
    }

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

    private void reconstruirClassificador() {
        classificador = new DiscreteNaiveBayes(
                DiscreteNaiveBayes.Model.MULTINOMIAL, categorias.size(), vocabulario.size());
        classificador.update(vetoresTreinamento, rotulosTreinamento);
    }

    private void readObject(ObjectInputStream entrada) throws IOException, ClassNotFoundException {
        entrada.defaultReadObject();
        reconstruirClassificador();
    }

    private int[] vetorizar(String texto) {
        int[] vetor = new int[vocabulario.size()];
        Map<String, Integer> indices = new LinkedHashMap<>();
        for (int i = 0; i < vocabulario.size(); i++) {
            indices.put(vocabulario.get(i), i);
        }
        for (String palavra : tokenizar(texto)) {
            Integer indice = indices.get(palavra);
            if (indice != null) {
                vetor[indice]++;
            }
        }
        return vetor;
    }

    private static List<String> tokenizar(String texto) {
        String normalizado = Objects.requireNonNull(texto).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        return normalizado.isEmpty() ? List.of() : Arrays.asList(normalizado.split("\\s+"));
    }

    private static String[] separarCsv(String linha) {
        boolean dentroDeAspas = false;
        for (int i = 0; i < linha.length(); i++) {
            if (linha.charAt(i) == '"') {
                dentroDeAspas = !dentroDeAspas;
            } else if (linha.charAt(i) == ',' && !dentroDeAspas) {
                return new String[] { linha.substring(0, i), linha.substring(i + 1) };
            }
        }
        return new String[] { linha };
    }

    private static String removerAspas(String valor) {
        if (valor.length() >= 2 && valor.startsWith("\"") && valor.endsWith("\"")) {
            return valor.substring(1, valor.length() - 1).replace("\"\"", "\"");
        }
        return valor;
    }
}
