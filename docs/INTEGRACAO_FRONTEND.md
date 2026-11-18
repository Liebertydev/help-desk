# Integração do Front-end com a API Help-Desk

Este documento descreve tudo o que o front-end precisa para consumir a API.

## 1. Configuração base

URL padrão da API:

```text
http://localhost:8080
```

Todas as rotas REST ficam abaixo de `/api`.

O CORS aceita por padrão:

```text
http://localhost:5173
http://localhost:3000
```

Para outro endereço, configure `CORS_ORIGENS` no `.env`, separando várias origens por vírgula:

```properties
CORS_ORIGENS=http://localhost:4200,http://127.0.0.1:4200
```

Nas chamadas JSON, use:

```http
Content-Type: application/json
```

Nas rotas protegidas, use:

```http
Authorization: Bearer <token>
```

Exemplo de configuração global no front:

```javascript
const API_URL = "http://localhost:8080";

async function api(path, options = {}) {
  const token = localStorage.getItem("token");

  const headers = {
    "Content-Type": "application/json",
    ...(options.headers || {}),
  };

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers,
  });

  if (response.status === 204) {
    return null;
  }

  const body = await response.json();

  if (!response.ok) {
    throw body;
  }

  return body;
}
```

## 2. Login e sessão

### Login

```http
POST /api/auth/login
```

Pública. Não exige token.

```json
{
  "email": "admin@helpdesk.com",
  "senha": "123456"
}
```

Resposta `200 OK`:

```json
{
  "token": "5752c526-6c1e-4f0b-9a3d-...",
  "usuario": {
    "id": 1,
    "name": "Administrador",
    "email": "admin@helpdesk.com",
    "cpf": null,
    "matricula": null,
    "ocupacao": null,
    "ativo": true,
    "role": "ADMIN",
    "departamentoId": 1,
    "departamentoNome": "Tecnologia da Informação",
    "createdAt": "2026-11-18T15:00:00"
  }
}
```

O front deve guardar `token` e `usuario`. O token deve ser enviado em todas as outras chamadas protegidas.

Exemplo de login:

```javascript
const data = await api("/api/auth/login", {
  method: "POST",
  body: JSON.stringify({
    email: "admin@helpdesk.com",
    senha: "123456",
  }),
});

localStorage.setItem("token", data.token);
localStorage.setItem("usuario", JSON.stringify(data.usuario));
```

### Usuário autenticado

```http
GET /api/auth/me
Authorization: Bearer <token>
```

Retorna `UsuarioResponse`.

### Logout

```http
POST /api/auth/logout
Authorization: Bearer <token>
```

Retorna `204 No Content`. Depois da resposta, remova o token do navegador.

## 3. Permissões

```text
USER
TECHNICIAN
ADMIN
```

Regra geral:

- `USER`: opera seus próprios dados e chamados.
- `TECHNICIAN`: pode listar usuários/técnicos, chamados e atuar em chamados.
- `ADMIN`: possui acesso administrativo completo.
- Endpoints públicos: login, cadastro de usuário e listagem pública de departamentos.

## 4. Erros

Formato padrão:

```json
{
  "timestamp": "2026-11-18T15:30:00",
  "status": 400,
  "erro": "Bad Request",
  "mensagem": "Dados inválidos.",
  "caminho": "/api/usuarios",
  "campos": {
    "email": "E-mail inválido."
  }
}
```

Significado dos campos:

| Campo | Significado |
|---|---|
| `timestamp` | Data e hora do erro |
| `status` | Código HTTP |
| `erro` | Nome HTTP do erro |
| `mensagem` | Mensagem geral |
| `caminho` | Rota chamada |
| `campos` | Erros de validação por campo; pode não existir |

Status HTTP comuns:

| Status | Uso |
|---|---|
| `200` | Requisição realizada com sucesso |
| `201` | Recurso criado; endereço em `Location` |
| `204` | Sucesso sem corpo |
| `400` | Dados inválidos |
| `401` | Sem token, token inválido ou sessão encerrada |
| `403` | Usuário sem permissão |
| `404` | Recurso não encontrado |
| `409` | Conflito de estado ou regra de negócio |

Ao receber `401`, envie o usuário para o login e remova o token.

## 5. Valores enumerados

### Roles

