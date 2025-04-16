package com.tiago.adc.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.datastore.*;
import com.tiago.adc.util.SessionUtils;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/deleteuser")
public class DeleteUser {

    private static final String KIND = "User";
    private static final String SESSION_KIND = "Session";

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteUser(String jsonPayload) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(jsonPayload);

            // Validar campos
            if (!jsonNode.hasNonNull("username") || !jsonNode.hasNonNull("sessionToken")) {
                return Response.status(400).entity("{\"erro\": \"Parâmetros 'username' e/ou 'sessionToken' em falta\"}").build();
            }

            String targetUsername = jsonNode.get("username").asText();
            String sessionToken = jsonNode.get("sessionToken").asText();

            Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

            // Validar sessão
            if (sessionToken.trim().isEmpty()) {
                return Response.status(400).entity("{\"erro\": \"Token de sessão vazio\"}").build();
            }

            if (!SessionUtils.isSessionValid(datastore, sessionToken)) {
                return Response.status(401).entity("{\"erro\": \"Sessão inválida ou expirada\"}").build();
            }

            Entity session = SessionUtils.getSessionEntity(datastore, sessionToken);
            if (session == null) {
                return Response.status(401).entity("{\"erro\": \"Sessão não encontrada\"}").build();
            }

            String requesterUsername = session.getString("user");

            // Obter requester
            Key requesterKey = datastore.newKeyFactory().setKind(KIND).newKey(requesterUsername);
            Entity requester = datastore.get(requesterKey);

            if (requester == null) {
                return Response.status(404).entity("{\"erro\": \"Utilizador da sessão não encontrado\"}").build();
            }

            String requesterRole = requester.getString("role").toLowerCase();

            if (requesterRole.equals("enduser")) {
                return Response.status(403).entity("{\"erro\": \"ENDUSER não tem permissões para apagar utilizadores\"}").build();
            }

            // Obter o utilizador alvo
            Key targetKey = datastore.newKeyFactory().setKind(KIND).newKey(targetUsername);
            Entity target = datastore.get(targetKey);

            if (target == null) {
                return Response.status(404).entity("{\"erro\": \"Utilizador não encontrado\"}").build();
            }

            String targetRole = target.getString("role").toLowerCase();

            // BACKOFFICE só pode apagar enduser ou partner
            if (requesterRole.equals("backoffice") &&
                !(targetRole.equals("enduser") || targetRole.equals("partner"))) {
                return Response.status(403).entity("{\"erro\": \"BACKOFFICE só pode apagar ENDUSER ou PARTNER\"}").build();
            }

            // Apagar sessões associadas ao utilizador alvo
            Query<Entity> sessionQuery = Query.newEntityQueryBuilder()
                .setKind(SESSION_KIND)
                .setFilter(StructuredQuery.PropertyFilter.eq("user", targetUsername))
                .build();

            QueryResults<Entity> userSessions = datastore.run(sessionQuery);
            while (userSessions.hasNext()) {
                datastore.delete(userSessions.next().getKey());
            }

            // Apagar utilizador
            datastore.delete(targetKey);

            return Response.ok("{\"mensagem\": \"Utilizador e sessões apagados com sucesso\"}").build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity("{\"erro\": \"Erro interno\"}").build();
        }
    }
}
