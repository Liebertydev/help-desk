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

O `.env` **não vai para o git**: cada pessoa tem o seu. Só é preciso preencher o que for diferente do padrão.

### 4. Subir a aplicação

```bash
sh mvnw spring-boot:run
```

Com **JDK 25**, acrescente `-Djava.version=25` a qualquer comando do Maven:

```bash
sh mvnw spring-boot:run -Djava.version=25
```

> 💡 Para usar `./mvnw` em vez de `sh mvnw`, rode uma vez `chmod +x mvnw`.

Na primeira execução, o **seed** popula o banco com dados de teste (veja abaixo).

### 5. Testes

```bash
sh mvnw test
```

Os testes dos services usam Mockito e não precisam de banco. O `HelpdeskApplicationTests` sobe a aplicação inteira e precisa do PostgreSQL configurado no `.env`.

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

Também são criados 4 departamentos, 4 chamados (um em cada status) e 2 solicitações de técnico (uma pendente, do Pedro, e uma aprovada).

**Para recriar os dados do zero**, apague as tabelas e suba a aplicação de novo:

```sql
DROP TABLE chamado, solicitacao_tecnico, users, departamento;
```

O seed só roda no perfil `dev`. Para rodar sem ele, use `SPRING_PROFILES_ACTIVE=prod`.

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
| Sem token, token inválido ou após o logout | `401 Unauthorized` |
| Logado, mas o role não tem permissão | `403 Forbidden` |

> ⚠️ Os tokens ficam em memória: **ao reiniciar a API, todos precisam fazer login de novo.**
> Usuários desativados perdem o acesso na hora, mesmo com um token válido.

---

## Endpoints

**Legenda de acesso:** 🌐 público (sem login) · 👤 qualquer usuário logado · 🔧 técnico ou admin · 👑 só admin

Rotas com `/me`, `/meus`, `/minhas` e `/atribuidos` sempre agem sobre **o usuário logado**.

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
| `GET` | `/{id}` | 👤 | Busca por id |
| `PUT` | `/me` | 👤 | Edita os próprios dados |
| `PATCH` | `/me/senha` | 👤 | Troca a própria senha. Corpo: `{ senhaAtual, novaSenha }` |
| `PUT` | `/{id}` | 👑 | Edita os dados de qualquer usuário |
| `PATCH` | `/{id}/role` | 👑 | Altera o role. Corpo: `{ role }` |
| `PATCH` | `/{id}/desativar` | 👑 | Desativa |
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
| `PUT` | `/{id}` | 👤 | Edita. Usuário comum só edita os próprios chamados |
| `GET` | `/` | 🔧 | Lista todos (`?status=ABERTO`) |
| `GET` | `/atribuidos` | 🔧 | Fila do técnico logado |
| `GET` | `/usuario/{usuarioId}` | 🔧 | Chamados abertos por um usuário |
| `GET` | `/tecnico/{tecnicoId}` | 🔧 | Chamados atribuídos a um técnico |
| `PATCH` | `/{id}/tecnico` | 🔧 | Atribui ou troca o técnico. Corpo: `{ tecnicoId }` |
| `PATCH` | `/{id}/status` | 🔧 | Muda o status. Corpo: `{ status }` |

<details>
<summary>Exemplo de abertura</summary>

```json
{
  "titulo": "Impressora não imprime",
  "descricao": "Mostra papel atolado, mas não há papel preso.",
  "categoria": "Hardware",
  "numeroPatrimonio": "PAT-000123",
  "equipamento": "Impressora HP LaserJet",
  "local": "Bloco A",
  "setor": "Sala 101",
  "detalheLocal": "Ao lado da porta",
  "prioridade": "ALTA",
  "departamentoId": 2
}
```

Só `titulo` e `descricao` são obrigatórios. Sem `departamentoId`, o chamado vai para o departamento de quem abriu. Sem `prioridade`, fica `MEDIA`.
</details>

### Solicitações de técnico — `/api/solicitacoes`

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/` | 👤 | O usuário logado pede para virar técnico (sem corpo) |
| `GET` | `/minhas` | 👤 | Solicitações do usuário logado |
| `GET` | `/` | 👑 | Lista todas (`?status=PENDENTE`) |
| `GET` | `/usuario/{usuarioId}` | 👑 | Solicitações de um usuário |
| `GET` | `/{id}` | 👑 | Busca por id |
| `PATCH` | `/{id}/aprovar` | 👑 | Aprova e promove o usuário a `TECHNICIAN` |
| `PATCH` | `/{id}/rejeitar` | 👑 | Rejeita |

---

## Regras de negócio

### Valores dos enums

| Enum | Valores |
|---|---|
| `Role` | `USER`, `TECHNICIAN`, `ADMIN` |
| `StatusChamado` | `ABERTO`, `EM_ANDAMENTO`, `RESOLVIDO`, `FECHADO` |
| `Prioridade` | `BAIXA`, `MEDIA`, `ALTA`, `URGENTE` |
| `StatusSolicitacao` | `PENDENTE`, `APROVADA`, `REJEITADA` |

### Fluxo do chamado

```
ABERTO → EM_ANDAMENTO → RESOLVIDO → FECHADO
              ↑______________|
       (reabrir, se a solução não resolveu)
```

- Só é possível ir para `EM_ANDAMENTO` com um técnico atribuído.
- Só usuários `TECHNICIAN` ativos podem ser atribuídos.
- O técnico não pode ser trocado em chamados `RESOLVIDO` ou `FECHADO`.
- `FECHADO` é final: o chamado não muda mais de status e não pode ser editado, **nem pelo admin**.

### Usuários e departamentos

- E-mail, CPF e matrícula são únicos.
- Não é possível cadastrar usuários nem abrir chamados em departamentos desativados.
- Usuários e departamentos nunca são apagados, só **desativados**, porque chamados antigos continuam ligados a eles.
- Senhas são guardadas com **BCrypt** e nunca aparecem nas respostas da API.

### Solicitações de técnico

- Só um usuário `USER` ativo pode solicitar, e apenas uma solicitação `PENDENTE` por vez.
- Solicitações já aprovadas ou rejeitadas não podem ser avaliadas de novo.
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
| `403 Forbidden` | Sem permissão para a ação |
| `404 Not Found` | Registro não encontrado |
| `409 Conflict` | Regra de negócio violada (dado duplicado, transição de status inválida etc.) |
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
