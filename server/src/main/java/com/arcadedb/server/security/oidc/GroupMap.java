package com.arcadedb.server.security.oidc;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GroupMap {
    private Map<String, Group> groups = new HashMap<>();
}
