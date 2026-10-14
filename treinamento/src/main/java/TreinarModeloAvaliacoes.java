import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class TreinarModeloAvaliacoes {
    // Treina e salva o modelo de avaliações.
    public static void main(String[] args) throws IOException {
        Path projeto = ModeloNaiveBayes.localizarProjeto();
        List<MensagemRotulada> mensagens = ModeloNaiveBayes.lerBase(
                projeto.resolve("dados/dataset-avaliacoes.csv"));
        ModeloNaiveBayes modelo = ModeloNaiveBayes.treinar(mensagens);
        Path arquivoModelo = projeto.resolve("modelos/modelo-avaliacoes.bin");
        modelo.salvar(arquivoModelo);

        imprimirResultado("avaliações", mensagens, modelo, arquivoModelo);
    }

    // Exibe os resultados do treinamento.
    private static void imprimirResultado(String nome, List<MensagemRotulada> mensagens,
            ModeloNaiveBayes modelo, Path arquivoModelo) {
        long acertos = mensagens.stream()
                .filter(mensagem -> modelo.classificar(mensagem.texto()).equals(mensagem.categoria()))
                .count();
        System.out.printf("Modelo de %s treinado: %d mensagens, %d categorias, %d palavras%n",
                nome, mensagens.size(), modelo.categorias().size(),
                modelo.tamanhoDoVocabulario());
        System.out.printf("Acurácia: %d/%d (%.1f%%)%n",
                acertos, mensagens.size(), 100.0 * acertos / mensagens.size());
        System.out.println("Modelo salvo em: " + arquivoModelo.toAbsolutePath());
    }
}
