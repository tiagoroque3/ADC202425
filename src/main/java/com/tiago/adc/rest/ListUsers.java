package com.tiago.adc.rest;

import com.google.cloud.datastore.*;
import com.tiago.adc.util.SessionUtils;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.*;

@Path("/listusers")
public class ListUsers {

    private static final String KIND = "User";
    private static final List<String> CAMPOS_OBRIGATORIOS = List.of("username", "email", "nome");

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response listUsers(Map<String, String> request) {
        try {
            Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

            String token = request.get("token");
            if (token == null || token.trim().isEmpty()) {
                return Response.status(400).entity("{\"erro\": \"Campo token em falta\"}").build();
            }

            if (!SessionUtils.isSessionValid(datastore, token)) {
                return Response.status(403).entity("{\"erro\": \"Sessão inválida ou expirada\"}").build();
            }

            Entity session = SessionUtils.getSessionEntity(datastore, token);
            if (session == null || !session.contains("user")) {
                return Response.status(403).entity("{\"erro\": \"Sessão inválida\"}").build();
            }

            String requesterUsername = session.getString("user");

            Key requesterKey = datastore.newKeyFactory().setKind(KIND).newKey(requesterUsername);
            Entity requester = datastore.get(requesterKey);
            if (requester == null) {
                return Response.status(403).entity("{\"erro\": \"Utilizador não encontrado\"}").build();
            }

            String requesterRole = requester.getString("role").toLowerCase();

            Query<Entity> query = Query.newEntityQueryBuilder().setKind(KIND).build();
            QueryResults<Entity> results = datastore.run(query);

            List<Map<String, String>> utilizadores = new ArrayList<>();

            while (results.hasNext()) {
                Entity user = results.next();

                String role = user.getString("role").toLowerCase();
                String estado = user.getString("estado").toUpperCase();
                String perfil = user.getString("perfil").toLowerCase();

                boolean podeVer = false;

                if (requesterRole.equals("admin")) {
                    podeVer = true;
                } else if (requesterRole.equals("backoffice")) {
                    podeVer = role.equals("enduser") || role.equals("partner");
                } else if (requesterRole.equals("enduser") || requesterRole.equals("partner")) {
                    podeVer = (role.equals("enduser") || role.equals("partner"))
                              && perfil.equals("publico") && estado.equals("ATIVADA");
                }

                if (!podeVer) continue;

                Map<String, String> userData = new LinkedHashMap<>();

                // Adiciona o username sempre (chave do Entity)
                userData.put("username", user.getKey().getName());

                if (requesterRole.equals("enduser") || requesterRole.equals("partner")) {
                    for (String campo : CAMPOS_OBRIGATORIOS) {
                        if (campo.equals("username")) continue; // já foi adicionado acima
                        String valor = user.contains(campo) && user.getString(campo) != null
                                ? user.getString(campo)
                                : "NOT DEFINED";
                        userData.put(campo, valor);
                    }
                } else {
                    userData.put("user", user.getKey().getName()); 

                 // Lista completa dos possíveis campos
                    List<String> todosOsCampos = List.of(
                        "email", "nome", "telefone", "password", "confirmacao", "perfil",
                        "cc", "nif", "empregador", "funcao", "morada", "nifEntidadeEmpregadora",
                        "foto", "role", "estado"
                    );

                    // Verifica cada campo manualmente
                    for (String campo : todosOsCampos) {
                        String valor = user.contains(campo) && user.getString(campo) != null
                                ? user.getString(campo)
                                : "NOT DEFINED";
                        userData.put(campo, valor);
                    }
                }

                utilizadores.add(userData);
            }

            return Response.ok(utilizadores).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity("{\"erro\": \"Erro interno\"}").build();
        }
    }

}