```text
USER
TECHNICIAN
ADMIN
```

### Categorias

```text
HARDWARE
SOFTWARE
REDE
IMPRESSORA
ACESSO
OUTROS
```

`ACESSO` representa senha, login e permissões de sistemas.

### Prioridades

```text
BAIXA
MEDIA
ALTA
URGENTE
```

### Status de chamado

```text
ABERTO
EM_ANDAMENTO
RESOLVIDO
FECHADO
```

### Status de solicitação técnica

```text
PENDENTE
APROVADA
REJEITADA
```

### Tipos de histórico

```text
ABERTURA
COMENTARIO
STATUS
TECNICO
```

## 6. Formato dos recursos

### UsuarioResponse

```json
{
  "id": 1,
  "name": "Nome",
  "email": "email@example.com",
  "cpf": "12345678901",
  "matricula": "1234",
  "ocupacao": "Cargo",
  "ativo": true,
  "role": "USER",
  "departamentoId": 1,
  "departamentoNome": "Tecnologia da Informação",
  "createdAt": "2026-11-18T15:00:00"
}
```

A senha nunca é devolvida pela API.

### DepartamentoResponse

```json
{
  "id": 1,
  "nome": "Tecnologia da Informação",
  "ativo": true
}
```

### ChamadoResponse

```json
{
  "id": 1,
  "titulo": "Computador não liga",
  "descricao": "O equipamento não liga.",
  "categoria": "HARDWARE",
  "numeroPatrimonio": "PC-123",
  "equipamento": "Computador",
  "local": "Prédio A",
  "sala": "201",
  "detalheLocal": "Mesa 14",
  "status": "ABERTO",
  "prioridade": "ALTA",
  "dataPrevisaoResolucao": "2026-11-20",
  "usuarioId": 4,
  "usuarioNome": "João Pereira",
  "tecnicoId": 2,
  "tecnicoNome": "Carlos Souza",
  "departamentoId": 1,
  "departamentoNome": "Tecnologia da Informação",
  "createdAt": "2026-11-18T15:00:00",
  "updatedAt": "2026-11-18T15:30:00"
}
```

Campos opcionais podem aparecer como `null`.

### HistoricoChamadoResponse

```json
{
  "id": 1,
  "tipo": "COMENTARIO",
  "mensagem": "Já reiniciei o equipamento.",
  "autorId": 4,
  "autorNome": "João Pereira",
  "autorRole": "USER",
  "createdAt": "2026-11-18T15:30:00"
}
```

### SolicitacaoTecnicoResponse

```json
{
  "id": 1,
  "usuarioId": 6,
  "usuarioNome": "Pedro Alves",
  "usuarioEmail": "pedro@helpdesk.com",
  "status": "PENDENTE",
  "avaliadoPorId": null,
  "avaliadoPorNome": null,
  "avaliadoEm": null,
  "observacao": null,
  "createdAt": "2026-11-18T15:00:00",
  "updatedAt": "2026-11-18T15:00:00"
}
```

## 7. Rotas de autenticação

| Método | Rota | Público | Corpo | Resposta |
|---|---|---:|---|---|
| `POST` | `/api/auth/login` | Sim | `LoginRequest` | `LoginResponse` |
| `GET` | `/api/auth/me` | Não | Sem corpo | `UsuarioResponse` |
| `POST` | `/api/auth/logout` | Não | Sem corpo | `204` |

Corpo do login:

```json
{
  "email": "admin@helpdesk.com",
  "senha": "123456"
}
```

## 8. Rotas de departamentos

| Método | Rota | Permissão | Corpo |
|---|---|---|---|
| `GET` | `/api/departamentos` | Pública | Nenhum |
| `GET` | `/api/departamentos?apenasAtivos=true` | Pública | Nenhum |
| `GET` | `/api/departamentos/{id}` | Autenticada | Nenhum |
| `POST` | `/api/departamentos` | `ADMIN` | `DepartamentoRequest` |
| `PUT` | `/api/departamentos/{id}` | `ADMIN` | `DepartamentoRequest` |
| `PATCH` | `/api/departamentos/{id}/desativar` | `ADMIN` | Nenhum |
| `PATCH` | `/api/departamentos/{id}/ativar` | `ADMIN` | Nenhum |

`DepartamentoRequest`:

```json
{
  "nome": "Financeiro"
}
```

Validação:

- `nome`: obrigatório
- máximo: 100 caracteres

Respostas:

- `GET`: `DepartamentoResponse[]`
- `GET por ID`: `DepartamentoResponse`
- `POST`: `201` com `DepartamentoResponse`
- `PUT`: `DepartamentoResponse`
- `desativar/ativar`: `204`

Exemplo no front:

```javascript
const departamentos = await api(
  "/api/departamentos?apenasAtivos=true"
);
```

## 9. Rotas de usuários

| Método | Rota | Permissão | Corpo |
|---|---|---|---|
| `GET` | `/api/usuarios` | `TECHNICIAN` ou `ADMIN` | Nenhum |
| `GET` | `/api/usuarios?role=TECHNICIAN` | `TECHNICIAN` ou `ADMIN` | Nenhum |
| `GET` | `/api/usuarios/{id}` | Ver regras abaixo | Nenhum |
| `POST` | `/api/usuarios` | Pública | `UsuarioRequest` |
| `PUT` | `/api/usuarios/me` | Autenticada | `UsuarioUpdateRequest` |
| `PATCH` | `/api/usuarios/me/senha` | Autenticada | `AlterarSenhaRequest` |
| `PUT` | `/api/usuarios/{id}` | `ADMIN` | `UsuarioUpdateRequest` |
| `PATCH` | `/api/usuarios/{id}/role` | `ADMIN` | `AlterarRoleRequest` |
| `PATCH` | `/api/usuarios/{id}/desativar` | `ADMIN` | Nenhum |
| `PATCH` | `/api/usuarios/{id}/ativar` | `ADMIN` | Nenhum |

Um `USER` só pode consultar os próprios dados em `GET /api/usuarios/{id}`. Técnicos e administradores podem consultar outros usuários.

### Cadastro público

```http
POST /api/usuarios
```

```json
{
  "name": "José Silva",
  "email": "jose@email.com",
  "password": "123456",
  "cpf": "123.456.789-01",
  "matricula": "1234",
  "ocupacao": "Assistente",
  "departamentoId": 1
}
```

Validação:

- `name`: obrigatório; máximo 150 caracteres
- `email`: obrigatório e válido
- `password`: mínimo 6 caracteres; máximo 72 bytes UTF-8
- `cpf`: opcional; aceita 11 dígitos ou CPF mascarado
- `matricula`: opcional; máximo 30 caracteres
- `ocupacao`: opcional; máximo 100 caracteres
- `departamentoId`: obrigatório

Todo cadastro público nasce com `role: "USER"` e `ativo: true`.

Resposta: `201` com `UsuarioResponse`.

### Atualizar dados próprios

```http
PUT /api/usuarios/me
```

```json
{
  "name": "José Silva",
  "email": "jose@email.com",
  "cpf": "12345678901",
  "matricula": "1234",
  "ocupacao": "Assistente",
  "departamentoId": 1
}
```

Senha e role não podem ser alterados nessa rota.

### Atualizar dados de outro usuário

```http
PUT /api/usuarios/{id}
```

Somente `ADMIN`.

Corpo igual ao `UsuarioUpdateRequest` usado em `/me`.

### Alterar própria senha

```http
PATCH /api/usuarios/me/senha
```

```json
{
  "senhaAtual": "123456",
  "novaSenha": "novaSenha123"
}
```

Retorna `204`.

### Alterar role

```http
PATCH /api/usuarios/{id}/role
```

Somente `ADMIN`.

```json
{
  "role": "TECHNICIAN"
}
```

Retorna `UsuarioResponse`.

### Ativar ou desativar usuário

```http
PATCH /api/usuarios/{id}/desativar
PATCH /api/usuarios/{id}/ativar
```

Somente `ADMIN`. Retornam `204`.

## 10. Rotas de chamados

