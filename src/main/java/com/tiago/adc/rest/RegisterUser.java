package com.tiago.adc.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.datastore.*;
import com.tiago.adc.model.User;
import com.tiago.adc.util.RootInitializer;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/register")
public class RegisterUser {

    private static final String KIND = "User";

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response registerUser(String body) {
        RootInitializer.init();

        try {
            Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

            ObjectMapper mapper = new ObjectMapper();
            User user = mapper.readValue(body, User.class);

            if (user.username == null || user.email == null || user.password == null ||
                user.confirmacao == null || user.nome == null || user.telefone == null) {
                return Response.status(400).entity("{\"erro\": \"Campos obrigatórios em falta\"}").build();
            }

            if (!user.email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                return Response.status(400).entity("{\"erro\": \"Email inválido\"}").build();
            }

            if (!user.password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^\\w\\s]).{8,}$")) {
                return Response.status(400).entity("{\"erro\": \"Password inválida: deve conter letras maiúsculas, minúsculas, números e sinais de pontuação\"}").build();
            }

            if (!user.password.equals(user.confirmacao)) {
                return Response.status(400).entity("{\"erro\": \"Password e confirmação não coincidem\"}").build();
            }

            // Impedir que o cliente defina manualmente 'role' ou 'estado'
            if (body.contains("\"role\"") || body.contains("\"estado\"")) {
                return Response.status(400).entity("{\"erro\": \"Não é permitido definir os campos 'role' ou 'estado' manualmente\"}").build();
            }

            // Verificar se já existe utilizador com o mesmo email
            Query<Entity> emailQuery = Query.newEntityQueryBuilder()
                .setKind(KIND)
                .setFilter(StructuredQuery.PropertyFilter.eq("email", user.email))
                .build();
            if (datastore.run(emailQuery).hasNext()) {
                return Response.status(400).entity("{\"erro\": \"Já existe um utilizador com este email\"}").build();
            }

            // Verificar se já existe utilizador com o mesmo número de telefone
            Query<Entity> telefoneQuery = Query.newEntityQueryBuilder()
                .setKind(KIND)
                .setFilter(StructuredQuery.PropertyFilter.eq("telefone", user.telefone))
                .build();
            if (datastore.run(telefoneQuery).hasNext()) {
                return Response.status(400).entity("{\"erro\": \"Já existe um utilizador com este número de telefone\"}").build();
            }

            // Verificar se o username já está em uso
            Key userKey = datastore.newKeyFactory().setKind(KIND).newKey(user.username);
            if (datastore.get(userKey) != null) {
                return Response.status(400).entity("{\"erro\": \"Username já está em uso\"}").build();
            }

            String perfil = user.perfil != null ? user.perfil : "privado";
            String role = "enduser";

            Entity.Builder userBuilder = Entity.newBuilder(userKey)
                .set("email", user.email)
                .set("nome", user.nome)
                .set("telefone", user.telefone)
                .set("password", user.password)
                .set("perfil", perfil)
                .set("role", role)
                .set("estado", "DESATIVADA");

            if (user.cc != null) userBuilder.set("cc", user.cc);
            if (user.nif != null) userBuilder.set("nif", user.nif);
            if (user.empregador != null) userBuilder.set("empregador", user.empregador);
            if (user.funcao != null) userBuilder.set("funcao", user.funcao);
            if (user.morada != null) userBuilder.set("morada", user.morada);
            if (user.nifEntidadeEmpregadora != null) userBuilder.set("nifEntidadeEmpregadora", user.nifEntidadeEmpregadora);
            if (user.foto != null) userBuilder.set("foto", user.foto);

            Entity newUser = userBuilder.build();
            datastore.put(newUser);

            return Response.ok("{\"mensagem\": \"Utilizador registado com sucesso\"}").build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity("{\"erro\": \"Erro interno\"}").build();
        }
    }
}
