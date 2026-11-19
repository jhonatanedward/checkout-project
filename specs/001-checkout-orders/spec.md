# Feature Specification: Checkout de Pedidos

**Feature Branch**: `not created`

**Created**: 2026-11-19

**Status**: Pronta para planejamento

**Input**: User description: "Criar e consultar pedidos em uma API de checkout. O cliente informa produtos e quantidades. O checkout consulta informações confiáveis de produtos, valida os itens e calcula o total no servidor. Ao criar o pedido, preservar nome, quantidade e preço unitário dos produtos como snapshot. O pedido criado deve poder ser consultado pelo identificador. A criação deve permitir que uma mesma requisição seja repetida com segurança sem gerar pedidos duplicados. O pedido e o registro de um evento de criação devem ser persistidos atomicamente. Neste primeiro incremento, não construir API de produtos, processamento de pagamentos, reserva de estoque ou publicação em broker. Identifique dúvidas e cenários de falha que precisam de decisão."

## Clarifications

### Session 2026-11-19

- Q: Qual origem fornecerá nome e preço dos produtos neste incremento? → A: Dados locais controlados; não será criada uma API de produtos.
- Q: A chave será obrigatória? Qual seu escopo e retenção? → A: Obrigatória para criação de pedidos, única nesse fluxo durante uma janela de deduplicação de 24 horas; conflitos de conteúdo serão rejeitados.

## User Scenarios & Testing _(mandatory)_

### User Story 1 - Criar pedido com total confiável (Priority: P1)

Como cliente, quero enviar produtos e quantidades para criar um pedido, para receber um valor calculado com dados confiáveis e consultar depois o que foi comprado.

**Why this priority**: A criação e a representação fiel do pedido são o valor central do checkout.

**Independent Test**: Enviar um pedido válido usando produtos disponíveis na fonte confiável e verificar o total, os itens salvos e o identificador retornado.

**Acceptance Scenarios**:

1. **Given** uma solicitação com ao menos um produto existente e quantidade positiva, **When** o cliente cria o pedido, **Then** o checkout obtém nome e preço unitário da fonte confiável, calcula o total no servidor e retorna um identificador.
2. **Given** um pedido criado, **When** o cliente consulta seu identificador, **Then** recebe o pedido com os nomes, quantidades e preços unitários preservados como estavam no momento da criação, além do total.
3. **Given** uma solicitação vazia, com quantidade inválida ou com produto inexistente, **When** o cliente tenta criar o pedido, **Then** a solicitação é rejeitada e nenhum pedido é criado parcialmente.

### User Story 2 - Repetir criação com segurança (Priority: P1)

Como cliente, quero repetir uma solicitação de criação quando não sei se a primeira tentativa foi concluída, para evitar pedidos duplicados.

**Why this priority**: Repetições são comuns em falhas de rede e a duplicação altera pedidos e cobranças futuras.

**Independent Test**: Enviar duas vezes a mesma criação com a mesma chave de idempotência e verificar que ambas as respostas identificam o mesmo pedido e que só existe um pedido e um evento de criação.

**Acceptance Scenarios**:

1. **Given** uma criação já concluída com uma chave de idempotência, **When** a mesma solicitação é repetida com a mesma chave, **Then** o checkout retorna o resultado associado ao pedido original sem criar outro pedido nem outro evento de criação.
2. **Given** uma chave já usada dentro da janela de deduplicação para uma solicitação diferente, **When** a chave é reutilizada, **Then** o checkout rejeita a solicitação conflitante sem alterar o pedido original.

### User Story 3 - Tratar falhas sem estado parcial (Priority: P1)

Como operador do checkout, quero que falhas ao validar produtos ou persistir o pedido e seu evento sejam explícitas e não deixem estado incompleto, para manter os pedidos confiáveis.

**Why this priority**: Um pedido sem validação confiável ou sem seu evento de criação viola a consistência exigida pelo incremento.

**Independent Test**: Simular indisponibilidade dos dados locais de produtos e falha ao persistir o evento; verificar respostas de falha e ausência de pedido/evento parcial.

**Acceptance Scenarios**:

