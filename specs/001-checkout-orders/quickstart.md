# Quickstart: Checkout de Pedidos

Este guia descreve a validação **após a implementação** das tarefas. Veja o [contrato REST](./contracts/rest-api.md) e o [modelo de dados](./data-model.md) para detalhes. Não há API de produtos, worker ou publicação em broker neste incremento.

## Pré-requisitos

- Java 21 e o Maven Wrapper do módulo.
- Docker disponível para os testes de integração Testcontainers e para o MongoDB local.
- MongoDB em replica set: transações multidocumentos não funcionam em um servidor standalone.

## Testes automatizados

No PowerShell, a partir da raiz do repositório:

```powershell
Set-Location .\services\checkout-service
.\mvnw.cmd test
.\mvnw.cmd verify
```

`test` executa os testes unitários; `verify` também executa os testes `*IT` via Failsafe com MongoDB Testcontainers em replica set. Espera-se sucesso em ambos, incluindo:

- total exato e validação de itens, moeda e preço;
- snapshot mantido mesmo após alteração do produto;
- repetição, conflito, expiração antes da limpeza TTL e criação concorrente com a mesma chave;
- rollback confirmado em falhas de persistência e recuperação com a mesma chave após commit de resultado desconhecido;
- fonte local indisponível e repetição após sua recuperação.

## Serviço e MongoDB locais

Os comandos seguintes são para um MongoDB local de teste, sem autenticação. Execute-os apenas em um ambiente de desenvolvimento e use outro nome ou porta caso já exista um contêiner `checkout-mongo`.

```powershell
docker run --name checkout-mongo --rm -d -p 27017:27017 mongo:7 --replSet rs0 --bind_ip_all
docker exec checkout-mongo mongosh --quiet --eval "rs.initiate({_id:'rs0',members:[{_id:0,host:'localhost:27017'}]})"
```

Aguarde o membro se tornar primário antes de executar a aplicação; confira com:

```powershell
docker exec checkout-mongo mongosh --quiet --eval "db.hello().isWritablePrimary"
```

Com o diretório atual em `services\checkout-service`, configure o banco dedicado e inicie o serviço:

```powershell
$env:SPRING_MONGODB_URI = 'mongodb://localhost:27017/checkout-service?replicaSet=rs0&directConnection=true'
.\mvnw.cmd spring-boot:run
```

O catálogo local planejado contém `WIDGET_A` a 29.99 USD e `WIDGET_B` a 40.01 USD. Nenhum nome ou preço fornecido pelo cliente é autoritativo.

## Criar e consultar

Em outro PowerShell:

```powershell
$headers = @{ 'Idempotency-Key' = [guid]::NewGuid().ToString() }
$body = '{"items":[{"productId":"WIDGET_A","quantity":2},{"productId":"WIDGET_B","quantity":1}]}'
$order = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/orders' -Headers $headers -ContentType 'application/json' -Body $body
$order
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/v1/orders/$($order.id)"
```

O pedido deve conter os nomes e preços do catálogo, total **99.99 USD**, identificador e snapshot. A consulta deve retornar o mesmo snapshot.

Repita a criação com os mesmos `$headers` e `$body`: deve retornar o mesmo identificador, sem criar outro pedido ou evento. Usar a mesma chave com quantidade diferente deve retornar HTTP 409. Produto inexistente deve retornar 422; entrada malformada, 400; identificador de pedido desconhecido, 404.

Para inspecionar as coleções, substitua `<ORDER_ID>` pelo ID retornado:

```powershell
docker exec checkout-mongo mongosh checkout-service --quiet --eval "db.orders.countDocuments({_id:'<ORDER_ID>'})"
docker exec checkout-mongo mongosh checkout-service --quiet --eval "db.order_events.countDocuments({orderId:'<ORDER_ID>'})"
```

Cada contagem deve ser 1. `order_events` é a outbox; não existe worker neste incremento.

## Falhas e recuperação

- Dados locais indisponíveis ou incompletos não podem produzir pedido ou evento. Após restaurar os dados, repita com a mesma chave. Se a configuração inválida impedir a inicialização, corrija-a e reinicie o serviço.
- Uma falha com rollback confirmado não deixa pedido nem evento. Se a comunicação falhar durante o commit, o resultado pode ser desconhecido; não suponha rollback. Repita com a mesma chave e conteúdo.
- A janela de idempotência é verificada pela expiração registrada, independentemente do momento da limpeza TTL.
- Os testes de integração alteram a fonte local de teste depois da criação para verificar o snapshot; não há endpoint administrativo de produtos.
