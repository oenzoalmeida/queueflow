export default function Privacidade() {
  return (
    <main className="legal-page">
      <article className="legal-card">
        <h1>Política de Privacidade</h1>
        <p className="muted">Última atualização: 12 de setembro de 2026 · versão 1.0</p>

        <h2>1. Dados tratados</h2>
        <p>Para contas de acesso são armazenados <strong>nome</strong>, <strong>e-mail</strong> e <strong>senha</strong> (apenas como hash). O uso do sistema registra filas, guichês, senhas emitidas e eventos de atendimento — dados operacionais, sem informação pessoal dos clientes atendidos. Não coletamos dados sensíveis, pagamento ou navegação.</p>

        <h2>2. Finalidades</h2>
        <p>Os dados são usados exclusivamente para autenticação, operação das filas de atendimento e exibição do histórico.</p>

        <h2>3. Base legal</h2>
        <p>Execução de contrato (criação e manutenção da conta) e legítimo interesse na operação da demonstração, conforme a LGPD (Lei nº 13.709/2018).</p>

        <h2>4. Armazenamento e segurança</h2>
        <p>Senhas são armazenadas com hash, o acesso às telas operacionais exige autenticação JWT com papel apropriado e a administração é restrita ao perfil ADMIN.</p>

        <h2>5. Transferência internacional</h2>
        <p>A demonstração é hospedada na Render, com processamento fora do Brasil, nas condições do art. 33 da LGPD.</p>

        <h2>6. Retenção e exclusão</h2>
        <p>Os dados permanecem enquanto a demonstração existir e podem ser removidos a qualquer tempo. Para solicitar exclusão ou correção, use o contato abaixo.</p>

        <h2>7. Compartilhamento</h2>
        <p>Não vendemos nem cedemos dados a terceiros; são utilizados apenas o provedor de hospedagem e os serviços necessários à operação.</p>

        <h2>8. Cookies</h2>
        <p>Utilizamos apenas o token de sessão em armazenamento local, necessário à autenticação. Não há cookies de rastreamento ou publicidade.</p>

        <h2>9. Direitos do titular</h2>
        <p>Nos termos da LGPD, você pode solicitar confirmação de tratamento, acesso, correção, anonimização ou exclusão dos dados pelo contato abaixo.</p>

        <h2>10. Menores de idade</h2>
        <p>O sistema não é direcionado a menores de 16 anos e não coleta intencionalmente seus dados.</p>

        <h2>11. Alterações desta política</h2>
        <p>Esta política pode ser atualizada; a data de revisão no topo indica a versão vigente.</p>

        <h2>12. Contato</h2>
        <p>enzoalmeida.dev@outlook.com.</p>

        <p className="legal-links">
          <a href="/termos">Termos de Uso</a> · <a href="/login">Voltar ao login</a>
        </p>
      </article>
    </main>
  )
}
