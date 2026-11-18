# ZarKits

Plugin independente de kits para Minecraft Paper 1.21.11, desenvolvido para o ZarMC.

## Compilação automática pelo GitHub

O repositório já inclui um workflow do GitHub Actions. Depois de enviar os arquivos para um repositório GitHub, abra a aba **Actions**. O workflow **Build ZarKits** compila o projeto com Java 21 e Maven e publica o JAR como artefato da execução.

## Compilação local

Requisitos: Java 21 e Maven 3.9 ou superior.

```bash
mvn clean package
```

O arquivo compilado será `target/ZarKits-1.0.0.jar`.

## Instalação no servidor

1. Copie `ZarKits-1.0.0.jar` para a pasta `plugins/` do Paper 1.21.11.
2. Reinicie o servidor.
3. Use `/zarkit create <nome>` para criar um kit.
4. Use `/zarkit edit <nome>` para abrir o editor.
5. Use `/kits` para abrir o menu.

## Comandos

- `/zarkit create <nome>` — cria um kit.
- `/zarkit edit <nome>` — abre o editor do kit.
- `/zarkit delete <nome>` — exclui um kit.
- `/zarkit icon <nome>` — define o material do item na sua mão como ícone.
- `/zarkit cooldown <nome> <segundos>` — define o cooldown.
- `/zarkit permission <nome> <permissao|none>` — define a permissão necessária ou desativa a exigência.
- `/zarkit reload` — recarrega a configuração e os kits.
- `/kits` — abre o menu de kits.

## Estrutura de dados

```text
plugins/ZarKits/
├── config.yml
├── messages.yml
├── kits/
│   └── <nome>.yml
└── playerdata.yml
```

Cada kit é salvo em um YAML individual. Os dados de cooldown e kits de uso único são armazenados em `playerdata.yml`.

## Observação sobre kits antigos

O carregador tenta ler itens serializados em `items.<n>.original` e `items.<n>.item`. A compatibilidade com arquivos exportados por outros plugins depende do formato de serialização e da versão do Paper que os gerou. Faça backup dos arquivos originais antes de migrar.

## Estado do projeto

Esta é uma versão inicial do código-fonte. O JAR ainda precisa ser compilado e testado em um servidor Paper 1.21.11 antes de ser usado em produção.
