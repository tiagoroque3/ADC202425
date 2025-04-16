package com.tiago.adc.rest;

import com.google.cloud.datastore.*;
import com.tiago.adc.model.LogoutRequest;
import com.tiago.adc.util.SessionUtils;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/logout")
public class Logout {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response logoutUser(LogoutRequest req) {
        try {
            if (req == null || req.token == null || req.token.trim().isEmpty()) {
                return Response.status(400).entity("{\"erro\": \"Token obrigatório\"}").build();
            }

            Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

            if (!SessionUtils.isSessionValid(datastore, req.token)) {
                return Response.status(404).entity("{\"erro\": \"Sessão não encontrada ou inválida\"}").build();
            }

            Entity session = SessionUtils.getSessionEntity(datastore, req.token);
            if (session == null || !session.getBoolean("active")) {
                return Response.status(404).entity("{\"erro\": \"Sessão já encerrada\"}").build();
            }

            Entity updated = Entity.newBuilder(session)
                    .set("active", false)
                    .build();

            datastore.put(updated);

            return Response.ok("{\"mensagem\": \"Logout efetuado com sucesso\"}").build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity("{\"erro\": \"Erro interno\"}").build();
        }
    }
}