1. **Given** os dados locais controlados de produtos indisponíveis, **When** uma criação precisa consultá-los, **Then** o checkout não confirma nem persiste um pedido com preço não verificado; a tentativa falha de forma recuperável e pode ser repetida após o restabelecimento dos dados.
2. **Given** uma falha ao persistir o pedido ou seu evento de criação, **When** a operação termina, **Then** nenhum dos dois registros fica persistido isoladamente e a criação pode ser repetida com segurança.
3. **Given** uma falha de comunicação após a criação ter sido concluída, **When** o cliente repete a criação com a mesma chave, **Then** recebe o pedido já criado, sem duplicação.

### User Story 4 - Usar fonte local confiável de produtos (Priority: P2)

Como responsável pelo incremento, quero usar dados locais controlados como fonte confiável de produtos, sem construir uma API de produtos, para que preços e nomes usados no pedido tenham uma origem explícita.

**Why this priority**: A fonte de dados é uma dependência necessária, embora o serviço de produtos esteja fora do escopo.

**Independent Test**: Demonstrar que uma criação válida usa os dados locais controlados e que valores de nome ou preço enviados pelo cliente não substituem esses dados.

**Acceptance Scenarios**:

1. **Given** um produto presente nos dados locais controlados, **When** o checkout valida a solicitação, **Then** usa a identificação, o nome e o preço unitário confiáveis desses dados, ignorando qualquer nome ou preço informado pelo cliente.
2. **Given** que a API de produtos está fora do escopo deste incremento, **When** o checkout cria um pedido, **Then** usa exclusivamente a fonte local controlada para validar os produtos e preços.

### Edge Cases

- Solicitação sem itens, quantidade zero, quantidade negativa ou formato de quantidade inválido: rejeitar sem criar pedido.
- Produto inexistente, desativado ou sem preço válido na fonte: rejeitar o pedido inteiro e não salvar itens parciais.
- Itens repetidos para o mesmo produto: rejeitar a solicitação inteira para evitar consolidação silenciosa ou interpretação ambígua.
- Preço ou nome do produto alterado entre a criação e a consulta posterior: a consulta deve continuar exibindo o snapshot salvo no pedido.
- Dados locais de produtos indisponíveis ou incompletos: não usar valores fornecidos pelo cliente nem confirmar preço sem validação; falhar de forma recuperável e permitir nova tentativa após a recuperação.
- Chave de idempotência ausente: rejeitar a criação. Chave repetida com conteúdo diferente durante as 24 horas: rejeitar sem alterar o pedido original. Após a janela de 24 horas: a chave pode ser usada para uma nova criação.
- Falha entre a persistência do pedido e do evento: operação toda sem efeito persistido; nunca deixar somente um dos registros.
- Identificador de pedido desconhecido: retornar indicação de não encontrado, sem revelar dados de outro pedido.
- Valores monetários não válidos ou incompatíveis com a moeda configurada: rejeitar sem arredondamento silencioso.

## Requirements _(mandatory)_

### Functional Requirements

