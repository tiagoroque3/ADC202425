package com.tiago.adc.rest;

import com.google.cloud.datastore.*;
import com.tiago.adc.model.PasswordChangeRequest;
import com.tiago.adc.util.SessionUtils;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/changepassword")
public class ChangePassword {

    private static final String KIND = "User";

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response changePassword(PasswordChangeRequest req) {
        try {
            Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

            if (req.token == null || req.passwordAtual == null || 
                req.novaPassword == null || req.confirmacaoNovaPassword == null) {
                return Response.status(400).entity("{\"erro\": \"Campos obrigatórios em falta\"}").build();
            }

            // Validar sessão
            if (!SessionUtils.isSessionValid(datastore, req.token)) {
                return Response.status(403).entity("{\"erro\": \"Sessão inválida ou expirada\"}").build();
            }

            Entity session = SessionUtils.getSessionEntity(datastore, req.token);
            if (session == null) {
                return Response.status(403).entity("{\"erro\": \"Sessão não encontrada\"}").build();
            }

            String username = session.getString("user");

            Key userKey = datastore.newKeyFactory().setKind(KIND).newKey(username);
            Entity user = datastore.get(userKey);
            if (user == null) {
                return Response.status(404).entity("{\"erro\": \"Utilizador não encontrado\"}").build();
            }

            String currentPassword = user.getString("password");

            if (!currentPassword.equals(req.passwordAtual)) {
                return Response.status(403).entity("{\"erro\": \"Password atual incorreta\"}").build();
            }

            if (!req.novaPassword.equals(req.confirmacaoNovaPassword)) {
                return Response.status(400).entity("{\"erro\": \"Nova password e confirmação não coincidem\"}").build();
            }

            Entity updated = Entity.newBuilder(user)
                    .set("password", req.novaPassword)
                    .build();

            datastore.put(updated);

            return Response.ok("{\"mensagem\": \"Password alterada com sucesso\"}").build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity("{\"erro\": \"Erro interno\"}").build();
        }
    }

}
