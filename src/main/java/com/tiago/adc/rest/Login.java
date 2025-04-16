package com.tiago.adc.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.datastore.*;
import com.tiago.adc.model.LoginRequest;
import com.tiago.adc.model.LoginResponse;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Path("/login")
public class Login {

    private static final String KIND = "User";

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response loginUser(LoginRequest req) {
        try {

            if (req.identificador == null || req.identificador.trim().isEmpty() ||
                req.password == null || req.password.trim().isEmpty()) {
                return Response.status(400).entity("{\"erro\": \"Identificador e password são obrigatórios\"}").build();
            }

            Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
            Entity user = null;

            // Tenta pelo username (como chave primária)
            Key userKey = datastore.newKeyFactory().setKind(KIND).newKey(req.identificador);
            user = datastore.get(userKey);

            // Se não encontrar pelo username, tenta pelo email
            if (user == null) {
                Query<Entity> emailQuery = Query.newEntityQueryBuilder()
                        .setKind(KIND)
                        .setFilter(StructuredQuery.PropertyFilter.eq("email", req.identificador))
                        .build();

                QueryResults<Entity> results = datastore.run(emailQuery);
                if (results.hasNext()) {
                    user = results.next();
                }
            }

            if (user == null) {
                return Response.status(401).entity("{\"erro\": \"Credenciais inválidas\"}").build();
            }

            // Verifica se a conta está ATIVADA
            String estado = user.getString("estado");
            if (!estado.equalsIgnoreCase("ATIVADA")) {
                return Response.status(403).entity("{\"erro\": \"Conta não está ativa. Estado atual: " + estado + "\"}").build();
            }

            String storedPassword = user.getString("password");
            if (!storedPassword.equals(req.password)) {
                return Response.status(401).entity("{\"erro\": \"Credenciais inválidas\"}").build();
            }

            // Validity
            Instant now = Instant.now();
            Instant expires = now.plus(30, ChronoUnit.MINUTES);

            // Verificador aleatório
            String verificador = generateVerifier();

            LoginResponse response = new LoginResponse();
            response.user = user.getKey().getName();
            response.role = user.getString("role");
            response.valid_from = now.toString();
            response.valid_to = expires.toString();
            response.verificador = verificador;
            
         // Invalidar todas as sessões anteriores ativas deste utilizador
            Query<Entity> activeSessionsQuery = Query.newEntityQueryBuilder()
                    .setKind("Session")
                    .setFilter(
                            StructuredQuery.CompositeFilter.and(
                                    StructuredQuery.PropertyFilter.eq("user", user.getKey().getName()),
                                    StructuredQuery.PropertyFilter.eq("active", true)
                            )
                    )
                    .build();

            QueryResults<Entity> activeSessions = datastore.run(activeSessionsQuery);
            while (activeSessions.hasNext()) {
                Entity oldSession = activeSessions.next();
                Entity updatedOldSession = Entity.newBuilder(oldSession)
                        .set("active", false)
                        .build();
                datastore.put(updatedOldSession);
            }


            // Guardar a sessão
            Key sessionKey = datastore.allocateId(datastore.newKeyFactory().setKind("Session").newKey());
            Entity session = Entity.newBuilder(sessionKey)
                    .set("user", response.user)
                    .set("role", response.role)
                    .set("valid_from", response.valid_from)
                    .set("valid_to", response.valid_to)
                    .set("verificador", verificador)
                    .set("active", true)
                    .build();

            datastore.put(session);

            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(response);
            return Response.ok(json).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity("{\"erro\": \"Erro interno\"}").build();
        }
    }

    private String generateVerifier() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getEncoder().encodeToString(randomBytes);
    }
}
