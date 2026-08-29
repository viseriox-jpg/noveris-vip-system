# Noveris VIP System — NeoForge 1.21.1

Sistema administrativo de planos VIP, kits configuráveis, itens temporários transferíveis, histórico e cofre de expiração.

## Planos

- `viajante`
- `nobre`
- `regente`
- `soberano`

## Criação de kits

Use `/vip kit criar <nome> <plano>`. A interface possui seis linhas:

- cabeçalho laranja e duas linhas: itens temporários, vinculados ao prazo do VIP;
- cabeçalho azul e duas linhas: itens permanentes;
- botões **SALVAR KIT** e **CANCELAR** no cabeçalho azul.

Os itens usados como modelo voltam para o inventário da staff depois de salvar. Kits existentes podem ser alterados com `/vip kit editar <nome>`.

## Catálogo de escolhas

O kit funciona como pacote-base. Benefícios variáveis são cadastrados uma única vez em categorias reutilizáveis:

1. `/vip catalogo criar arma 1` abre o editor e define que o jogador escolhe uma opção.
2. A área laranja recebe opções temporárias; a azul recebe opções permanentes.
3. `/vip plano catalogo adicionar soberano arma` vincula a categoria ao plano.
4. Ao usar `/vip dar <kit> <player>`, o jogador recebe o kit-base e ganha acesso a `/vip escolher`.

Cada categoria abre separadamente e exige exatamente a quantidade configurada. A entrega só acontece ao confirmar.
Fechar a tela preserva a pendência. Ter um perfil VIP ativo sem uma concessão administrativa não libera escolhas.

## Comandos

- `/vip ajuda` — mostra somente os comandos permitidos, com exemplos clicáveis.
- `/vip kits` — público; lista kits, planos e quantidades.
- `/vip kit ver <nome>` — público; abre uma visualização do kit.
- `/vip kit criar <nome> <plano>`
- `/vip kit editar <nome>`
- `/vip kit listar`
- `/vip kit excluir <nome>`
- `/vip catalogo criar <categoria> <limite>`
- `/vip catalogo editar <categoria> [novo limite]`
- `/vip catalogo listar`
- `/vip catalogo excluir <categoria>`
- `/vip plano catalogo adicionar|remover <plano> <categoria>`
- `/vip escolher` — disponível somente com escolhas liberadas por uma entrega administrativa.
- `/vip escolhas <player> [resetar]`
- `/vip plano listar`
- `/vip plano criar <id> <nome>`
- `/vip plano ativar|desativar <id>`
- `/vip plano renomear <id> <novo nome>`
- `/vip dar <kit> <player> [dias]` — padrão de 30 dias.
- `/vip dar <kit> <player> [dias] confirmar` — substitui um VIP existente conscientemente.
- `/vip renovar <player> <dias>`
- `/vip renovarcomkit <player> <dias>` — renova e entrega o kit novamente.
- `/vip entregarpermanentes <kit> <player>`
- `/vip remover <player> confirmar`
- `/vip daroffline <kit> <nick> [dias]` — entrega quando o jogador entrar.
- `/vip renovaroffline <nick> <dias>`
- `/vip consultar <player>`
- `/vip diagnostico <player>` — resume plano, prazo, itens, cofre, entregas e inconsistências.
- `/vip diagnostico todos` — OP; verifica perfis, kits ausentes, entregas e itens online.
- `/vip reparar <player>` — OP 4; corrige IDs, datas e registros órfãos.
- `/vip testar <kit> <player> [minutos]` — OP 4; teste curto, padrão de um minuto.
- `/vip historico <player> [todos|vip|item|cofre|kit] [pagina]`
- `/vip apagarhistorico <player>` — OP 4; exige confirmação clicável.
- `/vip cofre <player>` — visualização administrativa dos itens expirados.
- `/vip cofre <player> restaurar <slot> <dias>`
- `/vip cofre <player> permanente <slot>`
- `/vip cofre <player> excluir <slot>`

Os comandos administrativos exigem permission level 2. `/vip kits` e `/vip kit ver` ficam disponíveis para todos. O prefixo antigo `/noverisvip` continua funcionando.

## Itens temporários

Cada pilha temporária recebe identificação, proprietário original, kit e data absoluta de expiração. A descrição do item mostra os dias e a data de vencimento. Ele pode ser dropado, recolhido e usado por terceiros sem reiniciar a duração. Drop, recebimento, transferência e expiração entram no histórico dos jogadores envolvidos.

Comidas, poções e outros consumíveis mantêm o funcionamento normal: cada uso reduz a quantidade da pilha e o sistema nunca repõe o item consumido. A quantidade entregue é exatamente a quantidade salva no modelo do kit.

O inventário, armadura, mão secundária e Ender Chest dos jogadores conectados são verificados a cada segundo. Ao vencer, o item é retirado e armazenado no cofre administrativo durante sete dias; depois é eliminado do registro. Um item vencido que permaneceu no chão é recolhido pelo sistema assim que entrar novamente no inventário de um jogador.

Itens dropados passam por verificação periódica. Recipientes vanilla encontrados durante o carregamento de chunks entram em uma fila e são verificados gradualmente, com limite de oito recipientes por tick, evitando atrasar a abertura do mundo. Pilhas divididas recebem novas identificações de rastreamento, mantendo proprietário, kit e vencimento. Integrações com inventários internos de mods dependem de esses inventários exporem seus itens ao Minecraft/NeoForge.

O jogador recebe avisos configuráveis antes do vencimento. Os padrões são 7, 3 e 1 dia. Níveis de permissão e dias de aviso ficam em `serverconfig/noveris_vip_system-server.toml`.
O mesmo arquivo configura `history_retention_days` e `retired_item_retention_days`. A limpeza automática
remove históricos antigos, avisos obsoletos e identificadores aposentados após os prazos definidos.

Os dados ficam em `noveris_vip_system.json`, dentro da pasta do mundo, e sobrevivem a reinicializações.
Identificadores de itens já arquivados também são preservados. Se uma restauração de backup fizer uma cópia
antiga reaparecer, ela é bloqueada e a ocorrência entra no histórico como proteção contra duplicação.

## Plataforma e build

- Minecraft 1.21.1
- NeoForge 21.1.248
- Java 21
- ModDevGradle 2.0.144

Execute `./gradlew build` (`gradlew.bat build` no Windows). O JAR é criado em `build/libs/`. O GitHub Actions também compila cada push e disponibiliza o JAR por 14 dias.
