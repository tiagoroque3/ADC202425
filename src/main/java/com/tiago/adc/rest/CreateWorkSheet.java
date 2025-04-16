package com.tiago.adc.rest;

import com.google.cloud.datastore.*;
import com.tiago.adc.model.WorkSheetRequest;
import com.tiago.adc.util.SessionUtils;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;

@Path("/createworksheet")
public class CreateWorkSheet {

    private static final String KIND = "WorkSheet";
    private static final List<String> ESTADOS_ADJUDICACAO_VALIDOS = Arrays.asList("ADJUDICADO", "NÃO ADJUDICADO");
    private static final List<String> ESTADOS_OBRA_VALIDOS = Arrays.asList("NÃO INICIADO", "EM CURSO", "CONCLUÍDO");

    
    private static final List<String> TIPOS_ALVO_VALIDOS = Arrays.asList("Propriedade Pública", "Propriedade Privada");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    // Validador auxiliar
    private boolean isValidDate(String date) {
        try {
            DATE_FORMAT.setLenient(false);
            DATE_FORMAT.parse(date);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createWorkSheet(WorkSheetRequest req) {
        try {
            Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

            if (req.sessionToken == null || req.referenciaObra == null) {
                return Response.status(400).entity("{\"erro\": \"Token e referência da obra são obrigatórios\"}").build();
            }

            Entity session = SessionUtils.getSessionEntity(datastore, req.sessionToken);
            if (session == null || !SessionUtils.isSessionValid(datastore, req.sessionToken)) {
                return Response.status(403).entity("{\"erro\": \"Sessão inválida ou expirada\"}").build();
            }

            String requester = session.getString("user");
            Entity requesterEntity = datastore.get(datastore.newKeyFactory().setKind("User").newKey(requester));
            if (requesterEntity == null) {
                return Response.status(403).entity("{\"erro\": \"Utilizador não encontrado\"}").build();
            }

            String role = requesterEntity.getString("role").toLowerCase();
            Key obraKey = datastore.newKeyFactory().setKind(KIND).newKey(req.referenciaObra);
            Entity obra = datastore.get(obraKey);

            if ("partner".equals(role)) {
                // Impedir criação de novas obras por partners
                if (obra == null) {
                    return Response.status(403).entity("{\"erro\": \"Partners não têm permissão para criar novas folhas de obra\"}").build();
                }

                String contaAdjudicada = obra.contains("contaEntidade") ? obra.getString("contaEntidade") : null;
                if (!requester.equals(contaAdjudicada)) {
                    return Response.status(403).entity("{\"erro\": \"Esta obra não está adjudicada à sua conta\"}").build();
                }

                // Verificar se foram enviados campos não permitidos
                if (req.descricao != null || req.tipoAlvoObra != null || req.estadoAdjudicacao != null ||
                    req.dataAdjudicacao != null || req.dataPrevistaInicio != null || req.dataPrevistaConclusao != null ||
                    req.contaEntidade != null || req.entidadeAdjudicacao != null || req.nifEmpresa != null) {
                    return Response.status(403).entity("{\"erro\": \"Utilizadores partner apenas podem alterar estado da obra e observações\"}").build();
                }

                // Validar estadoObra
                if (req.estadoObra != null && !ESTADOS_OBRA_VALIDOS.contains(req.estadoObra.toUpperCase())) {
                    return Response.status(400).entity("{\"erro\": \"Estado de obra inválido\"}").build();
                }

                Entity updated = Entity.newBuilder(obra)
                        .set("estadoObra", req.estadoObra != null ? req.estadoObra.toUpperCase() : obra.getString("estadoObra"))
                        .set("observacoes", req.observacoes != null ? req.observacoes : obra.getString("observacoes"))
                        .build();

                datastore.put(updated);
                return Response.ok("{\"mensagem\": \"Estado da obra atualizado com sucesso\"}").build();
            }



            // Se não é PARTNER, então tem de ser BACKOFFICE
            if (!"backoffice".equals(role)) {
                return Response.status(403).entity("{\"erro\": \"Permissões insuficientes\"}").build();
            }

            // Validação de campos obrigatórios para BACKOFFICE
            if (req.descricao == null || req.tipoAlvoObra == null || req.estadoAdjudicacao == null) {
                return Response.status(400).entity("{\"erro\": \"Campos obrigatórios em falta para BACKOFFICE\"}").build();
            }

            // Validação de tipo alvo
            if (!TIPOS_ALVO_VALIDOS.contains(req.tipoAlvoObra)) {
                return Response.status(400).entity("{\"erro\": \"Tipo de alvo de obra inválido\"}").build();
            }

            if (!ESTADOS_ADJUDICACAO_VALIDOS.contains(req.estadoAdjudicacao.toUpperCase())) {
                return Response.status(400).entity("{\"erro\": \"Estado de adjudicação inválido\"}").build();
            }

            if (req.isAdjudicada()) {
                // Validação de campos obrigatórios em adjudicação
                if (req.dataAdjudicacao == null || req.dataPrevistaInicio == null || req.dataPrevistaConclusao == null ||
                    req.contaEntidade == null || req.contaEntidade.trim().isEmpty() ||
                    req.entidadeAdjudicacao == null || req.nifEmpresa == null ||
                    req.estadoObra == null || !ESTADOS_OBRA_VALIDOS.contains(req.estadoObra.toUpperCase())) {
                    return Response.status(400).entity("{\"erro\": \"Campos de adjudicação em falta ou inválidos\"}").build();
                }

                // Verifica se contaEntidade corresponde a um utilizador partner válido
                Entity partnerEntity = datastore.get(datastore.newKeyFactory().setKind("User").newKey(req.contaEntidade));
                if (partnerEntity == null || !"partner".equalsIgnoreCase(partnerEntity.getString("role"))) {
                    return Response.status(400).entity("{\"erro\": \"Conta de entidade adjudicatária inválida ou não é um parceiro válido\"}").build();
                }

                // Validação de formato de datas
                if (!isValidDate(req.dataAdjudicacao) || !isValidDate(req.dataPrevistaInicio) || !isValidDate(req.dataPrevistaConclusao)) {
                    return Response.status(400).entity("{\"erro\": \"Datas em formato inválido. Use yyyy-MM-dd.\"}").build();
                }
            }


            // Criar builder e manter dados anteriores, se obra já existir
            Entity.Builder builder = Entity.newBuilder(obraKey)
                    .set("referenciaObra", req.referenciaObra)
                    .set("descricao", req.descricao)
                    .set("tipoAlvoObra", req.tipoAlvoObra)
                    .set("estadoAdjudicacao", req.estadoAdjudicacao.toUpperCase());

            if (req.isAdjudicada()) {
                builder
                    .set("dataAdjudicacao", req.dataAdjudicacao)
                    .set("dataPrevistaInicio", req.dataPrevistaInicio)
                    .set("dataPrevistaConclusao", req.dataPrevistaConclusao)
                    .set("contaEntidade", req.contaEntidade)
                    .set("entidadeAdjudicacao", req.entidadeAdjudicacao)
                    .set("nifEmpresa", req.nifEmpresa)
                    .set("estadoObra", req.estadoObra.toUpperCase())
                    .set("observacoes", req.observacoes != null ? req.observacoes : "");
            } else if (obra != null) {
                // Preservar dados anteriores, se obra já existia
                if (obra.contains("dataAdjudicacao")) builder.set("dataAdjudicacao", obra.getString("dataAdjudicacao"));
                if (obra.contains("dataPrevistaInicio")) builder.set("dataPrevistaInicio", obra.getString("dataPrevistaInicio"));
                if (obra.contains("dataPrevistaConclusao")) builder.set("dataPrevistaConclusao", obra.getString("dataPrevistaConclusao"));
                if (obra.contains("contaEntidade")) builder.set("contaEntidade", obra.getString("contaEntidade"));
                if (obra.contains("entidadeAdjudicacao")) builder.set("entidadeAdjudicacao", obra.getString("entidadeAdjudicacao"));
                if (obra.contains("nifEmpresa")) builder.set("nifEmpresa", obra.getString("nifEmpresa"));
                if (obra.contains("estadoObra")) builder.set("estadoObra", obra.getString("estadoObra"));
                if (obra.contains("observacoes")) builder.set("observacoes", obra.getString("observacoes"));
            }

            datastore.put(builder.build());
            return Response.ok("{\"mensagem\": \"Folha de obra registada ou atualizada com sucesso\"}").build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity("{\"erro\": \"Erro interno ao registar folha de obra\"}").build();
        }
    }
}