| Método | Rota | Permissão | Corpo |
|---|---|---|---|
| `GET` | `/api/chamados` | `TECHNICIAN` ou `ADMIN` | Nenhum |
| `GET` | `/api/chamados?status=ABERTO` | `TECHNICIAN` ou `ADMIN` | Nenhum |
| `GET` | `/api/chamados/meus` | Autenticada | Nenhum |
| `GET` | `/api/chamados/atribuidos` | `TECHNICIAN` ou `ADMIN` | Nenhum |
| `GET` | `/api/chamados/usuario/{usuarioId}` | `TECHNICIAN` ou `ADMIN` | Nenhum |
| `GET` | `/api/chamados/tecnico/{tecnicoId}` | `TECHNICIAN` ou `ADMIN` | Nenhum |
| `GET` | `/api/chamados/{id}` | Dono, técnico ou `ADMIN` | Nenhum |
| `POST` | `/api/chamados` | Qualquer autenticado | `ChamadoRequest` |
| `PUT` | `/api/chamados/{id}` | Dono, técnico ou `ADMIN` | `ChamadoUpdateRequest` |
| `PATCH` | `/api/chamados/{id}/tecnico` | `TECHNICIAN` ou `ADMIN` | `AtribuirTecnicoRequest` |
| `PATCH` | `/api/chamados/{id}/status` | Ver fluxo abaixo | `AlterarStatusChamadoRequest` |
| `GET` | `/api/chamados/{id}/historico` | Dono, técnico ou `ADMIN` | Nenhum |
| `POST` | `/api/chamados/{id}/comentarios` | Dono, técnico ou `ADMIN` | `ComentarioRequest` |

### Abrir chamado

```http
POST /api/chamados
```

```json
{
  "titulo": "Computador não liga",
  "descricao": "O equipamento não liga após a atualização.",
  "categoria": "HARDWARE",
  "numeroPatrimonio": "PC-123",
  "equipamento": "Computador",
  "local": "Prédio A",
  "sala": "201",
  "detalheLocal": "Mesa 14",
  "prioridade": "ALTA",
  "departamentoId": 1
}
```

Validação:

- `titulo`: obrigatório; máximo 150 caracteres
- `descricao`: obrigatória; máximo 5000 caracteres
- `categoria`: opcional; padrão `OUTROS`
- `numeroPatrimonio`: opcional; máximo 50 caracteres
- `equipamento`: opcional; máximo 100 caracteres
- `local`: opcional; máximo 100 caracteres
- `sala`: opcional; máximo 100 caracteres
- `detalheLocal`: opcional; máximo 255 caracteres
- `prioridade`: opcional; padrão `MEDIA`
- `departamentoId`: opcional; usa o departamento do usuário se omitido

Somente técnico ou administrador pode informar uma prioridade diferente no momento da criação.

Resposta: `201` com `ChamadoResponse`.

### Atualizar chamado

```http
PUT /api/chamados/{id}
```

```json
{
  "titulo": "Computador não liga",
  "descricao": "Descrição atualizada",
  "categoria": "HARDWARE",
  "numeroPatrimonio": "PC-123",
  "equipamento": "Computador",
  "local": "Prédio A",
  "sala": "201",
  "detalheLocal": "Mesa 14",
  "prioridade": "ALTA",
  "dataPrevisaoResolucao": "2026-11-20"
}
```

Todos os campos editáveis devem ser enviados. `dataPrevisaoResolucao` aceita `null` e deve usar o formato de data ISO `AAAA-MM-DD`.

### Atribuir técnico

```http
PATCH /api/chamados/{id}/tecnico
```

Somente `TECHNICIAN` ou `ADMIN`.

```json
{
  "tecnicoId": 2
}
```

Resposta: `ChamadoResponse`.

### Alterar status

```http
PATCH /api/chamados/{id}/status
```

```json
{
  "status": "EM_ANDAMENTO"
}
```

Fluxo:

- técnico e administrador: podem percorrer o fluxo completo;
- dono: após `RESOLVIDO`, pode aceitar como `FECHADO` ou reabrir como `EM_ANDAMENTO`.

Valores:

```text
ABERTO
EM_ANDAMENTO
RESOLVIDO
FECHADO
```

Resposta: `ChamadoResponse`.

### Histórico

```http
GET /api/chamados/{id}/historico
```

Resposta: `HistoricoChamadoResponse[]`.

### Comentário

```http
POST /api/chamados/{id}/comentarios
```

```json
{
  "mensagem": "Já reiniciei o equipamento."
}
```

Validação:

- obrigatório
- máximo 2000 caracteres

Resposta: `201` com `HistoricoChamadoResponse`.

Exemplo no front:

