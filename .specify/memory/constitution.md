<!--
Sync Impact Report
Version change: 1.0.0 → 1.1.0 (MINOR: hexagonal architecture becomes mandatory)
Modified principles:
- II. Arquitetura de Aprendizagem Progressiva (clarified mandatory architecture baseline)
- III. Simplicidade e Padrões com Propósito (hexagonal architecture exempted from optional-pattern rule)
Added principles: VI. Arquitetura Hexagonal como Padrão
Added sections: none
Removed sections: none
Follow-up TODO: confirm the original ratification date.
-->

# Constituição do Monorepo de Checkout

## Princípios Fundamentais

### I. Java para Código de Aplicação

Todo código de aplicação deste monorepo MUST ser implementado em Java. Exemplos,
ferramentas auxiliares e configurações não substituem essa regra para os serviços
e componentes que implementam o sistema de checkout.

### II. Arquitetura de Aprendizagem Progressiva

Cada incremento MUST declarar os conceitos e padrões que pretende ensinar. A
arquitetura MUST evoluir por incrementos, introduzindo complexidade apenas quando
necessária ao objetivo funcional ou pedagógico daquele incremento. A arquitetura
hexagonal é a estrutura obrigatória para todos os serviços desde seu primeiro
incremento; sua adoção não depende do objetivo pedagógico de cada incremento.

### III. Simplicidade e Padrões com Propósito

Soluções simples MUST ser preferidas. Um padrão arquitetural MUST ser adotado
somente quando resolver um problema concreto ou quando seu objetivo de
aprendizagem estiver explicitado no incremento, exceto pela arquitetura
hexagonal, que é obrigatória conforme o Princípio VI. Abstrações especulativas
MUST NOT ser adicionadas antecipadamente.

### IV. Isolamento das Regras de Negócio

Regras de negócio MUST permanecer separadas de frameworks, protocolos, bancos de
dados e outros detalhes externos. As dependências MUST ser organizadas para
preservar essa direção, permitindo que o núcleo de negócio seja compreendido e
testado sem depender desses detalhes.

### V. Confiabilidade Verificada e Repetição Segura

Regras de negócio e fluxos críticos MUST ter testes automatizados, incluindo
cenários de falha relevantes. Operações importantes MUST definir como lidam com
repetições e consistência; em fluxos distribuídos, a repetição segura e a
consistência MUST ser tratadas explicitamente.

### VI. Arquitetura Hexagonal como Padrão

Todo serviço MUST seguir arquitetura hexagonal: o núcleo de domínio e aplicação
contém as regras de negócio e os casos de uso; portas definidas pelo núcleo
expressam as interações de entrada e saída; adaptadores traduzem protocolos e
detalhes externos para essas portas. As dependências MUST apontar dos adaptadores
para o núcleo, e o núcleo MUST NOT depender de frameworks, protocolos ou
adaptadores concretos. Portas MUST representar limites reais de interação, não
ser criadas mecanicamente para cada classe ou chamada interna. Adaptadores locais
ou em memória MAY ser usados nos primeiros incrementos, desde que preservem essa
separação e permitam evolução progressiva.

## Limites Tecnológicos e de Escopo

Esta constituição fixa Java para o código de aplicação, mas não fixa bancos de
dados, brokers, ferramentas de contêiner ou provedores de infraestrutura. Essas
escolhas MUST ser justificadas na especificação e no plano do incremento que
delas depender, incluindo alternativas e trade-offs quando forem decisões
arquiteturais relevantes. Nenhuma tecnologia específica de persistência,
mensageria ou infraestrutura é requisito permanente do monorepo por força desta
constituição.

## Fluxo de Desenvolvimento

Cada incremento MUST registrar seu escopo, objetivo de aprendizagem, decisões
técnicas necessárias e critérios de validação nos artefatos de especificação e
planejamento correspondentes. Decisões arquiteturais relevantes MUST documentar
contexto, alternativas consideradas e trade-offs para que o projeto permaneça
compreensível como material de estudo e portfólio. Revisões MUST verificar a
conformidade com estes princípios e exigir justificativa para exceções.

## Governança

Esta constituição orienta as especificações, planos, implementação e revisões do
monorepo. Uma emenda MUST registrar a motivação e o impacto sobre princípios,
incrementos existentes e artefatos de governança relacionados. A versão MUST
seguir SemVer: MAJOR para remoção ou redefinição incompatível de princípios,
MINOR para novos princípios ou expansão material das regras, e PATCH para
esclarecimentos sem mudança normativa material.

Antes de aprovar um incremento, autores e revisores MUST verificar sua
conformidade com esta constituição. Qualquer exceção MUST ser explícita,
justificada e documentada nos artefatos do incremento; exceções não alteram a
constituição por si mesmas.

**Version**: 1.1.0 | **Ratified**: TODO(RATIFICATION_DATE): data original de adoção desconhecida; confirmar com o responsável pelo projeto | **Last Amended**: 2026-11-19
