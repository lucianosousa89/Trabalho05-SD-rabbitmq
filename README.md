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

## Consumidor de avaliações

O consumidor em `consumidor-avaliacoes/` consome a fila
`review.product.queue`, carrega o modelo embarcado
`modelos/modelo-avaliacoes.bin` e classifica cada avaliação como `positivo`,
`negativo` ou `neutro`.

Para validar a compilação:

```bash
mvn -f consumidor-avaliacoes/pom.xml package
```

Para executar toda a infraestrutura:

```bash
docker compose up --build
```

Mensagens processadas com sucesso são confirmadas no RabbitMQ. Mensagens
inválidas ou vazias são rejeitadas sem reprocessamento e encaminhadas para a
fila de mensagens mortas.

## Consumidor de suporte

O consumidor em `consumidor-suporte/` consome a fila
`support.request.queue`, carrega o modelo `modelos/modelo-suporte.bin` e
classifica cada solicitação nas categorias `acesso`, `desempenho` ou
`conexao`.

Para validar a compilação:

```bash
mvn -f consumidor-suporte/pom.xml package
```