```javascript
const chamados = await api("/api/chamados/meus");

const chamado = await api("/api/chamados", {
  method: "POST",
  body: JSON.stringify({
    titulo: "Impressora indisponível",
    descricao: "A impressora não aceita documentos.",
    categoria: "IMPRESSORA",
    equipamento: "Impressora HP",
    local: "Financeiro",
    sala: "102",
    prioridade: "MEDIA",
  }),
});
```

## 11. Rotas de solicitações técnicas

| Método | Rota | Permissão | Corpo |
|---|---|---|---|
| `GET` | `/api/solicitacoes` | `ADMIN` | Nenhum |
| `GET` | `/api/solicitacoes?status=PENDENTE` | `ADMIN` | Nenhum |
| `GET` | `/api/solicitacoes/minhas` | Autenticada | Nenhum |
| `GET` | `/api/solicitacoes/usuario/{usuarioId}` | `ADMIN` | Nenhum |
| `GET` | `/api/solicitacoes/{id}` | `ADMIN` | Nenhum |
| `POST` | `/api/solicitacoes` | Autenticada | Nenhum |
| `PATCH` | `/api/solicitacoes/{id}/aprovar` | `ADMIN` | Corpo opcional |
| `PATCH` | `/api/solicitacoes/{id}/rejeitar` | `ADMIN` | Corpo obrigatório |

### Criar solicitação para virar técnico

```http
POST /api/solicitacoes
```

Não possui corpo.

Resposta: `201` com `SolicitacaoTecnicoResponse`.

Não pode haver duas solicitações pendentes para o mesmo usuário.

### Aprovar solicitação

```http
PATCH /api/solicitacoes/{id}/aprovar
```

Corpo opcional:

```json
{
  "observacao": "Bem-vindo à equipe!"
}
```

Resposta: `SolicitacaoTecnicoResponse`.

### Rejeitar solicitação

```http
PATCH /api/solicitacoes/{id}/rejeitar
```

Corpo obrigatório:

```json
{
  "motivo": "Ainda não concluiu o treinamento."
}
```

Validação:

- `motivo`: obrigatório
- máximo 500 caracteres

Resposta: `SolicitacaoTecnicoResponse`.

## 12. Usuários de demonstração

Todos possuem a senha:

```text
123456
```

| Nome | E-mail | Role | Departamento |
|---|---|---|---|
| Administrador | `admin@helpdesk.com` | `ADMIN` | Tecnologia da Informação |
| Carlos Souza | `carlos.tecnico@helpdesk.com` | `TECHNICIAN` | Tecnologia da Informação |
| Mariana Lima | `mariana.tecnica@helpdesk.com` | `TECHNICIAN` | Tecnologia da Informação |
| João Pereira | `joao@helpdesk.com` | `USER` | Recursos Humanos |
| Ana Costa | `ana@helpdesk.com` | `USER` | Financeiro |
| Pedro Alves | `pedro@helpdesk.com` | `USER` | Administrativo |

O seed só roda no perfil `dev`, que é o perfil padrão.

## 13. Exemplo de autenticação com fetch

```javascript
async function login(email, senha) {
  const response = await fetch("http://localhost:8080/api/auth/login", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ email, senha }),
  });

  const data = await response.json();

  if (!response.ok) {
    throw data;
  }

  localStorage.setItem("token", data.token);
  localStorage.setItem("usuario", JSON.stringify(data.usuario));

  return data;
}

async function buscarMeusChamados() {
  const token = localStorage.getItem("token");

  const response = await fetch(
    "http://localhost:8080/api/chamados/meus",
    {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    }
  );

  const data = await response.json();

  if (!response.ok) {
    throw data;
  }

  return data;
}

async function logout() {
  const token = localStorage.getItem("token");

  await fetch("http://localhost:8080/api/auth/logout", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });

  localStorage.removeItem("token");
  localStorage.removeItem("usuario");
}
```

## 14. Recomendação de armazenamento no front

Guarde pelo menos:

```javascript
{
  token: "<token>",
  usuario: {
    id: 1,
    name: "Administrador",
    role: "ADMIN",
    ativo: true
  }
}
```

Use `usuario.role` para montar a interface.

Não confie apenas no front para proteger ações: a API verifica a permissão em todas as rotas protegidas.
