package com.tiago.adc.rest;

import com.google.cloud.datastore.*;
import com.tiago.adc.model.AccountStateRequest;
import com.tiago.adc.util.SessionUtils;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;

@Path("/changeaccountstate")
public class ChangeAccountState {

    private static final String KIND = "User";
    private static final List<String> ESTADOS_VALIDOS = Arrays.asList("ATIVADA", "DESATIVADA", "SUSPENSA");

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response changeAccountState(AccountStateRequest req) {
        try {
            Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

            // Validação básica
            if (req.sessionToken == null || req.targetUsername == null || req.newState == null ||
                req.sessionToken.trim().isEmpty() || req.targetUsername.trim().isEmpty() || req.newState.trim().isEmpty()) {
                return Response.status(400).entity("{\"erro\": \"Campos obrigatórios em falta\"}").build();
            }

            // Validar sessão
            if (!SessionUtils.isSessionValid(datastore, req.sessionToken)) {
                return Response.status(401).entity("{\"erro\": \"Sessão inválida ou expirada\"}").build();
            }

            Entity session = SessionUtils.getSessionEntity(datastore, req.sessionToken);
            if (session == null) {
                return Response.status(401).entity("{\"erro\": \"Sessão não encontrada\"}").build();
            }

            String requesterUsername = session.getString("user");

            // Buscar requester
            Key requesterKey = datastore.newKeyFactory().setKind(KIND).newKey(requesterUsername);
            Entity requester = datastore.get(requesterKey);
            if (requester == null) {
                return Response.status(404).entity("{\"erro\": \"Requester não encontrado\"}").build();
            }

            String requesterRole = requester.getString("role").toLowerCase();

            if (!(requesterRole.equals("admin") || requesterRole.equals("backoffice"))) {
                return Response.status(403).entity("{\"erro\": \"Permissões insuficientes\"}").build();
            }

            // Validar novo estado
            String newState = req.newState.toUpperCase();
            if (!ESTADOS_VALIDOS.contains(newState)) {
                return Response.status(400).entity("{\"erro\": \"Novo estado inválido\"}").build();
            }

            // Buscar alvo
            Key targetKey = datastore.newKeyFactory().setKind(KIND).newKey(req.targetUsername);
            Entity target = datastore.get(targetKey);
            if (target == null) {
                return Response.status(404).entity("{\"erro\": \"Utilizador alvo não encontrado\"}").build();
            }

            String currentState = target.getString("estado").toUpperCase();
            String targetRole = target.getString("role").toLowerCase();

            // Permissões específicas para BACKOFFICE
            if (requesterRole.equals("backoffice")) {
                if (currentState.equals("SUSPENSA")) {
                    return Response.status(403).entity("{\"erro\": \"BACKOFFICE não pode alterar contas em estado SUSPENSA\"}").build();
                }
                if (!(newState.equals("ATIVADA") || newState.equals("DESATIVADA"))) {
                    return Response.status(403).entity("{\"erro\": \"BACKOFFICE só pode mudar entre ATIVADA e DESATIVADA\"}").build();
                }
            }

            if (currentState.equals(newState)) {
                return Response.status(400).entity("{\"erro\": \"Estado atual já é o estado pretendido\"}").build();
            }

            // Atualizar o estado
            Entity updated = Entity.newBuilder(target)
                    .set("estado", newState)
                    .build();
            datastore.put(updated);

            return Response.ok("{\"mensagem\": \"Estado da conta alterado para " + newState + "\"}").build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity("{\"erro\": \"Erro interno\"}").build();
        }
    }


}