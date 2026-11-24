# ADR-0011: AWS (SQS e Lambda) simulada localmente com ferramentas compativeis

**Status:** aceito

## Contexto
O sistema precisa de SQS (entrada do vendedor) e Lambda (emissao da NF-e), mas deve rodar sem conta
AWS. O LocalStack passou a exigir conta/token nas versoes recentes.

## Decisao
- O codigo usa sempre o **AWS SDK v2 oficial**. So o endpoint muda por ambiente:
  - **SQS -> ElasticMQ** (mesma API do SQS), fila `seller-batches` com redrive para `seller-batches-dlq`.
  - **Lambda -> imagem oficial `public.ecr.aws/lambda/java:21`**, que traz o Runtime Interface
    Emulator da AWS: a URL de Invoke e a mesma da API real. Na AWS, a mesma imagem vai para o ECR.
- Porta `InvoiceIssuer` com dois adaptadores: `LambdaInvoiceIssuer` (padrao) e
  `InProcessInvoiceIssuer` (`INVOICE_ISSUER=inprocess`), que roda o MESMO handler dentro do processo
  para ambientes sem Docker. O caso de uso nao sabe qual esta ativo.
- Clientes HTTP leves (`url-connection-client`), sem netty/apache (ADR-0009).

## Consequencias
- Descoberta no teste E2E: o emulador atende **uma invocacao por vez** (duas em paralelo derrubam o
  container). Localmente o bulkhead da Lambda e 1 (`NFE_LAMBDA_MAX_CONCURRENT=1`) e o container
  reinicia sozinho; na AWS esse limite vira a concorrencia reservada da funcao.
- Ir para a AWS: remover `AWS_ENDPOINT`/`NFE_LAMBDA_ENDPOINT`, usar IAM role e criar fila/funcao (IaC).
