# Conferência das correções 1 a 7

## Resultado automatizado

Validação final em 11/11/2026: **185 testes executados, 0 falhas, 0 erros e 0 ignorados**.
Foram usados Java 21 por parâmetro no comando e PostgreSQL 17 em instância isolada.
O `pom.xml` continua configurado para Java 26.

A execução incluiu serviços, DTOs, contratos HTTP, configuração da inicialização
e testes com transações concorrentes no PostgreSQL. O `HelpdeskApplicationTests`,
que depende do banco configurado no `.env`, ficou fora dessa rodada.

O log local está em `target/validacao-final-itens-4-a-7.log`. Os relatórios individuais
estão em `target/validacao-concorrencia/target/surefire-reports/`.

## O que foi entregue

| Item | Comportamento esperado |
|---|---|
| 1. Solicitações duplicadas | Duas requisições da mesma pessoa deixam somente uma solicitação pendente; a segunda recebe `409`. |
| 2. Desativação de departamento | Cadastro e transferência disputam o mesmo bloqueio da desativação; os dados são verificados depois da espera. |
| 3. Primeiro administrador | Inicialização explícita no perfil `prod`, sem senha padrão, com criação atômica e preservação das contas existentes. |
| 4. Prioridade | `USER` abre com `MEDIA`; outra prioridade recebe `403`. Técnicos e admins podem escolher na abertura. |
| 5. Reativação | Departamento inativo impede reativação, com `409`; a conta permanece inativa. |
| 6. Mudança de perfil | Promoção direta encerra pendências com autor, data e motivo; uma aprovação antiga nunca rebaixa um admin. |
| 7. Senhas | Cadastro e troca aceitam até 72 bytes UTF-8; excesso retorna `400` antes da gravação. |

## Conferência manual sugerida

Use contas e registros de desenvolvimento. As instruções de execução, autenticação
e configuração da inicialização estão no [README](../README.md).

### Prioridade

1. Com token `USER`, abra um chamado sem prioridade: deve retornar `201`, com `MEDIA`.
2. Repita com `prioridade: "URGENTE"`: deve retornar `403`, sem criar chamado.
3. Com token de técnico ou admin, a abertura com `URGENTE` deve retornar `201`.
4. Na edição, somente o técnico responsável ou admin altera prioridade e previsão.

### Reativação

1. Desative um usuário e, sem usuários ativos no seu departamento, desative o departamento.
2. Tente `PATCH /api/usuarios/{id}/ativar`: deve retornar `409` e manter a conta inativa.
3. Edite o usuário ainda inativo, escolhendo um departamento ativo, e repita a ativação:
   deve retornar `204`.

### Solicitações antigas

1. Com um `USER`, crie uma solicitação para virar técnico.
2. Como admin, altere diretamente seu perfil para `TECHNICIAN`: a pendência deve ficar
   `APROVADA`, com identificação do admin e justificativa da alteração direta.
3. Em outra conta com pedido pendente, altere o perfil para `ADMIN`: o pedido deve ficar
   `REJEITADA`, com justificativa de encerramento porque o perfil passou a ser `ADMIN`.
4. Tente aprovar esses pedidos: deve retornar `409` e preservar o perfil já concedido.
5. Pedidos legados ainda pendentes de quem já possui outro perfil também recebem `409`
   na aprovação. Podem ser rejeitados pela rota administrativa com motivo explícito.

### Limite da senha

1. No cadastro ou em `PATCH /api/usuarios/me/senha`, 36 letras `é` ocupam exatamente
   72 bytes e respeitam o limite. Já 37 letras `é` devem retornar `400`.
2. O mesmo limite permite 18 emojis `😀`; 19 devem retornar `400`.
3. A resposta identifica `password` ou `novaSenha` em `campos`.
4. Uma troca recusada mantém a senha e as sessões atuais. A senha não é truncada nem aparada.

## Repetir os testes

Com o JDK configurado e um PostgreSQL exclusivo para testes, defina `TEST_DB_URL`
apontando para `helpdesk_concorrencia_test`, além de `TEST_DB_USERNAME` e
`TEST_DB_PASSWORD`, conforme o README. Esse banco deve estar vazio ou conter apenas
dados descartáveis de testes.

```powershell
.\mvnw.cmd '-Dtest=*ServiceTest,*ControllerTest,*RunnerTest,*ValidacaoTest,SessaoConfigTest,*ConcorrenciaTest' test
```

Sem `TEST_DB_URL`, os testes de concorrência são ignorados. Na validação acima,
essa variável foi configurada e todos eles executaram.
