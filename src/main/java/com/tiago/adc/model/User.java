package com.tiago.adc.model;

public class User {
    // Campos obrigatórios
    public String username;
    public String email;
    public String nome;
    public String telefone;
    public String password;
    public String confirmacao;
    public String perfil = "privado"; // público ou privado

    // Atributos opcionais (podem ser atualizados depois)
    public String cc;
    public String nif;
    public String empregador;
    public String funcao;
    public String morada;
    public String nifEntidadeEmpregadora;
    public String foto;

    // Valores iniciais
    public String role = "enduser";
    public String estado = "DESATIVADA";

    public User() {}
}
