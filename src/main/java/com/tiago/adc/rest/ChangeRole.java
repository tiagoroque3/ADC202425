package com.tiago.adc.rest;

import com.google.cloud.datastore.*;
import com.tiago.adc.model.RoleRequest;
import com.tiago.adc.util.SessionUtils;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Set;

@Path("/changerole")
public class ChangeRole {

    private static final String KIND = "User";
    private static final Set<String> VALID_ROLES = Set.of("admin", "backoffice",  "enduser", "partner");

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response changeRole(RoleRequest req) {
        try {
            Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

            // Validar sessão
            if (!SessionUtils.isSessionValid(datastore, req.sessionToken)) {
                return Response.status(401).entity("{\"erro\": \"Sessão inválida ou expirada\"}").build();
            }

            Entity session = SessionUtils.getSessionEntity(datastore, req.sessionToken);
            if (session == null) {
                return Response.status(401).entity("{\"erro\": \"Sessão não encontrada\"}").build();
            }

            String requesterUsername = session.getString("user");

            // Verifica se quem faz o pedido existe
            Key requesterKey = datastore.newKeyFactory().setKind(KIND).newKey(requesterUsername);
            Entity requester = datastore.get(requesterKey);

            if (requester == null) {
                return Response.status(404).entity("{\"erro\": \"Requester não encontrado\"}").build();
            }

            String requesterRole = requester.getString("role").toLowerCase();

            if (!(requesterRole.equals("admin") || requesterRole.equals("backoffice"))) {
                return Response.status(403).entity("{\"erro\": \"Permissões insuficientes\"}").build();
            }

            // Validar novo role
            String newRole = req.newRole.toLowerCase();
            if (!VALID_ROLES.contains(newRole)) {
                return Response.status(400).entity("{\"erro\": \"Role inválido\"}").build();
            }

            // Buscar o utilizador alvo
            Key targetKey = datastore.newKeyFactory().setKind(KIND).newKey(req.targetUsername);
            Entity target = datastore.get(targetKey);

            if (target == null) {
                return Response.status(404).entity("{\"erro\": \"Utilizador não encontrado\"}").build();
            }

            String currentRole = target.getString("role").toLowerCase();

            // Restrições de BACKOFFICE
            if (requesterRole.equals("backoffice")) {
                boolean currentValid = currentRole.equals("enduser") || currentRole.equals("partner");
                boolean newValid = newRole.equals("enduser") || newRole.equals("partner");

                if (!(currentValid && newValid)) {
                    return Response.status(403).entity("{\"erro\": \"Backoffice só pode alterar entre enduser e partner\"}").build();
                }
            }


            // Atualiza o role
            Entity updated = Entity.newBuilder(target)
                    .set("role", newRole)
                    .build();
            datastore.put(updated);

            return Response.ok("{\"mensagem\": \"Role atualizado com sucesso\"}").build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity("{\"erro\": \"Erro interno\"}").build();
        }
    }

}
