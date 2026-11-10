package com.helpdesk.helpdesk.config;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.models.Chamado;
import com.helpdesk.helpdesk.models.Departamento;
import com.helpdesk.helpdesk.models.Prioridade;
import com.helpdesk.helpdesk.models.Role;
import com.helpdesk.helpdesk.models.SolicitacaoTecnico;
import com.helpdesk.helpdesk.models.StatusChamado;
import com.helpdesk.helpdesk.models.StatusSolicitacao;
import com.helpdesk.helpdesk.models.Usuario;
import com.helpdesk.helpdesk.repository.ChamadoRepository;
import com.helpdesk.helpdesk.repository.DepartamentoRepository;
import com.helpdesk.helpdesk.repository.SolicitacaoRepository;
import com.helpdesk.helpdesk.repository.UsuarioRepository;

/**
 * Popula o banco com dados de teste ao iniciar a aplicação.
 *
 * - Só roda no perfil "dev" (@Profile), então nunca executa em produção.
 * - Só insere se o banco estiver vazio, então não duplica dados a cada reinício.
 *
 * Para recriar os dados do zero: apague as tabelas e suba a aplicação de novo.
 */
@Component
@Profile("dev")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    // Senha de todos os usuários de teste. É gravada criptografada com BCrypt.
    private static final String SENHA_PADRAO = "123456";

    private final DepartamentoRepository departamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ChamadoRepository chamadoRepository;
    private final SolicitacaoRepository solicitacaoRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(DepartamentoRepository departamentoRepository,
            UsuarioRepository usuarioRepository,
            ChamadoRepository chamadoRepository,
            SolicitacaoRepository solicitacaoRepository,
            PasswordEncoder passwordEncoder) {
        this.departamentoRepository = departamentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.chamadoRepository = chamadoRepository;
        this.solicitacaoRepository = solicitacaoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (departamentoRepository.count() > 0) {
            log.info("Seed ignorado: o banco já possui dados.");
            return;
        }

        // ---------- Departamentos ----------
        Departamento ti = departamentoRepository.save(new Departamento("Tecnologia da Informação"));
        Departamento rh = departamentoRepository.save(new Departamento("Recursos Humanos"));
        Departamento financeiro = departamentoRepository.save(new Departamento("Financeiro"));
        Departamento administrativo = departamentoRepository.save(new Departamento("Administrativo"));

        // ---------- Usuários (um de cada Role, mais alguns comuns) ----------
        Usuario admin = criarUsuario("Administrador", "admin@helpdesk.com", "Coordenador de TI",
                ti, Role.ADMIN, "00000000001", "ADM001");

        Usuario tecnicoCarlos = criarUsuario("Carlos Souza", "carlos.tecnico@helpdesk.com", "Técnico de Suporte",
                ti, Role.TECHNICIAN, "00000000002", "TEC001");

        Usuario tecnicaMariana = criarUsuario("Mariana Lima", "mariana.tecnica@helpdesk.com", "Técnica de Redes",
                ti, Role.TECHNICIAN, "00000000003", "TEC002");

        Usuario joao = criarUsuario("João Pereira", "joao@helpdesk.com", "Analista de RH",
                rh, Role.USER, "00000000004", "USR001");

        Usuario ana = criarUsuario("Ana Costa", "ana@helpdesk.com", "Analista Financeira",
                financeiro, Role.USER, "00000000005", "USR002");

        Usuario pedro = criarUsuario("Pedro Alves", "pedro@helpdesk.com", "Assistente Administrativo",
                administrativo, Role.USER, "00000000006", "USR003");

        // ---------- Chamados (um em cada status) ----------
        Chamado impressora = new Chamado("Impressora não imprime",
                "A impressora do RH mostra a mensagem de papel atolado, mas não há papel preso.",
                "Hardware", "PAT-000123", "Impressora HP LaserJet", joao, null, rh);
        impressora.setLocal("Bloco A");
        impressora.setSetor("Sala 101");
        impressora.setPrioridade(Prioridade.MEDIA);
        chamadoRepository.save(impressora);

        Chamado internet = new Chamado("Sem acesso à internet",
                "Todos os computadores do financeiro estão sem internet desde as 8h.",
                "Rede", null, null, ana, tecnicaMariana, financeiro);
        internet.setLocal("Bloco B");
        internet.setSetor("Sala 204");
        internet.setStatus(StatusChamado.EM_ANDAMENTO);
        internet.setPrioridade(Prioridade.URGENTE);
        internet.setDataPrevisaoResolucao(LocalDate.now().plusDays(1));
        chamadoRepository.save(internet);

        Chamado sistema = new Chamado("Erro ao gerar relatório",
                "O sistema de folha de pagamento fecha sozinho ao exportar o relatório mensal.",
                "Software", null, null, joao, tecnicoCarlos, rh);
        sistema.setStatus(StatusChamado.RESOLVIDO);
        sistema.setPrioridade(Prioridade.ALTA);
        sistema.setDataPrevisaoResolucao(LocalDate.now().minusDays(2));
        chamadoRepository.save(sistema);

        Chamado mouse = new Chamado("Troca de mouse",
                "O mouse está com o clique duplicando. Solicito a troca.",
                "Hardware", "PAT-000456", "Mouse USB", pedro, tecnicoCarlos, administrativo);
        mouse.setStatus(StatusChamado.FECHADO);
        mouse.setPrioridade(Prioridade.BAIXA);
        mouse.setDetalheLocal("Mesa próxima à janela");
        chamadoRepository.save(mouse);

        // ---------- Solicitações para virar técnico ----------
        // Pendente: aguardando o admin avaliar.
        solicitacaoRepository.save(new SolicitacaoTecnico(pedro));

        // Aprovada: a Mariana pediu e hoje já é técnica.
        SolicitacaoTecnico aprovada = new SolicitacaoTecnico(tecnicaMariana);
        aprovada.setStatus(StatusSolicitacao.APROVADA);
        solicitacaoRepository.save(aprovada);

        log.info("Seed concluído: {} departamentos, {} usuários, {} chamados, {} solicitações. "
                + "Login do admin: {} / senha {}",
                departamentoRepository.count(), usuarioRepository.count(),
                chamadoRepository.count(), solicitacaoRepository.count(),
                admin.getEmail(), SENHA_PADRAO);
    }

    private Usuario criarUsuario(String nome, String email, String ocupacao, Departamento departamento,
            Role role, String cpf, String matricula) {
        Usuario usuario = new Usuario(nome, email, passwordEncoder.encode(SENHA_PADRAO), ocupacao, departamento);
        usuario.setRole(role);
        usuario.setCpf(cpf);
        usuario.setMatricula(matricula);
        return usuarioRepository.save(usuario);
    }
}