- **FR-001**: O checkout MUST aceitar uma solicitação de criação com um ou mais identificadores de produto e respectivas quantidades positivas.
- **FR-002**: O checkout MUST validar os produtos e obter nome e preço unitário dos dados locais controlados; MUST NOT tratar nome ou preço informado pelo cliente como fonte autoritativa.
- **FR-003**: O checkout MUST rejeitar a solicitação inteira se ela estiver vazia, contiver quantidade inválida ou incluir produto inexistente ou sem preço válido; MUST NOT persistir pedidos parciais.
- **FR-004**: O checkout MUST calcular no servidor o total com base nos preços unitários validados e nas quantidades solicitadas, sem confiar em total fornecido pelo cliente.
- **FR-005**: O pedido MUST preservar, para cada item, o nome, a quantidade e o preço unitário vigentes no momento da criação; consultas posteriores MUST retornar esse snapshot mesmo que o produto mude.
- **FR-006**: O checkout MUST retornar um identificador único para cada pedido criado e permitir consultar o pedido por esse identificador; identificadores desconhecidos MUST ser tratados como não encontrados.
- **FR-007**: A criação MUST exigir uma chave de idempotência, única no fluxo de criação de pedidos por 24 horas; durante essa janela, uma repetição com o mesmo conteúdo MUST retornar o pedido original e uma repetição com conteúdo diferente MUST ser rejeitada sem alterações. Após a janela, a chave MAY ser usada para uma nova criação.
- **FR-008**: O checkout MUST persistir o pedido e seu evento de criação como uma única operação atômica; se qualquer persistência falhar, nenhum dos dois registros MUST permanecer.
- **FR-009**: O evento de criação MUST identificar o pedido criado e o momento da criação. A publicação desse evento em broker não faz parte deste incremento.
- **FR-010**: Falhas de validação ou indisponibilidade dos dados locais MUST ser comunicadas sem confirmar pedido inexistente, preço não validado ou estado parcial.
- **FR-011**: Este incremento MUST NOT incluir API de produtos, processamento de pagamentos, reserva de estoque ou publicação em broker.
- **FR-012**: O checkout MUST rejeitar a solicitação inteira quando o mesmo produto aparecer em mais de uma linha, sem persistir pedido parcial.

### Key Entities _(include if feature involves data)_

- **Pedido**: compra criada, identificada de forma única, com total e itens preservados no momento da criação.
- **Item do pedido**: snapshot de um produto no pedido, incluindo identificador do produto, nome, quantidade e preço unitário.
- **Chave de idempotência**: identificador obrigatório e único no fluxo de criação de pedidos, usado para reconhecer repetições por 24 horas; após esse prazo, pode ser reutilizado.
- **Evento de criação do pedido**: registro persistido que identifica o pedido e o momento em que foi criado, atomicamente com o pedido.
- **Produto confiável**: dados locais controlados usados para validar produtos e obter nome e preço para o cálculo; uma API de produtos está fora do escopo.

## Success Criteria _(mandatory)_

### Measurable Outcomes

- **SC-001**: Em todos os testes de criação válida, o total do pedido corresponde exatamente à soma dos preços unitários confiáveis multiplicados pelas quantidades.
- **SC-002**: Em 100% dos testes de consulta após criação ou alteração posterior dos dados do produto, o pedido exibe os valores originais do snapshot.
- **SC-003**: Em 100% dos testes de repetição elegível, uma mesma solicitação resulta em exatamente um pedido e um evento de criação.
- **SC-004**: Em 100% dos testes de falha de persistência, não permanece pedido sem evento nem evento sem pedido.
- **SC-005**: Pelo menos 90% dos usuários de teste conseguem criar um pedido válido e recuperar sua representação usando o identificador retornado sem reenviar os dados do produto.
- **SC-006**: Nenhum cenário de validação malsucedida resulta em pedido parcial ou total calculado a partir de dados de produto fornecidos pelo cliente.

## Assumptions

- O cliente envia identificadores de produtos e quantidades inteiras; nome e preço enviados pelo cliente, se presentes, são ignorados.
- Os preços dos dados locais controlados são válidos e usam uma moeda única configurada para o checkout neste incremento; total é calculado sem arredondamento silencioso.
- Uma solicitação com qualquer item inválido é rejeitada por inteiro.
- O evento de criação é um registro persistido localmente para este incremento; não há publicação ou consumo por broker.
- Dados locais controlados são a fonte confiável de produtos neste incremento; se estiverem indisponíveis ou incompletos, a criação falha sem pedido e pode ser repetida após a recuperação.
- A chave de idempotência é obrigatória e única no fluxo de criação de pedidos durante 24 horas; conteúdo diferente associado a uma chave ainda válida é rejeitado.
- Linhas repetidas para o mesmo produto são rejeitadas; o cliente deve enviar no máximo uma linha por produto.
- Objetivo de aprendizagem do incremento: modelar pedidos e snapshots, estabelecer uma fonte local de dados confiáveis e praticar repetição segura e consistência atômica.
- O serviço deve respeitar as restrições de arquitetura e linguagem da constituição do monorepo; as escolhas técnicas concretas serão tratadas no planejamento.
