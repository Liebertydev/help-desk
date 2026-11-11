# Help-Desk

API REST para abertura e acompanhamento de chamados de suporte técnico.
Usuários abrem chamados, técnicos atendem e administradores gerenciam departamentos, usuários e permissões.

**Tecnologias:** Java · Spring Boot 4 · Spring Data JPA (Hibernate) · PostgreSQL · Bean Validation · BCrypt

---

## Sumário

- [Como rodar](#como-rodar)
- [Usuários de teste](#usuários-de-teste)
- [Autenticação](#autenticação)
- [Endpoints](#endpoints)
- [Quem pode fazer o quê](#quem-pode-fazer-o-quê)
- [Regras de negócio](#regras-de-negócio)
- [Erros](#erros)
- [Estrutura do projeto](#estrutura-do-projeto)

---

## Como rodar

### 1. Pré-requisitos

- **JDK 26**, ou **JDK 25** com o ajuste do passo 4.
- **PostgreSQL** rodando em `localhost:5432`.

No macOS, com Homebrew:

```bash
brew install --cask temurin@25
brew install postgresql@17
brew services start postgresql@17
```

### 2. Banco de dados

Crie o banco vazio. As tabelas são criadas automaticamente pelo Hibernate.

```bash
createdb helpdesk
```

### 3. Configuração local (`.env`)

Copie o modelo e ajuste os valores **da sua máquina**:

```bash
cp .env.example .env
```

| Variável | Padrão | Para que serve |
|---|---|---|
| `SERVER_PORT` | `8080` | Porta da API |
| `DB_URL` | `jdbc:postgresql://localhost:5432/helpdesk` | Endereço do banco |
| `DB_USERNAME` | `postgres` | Usuário do banco (no Postgres do Homebrew, é o seu usuário do Mac) |
| `DB_PASSWORD` | *(vazio)* | Senha do banco |
| `CORS_ORIGENS` | `http://localhost:5173,http://localhost:3000` | Endereços do front autorizados a chamar a API |
| `SESSAO_DURACAO` | `PT8H` | Validade fixa da sessão após o login, em ISO-8601 (`PT8H` = 8 horas; `PT30M` = 30 minutos) |
| `SESSAO_INTERVALO_LIMPEZA_MS` | `60000` | Intervalo, em milissegundos, para remover da memória os tokens expirados |

O `.env` **não vai para o git**: cada pessoa tem o seu. Só é preciso preencher o que for diferente do padrão.

### 4. Subir a aplicação

```bash
./mvnw spring-boot:run
```

Com **JDK 25**, acrescente `-Djava.version=25` a qualquer comando do Maven:

```bash
./mvnw spring-boot:run -Djava.version=25
```

> 💡 No Windows, use `mvnw.cmd` no lugar de `./mvnw`.

Na primeira execução, o **seed** popula o banco com dados de teste (veja abaixo).

### 5. Testes

```bash
./mvnw test
```

Os testes unitários dos services usam Mockito e não precisam de banco. O `HelpdeskApplicationTests` sobe a aplicação inteira e precisa do PostgreSQL configurado no `.env`.

Os testes `ChamadoConcorrenciaTest` e `AdministradorConcorrenciaTest` verificam,
com PostgreSQL real, atribuições simultâneas e a proteção do último admin ativo.
Eles só executam quando `TEST_DB_URL` aponta explicitamente para um banco chamado
`helpdesk_concorrencia_test`. Esse banco deve ser exclusivo para testes: suas tabelas
são criadas e apagadas durante a execução. Os testes não importam o `.env` nem rodam o seed.

Exemplo no PowerShell, depois de criar esse banco vazio em uma instância de teste:

```powershell
$env:TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:55439/helpdesk_concorrencia_test'
$env:TEST_DB_USERNAME = 'helpdesk_test'
$env:TEST_DB_PASSWORD = ''
.\mvnw.cmd '-Dtest=*ConcorrenciaTest' test
```

Sem `TEST_DB_URL`, esses testes são ignorados. Os testes unitários e HTTP podem ser
executados sem banco com `-Dtest=*ServiceTest,*ControllerTest,UsuarioRequestValidacaoTest,SessaoConfigTest`.

### Atualizando um banco criado antes da categoria fixa e da sala

O Hibernate (`ddl-auto=update`) cria tabelas e colunas novas sozinho, mas **não renomeia colunas nem converte dados**. Se o seu banco foi criado quando o chamado ainda tinha `setor` e categoria em texto livre, escolha uma opção:

- **Recriar os dados de teste:** apague as tabelas (comando na seção [Usuários de teste](#usuários-de-teste)) e suba a aplicação.
- **Manter os dados:** rode esta migração **antes** de subir a aplicação.

  ```sql
  BEGIN;
  ALTER TABLE chamado RENAME COLUMN setor TO sala;
  UPDATE chamado SET categoria = CASE lower(trim(categoria))
      WHEN 'hardware' THEN 'HARDWARE' WHEN 'software' THEN 'SOFTWARE'
      WHEN 'rede' THEN 'REDE' WHEN 'impressora' THEN 'IMPRESSORA'
      WHEN 'acesso' THEN 'ACESSO' ELSE 'OUTROS' END;
  ALTER TABLE chamado ALTER COLUMN categoria SET NOT NULL;
  COMMIT;
  ```

---

## Usuários de teste

No perfil `dev` (o padrão), o `DataSeeder` cria os dados abaixo **se o banco estiver vazio**.
Todos os usuários têm a senha **`123456`**.

| Nome | E-mail | Role | Departamento |
|---|---|---|---|
| Administrador | `admin@helpdesk.com` | `ADMIN` | Tecnologia da Informação |
| Carlos Souza | `carlos.tecnico@helpdesk.com` | `TECHNICIAN` | Tecnologia da Informação |
| Mariana Lima | `mariana.tecnica@helpdesk.com` | `TECHNICIAN` | Tecnologia da Informação |
| João Pereira | `joao@helpdesk.com` | `USER` | Recursos Humanos |
| Ana Costa | `ana@helpdesk.com` | `USER` | Financeiro |
| Pedro Alves | `pedro@helpdesk.com` | `USER` | Administrativo |

Também são criados:

- 4 departamentos;
- 4 chamados, um em cada status, já com **histórico** (abertura, atribuição, mudanças de status e comentários);
- 2 solicitações de técnico: uma pendente, do Pedro, e uma aprovada pelo admin, da Mariana.

**Para recriar os dados do zero**, apague as tabelas e suba a aplicação de novo:

```sql
DROP TABLE historico_chamado, chamado, solicitacao_tecnico, users, departamento;
```

O seed só roda no perfil `dev`, que também mostra no console cada SQL executado (configurado em `application-dev.properties`). Para rodar sem os dois, use `SPRING_PROFILES_ACTIVE=prod`.

---

## Autenticação

1. Faça login para receber um **token**:

   ```http
   POST /api/auth/login
   Content-Type: application/json

   { "email": "admin@helpdesk.com", "senha": "123456" }
   ```

   ```json
   {
     "token": "5752c526-6c1e-4f0b-9a3d-...",
     "usuario": { "id": 1, "name": "Administrador", "role": "ADMIN", "...": "..." }
   }
   ```

2. Envie o token em **todas** as outras requisições:

   ```http
   Authorization: Bearer 5752c526-6c1e-4f0b-9a3d-...
   ```

3. Para sair, chame `POST /api/auth/logout`. O token deixa de valer.

| Situação | Resposta |
|---|---|
| Sem token, token inválido, expirado ou encerrado | `401 Unauthorized` |
| Logado, mas sem permissão para a ação (role ou dono do registro) | `403 Forbidden` |

> ⚠️ Os tokens ficam em memória: **ao reiniciar a API, todos precisam fazer login de novo.**
> Usuários desativados perdem o acesso na hora, mesmo com um token válido.

Cada sessão vence **8 horas depois do login**, por padrão. Fazer requisições não renova
esse prazo. Após o vencimento, a API responde `401` e é necessário fazer login novamente.
A duração pode ser alterada com `SESSAO_DURACAO` e deve ser maior que zero.

O logout encerra apenas o token enviado. **Trocar a senha ou desativar o usuário encerra
todas as sessões dele**, em todos os dispositivos. Depois da troca de senha, inclusive
o dispositivo atual precisa fazer login com a nova senha. Reativar uma conta não
restaura as sessões antigas.

Tokens expirados são recusados imediatamente, mesmo entre duas limpezas. Uma tarefa
periódica também remove da memória aqueles que não forem mais usados. As sessões
continuam locais à instância da API; ainda não há compartilhamento entre servidores.

---

## Endpoints

**Legenda de acesso:** 🌐 público (sem login) · 👤 qualquer usuário logado · 🔧 técnico ou admin · 👑 só admin

Rotas com `/me`, `/meus`, `/minhas` e `/atribuidos` sempre agem sobre **o usuário logado**.
Algumas rotas têm, além do ícone, uma regra sobre **qual registro** a pessoa pode acessar. Ela aparece na descrição e está detalhada em [Quem pode fazer o quê](#quem-pode-fazer-o-quê).

### Autenticação — `/api/auth`

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/login` | 🌐 | Login. Corpo: `{ email, senha }` |
| `POST` | `/logout` | 👤 | Encerra a sessão |
| `GET` | `/me` | 👤 | Dados do usuário logado |

### Departamentos — `/api/departamentos`

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `GET` | `/` | 🌐 | Lista os departamentos (`?apenasAtivos=true`) |
| `GET` | `/{id}` | 👤 | Busca por id |
| `POST` | `/` | 👑 | Cria. Corpo: `{ nome }` |
| `PUT` | `/{id}` | 👑 | Renomeia. Corpo: `{ nome }` |
| `PATCH` | `/{id}/desativar` | 👑 | Desativa |
| `PATCH` | `/{id}/ativar` | 👑 | Reativa |

### Usuários — `/api/usuarios`

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/` | 🌐 | Cadastro. Todo usuário novo nasce `USER` |
| `GET` | `/` | 🔧 | Lista os usuários (`?role=TECHNICIAN`) |
| `GET` | `/{id}` | 👤 | Busca por id. Usuário comum só vê os próprios dados (CPF, matrícula, e-mail) |
| `PUT` | `/me` | 👤 | Edita os próprios dados |
| `PATCH` | `/me/senha` | 👤 | Troca a própria senha. Corpo: `{ senhaAtual, novaSenha }` |
| `PUT` | `/{id}` | 👑 | Edita os dados de qualquer usuário |
| `PATCH` | `/{id}/role` | 👑 | Altera o role. Corpo: `{ role }`. Técnico que deixa de ser `TECHNICIAN` tem os chamados pendentes devolvidos à fila |
| `PATCH` | `/{id}/desativar` | 👑 | Desativa. Se for técnico, os chamados pendentes dele voltam para a fila |
| `PATCH` | `/{id}/ativar` | 👑 | Reativa |

<details>
<summary>Exemplo de cadastro</summary>

```json
{
  "name": "Bia Rocha",
  "email": "bia@email.com",
  "password": "segredo1",
  "cpf": "123.456.789-01",
  "matricula": "USR010",
  "ocupacao": "Analista",
  "departamentoId": 2
}
```

`cpf`, `matricula` e `ocupacao` são opcionais. O e-mail é salvo em minúsculas e o CPF só com os dígitos.
</details>

### Chamados — `/api/chamados`

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/` | 👤 | Abre um chamado. O solicitante é o usuário logado |
| `GET` | `/meus` | 👤 | Chamados que o usuário logado abriu |
| `GET` | `/{id}` | 👤 | Busca por id. Usuário comum só vê os próprios chamados |
| `PUT` | `/{id}` | 👤 | Edita. Dono e responsável editam; prioridade e previsão, só o responsável |
| `PATCH` | `/{id}/status` | 👤 | Muda o status. Corpo: `{ status }`. Responsável: fluxo completo. Dono: só fechar ou reabrir depois de `RESOLVIDO` |
| `GET` | `/{id}/historico` | 👤 | Linha do tempo do chamado (mesma regra de acesso do `GET /{id}`) |
| `POST` | `/{id}/comentarios` | 👤 | Comenta. Corpo: `{ mensagem }`. Não é aceito em chamados `FECHADO` |
| `GET` | `/` | 🔧 | Lista todos (`?status=ABERTO`) |
| `GET` | `/atribuidos` | 🔧 | Fila do técnico logado |
| `GET` | `/usuario/{usuarioId}` | 🔧 | Chamados abertos por um usuário |
| `GET` | `/tecnico/{tecnicoId}` | 🔧 | Chamados atribuídos a um técnico |
| `PATCH` | `/{id}/tecnico` | 🔧 | Corpo: `{ tecnicoId }`. Admin atribui ou transfere; técnico só **assume** chamado sem técnico |

> **Responsável** pelo chamado = qualquer admin, ou o técnico atribuído a ele.

<details>
<summary>Exemplo de abertura</summary>

```json
{
  "titulo": "Impressora não imprime",
  "descricao": "Mostra papel atolado, mas não há papel preso.",
  "categoria": "IMPRESSORA",
  "numeroPatrimonio": "PAT-000123",
  "equipamento": "Impressora HP LaserJet",
  "local": "Bloco A",
  "sala": "Sala 101",
  "detalheLocal": "Ao lado da porta",
  "prioridade": "ALTA",
  "departamentoId": 2
}
```

Só `titulo` e `descricao` são obrigatórios. Sem `departamentoId`, o chamado vai para o departamento de quem abriu. Sem `prioridade`, fica `MEDIA`. Sem `categoria`, fica `OUTROS`.

O endereço físico do problema fica em `local` (bloco ou prédio), `sala` e `detalheLocal`. O `departamento` é a área responsável pelo chamado.
</details>

<details>
<summary>Exemplo de histórico (<code>GET /api/chamados/{id}/historico</code>)</summary>

```json
[
  { "id": 1, "tipo": "ABERTURA",   "mensagem": "Chamado aberto.",
    "autorId": 4, "autorNome": "João Pereira", "autorRole": "USER", "createdAt": "2026-11-10T18:40:01" },
  { "id": 2, "tipo": "TECNICO",    "mensagem": "Técnico atribuído: Carlos Souza.",
    "autorId": 1, "autorNome": "Administrador", "autorRole": "ADMIN", "createdAt": "2026-11-10T18:41:10" },
  { "id": 3, "tipo": "STATUS",     "mensagem": "Status alterado de ABERTO para EM_ANDAMENTO.",
    "autorId": 2, "autorNome": "Carlos Souza", "autorRole": "TECHNICIAN", "createdAt": "2026-11-10T18:42:30" },
  { "id": 4, "tipo": "COMENTARIO", "mensagem": "Resetei sua senha, tente agora.",
    "autorId": 2, "autorNome": "Carlos Souza", "autorRole": "TECHNICIAN", "createdAt": "2026-11-10T18:43:05" }
]
```

Os eventos `ABERTURA`, `STATUS` e `TECNICO` são registrados **automaticamente**. `COMENTARIO` vem do `POST /{id}/comentarios`. O histórico nunca é editado nem apagado.
</details>

### Solicitações de técnico — `/api/solicitacoes`

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/` | 👤 | O usuário logado pede para virar técnico (sem corpo) |
| `GET` | `/minhas` | 👤 | Solicitações do usuário logado |
| `GET` | `/` | 👑 | Lista todas (`?status=PENDENTE`) |
| `GET` | `/usuario/{usuarioId}` | 👑 | Solicitações de um usuário |
| `GET` | `/{id}` | 👑 | Busca por id |
| `PATCH` | `/{id}/aprovar` | 👑 | Aprova e promove o usuário a `TECHNICIAN`. Corpo **opcional**: `{ observacao }` |
| `PATCH` | `/{id}/rejeitar` | 👑 | Rejeita. Corpo **obrigatório**: `{ motivo }` |

A resposta traz quem avaliou e quando (`avaliadoPorId`, `avaliadoPorNome`, `avaliadoEm`) e a `observacao` (o motivo, no caso de rejeição).

---

## Quem pode fazer o quê

### No chamado

| Ação | Admin | Técnico atribuído | Outro técnico | Dono (`USER`) | Outro `USER` |
|---|---|---|---|---|---|
| Ver e ver o histórico | ✅ | ✅ | ✅ | ✅ | ❌ |
| Comentar (se não estiver `FECHADO`) | ✅ | ✅ | ✅ | ✅ | ❌ |
| Editar título, descrição, local etc. | ✅ | ✅ | ❌ | ✅ | ❌ |
| Alterar prioridade e previsão | ✅ | ✅ | ❌ | ❌ | ❌ |
| Mudar status | ✅ | ✅ | ❌ | só de `RESOLVIDO` para `FECHADO` ou `EM_ANDAMENTO` | ❌ |
| Atribuir técnico | ✅ qualquer um, inclusive transferir | ❌ | só assumir para si, se estiver sem técnico | ❌ | ❌ |

### Nos usuários

| Ação | Admin | Técnico | `USER` |
|---|---|---|---|
| Ver dados de outro usuário (`GET /{id}`) | ✅ | ✅ | ❌ (só os próprios) |
| Listar usuários | ✅ | ✅ | ❌ |
| Editar os próprios dados e a senha (`/me`) | ✅ | ✅ | ✅ |
| Editar outro usuário, alterar role, ativar e desativar | ✅ | ❌ | ❌ |

---

## Regras de negócio

### Valores dos enums

| Enum | Valores |
|---|---|
| `Role` | `USER`, `TECHNICIAN`, `ADMIN` |
| `StatusChamado` | `ABERTO`, `EM_ANDAMENTO`, `RESOLVIDO`, `FECHADO` |
| `Prioridade` | `BAIXA`, `MEDIA`, `ALTA`, `URGENTE` |
| `Categoria` | `HARDWARE`, `SOFTWARE`, `REDE`, `IMPRESSORA`, `ACESSO`, `OUTROS` |
| `TipoHistorico` | `ABERTURA`, `COMENTARIO`, `STATUS`, `TECNICO` |
| `StatusSolicitacao` | `PENDENTE`, `APROVADA`, `REJEITADA` |

### Fluxo do chamado

```
ABERTO → EM_ANDAMENTO → RESOLVIDO → FECHADO
              ↑______________|
       (reabrir, se a solução não resolveu)
```

- Só é possível ir para `EM_ANDAMENTO` com um técnico atribuído.
- Depois que o técnico marca `RESOLVIDO`, **o dono do chamado** decide: aceita a solução (`FECHADO`) ou reabre (`EM_ANDAMENTO`). O responsável também pode fazer isso.
- Só usuários `TECHNICIAN` ativos podem ser atribuídos, e o técnico não pode ser trocado em chamados `RESOLVIDO` ou `FECHADO`.
- A atribuição bloqueia o chamado no banco até concluir a transação, incluindo o histórico.
  Se dois técnicos tentarem assumir o mesmo chamado, a segunda tentativa espera a primeira
  terminar e verifica os dados atualizados. Se o chamado já tiver técnico, recebe `409 Conflict`
  com orientação para atualizar a fila. Apenas a atribuição concluída fica no histórico.
  O admin continua podendo transferir chamados. Falhas ao obter o bloqueio também retornam `409`.
- `FECHADO` é final: o chamado não muda mais de status, não pode ser editado e não aceita comentários, **nem do admin**.
- A previsão de resolução não pode ser **alterada** para uma data no passado. Uma previsão antiga que só está sendo mantida na edição é aceita.

### Técnico que deixa de atender

Quando um técnico é **desativado** ou deixa de ser `TECHNICIAN`, inclusive quando é promovido a `ADMIN`:

- os chamados `ABERTO` e `EM_ANDAMENTO` dele ficam **sem técnico** e voltam para a fila;
- os `EM_ANDAMENTO` voltam para `ABERTO`;
- os `RESOLVIDO` e `FECHADO` mantêm o técnico, como registro de quem resolveu. Se um desses for reaberto, volta para `ABERTO`, sem técnico;
- tudo fica registrado no histórico de cada chamado.

### Usuários e departamentos

- E-mail, CPF e matrícula são únicos.
- O sistema sempre precisa ter **pelo menos um admin ativo**: não é possível rebaixar nem desativar o último.
- Alterações de role e desativações usam o mesmo ponto de bloqueio no banco, antes de
  consultar a conta e contar os admins ativos. Esse ponto é a linha do primeiro usuário,
  que nunca é apagada pela aplicação e continua existindo após desativação ou mudança de role.
  Assim, mesmo operações sobre admins diferentes esperam uma pela outra até o fim da transação.
  Depois da espera, a conta é recarregada e a contagem usa o estado atual do banco.
  Se a próxima operação remover o último admin ativo, ela recebe `409 Conflict` e é desfeita.
- Não é possível cadastrar usuários nem abrir chamados em departamentos desativados.
- Um departamento só pode ser desativado quando **não tem usuários ativos**. Antes, é preciso movê-los para outro departamento.
- Usuários e departamentos nunca são apagados, só **desativados**, porque chamados antigos continuam ligados a eles.
- Senhas são guardadas com **BCrypt** e nunca aparecem nas respostas da API.

### Solicitações de técnico

- Só um usuário `USER` ativo pode solicitar, e apenas uma solicitação `PENDENTE` por vez.
- Solicitações já aprovadas ou rejeitadas não podem ser avaliadas de novo.
- A rejeição exige um **motivo**. Toda avaliação registra o admin que avaliou e quando.
- Depois de uma rejeição, o usuário pode solicitar outra vez.

---

## Erros

Todos os erros seguem o mesmo formato:

```json
{
  "timestamp": "2026-11-10T17:36:47.679",
  "status": 409,
  "erro": "Conflict",
  "mensagem": "Já existe um usuário com este e-mail.",
  "caminho": "/api/usuarios"
}
```

Em erros de validação, o campo `campos` traz a mensagem de cada campo inválido:

```json
{
  "status": 400,
  "mensagem": "Existem campos inválidos.",
  "campos": {
    "email": "E-mail inválido.",
    "password": "A senha deve ter entre 6 e 72 caracteres."
  }
}
```

| Status | Quando |
|---|---|
| `400 Bad Request` | Campos inválidos, JSON malformado, enum ou id inválido |
| `401 Unauthorized` | Sem login, token inválido ou credenciais erradas |
| `403 Forbidden` | Sem permissão para a ação (veja [Quem pode fazer o quê](#quem-pode-fazer-o-quê)) |
| `404 Not Found` | Registro ou rota não encontrados |
| `405 Method Not Allowed` | Método HTTP não suportado na rota (ex.: `DELETE` onde só existe `GET`) |
| `409 Conflict` | Regra de negócio violada (dado duplicado, transição de status inválida, último admin etc.) |
| `415 Unsupported Media Type` | Corpo enviado sem `Content-Type: application/json` |
| `500 Internal Server Error` | Erro inesperado (o detalhe fica no log do servidor) |

---

## Estrutura do projeto

```
src/main/java/com/helpdesk/helpdesk/
├── auth/          # @Publico, @PermitirRoles e o interceptor que valida o token
├── config/        # BCrypt, CORS e o seed de dados de teste
├── controller/    # Endpoints REST (só traduzem HTTP ↔ service)
├── dto/           # O que entra e sai da API (records)
├── exception/     # Exceções de negócio e o tratamento global de erros
├── models/        # Entidades JPA e enums
├── repository/    # Acesso ao banco (Spring Data JPA)
└── service/       # Regras de negócio
```
