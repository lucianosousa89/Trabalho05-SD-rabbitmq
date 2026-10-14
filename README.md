# Trabalho 05 - RabbitMQ

## Organização

- `dados/`: bases CSV usadas no treinamento;
- `treinamento/`: código que treina os dois modelos;
- `modelos/`: artefatos binários gerados pelo treinamento;
- `gerador/`: produtor de mensagens do RabbitMQ;
- `consumidor-avaliacoes/`: consumidor responsável por avaliações;
- `consumidor-suporte/`: consumidor responsável por solicitações de suporte.

O gerador não contém código nem dependências de aprendizado de máquina.

## Treinamento dos modelos

O módulo de treinamento possui dois classificadores Multinomial Naive Bayes:

- `modelo-avaliacoes.bin`: `positivo`, `negativo` ou `neutro`;
- `modelo-suporte.bin`: `acesso`, `desempenho` ou `conexao`.

Para treinar o modelo de avaliações:

```bash
mvn -f treinamento/pom.xml compile exec:java \
  -Dexec.mainClass=TreinarModeloAvaliacoes
```

Para treinar o modelo de suporte:

```bash
mvn -f treinamento/pom.xml compile exec:java \
  -Dexec.mainClass=TreinarModeloSuporte
```

Os dois comandos salvam os modelos em `modelos/` e exibem a acurácia na
respectiva base de treinamento.

Para validar o gerador separadamente:

```bash
mvn -f gerador/pom.xml package
```
