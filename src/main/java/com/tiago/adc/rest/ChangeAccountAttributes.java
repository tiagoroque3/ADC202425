package com.tiago.adc.rest;

import com.google.cloud.datastore.*;
import com.tiago.adc.model.ChangeAttributesRequest;
import com.tiago.adc.util.SessionUtils;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.Set;

@Path("/changeattributes")
public class ChangeAccountAttributes {

    private static final String KIND = "User";

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response changeAttributes(ChangeAttributesRequest req) {
        try {
            if (req.token == null || req.targetUsername == null || req.newAttributes == null) {
                return Response.status(400).entity("{\"erro\": \"Campos obrigatórios em falta\"}").build();
            }

            Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

            // Verificar sessão
            if (!SessionUtils.isSessionValid(datastore, req.token)) {
                return Response.status(403).entity("{\"erro\": \"Sessão inválida ou expirada\"}").build();
            }

            Entity session = SessionUtils.getSessionEntity(datastore, req.token);
            if (session == null) {
                return Response.status(403).entity("{\"erro\": \"Sessão não encontrada\"}").build();
            }

            String requesterUsername = session.getString("user");

            Key requesterKey = datastore.newKeyFactory().setKind(KIND).newKey(requesterUsername);
            Key targetKey = datastore.newKeyFactory().setKind(KIND).newKey(req.targetUsername);

            Entity requester = datastore.get(requesterKey);
            Entity target = datastore.get(targetKey);

            if (requester == null || target == null) {
                return Response.status(404).entity("{\"erro\": \"Utilizador não encontrado\"}").build();
            }

            String requesterRole = requester.getString("role").toLowerCase();
            String targetRole = target.getString("role").toLowerCase();
            String targetEstado = target.getString("estado").toUpperCase();

            if (requesterRole.equals("enduser") || requesterRole.equals("partner")) {
                if (!requesterUsername.equals(req.targetUsername)) {
                    return Response.status(403).entity("{\"erro\": \"ENDUSER e PARTNER só podem editar a própria conta\"}").build();
                }
                if (!targetEstado.equals("ATIVADA")) {
                    return Response.status(403).entity("{\"erro\": \"Conta deve estar ativada\"}").build();
                }
                Set<String> proibidos = Set.of("user", "email", "nome", "role", "estado");
                for (String key : req.newAttributes.keySet()) {
                    if (proibidos.contains(key.toLowerCase())) {
                        return Response.status(403).entity("{\"erro\": \"Não pode alterar o campo: " + key + "\"}").build();
                    }
                }

            } else if (requesterRole.equals("backoffice")) {
                if (!(targetRole.equals("enduser") || targetRole.equals("partner"))) {
                    return Response.status(403).entity("{\"erro\": \"Só pode editar contas ENDUSER ou PARTNER\"}").build();
                }
                if (!targetEstado.equals("ATIVADA")) {
                    return Response.status(403).entity("{\"erro\": \"A conta não está ativada\"}").build();
                }
                Set<String> proibidos = Set.of("user", "email");
                for (String key : req.newAttributes.keySet()) {
                    if (proibidos.contains(key.toLowerCase())) {
                        return Response.status(403).entity("{\"erro\": \"Não pode alterar o campo: " + key + "\"}").build();
                    }
                }

            } else if (!requesterRole.equals("admin")) {
                return Response.status(403).entity("{\"erro\": \"Role sem permissões para editar atributos\"}").build();
            }
            

         String novoTelemovel = req.newAttributes.get("telefone");
         if (novoTelemovel != null) {
             // Procurar se existe outro utilizador com o mesmo número
             Query<Entity> query = Query.newEntityQueryBuilder()
                     .setKind(KIND)
                     .setFilter(StructuredQuery.CompositeFilter.and(
                         StructuredQuery.PropertyFilter.eq("telefone", novoTelemovel),
                         StructuredQuery.PropertyFilter.neq("__key__", targetKey) // exclui o próprio
                     ))
                     .build();

             QueryResults<Entity> results = datastore.run(query);
             if (results.hasNext()) {
                 return Response.status(409).entity("{\"erro\": \"Número de telemóvel já está a ser usado por outro utilizador\"}").build();
             }
         }

         Entity.Builder builder = Entity.newBuilder(target);
         for (Map.Entry<String, String> entry : req.newAttributes.entrySet()) {
             builder.set(entry.getKey(), entry.getValue());
         }

         datastore.put(builder.build());
         return Response.ok("{\"mensagem\": \"Atributos atualizados com sucesso\"}").build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity("{\"erro\": \"Erro interno\"}").build();
        }
    }

    
}
