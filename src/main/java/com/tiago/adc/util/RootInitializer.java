package com.tiago.adc.util;

import com.google.cloud.datastore.*;

public class RootInitializer {

    public static void init() {
        Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
        Key rootKey = datastore.newKeyFactory().setKind("User").newKey("root");
        Entity root = datastore.get(rootKey);

        if (root == null) {
            Entity newRoot = Entity.newBuilder(rootKey)
                    .set("email", "root@admin.local")
                    .set("nome", "Administrador do Sistema")
                    .set("telefone", "+351000000000")
                    .set("password", "Admin2025!@#")
                    .set("perfil", "privado")
                    .set("role", "admin")
                    .set("estado", "ATIVADA")
                    //.set("cc", "")
                    //.set("nif", "")
                    //.set("empregador", "")
                    .set("funcao", "Administrador")
                    //.set("morada", "")
                    //.set("nifEntidadeEmpregadora", "")
                    //.set("foto", "")
                    .build();
            datastore.put(newRoot);
        }
    }
}
