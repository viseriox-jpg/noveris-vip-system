# Noveris VIP System — NeoForge 1.21.1

Sistema administrativo de planos VIP, kits configuráveis, itens temporários transferíveis, histórico e cofre de expiração.

## Planos

- `viajante`
- `nobre`
- `regente`
- `soberano`

## Criação de kits

Use `/noverisvip kit criar <nome> <plano>`. A interface possui seis linhas:

- linhas 1 a 3: itens temporários, vinculados ao prazo do VIP;
- linhas 4 e 5: itens permanentes;
- linha 6: instruções, botão **SALVAR KIT** e botão **CANCELAR**.

Os itens usados como modelo voltam para o inventário da staff depois de salvar. Kits existentes podem ser alterados com `/noverisvip kit editar <nome>`.

## Comandos

- `/noverisvip kit criar <nome> <plano>`
- `/noverisvip kit editar <nome>`
- `/noverisvip kit listar`
- `/noverisvip kit excluir <nome>`
- `/noverisvip dar <kit> <player> [dias]` — padrão de 30 dias.
- `/noverisvip renovar <player> <dias>`
- `/noverisvip consultar <player>`
- `/noverisvip historico <player>`
- `/noverisvip cofre <player>` — visualização administrativa dos itens expirados.

Todos os comandos exigem permission level 2 por padrão.

## Itens temporários

Cada item recebe UUID exclusivo, proprietário original, kit e data absoluta de expiração. Ele pode ser dropado, recolhido e usado por terceiros sem reiniciar a duração. Drop, recebimento, transferência e expiração entram no histórico dos jogadores envolvidos.

O inventário, armadura, mão secundária e Ender Chest dos jogadores conectados são verificados a cada segundo. Ao vencer, o item é retirado e armazenado no cofre administrativo durante sete dias; depois é eliminado do registro. Um item vencido que permaneceu no chão é recolhido pelo sistema assim que entrar novamente no inventário de um jogador.

Os dados ficam em `noveris_vip_system.json`, dentro da pasta do mundo, e sobrevivem a reinicializações.

## Plataforma e build

- Minecraft 1.21.1
- NeoForge 21.1.248
- Java 21
- ModDevGradle 2.0.144

Execute `./gradlew build` (`gradlew.bat build` no Windows). O JAR é criado em `build/libs/`. O GitHub Actions também compila cada push e disponibiliza o JAR por 14 dias.
