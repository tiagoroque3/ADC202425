package com.tiago.adc.model;

public class WorkSheetRequest {
    public String sessionToken;

    // Atributos obrigatórios
    public String referenciaObra;
    public String descricao;
    public String tipoAlvoObra;
    public String estadoAdjudicacao;

    // Atributos adicionais (preenchidos apenas se adjudicado)
    public String dataAdjudicacao;
    public String dataPrevistaInicio;
    public String dataPrevistaConclusao;
    public String contaEntidade;
    public String entidadeAdjudicacao;
    public String nifEmpresa;
    public String estadoObra;
    public String observacoes;

    public boolean isAdjudicada() {
        return estadoAdjudicacao != null && estadoAdjudicacao.equalsIgnoreCase("ADJUDICADO");
    }
}
